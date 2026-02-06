package com.prayerloadouts;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

public class PrayerLoadoutsPanel extends PluginPanel {
    private final PrayerLoadoutsPlugin plugin;

    // Fixed sections (created once, visibility controlled)
    private final JPanel savePanel;
    private final JPanel footerSection;

    // Scrollable content (rebuilt on state change)
    private final JPanel scrollableContent;

    // Reusable info panels
    private final JPanel noLoadoutsPanel;
    private final JPanel loginRequiredPanel;
    private final JPanel prayerPluginRequiredPanel;

    // ========== Section Builders ==========

    private JPanel createHeaderSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(ColorScheme.DARK_GRAY_COLOR);
        section.setBorder(new EmptyBorder(10, 10, 10, 10));
        section.setPreferredSize(new Dimension(0, 42));

        // Title on the left
        JLabel title = new JLabel("Prayer Loadouts");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        section.add(title, BorderLayout.WEST);

        // Buttons on the right
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        JButton refreshButton = new JButton("↻");
        refreshButton.setToolTipText("Refresh loadout list");
        refreshButton.addActionListener(e -> rebuild());
        buttonsPanel.add(refreshButton);
        
        section.add(buttonsPanel, BorderLayout.EAST);

        return section;
    }

    private JPanel createSavePanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 5, 0));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(0, 10, 10, 10));

        JButton saveButton = new JButton("Save");
        saveButton.setBackground(new Color(0, 150, 0));
        saveButton.setForeground(Color.WHITE);
        saveButton.setToolTipText("Save current prayer arrangement as a loadout");
        saveButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this,
                    "Enter a name for this loadout:",
                    "Save Loadout",
                    JOptionPane.PLAIN_MESSAGE);
            
            if (name == null) {
                return; // User cancelled
            }
            
            name = name.trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a name for the loadout.",
                        "Name Required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (plugin.getLoadoutNames().contains(name)) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Loadout '" + name + "' already exists. Overwrite?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            plugin.saveLoadoutFromPanel(name);
        });
        panel.add(saveButton);

        JButton defaultsButton = new JButton("Defaults");
        defaultsButton.setToolTipText("Reset prayer book to vanilla OSRS defaults");
        defaultsButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Reset prayer book to default OSRS layout?\nThis will clear custom ordering, hidden prayers, and filters.",
                    "Confirm Reset",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                plugin.resetToDefaultsFromPanel();
            }
        });
        panel.add(defaultsButton);

        return panel;
    }

    private JPanel createFooterSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(ColorScheme.DARK_GRAY_COLOR);
        section.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton importButton = new JButton("Import from Clipboard");
        importButton.setToolTipText("Import a loadout from clipboard data");
        importButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this,
                    "Enter name for imported loadout (leave blank to use original):",
                    "Import Loadout",
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null) {
                boolean success = plugin.importLoadoutFromPanel(name.isEmpty() ? null : name);
                if (!success) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid clipboard data. Make sure you copied a valid loadout export.",
                            "Import Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        section.add(importButton, BorderLayout.CENTER);

        return section;
    }

    /**
     * Creates a centered info panel with optional icon, title, and description.
     * Used for status messages like "Login Required", "No loadouts", etc.
     */
    private static JPanel createInfoPanel(String icon, Color iconColor, String title, String description) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(20, 10, 20, 10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Icon (optional)
        if (icon != null) {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setFont(iconLabel.getFont().deriveFont(32f));
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            if (iconColor != null) {
                iconLabel.setForeground(iconColor);
            }
            panel.add(iconLabel);
            panel.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        // Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Description
        JLabel descLabel = new JLabel("<html><center>" + description + "</center></html>");
        descLabel.setForeground(Color.GRAY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        descLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(descLabel);

        return panel;
    }

    /**
     * Creates a loadout card panel with name, load button, and action buttons.
     */
    private static final Color ACTIVE_COLOR = new Color(0, 100, 0);
    private static final Color ACTIVE_HOVER_COLOR = new Color(0, 130, 0);

    private JPanel createLoadoutPanel(String name, boolean isActive) {
        final Color bgColor = isActive ? ACTIVE_COLOR : ColorScheme.DARKER_GRAY_COLOR;
        final Color hoverColor = isActive ? ACTIVE_HOVER_COLOR : ColorScheme.DARKER_GRAY_HOVER_COLOR;

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(bgColor);
        panel.setBorder(new EmptyBorder(8, 10, 8, 10));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        String displayName = isActive ? "● " + name : name;
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(isActive ? new Color(144, 238, 144) : Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);

        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttonsPanel.setBackground(bgColor);
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton loadButton = new JButton("Load");
        loadButton.setBackground(ColorScheme.BRAND_ORANGE);
        loadButton.setToolTipText("Load this prayer arrangement");
        loadButton.addActionListener(e -> {
            // Async load - callback handles success/failure
            plugin.loadLoadoutFromPanel(name, success -> {
                if (!success) {
                    JOptionPane.showMessageDialog(this,
                            "This loadout has no data for your current prayerbook.\nTry saving it again.",
                            "Load Failed",
                            JOptionPane.WARNING_MESSAGE);
                }
            });
        });
        buttonsPanel.add(loadButton);

        JButton exportButton = new JButton("📋");
        exportButton.setToolTipText("Copy to clipboard");
        exportButton.addActionListener(e -> {
            boolean success = plugin.exportLoadoutFromPanel(name);
            if (!success) {
                JOptionPane.showMessageDialog(this,
                        "This loadout has no data to export.\nTry saving it again.",
                        "Export Failed",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        buttonsPanel.add(exportButton);

        JButton renameButton = new JButton("✏");
        renameButton.setToolTipText("Rename this loadout");
        renameButton.addActionListener(e -> {
            String newName = (String) JOptionPane.showInputDialog(
                    this,
                    "Enter new name for '" + name + "':",
                    "Rename Loadout",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    name);
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(name)) {
                newName = newName.trim();
                if (plugin.getLoadoutNames().contains(newName)) {
                    JOptionPane.showMessageDialog(this,
                            "A loadout named '" + newName + "' already exists.",
                            "Name Already Exists",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                plugin.renameLoadoutFromPanel(name, newName);
            }
        });
        buttonsPanel.add(renameButton);

        JButton deleteButton = new JButton("🗑");
        deleteButton.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        deleteButton.setToolTipText("Delete this loadout");
        deleteButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Delete loadout '" + name + "'?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                plugin.deleteLoadoutFromPanel(name);
            }
        });
        buttonsPanel.add(deleteButton);

        panel.add(buttonsPanel);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setBackground(hoverColor);
                buttonsPanel.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setBackground(bgColor);
                buttonsPanel.setBackground(bgColor);
            }
        });

        return panel;
    }

    // ========== Constructor ==========

    public PrayerLoadoutsPanel(PrayerLoadoutsPlugin plugin) {
        // Disable PluginPanel's built-in scroll wrapping so we can have fixed header/footer
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Create reusable info panels
        noLoadoutsPanel = createInfoPanel(
                null, null,
                "No loadouts saved",
                "Enter a name above and<br>click 'Save Current' to save<br>your first loadout.");

        loginRequiredPanel = createInfoPanel(
                "🔒", null,
                "Login Required",
                "Please log in to manage<br>your prayer loadouts.");

        prayerPluginRequiredPanel = createInfoPanel(
                "⚠", new Color(255, 200, 0),
                "Prayer Plugin Required",
                "Please enable the <br><b>Prayer</b> core plugin to use<br>Prayer Loadouts.");

        // ===== HEADER SECTION (NORTH) =====
        // Contains: Title + Refresh button (always) + Save panel (when logged in)
        JPanel headerSection = new JPanel(new BorderLayout());
        headerSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Title bar at top of header
        headerSection.add(createHeaderSection(), BorderLayout.NORTH);
        
        // Save panel below title
        savePanel = createSavePanel();
        headerSection.add(savePanel, BorderLayout.SOUTH);

        // ===== CONTENT SECTION (CENTER) =====
        // Scrollable area containing loadouts or error messages
        scrollableContent = new JPanel();
        scrollableContent.setLayout(new BoxLayout(scrollableContent, BoxLayout.Y_AXIS));
        scrollableContent.setBorder(new EmptyBorder(5, 10, 5, 10));

        JScrollPane scrollPane = new JScrollPane(scrollableContent);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // ===== FOOTER SECTION (SOUTH) =====
        footerSection = createFooterSection();

        // ===== ASSEMBLE LAYOUT =====
        add(headerSection, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(footerSection, BorderLayout.SOUTH);

        rebuild();
    }

    // ========== Rebuild Logic ==========

    public void rebuild() {
        // Only clear and rebuild the scrollable content - header and footer stay intact
        scrollableContent.removeAll();

        boolean isLoggedIn = plugin.isLoggedIn();
        boolean isPrayerPluginEnabled = plugin.isPrayerPluginEnabled();
        boolean canUsePlugin = isLoggedIn && isPrayerPluginEnabled;

        // Control visibility of save panel and footer
        savePanel.setVisible(canUsePlugin);
        footerSection.setVisible(canUsePlugin);

        if (!isPrayerPluginEnabled) {
            scrollableContent.add(prayerPluginRequiredPanel);
        } else if (!isLoggedIn) {
            scrollableContent.add(loginRequiredPanel);
        } else {
            Set<String> loadoutNames = plugin.getLoadoutNames();
            String activeLoadout = plugin.getActiveLoadoutName();

            if (loadoutNames.isEmpty()) {
                scrollableContent.add(noLoadoutsPanel);
            } else {
                for (String name : loadoutNames) {
                    boolean isActive = name.equals(activeLoadout);
                    scrollableContent.add(createLoadoutPanel(name, isActive));
                    scrollableContent.add(Box.createRigidArea(new Dimension(0, 5)));
                }
            }
        }

        // Refresh the UI
        scrollableContent.revalidate();
        scrollableContent.repaint();
        revalidate();
        repaint();
    }
}
