package com.hytame.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hytame.HyTamePlugin;
import com.hytame.models.AnimalType;
import com.hytame.util.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration panel for Animal Breeding plugin.
 *
 * Layout:
 * - Left sidebar: Preset list with search and pagination
 * - Middle: Current preset settings + Animal grid with pagination
 * - Right: Selected animal config
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/ConfigPanel.ui
 */
public class ConfigPanelUIPage extends InteractiveCustomUIPage<ConfigPanelUIPage.ConfigEventData> {

    // Pagination constants
    private static final int PRESETS_PER_PAGE = 8;
    private static final int ANIMALS_PER_PAGE = 20;  // 5 columns x 4 rows

    // State
    private int presetPage = 0;
    private int animalPage = 0;
    private String selectedAnimal = null;
    private String presetSearchFilter = "";
    private String animalSearchFilter = "";

    /**
     * Event data received from UI interactions.
     */
    public static class ConfigEventData {
        public String action;
        public String growthTime;
        public String cooldown;
        public String presetSearch;
        public String animalSearch;
        public String animalGrowthTime;
        public String animalCooldown;

        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec
            .builder(ConfigEventData.class, ConfigEventData::new)
            .append(new KeyedCodec<>("@action", new StringCodec()),
                (obj, val) -> obj.action = val,
                obj -> obj.action)
            .add()
            .append(new KeyedCodec<>("@growthTime", new StringCodec()),
                (obj, val) -> obj.growthTime = val,
                obj -> obj.growthTime)
            .add()
            .append(new KeyedCodec<>("@cooldown", new StringCodec()),
                (obj, val) -> obj.cooldown = val,
                obj -> obj.cooldown)
            .add()
            .append(new KeyedCodec<>("@presetSearch", new StringCodec()),
                (obj, val) -> obj.presetSearch = val,
                obj -> obj.presetSearch)
            .add()
            .append(new KeyedCodec<>("@animalSearch", new StringCodec()),
                (obj, val) -> obj.animalSearch = val,
                obj -> obj.animalSearch)
            .add()
            .append(new KeyedCodec<>("@animalGrowthTime", new StringCodec()),
                (obj, val) -> obj.animalGrowthTime = val,
                obj -> obj.animalGrowthTime)
            .add()
            .append(new KeyedCodec<>("@animalCooldown", new StringCodec()),
                (obj, val) -> obj.animalCooldown = val,
                obj -> obj.animalCooldown)
            .add()
            .build();
    }

