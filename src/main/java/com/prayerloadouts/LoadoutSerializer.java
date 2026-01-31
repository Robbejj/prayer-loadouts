package com.prayerloadouts;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Set;

/**
 * Handles export and import of loadouts via the system clipboard.
 */
@Singleton
public class LoadoutSerializer {
    private final ConfigManager configManager;
    private final LoadoutManager loadoutManager;

    @Inject
    public LoadoutSerializer(ConfigManager configManager, LoadoutManager loadoutManager) {
        this.configManager = configManager;
        this.loadoutManager = loadoutManager;
    }

    /**
     * Exports a loadout to clipboard.
     * @return true if data was exported, false if loadout has no data
     */
    public boolean exportLoadout(String name) {
        Set<String> names = loadoutManager.getLoadoutNames();
        if (!names.contains(name)) {
            return false;
        }

        String safeKey = loadoutManager.toSafeKey(name);

        StringBuilder export = new StringBuilder();
        export.append("PRAYERLOADOUT:").append(name).append("\n");

        boolean hasData = false;
        for (int prayerbook = 0; prayerbook <= 1; prayerbook++) {
            String order = configManager.getConfiguration(
                    LoadoutManager.CONFIG_GROUP,
                    "loadout_" + safeKey + "_order_book_" + prayerbook);

            if (order != null && !order.isEmpty()) {
                hasData = true;
                export.append("ORDER_").append(prayerbook).append(":").append(order).append("\n");

                String filterPrefix = "loadout_" + safeKey + "_filter_book_" + prayerbook + "_";
                String[] filterKeys = { "blocklowtier", "allowcombinedtier", "blockhealing",
                        "blocklacklevel", "blocklocked", "hidefilterbutton" };

                for (String filterKey : filterKeys) {
                    String value = configManager.getConfiguration(LoadoutManager.CONFIG_GROUP,
                            filterPrefix + filterKey);
                    if (value != null) {
                        export.append("FILTER_").append(prayerbook).append("_").append(filterKey).append(":")
                                .append(value).append("\n");
                    }
                }

                String hiddenPrefix = LoadoutManager.CONFIG_GROUP + ".loadout_" + safeKey + "_"
                        + LoadoutManager.PRAYER_HIDDEN_KEY_PREFIX + prayerbook;
                for (String key : configManager.getConfigurationKeys(hiddenPrefix)) {
                    String[] parts = key.split("\\.", 2);
                    if (parts.length == 2) {
                        String value = configManager.getConfiguration(parts[0], parts[1]);
                        String prayerKey = parts[1].substring(("loadout_" + safeKey + "_").length());
                        export.append("HIDDEN_").append(prayerbook).append("_").append(prayerKey).append(":")
                                .append(value).append("\n");
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

        String safeKey = loadoutManager.toSafeKey(importName);

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
                    configManager.setConfiguration(
                            LoadoutManager.CONFIG_GROUP,
                            "loadout_" + safeKey + "_order_book_" + prayerbook,
                            value);
                } else if (key.startsWith("FILTER_")) {
                    String rest = key.substring("FILTER_".length());
                    int underscoreIdx = rest.indexOf('_');
                    if (underscoreIdx == -1) {
                        continue;
                    }
                    int prayerbook = Integer.parseInt(rest.substring(0, underscoreIdx));
                    String filterKey = rest.substring(underscoreIdx + 1);
                    configManager.setConfiguration(
                            LoadoutManager.CONFIG_GROUP,
                            "loadout_" + safeKey + "_filter_book_" + prayerbook + "_" + filterKey,
                            value);
                } else if (key.startsWith("HIDDEN_")) {
                    String rest = key.substring("HIDDEN_".length());
                    int underscoreIdx = rest.indexOf('_');
                    if (underscoreIdx == -1) {
                        continue;
                    }
                    String hiddenKey = rest.substring(underscoreIdx + 1);
                    configManager.setConfiguration(
                            LoadoutManager.CONFIG_GROUP,
                            "loadout_" + safeKey + "_" + hiddenKey,
                            value);
                }
            } catch (NumberFormatException e) {
                // Skip invalid lines
                continue;
            }
        }

        configManager.setConfiguration(
                LoadoutManager.CONFIG_GROUP,
                "loadout_" + safeKey + "_displayname",
                importName);

        Set<String> names = loadoutManager.getLoadoutNames();
        names.add(importName);
        loadoutManager.saveLoadoutNames(names);
        return true;
    }
}
