package com.prayerloadouts;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages loadout operations: save, load, delete, rename, and active detection.
 * All loadouts are stored in a single JSON config key.
 */
@Singleton
public class LoadoutManager {
    static final String CONFIG_GROUP = "prayerloadouts";
    static final String PRAYER_CONFIG_GROUP = "prayer";
    static final String PRAYER_ORDER_KEY_PREFIX = "prayer_order_book_";
    static final String PRAYER_HIDDEN_KEY_PREFIX = "prayer_hidden_book_";
    private static final String LOADOUTS_KEY = "loadouts";
    static final String LAST_LOADOUT_KEY = "last_loadout";

    private static final Type LOADOUTS_TYPE = new TypeToken<Map<String, LoadoutData>>() {}.getType();

    private final Client client;
    private final ConfigManager configManager;
    private final PrayerStateManager prayerStateManager;
    private final Gson gson;

    private volatile String cachedFilterFingerprint = "";
    private volatile int cachedPrayerbook = 0;

    @Inject
    public LoadoutManager(Client client, ConfigManager configManager,
            PrayerStateManager prayerStateManager, Gson gson) {
        this.client = client;
        this.configManager = configManager;
        this.prayerStateManager = prayerStateManager;
        this.gson = gson;
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

    /**
     * Gets all loadouts from config.
     */
    public Map<String, LoadoutData> getAllLoadouts() {
        String json = configManager.getConfiguration(CONFIG_GROUP, LOADOUTS_KEY);
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, LoadoutData> loadouts = gson.fromJson(json, LOADOUTS_TYPE);
            return loadouts != null ? loadouts : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Saves all loadouts to config.
     */
    private void saveAllLoadouts(Map<String, LoadoutData> loadouts) {
        if (loadouts.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, LOADOUTS_KEY);
        } else {
            String json = gson.toJson(loadouts, LOADOUTS_TYPE);
            configManager.setConfiguration(CONFIG_GROUP, LOADOUTS_KEY, json);
        }
    }

    /**
     * Gets a single loadout by name.
     */
    public LoadoutData getLoadout(String name) {
        return getAllLoadouts().get(name);
    }

    public Set<String> getLoadoutNames() {
        return getAllLoadouts().keySet();
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

        // Get or create loadout data
        Map<String, LoadoutData> loadouts = getAllLoadouts();
        LoadoutData loadout = loadouts.getOrDefault(name, new LoadoutData(name));
        loadout.setDisplayName(name);

        // Save prayer order
        String currentOrder = configManager.getConfiguration(
                PRAYER_CONFIG_GROUP,
                PRAYER_ORDER_KEY_PREFIX + prayerbook);
        String orderValue = (currentOrder == null || currentOrder.isEmpty()) ? "DEFAULT" : currentOrder;
        loadout.setPrayerOrder(prayerbook, orderValue);

        // Save filter settings
        LoadoutData.FilterSettings filters = new LoadoutData.FilterSettings(
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOWTIER),
                client.getVarbitValue(VarbitID.PRAYER_FILTER_ALLOWCOMBINEDTIER),
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKHEALING),
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLACKLEVEL),
                client.getVarbitValue(VarbitID.PRAYER_FILTER_BLOCKLOCKED),
                client.getVarbitValue(VarbitID.PRAYER_HIDEFILTERBUTTON)
        );
        loadout.setFilters(prayerbook, filters);

        // Save hidden prayers
        Map<String, String> hiddenPrayers = prayerStateManager.getCurrentHiddenPrayers(prayerbook);
        loadout.setHiddenPrayers(prayerbook, hiddenPrayers);

        // Save to config
        loadouts.put(name, loadout);
        saveAllLoadouts(loadouts);

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

        LoadoutData loadout = getLoadout(name);
        if (loadout == null) {
            return false;
        }

        int prayerbook = client.getVarbitValue(VarbitID.PRAYERBOOK);

        if (!loadout.hasPrayerOrder(prayerbook)) {
            return false;
        }

        String savedOrder = loadout.getPrayerOrder(prayerbook);

        // Restore prayer order
        if ("DEFAULT".equals(savedOrder)) {
            configManager.unsetConfiguration(PRAYER_CONFIG_GROUP, PRAYER_ORDER_KEY_PREFIX + prayerbook);
        } else {
            configManager.setConfiguration(
                    PRAYER_CONFIG_GROUP,
                    PRAYER_ORDER_KEY_PREFIX + prayerbook,
                    savedOrder);
        }

        // Restore hidden prayers
        prayerStateManager.loadHiddenPrayers(loadout.getHiddenPrayers(prayerbook), prayerbook);

        // Load filters and chain the completion callback
        LoadoutData.FilterSettings filters = loadout.getFilters(prayerbook);
        prayerStateManager.loadPrayerFilters(filters, () -> {
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

        Map<String, LoadoutData> loadouts = getAllLoadouts();
        if (!loadouts.containsKey(name)) {
            return;
        }

        loadouts.remove(name);
        saveAllLoadouts(loadouts);
    }

    public void renameLoadout(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.trim().isEmpty() || newName.trim().isEmpty()) {
            return;
        }

        Map<String, LoadoutData> loadouts = getAllLoadouts();
        if (!loadouts.containsKey(oldName) || loadouts.containsKey(newName)) {
            return;
        }

        LoadoutData loadout = loadouts.remove(oldName);
        loadout.setDisplayName(newName);
        loadouts.put(newName, loadout);
        saveAllLoadouts(loadouts);
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
        String currentHiddenFingerprint = prayerStateManager.getHiddenPrayersFingerprint(prayerbook);
        String currentFilterFingerprint = cachedFilterFingerprint;

        // First, check if the last loaded loadout still matches (prioritize it)
        String lastLoadout = getLastLoadoutName();
        Map<String, LoadoutData> loadouts = getAllLoadouts();

        if (lastLoadout != null && loadouts.containsKey(lastLoadout)) {
            if (loadoutMatchesCurrent(loadouts.get(lastLoadout), prayerbook, isDefaultOrder, currentOrder,
                    currentHiddenFingerprint, currentFilterFingerprint)) {
                return lastLoadout;
            }
        }

        // Fall back to finding any matching loadout
        for (Map.Entry<String, LoadoutData> entry : loadouts.entrySet()) {
            if (loadoutMatchesCurrent(entry.getValue(), prayerbook, isDefaultOrder, currentOrder,
                    currentHiddenFingerprint, currentFilterFingerprint)) {
                return entry.getKey();
            }
        }

        return null;
    }

    private boolean loadoutMatchesCurrent(LoadoutData loadout, int prayerbook, boolean isDefaultOrder,
            String currentOrder, String currentHiddenFingerprint, String currentFilterFingerprint) {

        String savedOrder = loadout.getPrayerOrder(prayerbook);
        if (savedOrder == null) {
            return false;
        }

        boolean orderMatches = isDefaultOrder
                ? "DEFAULT".equals(savedOrder)
                : currentOrder.equals(savedOrder);

        if (!orderMatches) {
            return false;
        }

        String savedHiddenFingerprint = getHiddenPrayersFingerprint(loadout.getHiddenPrayers(prayerbook));
        if (!currentHiddenFingerprint.equals(savedHiddenFingerprint)) {
            return false;
        }

        LoadoutData.FilterSettings filters = loadout.getFilters(prayerbook);
        String savedFilterFingerprint = filters != null ? filters.toFingerprint() : "0,0,0,0,0,0";
        return currentFilterFingerprint.equals(savedFilterFingerprint);
    }

    private String getHiddenPrayersFingerprint(Map<String, String> hiddenPrayers) {
        if (hiddenPrayers == null || hiddenPrayers.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        hiddenPrayers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append(";"));
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

    /**
     * Saves a loadout directly (used by import).
     */
    public void saveLoadoutData(String name, LoadoutData loadout) {
        Map<String, LoadoutData> loadouts = getAllLoadouts();
        loadouts.put(name, loadout);
        saveAllLoadouts(loadouts);
    }
}