    public ConfigPanelUIPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    /**
     * Constructor with state preservation for pagination.
     */
    public ConfigPanelUIPage(PlayerRef playerRef, int presetPage, int animalPage,
                             String selectedAnimal, String presetSearch, String animalSearch) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
        this.presetPage = presetPage;
        this.animalPage = animalPage;
        this.selectedAnimal = selectedAnimal;
        this.presetSearchFilter = presetSearch != null ? presetSearch : "";
        this.animalSearchFilter = animalSearch != null ? animalSearch : "";
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        // Load the UI layout file
        cmd.append("Pages/ConfigPanel.ui");

<<<<<<< HEAD:src/main/java/com/hytame/ui/ConfigPanelUIPage.java
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) return;

        ConfigManager config = plugin.getConfigManager();
        if (config == null) return;

        // Populate preset settings
        populatePresetSettings(cmd, config);

        // Populate preset list
        List<String> presets = getFilteredPresets(config);
        populatePresetList(cmd, events, presets, config.getActivePreset());

        // Populate animal grid
        List<AnimalType> animals = getFilteredAnimals();
        populateAnimalGrid(cmd, events, animals);

        // Populate selected animal config (pass events for bindings)
        populateSelectedAnimalConfig(cmd, events, config);

        // Set up action markers and event bindings
        setupEventBindings(cmd, events);
    }

    /**
     * Populate the preset settings section with current values.
     */
    private void populatePresetSettings(UICommandBuilder cmd, ConfigManager config) {
        String activePreset = config.getActivePreset();
        cmd.set("#presetNameLabel.Text", activePreset != null ? activePreset : "none");
        cmd.set("#growthToggleBtn.Text", config.isGrowthEnabled() ? "ON" : "OFF");
        cmd.set("#growthTimeInput.Value", String.valueOf(config.getDefaultGrowthTimeMinutes()));
        cmd.set("#cooldownInput.Value", String.valueOf(config.getDefaultBreedCooldownMinutes()));
    }

    /**
     * Get filtered list of presets based on search filter.
     */
    private List<String> getFilteredPresets(ConfigManager config) {
        List<String> allPresets = config.getAvailablePresets();
        if (presetSearchFilter == null || presetSearchFilter.isEmpty()) {
            return allPresets;
        }
        List<String> filtered = new ArrayList<>();
        String filterLower = presetSearchFilter.toLowerCase();
        for (String preset : allPresets) {
            if (preset.toLowerCase().contains(filterLower)) {
                filtered.add(preset);
            }
        }
        return filtered;
    }

    /**
     * Populate the preset list slots.
     */
    private void populatePresetList(UICommandBuilder cmd, UIEventBuilder events,
                                    List<String> presets, String activePreset) {
        int totalPages = Math.max(1, (presets.size() + PRESETS_PER_PAGE - 1) / PRESETS_PER_PAGE);
        presetPage = Math.min(presetPage, totalPages - 1);

        int startIndex = presetPage * PRESETS_PER_PAGE;

        // Populate preset slots
        for (int i = 0; i < PRESETS_PER_PAGE; i++) {
            int presetIndex = startIndex + i;
            String slotId = "#preset" + i;
            String actionFieldId = "#presetAction" + i;

            if (presetIndex < presets.size()) {
                String presetName = presets.get(presetIndex);
                cmd.set(slotId + ".Text", presetName);

                // Set action value in hidden field
                cmd.set(actionFieldId + ".Value", "SELECT_PRESET:" + presetName);

                // Bind click event - reference the hidden field
                events.addEventBinding(CustomUIEventBindingType.Activating, slotId,
                    new EventData()
                        .append("@action", actionFieldId + ".Value")
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            } else {
                // Empty slot - clear text (can't hide dynamically)
                cmd.set(slotId + ".Text", "");
                cmd.set(actionFieldId + ".Value", "");
            }
        }

        // Update pagination label
        cmd.set("#presetPageLabel.Text", (presetPage + 1) + "/" + totalPages);

        // Set pagination action values in hidden fields
        cmd.set("#actionPresetPrev.Value", "PRESET_PREV");
        cmd.set("#actionPresetNext.Value", "PRESET_NEXT");

        // Bind pagination buttons - reference hidden fields
        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetPrevBtn",
            new EventData()
                .append("@action", "#actionPresetPrev.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetNextBtn",
            new EventData()
                .append("@action", "#actionPresetNext.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
    }

    /**
     * Get filtered list of animals based on search filter.
     */
    private List<AnimalType> getFilteredAnimals() {
        AnimalType[] allAnimals = AnimalType.values();
        if (animalSearchFilter == null || animalSearchFilter.isEmpty()) {
            List<AnimalType> list = new ArrayList<>();
            for (AnimalType animal : allAnimals) {
                list.add(animal);
            }
            return list;
        }
        List<AnimalType> filtered = new ArrayList<>();
        String filterLower = animalSearchFilter.toLowerCase();
        for (AnimalType animal : allAnimals) {
            if (animal.name().toLowerCase().contains(filterLower) ||
                animal.getModelAssetId().toLowerCase().contains(filterLower)) {
                filtered.add(animal);
            }
        }
        return filtered;
    }

    /**
     * Populate the animal grid slots dynamically using appendInline.
     * This allows setting Background with icon textures inline.
     */
    private void populateAnimalGrid(UICommandBuilder cmd, UIEventBuilder events, List<AnimalType> animals) {
        int totalPages = Math.max(1, (animals.size() + ANIMALS_PER_PAGE - 1) / ANIMALS_PER_PAGE);
        animalPage = Math.min(animalPage, totalPages - 1);

        int startIndex = animalPage * ANIMALS_PER_PAGE;
        int animalsPerRow = 5;

        // Clear all rows first
        cmd.clear("#animalRow0");
        cmd.clear("#animalRow1");
        cmd.clear("#animalRow2");
        cmd.clear("#animalRow3");

        // Populate each row dynamically
        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            int animalIndex = startIndex + i;
            int rowIndex = i / animalsPerRow;
            int colIndex = i % animalsPerRow;
            String rowId = "#animalRow" + rowIndex;
            boolean isLastInRow = (colIndex == animalsPerRow - 1);

            if (animalIndex < animals.size()) {
                AnimalType animal = animals.get(animalIndex);
                String displayName = getAbbreviatedName(animal);
                String iconPath = getAnimalIconPath(animal);

                // Build the button inline with icon background and label at bottom
                StringBuilder buttonMarkup = new StringBuilder();
                buttonMarkup.append("Button #slot").append(i).append(" {\n");
                buttonMarkup.append("  Anchor: (Width: 88, Height: 76, Right: 8);\n");

                // Try icon texture, fall back to category color
                if (iconPath != null) {
                    buttonMarkup.append("  Background: (TexturePath: \"").append(iconPath).append("\");\n");
                } else {
                    buttonMarkup.append("  Background: ").append(getAnimalBackgroundColor(animal)).append(";\n");
                }

                buttonMarkup.append("  Style: ButtonStyle(\n");
                buttonMarkup.append("    Hovered: (Background: #ffffff(0.15)),\n");
                buttonMarkup.append("    Pressed: (Background: #000000(0.2))\n");
                buttonMarkup.append("  );\n");
                // Label container at bottom with semi-transparent background
                buttonMarkup.append("  Group #labelBg {\n");
                buttonMarkup.append("    Anchor: (Bottom: 0, Left: 0, Right: 0, Height: 18);\n");
                buttonMarkup.append("    Background: #000000(0.7);\n");
                buttonMarkup.append("    LayoutMode: Center;\n");
                buttonMarkup.append("    Label #name { Text: \"").append(displayName).append("\"; ");
                buttonMarkup.append("Style: (FontSize: 10, TextColor: #ffffff, Alignment: Center); }\n");
                buttonMarkup.append("  }\n");
                buttonMarkup.append("}");

                cmd.appendInline(rowId, buttonMarkup.toString());

                // Set action value in hidden field and bind event
                String actionFieldId = "#animalAction" + i;
                cmd.set(actionFieldId + ".Value", "SELECT_ANIMAL:" + animal.name());

                // Bind click event using the dynamically created button
                events.addEventBinding(CustomUIEventBindingType.Activating, rowId + " #slot" + i,
                    new EventData()
                        .append("@action", actionFieldId + ".Value")
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            }
            // Empty slots are just not added (rows will have fewer items)
        }

        // Update pagination label
        cmd.set("#animalPageLabel.Text", (animalPage + 1) + "/" + totalPages);

        // Set pagination action values in hidden fields
        cmd.set("#actionAnimalPrev.Value", "ANIMAL_PREV");
        cmd.set("#actionAnimalNext.Value", "ANIMAL_NEXT");

        // Bind pagination buttons - reference hidden fields
        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalPrevBtn",
            new EventData()
                .append("@action", "#actionAnimalPrev.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalNextBtn",
            new EventData()
                .append("@action", "#actionAnimalNext.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
    }

    /**
     * Get abbreviated display name for animal to fit in cell.
     * For variants (e.g., CHICKEN_FOREST), shows "CHICKEN F" to distinguish.
     */
    private String getAbbreviatedName(AnimalType animal) {
        String name = animal.getModelAssetId();

        // Handle variants (e.g., Chicken_Forest -> "CHICKEN F")
        if (name.contains("_")) {
            String[] parts = name.split("_");
            String base = parts[0].toUpperCase();
            String variant = parts.length > 1 ? parts[1] : "";

            // If base name is short enough, add variant initial
            if (base.length() <= 7 && !variant.isEmpty()) {
                return base + " " + variant.substring(0, 1).toUpperCase();
            }
            // Otherwise just truncate base
            if (base.length() > 9) {
                return base.substring(0, 8) + "..";
            }
            return base;
        }

        // Simple name - just uppercase and truncate if needed
        name = name.toUpperCase();
        if (name.length() > 9) {
            return name.substring(0, 8) + "..";
        }
        return name;
    }

    /**
     * Get the icon path for an animal model.
     * NPC icons are in the Memories folder.
     * For variants without their own icon, falls back to base animal icon.
     */
    private String getAnimalIconPath(AnimalType animal) {
        String modelAssetId = animal.getModelAssetId();

        // Known variants that DON'T have their own icon - use base name
        // e.g., Pig_Wild -> Pig, Frog_Blue -> Frog, Mosshorn_Plain -> Mosshorn
        if (modelAssetId.contains("_")) {
            String[] parts = modelAssetId.split("_");
            String suffix = parts[parts.length - 1];

            // These suffixes have their own icons (babies, undead, etc.)
            boolean hasOwnIcon = suffix.equals("Calf") || suffix.equals("Piglet") ||
                suffix.equals("Chick") || suffix.equals("Lamb") || suffix.equals("Foal") ||
                suffix.equals("Kid") || suffix.equals("Undead") || suffix.equals("Ice") ||
                suffix.equals("Void") || suffix.equals("Grizzly") || suffix.equals("Polar") ||
                suffix.equals("Doe") || suffix.equals("Stag") || suffix.equals("Bull") ||
                suffix.equals("Desert") || suffix.equals("Snow") || suffix.equals("Black") ||
                suffix.equals("White") || suffix.equals("Frost") || suffix.equals("Electric");

            // If suffix is a variant without its own icon, use base name
            if (!hasOwnIcon) {
                String baseName = parts[0];
                return "Pages/Memories/npcs/" + baseName + ".png";
            }
        }

        // NPC icons are at: Common/UI/Custom/Pages/Memories/npcs/{ModelAssetId}.png
        return "Pages/Memories/npcs/" + modelAssetId + ".png";
    }

    /**
     * Get background color for an animal based on its category.
     * Used as fallback when icon texture doesn't exist.
     */
    private String getAnimalBackgroundColor(AnimalType animal) {
        // Color-code by category for visual distinction
        switch (animal.getCategory()) {
            case LIVESTOCK:
                return "#2a4a2a";  // Green for farm animals
            case MAMMAL:
                return "#4a3a2a";  // Brown for wild mammals
            case AVIAN:
                return "#2a3a4a";  // Blue for birds
            case CRITTER:
                return "#4a4a2a";  // Yellow-brown for critters
            case AQUATIC:
                return "#2a4a4a";  // Cyan for aquatic
            case REPTILE:
                return "#3a4a2a";  // Olive for reptiles
            case VERMIN:
            case SCARAK:
                return "#4a2a4a";  // Purple for bugs/vermin
            case MYTHIC:
                return "#4a2a3a";  // Magenta for mythical
            default:
                return "#2a2a3a";  // Default dark
        }
    }

    /**
     * Populate the selected animal config section.
     */
    private void populateSelectedAnimalConfig(UICommandBuilder cmd, UIEventBuilder events, ConfigManager config) {
        if (selectedAnimal == null) {
            cmd.set("#selectedAnimalName.Text", "(none selected)");
            cmd.set("#animalSettingsPlaceholder.Text", "Click an animal to edit its settings");
            // Clear form fields (can't hide dynamically)
            cmd.set("#animalEnabledBtn.Text", "-");
            cmd.set("#animalGrowthTimeInput.Value", "");
            cmd.set("#animalCooldownInput.Value", "");
            cmd.set("#animalFoodsLabel.Text", "");
            return;
        }

        try {
            AnimalType animal = AnimalType.valueOf(selectedAnimal);
            cmd.set("#selectedAnimalName.Text", animal.getModelAssetId());

            // Hide placeholder
            cmd.set("#animalSettingsPlaceholder.Text", "");

            // Get animal config
            ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
            if (animalConfig != null) {
                // Populate form fields
                cmd.set("#animalEnabledBtn.Text", animalConfig.enabled ? "ENABLED" : "DISABLED");
                cmd.set("#animalGrowthTimeInput.Value", String.valueOf(animalConfig.growthTimeMinutes));
                cmd.set("#animalCooldownInput.Value", String.valueOf(animalConfig.breedCooldownMinutes));

                // Show breeding foods
                String foods = String.join(", ", animalConfig.breedingFoods);
                if (foods.isEmpty()) {
                    foods = "(none)";
                } else if (foods.length() > 50) {
                    foods = foods.substring(0, 47) + "...";
                }
                cmd.set("#animalFoodsLabel.Text", foods);

                // Set up event bindings for animal config
                cmd.set("#actionAnimalToggle.Value", "ANIMAL_TOGGLE_ENABLED");

                events.addEventBinding(CustomUIEventBindingType.Activating, "#animalEnabledBtn",
                    new EventData()
                        .append("@action", "#actionAnimalToggle.Value")
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            } else {
                cmd.set("#animalSettingsPlaceholder.Text", "No config found for this animal");
                cmd.set("#animalEnabledBtn.Text", "-");
                cmd.set("#animalGrowthTimeInput.Value", "");
                cmd.set("#animalCooldownInput.Value", "");
                cmd.set("#animalFoodsLabel.Text", "");
            }
        } catch (Exception e) {
            cmd.set("#selectedAnimalName.Text", "(invalid)");
            cmd.set("#animalSettingsPlaceholder.Text", "Click an animal to edit its settings");
            cmd.set("#animalEnabledBtn.Text", "-");
            cmd.set("#animalGrowthTimeInput.Value", "");
            cmd.set("#animalCooldownInput.Value", "");
            cmd.set("#animalFoodsLabel.Text", "");
        }
    }

    /**
     * Set up hidden action markers and event bindings.
     */
    private void setupEventBindings(UICommandBuilder cmd, UIEventBuilder events) {
        // Hidden action markers
        cmd.set("#actionToggleGrowth.Value", "TOGGLE_GROWTH");
        cmd.set("#actionSave.Value", "SAVE");
        cmd.set("#actionAddPreset.Value", "ADD_PRESET");

        // Growth toggle button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#growthToggleBtn",
            new EventData()
                .append("@action", "#actionToggleGrowth.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Save button - includes both global and animal-specific config
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveBtn",
            new EventData()
                .append("@action", "#actionSave.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value")
                .append("@animalGrowthTime", "#animalGrowthTimeInput.Value")
                .append("@animalCooldown", "#animalCooldownInput.Value"));

        // Add preset button - reference hidden field
        events.addEventBinding(CustomUIEventBindingType.Activating, "#addPresetBtn",
            new EventData()
                .append("@action", "#actionAddPreset.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ConfigEventData data) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            HyTamePlugin plugin = HyTamePlugin.getInstance();

            if (plugin == null) {
                closePage(player, ref, store);
                return;
            }

            ConfigManager config = plugin.getConfigManager();
            if (config == null) {
                closePage(player, ref, store);
                return;
            }

            // Update search filters from event data
            if (data.presetSearch != null) {
                presetSearchFilter = data.presetSearch;
            }
            if (data.animalSearch != null) {
                animalSearchFilter = data.animalSearch;
            }

            String action = data.action;
            if (action == null) {
                action = "";
            }
            action = action.trim();

            log("Config panel action: " + action);

            // Handle preset selection
            if (action.startsWith("SELECT_PRESET:")) {
                String presetName = action.substring("SELECT_PRESET:".length());
                if (config.applyPreset(presetName)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Applied preset: " + presetName).color("#55FF55"));
                    }
                }
                reopenPage(player, ref, store);
                return;
            }

            // Handle animal selection
            if (action.startsWith("SELECT_ANIMAL:")) {
                selectedAnimal = action.substring("SELECT_ANIMAL:".length());
                reopenPage(player, ref, store);
                return;
            }

            // Handle preset pagination
            if (action.equals("PRESET_PREV")) {
                if (presetPage > 0) {
                    presetPage--;
                }
                reopenPage(player, ref, store);
                return;
            }
            if (action.equals("PRESET_NEXT")) {
                List<String> presets = getFilteredPresets(config);
                int totalPages = (presets.size() + PRESETS_PER_PAGE - 1) / PRESETS_PER_PAGE;
                if (presetPage < totalPages - 1) {
                    presetPage++;
                }
                reopenPage(player, ref, store);
                return;
            }

            // Handle animal pagination
            if (action.equals("ANIMAL_PREV")) {
                if (animalPage > 0) {
                    animalPage--;
                }
                reopenPage(player, ref, store);
                return;
            }
            if (action.equals("ANIMAL_NEXT")) {
                List<AnimalType> animals = getFilteredAnimals();
                int totalPages = (animals.size() + ANIMALS_PER_PAGE - 1) / ANIMALS_PER_PAGE;
                if (animalPage < totalPages - 1) {
                    animalPage++;
                }
                reopenPage(player, ref, store);
                return;
            }

            // Handle animal enabled toggle
            if (action.equals("ANIMAL_TOGGLE_ENABLED")) {
                if (selectedAnimal != null) {
                    try {
                        AnimalType animal = AnimalType.valueOf(selectedAnimal);
                        ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
                        if (animalConfig != null) {
                            boolean newState = !animalConfig.enabled;
                            config.setAnimalEnabled(animal, newState);
                            if (player != null) {
                                player.sendMessage(Message.raw(animal.getModelAssetId() + " breeding " +
                                    (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                            }
                        }
                    } catch (Exception e) {
                        log("Error toggling animal enabled: " + e.getMessage());
                    }
                }
                reopenPage(player, ref, store);
                return;
            }

            // Handle toggle growth
            if (action.equals("TOGGLE_GROWTH")) {
                boolean newState = !config.isGrowthEnabled();
                config.setGrowthEnabled(newState);
                if (player != null) {
                    player.sendMessage(Message.raw("Baby growth " + (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                }
                reopenPage(player, ref, store);
                return;
            }

            // Handle save
            if (action.equals("SAVE")) {
                boolean hasErrors = false;

                // Parse global growth time
                if (data.growthTime != null && !data.growthTime.isEmpty()) {
                    try {
                        double growthTime = Double.parseDouble(data.growthTime.trim());
                        if (growthTime > 0) {
                            config.setDefaultGrowthTime(growthTime);
                        } else {
                            hasErrors = true;
                        }
                    } catch (NumberFormatException e) {
                        hasErrors = true;
                    }
                }

                // Parse global cooldown
                if (data.cooldown != null && !data.cooldown.isEmpty()) {
                    try {
                        double cooldown = Double.parseDouble(data.cooldown.trim());
                        if (cooldown >= 0) {
                            config.setDefaultBreedCooldown(cooldown);
                        } else {
                            hasErrors = true;
                        }
                    } catch (NumberFormatException e) {
                        hasErrors = true;
                    }
                }

                // Save animal-specific config if an animal is selected
                if (selectedAnimal != null) {
                    try {
                        AnimalType animal = AnimalType.valueOf(selectedAnimal);

                        // Parse animal growth time
                        if (data.animalGrowthTime != null && !data.animalGrowthTime.isEmpty()) {
                            try {
                                double animalGrowth = Double.parseDouble(data.animalGrowthTime.trim());
                                if (animalGrowth > 0) {
                                    config.setGrowthTime(animal, animalGrowth);
                                }
                            } catch (NumberFormatException e) {
                                hasErrors = true;
                            }
                        }

                        // Parse animal cooldown
                        if (data.animalCooldown != null && !data.animalCooldown.isEmpty()) {
                            try {
                                double animalCooldown = Double.parseDouble(data.animalCooldown.trim());
                                if (animalCooldown >= 0) {
                                    config.setBreedingCooldown(animal, animalCooldown);
                                }
                            } catch (NumberFormatException e) {
                                hasErrors = true;
                            }
                        }
                    } catch (Exception e) {
                        log("Error saving animal config: " + e.getMessage());
                    }
                }

                // Save to file
                config.saveToFile();

                if (player != null) {
                    if (hasErrors) {
                        player.sendMessage(Message.raw("Saved with some invalid values ignored.").color("#FFAA00"));
                    } else {
                        player.sendMessage(Message.raw("Configuration saved!").color("#55FF55"));
                    }
                }
                // Refresh page to show updated values
                reopenPage(player, ref, store);
                return;
            }

            // Handle add preset
            if (action.equals("ADD_PRESET")) {
                // For now, just show a message - could open a naming dialog
                if (player != null) {
                    player.sendMessage(Message.raw("Use /breedconfig savepreset <name> to save current config as preset").color("#FFAA00"));
                }
                return;
            }

            // Unknown action - close
            closePage(player, ref, store);

        } catch (Exception e) {
            log("Error in handleDataEvent: " + e.getMessage());
        }
    }

    private void reopenPage(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (player != null) {
            try {
                PlayerRef playerRef = player.getPlayerRef();
                player.getPageManager().openCustomPage(ref, store,
                    new ConfigPanelUIPage(playerRef, presetPage, animalPage,
                        selectedAnimal, presetSearchFilter, animalSearchFilter));
            } catch (Exception e) {
                log("Failed to reopen config page: " + e.getMessage());
            }
        }
    }

    private void closePage(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (player != null) {
            try {
                player.getPageManager().setPage(ref, store, Page.None);
            } catch (Exception e) {
                log("Failed to close page: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && HyTamePlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[ConfigPanel] " + message);
        }
    }
}
