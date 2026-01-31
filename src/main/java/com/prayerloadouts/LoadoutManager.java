package com.prayerloadouts;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages loadout operations: save, load, delete, rename, and active detection.
 */
@Singleton
public class LoadoutManager {
    static final String CONFIG_GROUP = "prayerloadouts";
    static final String PRAYER_CONFIG_GROUP = "prayer";
    static final String PRAYER_ORDER_KEY_PREFIX = "prayer_order_book_";
    static final String PRAYER_HIDDEN_KEY_PREFIX = "prayer_hidden_book_";
    private static final String LOADOUT_NAMES_KEY = "loadout_names";
    static final String LAST_LOADOUT_KEY = "last_loadout";

    private final Client client;
    private final ConfigManager configManager;
    private final PrayerStateManager prayerStateManager;

    private volatile String cachedFilterFingerprint = "";
    private volatile int cachedPrayerbook = 0;

    @Inject
    public LoadoutManager(Client client, ConfigManager configManager,
            PrayerStateManager prayerStateManager) {
        this.client = client;
        this.configManager = configManager;
        this.prayerStateManager = prayerStateManager;
    }

    public void updateCachedFilters() {
        int prayerbook = client.getVarbitValue(VarbitID.PRAYERBOOK);
        cachedPrayerbook = prayerbook;
        cachedFilterFingerprint = getCurrentFilterFingerprint();
    }

