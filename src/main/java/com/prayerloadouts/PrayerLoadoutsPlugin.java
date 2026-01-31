package com.prayerloadouts;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.prayer.PrayerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDependency(PrayerPlugin.class)
@PluginDescriptor(name = "Prayer Loadouts", description = "Save and load named prayer book arrangements", tags = {
        "prayer", "loadout", "reorder", "preset" })
public class PrayerLoadoutsPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private SkillIconManager skillIconManager;

    @Inject
    private LoadoutManager loadoutManager;

    @Inject
    private LoadoutSerializer loadoutSerializer;

    @Inject
    private PrayerStateManager prayerStateManager;

    @Inject
    private ScheduledExecutorService executor;

    private PrayerLoadoutsPanel panel;
    private NavigationButton navButton;
    private volatile boolean loggedIn = false;

    @Override
    protected void startUp() {
        // Check initial login state and update cache if already logged in
        clientThread.invokeLater(() -> {
            loggedIn = client.getGameState() == GameState.LOGGED_IN;
            if (loggedIn) {
                loadoutManager.updateCachedFilters();
            }
        });

        // Create and register the panel
        panel = new PrayerLoadoutsPanel(this);
        final BufferedImage icon = skillIconManager.getSkillImage(Skill.PRAYER);
        navButton = NavigationButton.builder()
                .tooltip("Prayer Loadouts")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        panel = null;
        navButton = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState state = event.getGameState();
        boolean wasLoggedIn = loggedIn;
        loggedIn = (state == GameState.LOGGED_IN);

        if (wasLoggedIn != loggedIn) {
            refreshPanel();

            // Auto-load last loadout on login (delayed to allow game to fully load)
            if (loggedIn) {
                executor.schedule(() -> clientThread.invokeLater(() -> {
                    // Update cache first to get current state
                    loadoutManager.updateCachedFilters();
                    
                    String lastLoadout = loadoutManager.getLastLoadoutName();
                    if (lastLoadout != null && !lastLoadout.isEmpty()
                            && loadoutManager.getLoadoutNames().contains(lastLoadout)) {
                        // Pass callback to refresh panel after cache is updated
                        boolean success = loadoutManager.loadLoadout(lastLoadout, this::refreshPanel);
                        if (!success) {
                            // Refresh panel even if load failed (cache already updated above)
                            refreshPanel();
                        }
                        // Panel is refreshed via callback if load succeeded
                    } else {
                        // No loadout to load, just refresh panel
                        refreshPanel();
                    }
                }), 2, TimeUnit.SECONDS);
            }
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        // Refresh the panel when the Prayer plugin is enabled or disabled
        if (event.getPlugin() instanceof PrayerPlugin) {
            refreshPanel();
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        int varbitId = event.getVarbitId();
        
        // Update cache when filter varbits change (keeps active loadout detection accurate)
        if (varbitId == VarbitID.PRAYER_FILTER_BLOCKLOWTIER ||
            varbitId == VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER ||
            varbitId == VarbitID.PRAYER_FILTER_BLOCKHEALING ||
            varbitId == VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL ||
            varbitId == VarbitID.PRAYER_FILTER_BLOCKLOCKED ||
            varbitId == VarbitID.PRAYER_HIDEFILTERBUTTON) {
            
            clientThread.invokeLater(() -> {
                loadoutManager.updateCachedFilters();
                refreshPanel();
            });
        }
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isPrayerPluginEnabled() {
        return prayerStateManager.isPrayerPluginEnabled();
    }

    public Set<String> getLoadoutNames() {
        return loadoutManager.getLoadoutNames();
    }

    public String getActiveLoadoutName() {
        return loadoutManager.getActiveLoadoutName(loggedIn);
    }

    public void saveLoadoutFromPanel(String name) {
        clientThread.invokeLater(() -> {
            loadoutManager.saveLoadout(name);
            loadoutManager.updateCachedFilters();
            refreshPanel();
        });
    }

    /**
     * Loads a loadout. Returns false if no data exists for current prayerbook.
     * Uses a CountDownLatch to wait for the client thread result.
     */
    public boolean loadLoadoutFromPanel(String name) {
        final boolean[] success = {false};
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        clientThread.invokeLater(() -> {
            try {
                // Pass a callback that refreshes the panel after cache is updated
                success[0] = loadoutManager.loadLoadout(name, this::refreshPanel);
            } finally {
                latch.countDown();
            }
        });
        
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        
        return success[0];
    }

    public boolean exportLoadoutFromPanel(String name) {
        return loadoutSerializer.exportLoadout(name);
    }

    public void deleteLoadoutFromPanel(String name) {
        loadoutManager.deleteLoadout(name);
        refreshPanel();
    }

    public void renameLoadoutFromPanel(String oldName, String newName) {
        loadoutManager.renameLoadout(oldName, newName);
        refreshPanel();
    }

    public boolean importLoadoutFromPanel(String name) {
        boolean success = loadoutSerializer.importLoadout(name);
        refreshPanel();
        return success;
    }

    public void resetToDefaultsFromPanel() {
        clientThread.invokeLater(() -> {
            loadoutManager.resetToDefaults();
            // Update cache after a brief delay to allow varbits to be set
            executor.schedule(() -> clientThread.invokeLater(() -> {
                loadoutManager.updateCachedFilters();
                refreshPanel();
            }), 150, TimeUnit.MILLISECONDS);
        });
    }

    private void refreshPanel() {
        if (panel != null) {
            panel.rebuild();
        }
    }

}
