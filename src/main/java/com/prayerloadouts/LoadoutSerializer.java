package com.prayerloadouts;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles export and import of loadouts via the system clipboard.
 */
@Singleton
public class LoadoutSerializer {
    private final LoadoutManager loadoutManager;

    @Inject
    public LoadoutSerializer(LoadoutManager loadoutManager) {
        this.loadoutManager = loadoutManager;
    }

    /**
     * Exports a loadout to clipboard.
     * @return true if data was exported, false if loadout has no data
     */
    public boolean exportLoadout(String name) {
        LoadoutData loadout = loadoutManager.getLoadout(name);
        if (loadout == null) {
            return false;
        }

        StringBuilder export = new StringBuilder();
        export.append("PRAYERLOADOUT:").append(name).append("\n");

        boolean hasData = false;
        for (int prayerbook = 0; prayerbook <= 1; prayerbook++) {
            String order = loadout.getPrayerOrder(prayerbook);

            if (order != null && !order.isEmpty()) {
                hasData = true;
                export.append("ORDER_").append(prayerbook).append(":").append(order).append("\n");

                // Export filter settings
                LoadoutData.FilterSettings filters = loadout.getFilters(prayerbook);
                if (filters != null) {
                    export.append("FILTER_").append(prayerbook).append("_blocklowtier:")
                            .append(filters.getBlockLowTier()).append("\n");
                    export.append("FILTER_").append(prayerbook).append("_allowcombinedtier:")
                            .append(filters.getAllowCombinedTier()).append("\n");
                    export.append("FILTER_").append(prayerbook).append("_blockhealing:")
                            .append(filters.getBlockHealing()).append("\n");
                    export.append("FILTER_").append(prayerbook).append("_blocklacklevel:")
                            .append(filters.getBlockLackLevel()).append("\n");
                    export.append("FILTER_").append(prayerbook).append("_blocklocked:")
                            .append(filters.getBlockLocked()).append("\n");
                    export.append("FILTER_").append(prayerbook).append("_hidefilterbutton:")
                            .append(filters.getHideFilterButton()).append("\n");
                }

                // Export hidden prayers
                Map<String, String> hiddenPrayers = loadout.getHiddenPrayers(prayerbook);
                if (hiddenPrayers != null) {
                    for (Map.Entry<String, String> entry : hiddenPrayers.entrySet()) {
                        export.append("HIDDEN_").append(prayerbook).append("_").append(entry.getKey())
                                .append(":").append(entry.getValue()).append("\n");
                    }
                }
            }
        }

        if (!hasData) {
            return false;
        }

        export.append("END");

        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(export.toString()), null);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Imports a loadout from clipboard data.
     * @param importName Name to use for the imported loadout (null/empty to use original name)
     * @return true if import was successful, false otherwise
     */
    public boolean importLoadout(String importName) {
        String clipboardData;
        try {
            clipboardData = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }

        // Validate clipboard data format
        if (clipboardData == null || !clipboardData.startsWith("PRAYERLOADOUT:")) {
            return false;
        }

        if (!clipboardData.contains("\nEND")) {
            return false;
        }

        String[] lines = clipboardData.split("\n");
        if (lines.length < 2) {
            return false;
        }

        // Extract and validate original name
        String originalName = lines[0].substring("PRAYERLOADOUT:".length());
        if (originalName.isEmpty() || originalName.contains(",")) {
            return false;
        }

        // Use provided name or fall back to original
        if (importName == null || importName.trim().isEmpty()) {
            importName = originalName;
        } else {
            importName = importName.trim();
        }

        // Create new LoadoutData
        LoadoutData loadout = new LoadoutData(importName);

        // Temporary storage for filter values during parsing
        Map<Integer, int[]> filterValues = new HashMap<>();

        // Parse and import loadout data
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("END")) {
                break;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) {
                continue;
            }

            String key = line.substring(0, colonIndex);
            String value = line.substring(colonIndex + 1);

            try {
                if (key.startsWith("ORDER_")) {
                    int prayerbook = Integer.parseInt(key.substring("ORDER_".length()));
                    loadout.setPrayerOrder(prayerbook, value);
                } else if (key.startsWith("FILTER_")) {
                    String rest = key.substring("FILTER_".length());
                    int underscoreIdx = rest.indexOf('_');
                    if (underscoreIdx == -1) {
                        continue;
                    }
                    int prayerbook = Integer.parseInt(rest.substring(0, underscoreIdx));
                    String filterKey = rest.substring(underscoreIdx + 1);
                    int filterValue = Integer.parseInt(value);

                    // Store filter values temporarily
                    int[] filters = filterValues.computeIfAbsent(prayerbook, k -> new int[6]);
                    switch (filterKey) {
                        case "blocklowtier":
                            filters[0] = filterValue;
                            break;
                        case "allowcombinedtier":
                            filters[1] = filterValue;
                            break;
                        case "blockhealing":
                            filters[2] = filterValue;
                            break;
                        case "blocklacklevel":
                            filters[3] = filterValue;
                            break;
                        case "blocklocked":
                            filters[4] = filterValue;
                            break;
                        case "hidefilterbutton":
                            filters[5] = filterValue;
                            break;
                    }
                } else if (key.startsWith("HIDDEN_")) {
                    String rest = key.substring("HIDDEN_".length());
                    int underscoreIdx = rest.indexOf('_');
                    if (underscoreIdx == -1) {
                        continue;
                    }
                    int prayerbook = Integer.parseInt(rest.substring(0, underscoreIdx));
                    String hiddenKey = rest.substring(underscoreIdx + 1);

                    Map<String, String> hiddenPrayers = loadout.getHiddenPrayers(prayerbook);
                    if (hiddenPrayers.isEmpty()) {
                        hiddenPrayers = new HashMap<>();
                    }
                    hiddenPrayers.put(hiddenKey, value);
                    loadout.setHiddenPrayers(prayerbook, hiddenPrayers);
                }
            } catch (NumberFormatException e) {
                // Skip invalid lines
                continue;
            }
        }

        // Convert temporary filter arrays to FilterSettings objects
        for (Map.Entry<Integer, int[]> entry : filterValues.entrySet()) {
            int[] f = entry.getValue();
            LoadoutData.FilterSettings filters = new LoadoutData.FilterSettings(
                    f[0], f[1], f[2], f[3], f[4], f[5]
            );
            loadout.setFilters(entry.getKey(), filters);
        }

        // Save the loadout
        loadoutManager.saveLoadoutData(importName, loadout);
        return true;
    }
}
