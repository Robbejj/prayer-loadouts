package com.prayerloadouts;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.prayer.PrayerPlugin;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Manages prayer-specific state including hidden prayers, filter settings, and
 * UI refresh.
 */
@Singleton
public class PrayerStateManager {

    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final PrayerPlugin prayerPlugin;

    @Inject
    public PrayerStateManager(Client client, ClientThread clientThread,
            ConfigManager configManager, PluginManager pluginManager,
            PrayerPlugin prayerPlugin) {
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.prayerPlugin = prayerPlugin;
    }

    public boolean isPrayerPluginEnabled() {
        return pluginManager.isPluginEnabled(prayerPlugin);
    }

    public void saveHiddenPrayers(String safeKey, int prayerbook) {
        // Clear any existing saved hidden prayers for this loadout
        String loadoutPrefix = LoadoutManager.CONFIG_GROUP + ".loadout_" + safeKey + "_"
                + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook;
        for (String key : configManager.getConfigurationKeys(loadoutPrefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Copy current hidden prayers to loadout storage
        for (String key : configManager.getConfigurationKeys(
                LoadoutManager.PRAYER_CONFIG_GROUP + "." + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                String value = configManager.getConfiguration(parts[0], parts[1]);
                String loadoutKey = "loadout_" + safeKey + "_" + parts[1];
                configManager.setConfiguration(LoadoutManager.CONFIG_GROUP, loadoutKey, value);
            }
        }
    }

    public void loadHiddenPrayers(String safeKey, int prayerbook) {
        // Clear current hidden prayers
        for (String key : configManager.getConfigurationKeys(
                LoadoutManager.PRAYER_CONFIG_GROUP + "." + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Restore hidden prayers from loadout storage
        String prefix = LoadoutManager.CONFIG_GROUP + ".loadout_" + safeKey + "_"
                + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook;
        for (String key : configManager.getConfigurationKeys(prefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                String value = configManager.getConfiguration(parts[0], parts[1]);
                String originalKey = parts[1].substring(("loadout_" + safeKey + "_").length());
                configManager.setConfiguration(LoadoutManager.PRAYER_CONFIG_GROUP, originalKey, value);
            }
        }
    }

    public void savePrayerFilters(String safeKey, int prayerbook) {
        String prefix = "loadout_" + safeKey + "_filter_book_" + prayerbook + "_";

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklowtier",
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOWTIER));

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "allowcombinedtier",
                client.getVarbitValue(VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER));

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blockhealing",
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKHEALING));

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklacklevel",
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL));

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklocked",
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOCKED));

        configManager.setConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "hidefilterbutton",
                client.getVarbitValue(VarbitID.PRAYER_HIDEFILTERBUTTON));
    }

    public void loadPrayerFilters(String safeKey, int prayerbook) {
        loadPrayerFilters(safeKey, prayerbook, null);
    }

    /**
     * Loads prayer filter varbits from saved configuration.
     * @param safeKey Safe key for the loadout
     * @param prayerbook Current prayerbook ID
     * @param onComplete Optional callback to run after varbits are set (on client thread)
     */
    public void loadPrayerFilters(String safeKey, int prayerbook, Runnable onComplete) {
        String prefix = "loadout_" + safeKey + "_filter_book_" + prayerbook + "_";

        Integer blockLowTier = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklowtier", Integer.class);
        Integer allowCombinedTier = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "allowcombinedtier", Integer.class);
        Integer blockHealing = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blockhealing", Integer.class);
        Integer blockLackLevel = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklacklevel", Integer.class);
        Integer blockLocked = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "blocklocked", Integer.class);
        Integer hideFilterButton = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                prefix + "hidefilterbutton", Integer.class);

        // Set all varbits on the client thread, then redraw
        clientThread.invokeLater(() -> {
            if (blockLowTier != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOWTIER, blockLowTier);
            }
            if (allowCombinedTier != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER, allowCombinedTier);
            }
            if (blockHealing != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKHEALING, blockHealing);
            }
            if (blockLackLevel != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL, blockLackLevel);
            }
            if (blockLocked != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOCKED, blockLocked);
            }
            if (hideFilterButton != null) {
                client.setVarbit(VarbitID.PRAYER_HIDEFILTERBUTTON, hideFilterButton);
            }
            
            // Redraw after setting all varbits to update the filter UI
            redrawPrayers();
            
            // Run callback after varbits are set and UI is updated
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void redrawPrayers() {
        Widget prayerWidget = client.getWidget(InterfaceID.PRAYERBOOK, 0);
        if (prayerWidget != null) {
            client.runScript(prayerWidget.getOnVarTransmitListener());
        }
    }

    public void resetToDefaults(int prayerbook) {
        // Clear prayer order configuration
        configManager.unsetConfiguration(
                LoadoutManager.PRAYER_CONFIG_GROUP,
                LoadoutManager.PRAYER_ORDER_KEY_PREFIX + prayerbook);

        // Clear hidden prayers configuration
        for (String key : configManager.getConfigurationKeys(
                LoadoutManager.PRAYER_CONFIG_GROUP + "." + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Reset filter varbits to defaults on the client thread
        clientThread.invokeLater(() -> {
            client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOWTIER, 0);
            client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKHEALING, 0);
            client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL, 0);
            client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOCKED, 0);
            client.setVarbit(VarbitID.PRAYER_HIDEFILTERBUTTON, 0);

            // Redraw to update the filter UI after setting all varbits
            redrawPrayers();
        });
    }
}
