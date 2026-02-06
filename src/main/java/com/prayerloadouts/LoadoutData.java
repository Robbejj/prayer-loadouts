package com.prayerloadouts;

import java.util.HashMap;
import java.util.Map;

/**
 * Data model for a single prayer loadout.
 * Designed for JSON serialization via Gson.
 */
public class LoadoutData {
    /**
     * Display name of the loadout (preserves original capitalization).
     */
    String displayName;

    /**
     * Prayer order strings per prayerbook.
     * Value is "DEFAULT" if using vanilla order, or comma-separated prayer IDs.
     */
    Map<Integer, String> prayerOrders = new HashMap<>();

    /**
     * Filter settings per prayerbook.
     */
    Map<Integer, FilterSettings> filters = new HashMap<>();

    /**
     * Hidden prayers per prayerbook.
     * Maps prayerbook -> (prayer key -> hidden value).
     */
    Map<Integer, Map<String, String>> hiddenPrayers = new HashMap<>();

    public LoadoutData() {
    }

    public LoadoutData(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrayerOrder(int prayerbook) {
        return prayerOrders.get(prayerbook);
    }

    public void setPrayerOrder(int prayerbook, String order) {
        prayerOrders.put(prayerbook, order);
    }

    public boolean hasPrayerOrder(int prayerbook) {
        String order = prayerOrders.get(prayerbook);
        return order != null && !order.isEmpty();
    }

    public FilterSettings getFilters(int prayerbook) {
        return filters.get(prayerbook);
    }

    public void setFilters(int prayerbook, FilterSettings filterSettings) {
        filters.put(prayerbook, filterSettings);
    }

    public Map<String, String> getHiddenPrayers(int prayerbook) {
        return hiddenPrayers.getOrDefault(prayerbook, new HashMap<>());
    }

    public void setHiddenPrayers(int prayerbook, Map<String, String> hidden) {
        hiddenPrayers.put(prayerbook, hidden);
    }

    /**
     * Filter settings for a prayerbook.
     */
    public static class FilterSettings {
        int blockLowTier;
        int allowCombinedTier;
        int blockHealing;
        int blockLackLevel;
        int blockLocked;
        int hideFilterButton;

        public FilterSettings() {
        }

        public FilterSettings(int blockLowTier, int allowCombinedTier, int blockHealing,
                              int blockLackLevel, int blockLocked, int hideFilterButton) {
            this.blockLowTier = blockLowTier;
            this.allowCombinedTier = allowCombinedTier;
            this.blockHealing = blockHealing;
            this.blockLackLevel = blockLackLevel;
            this.blockLocked = blockLocked;
            this.hideFilterButton = hideFilterButton;
        }

        public String toFingerprint() {
            return blockLowTier + "," + allowCombinedTier + "," + blockHealing + ","
                    + blockLackLevel + "," + blockLocked + "," + hideFilterButton;
        }

        public int getBlockLowTier() {
            return blockLowTier;
        }

        public int getAllowCombinedTier() {
            return allowCombinedTier;
        }

        public int getBlockHealing() {
            return blockHealing;
        }

        public int getBlockLackLevel() {
            return blockLackLevel;
        }

        public int getBlockLocked() {
            return blockLocked;
        }

        public int getHideFilterButton() {
            return hideFilterButton;
        }
    }
}