    private String getCurrentFilterFingerprint() {
        return client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOWTIER) + ","
                + client.getVarbitValue(VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER) + ","
                + client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKHEALING) + ","
                + client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL) + ","
                + client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOCKED) + ","
                + client.getVarbitValue(VarbitID.PRAYER_HIDEFILTERBUTTON);
    }

    public Set<String> getLoadoutNames() {
        String names = configManager.getConfiguration(CONFIG_GROUP, LOADOUT_NAMES_KEY);
        if (names == null || names.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    void saveLoadoutNames(Set<String> names) {
        if (names.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, LOADOUT_NAMES_KEY);
        } else {
            String joined = String.join(",", names);
            configManager.setConfiguration(CONFIG_GROUP, LOADOUT_NAMES_KEY, joined);
        }
    }

    String toSafeKey(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }

    public void saveLoadout(String name) {
        if (client.getGameState() != GameState.LOGGED_IN || !prayerStateManager.isPrayerPluginEnabled()
                || name == null || name.trim().isEmpty()) {
            return;
        }

        int prayerbook = client.getVarbitValue(VarbitID.PRAYERBOOK);
        String safeKey = toSafeKey(name);

        String currentOrder = configManager.getConfiguration(
                PRAYER_CONFIG_GROUP,
                PRAYER_ORDER_KEY_PREFIX + prayerbook);

        // Save order (use "DEFAULT" if no custom order exists)
        String orderValue = (currentOrder == null || currentOrder.isEmpty()) ? "DEFAULT" : currentOrder;
        configManager.setConfiguration(
                CONFIG_GROUP,
                "loadout_" + safeKey + "_order_book_" + prayerbook,
                orderValue);

        // Save display name
        configManager.setConfiguration(
                CONFIG_GROUP,
                "loadout_" + safeKey + "_displayname",
                name);

        // Save hidden prayers and filter settings
        prayerStateManager.saveHiddenPrayers(safeKey, prayerbook);
        prayerStateManager.savePrayerFilters(safeKey, prayerbook);

        // Add to loadout names list
        Set<String> names = getLoadoutNames();
        names.add(name);
        saveLoadoutNames(names);

        // Update last loaded loadout
        configManager.setConfiguration(CONFIG_GROUP, LAST_LOADOUT_KEY, name);
    }

    /**
     * Loads a loadout by name.
     * @return true if successful, false if no data exists for current prayerbook
     */
    public boolean loadLoadout(String name) {
        return loadLoadout(name, null);
    }

    /**
     * Loads a loadout by name with an optional completion callback.
     * @param name Loadout name to load
     * @param onComplete Optional callback to run after loadout is fully loaded (on client thread)
     * @return true if successful, false if no data exists for current prayerbook
     */
    public boolean loadLoadout(String name, Runnable onComplete) {
        if (client.getGameState() != GameState.LOGGED_IN || !prayerStateManager.isPrayerPluginEnabled()) {
            return false;
        }

        int prayerbook = client.getVarbitValue(VarbitID.PRAYERBOOK);
        String safeKey = toSafeKey(name);

        String savedOrder = configManager.getConfiguration(
                CONFIG_GROUP,
                "loadout_" + safeKey + "_order_book_" + prayerbook);

        if (savedOrder == null || savedOrder.isEmpty()) {
            return false;
        }

        // Restore prayer order
        if ("DEFAULT".equals(savedOrder)) {
            configManager.unsetConfiguration(PRAYER_CONFIG_GROUP, PRAYER_ORDER_KEY_PREFIX + prayerbook);
        } else {
            configManager.setConfiguration(
                    PRAYER_CONFIG_GROUP,
                    PRAYER_ORDER_KEY_PREFIX + prayerbook,
                    savedOrder);
        }

        // Restore hidden prayers and filters
        prayerStateManager.loadHiddenPrayers(safeKey, prayerbook);
        // Load filters and update cache after varbits are set
        // Chain the completion callback to run after cache is updated
        prayerStateManager.loadPrayerFilters(safeKey, prayerbook, () -> {
            updateCachedFilters();
            if (onComplete != null) {
                onComplete.run();
            }
        });

        // Update last loaded loadout
        configManager.setConfiguration(CONFIG_GROUP, LAST_LOADOUT_KEY, name);
        return true;
    }

    public void deleteLoadout(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        Set<String> names = getLoadoutNames();
        if (!names.contains(name)) {
            return;
        }

        // Delete all configuration keys for this loadout
        String safeKey = toSafeKey(name);
        String prefix = CONFIG_GROUP + ".loadout_" + safeKey;
        for (String key : configManager.getConfigurationKeys(prefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Remove from loadout names list
        names.remove(name);
        saveLoadoutNames(names);
    }

    public void renameLoadout(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.trim().isEmpty() || newName.trim().isEmpty()) {
            return;
        }

        Set<String> names = getLoadoutNames();
        if (!names.contains(oldName) || names.contains(newName)) {
            return;
        }

        // Rename all configuration keys
        String oldSafeKey = toSafeKey(oldName);
        String newSafeKey = toSafeKey(newName);

        String prefix = CONFIG_GROUP + ".loadout_" + oldSafeKey;
        for (String key : configManager.getConfigurationKeys(prefix)) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                String value = configManager.getConfiguration(parts[0], parts[1]);
                String newKey = parts[1].replaceFirst("loadout_" + oldSafeKey, "loadout_" + newSafeKey);
                configManager.setConfiguration(CONFIG_GROUP, newKey, value);
                configManager.unsetConfiguration(parts[0], parts[1]);
            }
        }

        // Update display name
        configManager.setConfiguration(
                CONFIG_GROUP,
                "loadout_" + newSafeKey + "_displayname",
                newName);

        // Update loadout names list
        names.remove(oldName);
        names.add(newName);
        saveLoadoutNames(names);
    }

    public String getActiveLoadoutName(boolean isLoggedIn) {
        if (!isLoggedIn) {
            return null;
        }

        int prayerbook = cachedPrayerbook;
        String currentOrder = configManager.getConfiguration(
                PRAYER_CONFIG_GROUP,
                PRAYER_ORDER_KEY_PREFIX + prayerbook);
        boolean isDefaultOrder = (currentOrder == null || currentOrder.isEmpty());
        String currentHiddenFingerprint = getHiddenPrayersFingerprint(PRAYER_CONFIG_GROUP,
                PRAYER_HIDDEN_KEY_PREFIX + prayerbook);
        String currentFilterFingerprint = cachedFilterFingerprint;

        // First, check if the last loaded loadout still matches (prioritize it)
        String lastLoadout = getLastLoadoutName();
        if (lastLoadout != null && getLoadoutNames().contains(lastLoadout)) {
            if (loadoutMatchesCurrent(lastLoadout, prayerbook, isDefaultOrder, currentOrder,
                    currentHiddenFingerprint, currentFilterFingerprint)) {
                return lastLoadout;
            }
        }

        // Fall back to finding any matching loadout
        for (String name : getLoadoutNames()) {
            if (loadoutMatchesCurrent(name, prayerbook, isDefaultOrder, currentOrder,
                    currentHiddenFingerprint, currentFilterFingerprint)) {
                return name;
            }
        }

        return null;
    }

    private boolean loadoutMatchesCurrent(String name, int prayerbook, boolean isDefaultOrder,
            String currentOrder, String currentHiddenFingerprint, String currentFilterFingerprint) {
        String safeKey = toSafeKey(name);
        String savedOrder = configManager.getConfiguration(
                CONFIG_GROUP,
                "loadout_" + safeKey + "_order_book_" + prayerbook);

        boolean orderMatches = isDefaultOrder
                ? "DEFAULT".equals(savedOrder)
                : currentOrder.equals(savedOrder);

        if (!orderMatches) {
            return false;
        }

        String savedHiddenFingerprint = getHiddenPrayersFingerprint(
                CONFIG_GROUP,
                "loadout_" + safeKey + "_" + PRAYER_HIDDEN_KEY_PREFIX + prayerbook);

        if (!currentHiddenFingerprint.equals(savedHiddenFingerprint)) {
            return false;
        }

        String savedFilterFingerprint = getSavedFilterFingerprint(safeKey, prayerbook);
        return currentFilterFingerprint.equals(savedFilterFingerprint);
    }

    private String getSavedFilterFingerprint(String safeKey, int prayerbook) {
        String prefix = "loadout_" + safeKey + "_filter_book_" + prayerbook + "_";

        String blockLowTier = configManager.getConfiguration(CONFIG_GROUP, prefix + "blocklowtier");
        String allowCombinedTier = configManager.getConfiguration(CONFIG_GROUP, prefix + "allowcombinedtier");
        String blockHealing = configManager.getConfiguration(CONFIG_GROUP, prefix + "blockhealing");
        String blockLackLevel = configManager.getConfiguration(CONFIG_GROUP, prefix + "blocklacklevel");
        String blockLocked = configManager.getConfiguration(CONFIG_GROUP, prefix + "blocklocked");
        String hideFilterButton = configManager.getConfiguration(CONFIG_GROUP, prefix + "hidefilterbutton");

        // Return fingerprint with defaults (0) for missing values
        return (blockLowTier != null ? blockLowTier : "0") + ","
                + (allowCombinedTier != null ? allowCombinedTier : "0") + ","
                + (blockHealing != null ? blockHealing : "0") + ","
                + (blockLackLevel != null ? blockLackLevel : "0") + ","
                + (blockLocked != null ? blockLocked : "0") + ","
                + (hideFilterButton != null ? hideFilterButton : "0");
    }

    private String getHiddenPrayersFingerprint(String configGroup, String keyPrefix) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> keys = configManager.getConfigurationKeys(configGroup + "." + keyPrefix);
        java.util.Collections.sort(keys);

        for (String key : keys) {
            String[] parts = key.split("\\.", 2);
            if (parts.length == 2) {
                String value = configManager.getConfiguration(parts[0], parts[1]);
                String prayerPart = parts[1].substring(keyPrefix.length());
                sb.append(prayerPart).append("=").append(value).append(";");
            }
        }

        return sb.toString();
    }

    public String getLastLoadoutName() {
        return configManager.getConfiguration(CONFIG_GROUP, LAST_LOADOUT_KEY);
    }

    public void resetToDefaults() {
        if (client.getGameState() != GameState.LOGGED_IN || !prayerStateManager.isPrayerPluginEnabled()) {
            return;
        }

        int prayerbook = client.getVarbitValue(VarbitID.PRAYERBOOK);
        prayerStateManager.resetToDefaults(prayerbook);
        configManager.unsetConfiguration(CONFIG_GROUP, LAST_LOADOUT_KEY);
    }

}
