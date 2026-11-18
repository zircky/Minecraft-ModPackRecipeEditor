package dev.recipeeditor.ui;

public enum NavigationSection {
    PROJECTS("Projects", "No project open", "Use Import Modpack to select an existing Minecraft instance."),
    MODS("Mods", "No mods loaded", "Use Import Modpack to load installed mods."),
    ITEMS("Items", "No items loaded", "Import a Minecraft 1.20.1 instance to index static resource candidates."),
    BLOCKS("Blocks", "No blocks indexed", "Block models and blockstates are static evidence, not runtime registration."),
    FLUIDS("Fluids", "No fluid references indexed", "Only explicit fluid tag references are indexed. Programmatic registrations may be absent."),
    RECIPES("Recipes", "No recipes loaded", "Import and index a Minecraft 1.20.1 instance to edit saved project recipes."),
    RUNTIME("Runtime", "No runtime connection", "Connect to the read-only Minecraft Companion and collect a complete snapshot."),
    ANALYZER("Analyzer", "Nothing to analyze", "Recipe analysis will be available when recipe loading is implemented."),
    SETTINGS("Settings", "No settings available yet", "Application preferences will be added in a later phase.");

    private final String title;
    private final String emptyTitle;
    private final String description;

    NavigationSection(final String title, final String emptyTitle, final String description) {
        this.title = title;
        this.emptyTitle = emptyTitle;
        this.description = description;
    }

    public String title() { return title; }
    public String emptyTitle() { return emptyTitle; }
    public String description() { return description; }
}
