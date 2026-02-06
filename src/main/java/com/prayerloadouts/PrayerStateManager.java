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
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Gets the current hidden prayers from the Prayer plugin's config.
     * @return Map of prayer key to hidden value
     */
    public Map<String, String> getCurrentHiddenPrayers(int prayerbook) {
        Map<String, String> hiddenPrayers = new HashMap<>();
        String prefix = LoadoutManager.PRAYER_CONFIG_GROUP + "." + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook;

        for (String key : configManager.getConfigurationKeys(prefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                String value = configManager.getConfiguration(parts[0], parts[1]);
                // Extract the prayer-specific part of the key (after the prefix)
                String prayerKey = parts[1].substring(LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX.length() + 1);
                hiddenPrayers.put(prayerKey, value);
            }
        }

        return hiddenPrayers;
    }

    /**
     * Gets a fingerprint of the current hidden prayers for comparison.
     */
    public String getHiddenPrayersFingerprint(int prayerbook) {
        Map<String, String> hiddenPrayers = getCurrentHiddenPrayers(prayerbook);
        if (hiddenPrayers.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        hiddenPrayers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";"));
        return sb.toString();
    }

    /**
     * Loads hidden prayers from a map into the Prayer plugin's config.
     */
    public void loadHiddenPrayers(Map<String, String> hiddenPrayers, int prayerbook) {
        // Clear current hidden prayers
        String currentPrefix = LoadoutManager.PRAYER_CONFIG_GROUP + "." + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook;
        for (String key : configManager.getConfigurationKeys(currentPrefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Restore hidden prayers from the provided map
        if (hiddenPrayers != null) {
            for (Map.Entry<String, String> entry : hiddenPrayers.entrySet()) {
                String configKey = LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook + entry.getKey();
                configManager.setConfiguration(LoadoutManager.PRAYER_CONFIG_GROUP, configKey, entry.getValue());
            }
        }
    }

    /**
     * Loads prayer filter settings from FilterSettings object.
     * @param filters Filter settings to apply (can be null for defaults)
     * @param onComplete Optional callback to run after varbits are set (on client thread)
     */
    public void loadPrayerFilters(LoadoutData.FilterSettings filters, Runnable onComplete) {
        clientThread.invokeLater(() -> {
            if (filters != null) {
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOWTIER, filters.getBlockLowTier());
                client.setVarbit(VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER, filters.getAllowCombinedTier());
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKHEALING, filters.getBlockHealing());
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL, filters.getBlockLackLevel());
                client.setVarbit(VarbitID.PRAYER_FILTER_BLOCKLOCKED, filters.getBlockLocked());
                client.setVarbit(VarbitID.PRAYER_HIDEFILTERBUTTON, filters.getHideFilterButton());
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
