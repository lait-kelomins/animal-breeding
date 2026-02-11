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
import com.hytame.patch.PatchSyncService;
import com.hytame.util.ConfigManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration panel for Animal Breeding plugin.
 *
 * Layout:
 * - Left column: Preset list with search/pagination + preset settings
 * - Right column: Animal table with inline editing (icon, toggle, cooldown, growth, foods)
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/ConfigPanel.ui
 */
public class ConfigPanelUIPage extends InteractiveCustomUIPage<ConfigPanelUIPage.ConfigEventData> {

    private static final int PRESETS_PER_PAGE = 8;
    private static final int ANIMALS_PER_PAGE = 10;

    // State
    private int presetPage = 0;
    private int animalPage = 0;
    private String presetSearchFilter = "";
    private String animalSearchFilter = "";
    private boolean dirty = false;

    /**
     * Event data received from UI interactions.
     * Contains preset fields + 24 row fields (8 rows x 3 editable fields).
     */
    public static class ConfigEventData {
        public String action;
        public String growthTime;
        public String cooldown;
        public String presetSearch;
        public String animalSearch;
        public String presetRename;
        public String[] rowCooldown = new String[10];
        public String[] rowGrowth = new String[10];
        public String[] rowFoods = new String[10];

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
            .append(new KeyedCodec<>("@presetRename", new StringCodec()),
                (obj, val) -> obj.presetRename = val,
                obj -> obj.presetRename)
            .add()
            // Row 0
            .append(new KeyedCodec<>("@row0Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[0] = val, obj -> obj.rowCooldown[0])
            .add()
            .append(new KeyedCodec<>("@row0Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[0] = val, obj -> obj.rowGrowth[0])
            .add()
            .append(new KeyedCodec<>("@row0Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[0] = val, obj -> obj.rowFoods[0])
            .add()
            // Row 1
            .append(new KeyedCodec<>("@row1Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[1] = val, obj -> obj.rowCooldown[1])
            .add()
            .append(new KeyedCodec<>("@row1Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[1] = val, obj -> obj.rowGrowth[1])
            .add()
            .append(new KeyedCodec<>("@row1Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[1] = val, obj -> obj.rowFoods[1])
            .add()
            // Row 2
            .append(new KeyedCodec<>("@row2Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[2] = val, obj -> obj.rowCooldown[2])
            .add()
            .append(new KeyedCodec<>("@row2Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[2] = val, obj -> obj.rowGrowth[2])
            .add()
            .append(new KeyedCodec<>("@row2Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[2] = val, obj -> obj.rowFoods[2])
            .add()
            // Row 3
            .append(new KeyedCodec<>("@row3Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[3] = val, obj -> obj.rowCooldown[3])
            .add()
            .append(new KeyedCodec<>("@row3Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[3] = val, obj -> obj.rowGrowth[3])
            .add()
            .append(new KeyedCodec<>("@row3Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[3] = val, obj -> obj.rowFoods[3])
            .add()
            // Row 4
            .append(new KeyedCodec<>("@row4Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[4] = val, obj -> obj.rowCooldown[4])
            .add()
            .append(new KeyedCodec<>("@row4Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[4] = val, obj -> obj.rowGrowth[4])
            .add()
            .append(new KeyedCodec<>("@row4Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[4] = val, obj -> obj.rowFoods[4])
            .add()
            // Row 5
            .append(new KeyedCodec<>("@row5Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[5] = val, obj -> obj.rowCooldown[5])
            .add()
            .append(new KeyedCodec<>("@row5Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[5] = val, obj -> obj.rowGrowth[5])
            .add()
            .append(new KeyedCodec<>("@row5Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[5] = val, obj -> obj.rowFoods[5])
            .add()
            // Row 6
            .append(new KeyedCodec<>("@row6Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[6] = val, obj -> obj.rowCooldown[6])
            .add()
            .append(new KeyedCodec<>("@row6Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[6] = val, obj -> obj.rowGrowth[6])
            .add()
            .append(new KeyedCodec<>("@row6Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[6] = val, obj -> obj.rowFoods[6])
            .add()
            // Row 7
            .append(new KeyedCodec<>("@row7Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[7] = val, obj -> obj.rowCooldown[7])
            .add()
            .append(new KeyedCodec<>("@row7Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[7] = val, obj -> obj.rowGrowth[7])
            .add()
            .append(new KeyedCodec<>("@row7Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[7] = val, obj -> obj.rowFoods[7])
            .add()
            // Row 8
            .append(new KeyedCodec<>("@row8Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[8] = val, obj -> obj.rowCooldown[8])
            .add()
            .append(new KeyedCodec<>("@row8Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[8] = val, obj -> obj.rowGrowth[8])
            .add()
            .append(new KeyedCodec<>("@row8Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[8] = val, obj -> obj.rowFoods[8])
            .add()
            // Row 9
            .append(new KeyedCodec<>("@row9Cooldown", new StringCodec()),
                (obj, val) -> obj.rowCooldown[9] = val, obj -> obj.rowCooldown[9])
            .add()
            .append(new KeyedCodec<>("@row9Growth", new StringCodec()),
                (obj, val) -> obj.rowGrowth[9] = val, obj -> obj.rowGrowth[9])
            .add()
            .append(new KeyedCodec<>("@row9Foods", new StringCodec()),
                (obj, val) -> obj.rowFoods[9] = val, obj -> obj.rowFoods[9])
            .add()
            .build();
    }

    public ConfigPanelUIPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    /**
     * Constructor with state preservation for pagination and dirty tracking.
     */
    public ConfigPanelUIPage(PlayerRef playerRef, int presetPage, int animalPage,
                             String presetSearch, String animalSearch, boolean dirty) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
        this.presetPage = presetPage;
        this.animalPage = animalPage;
        this.presetSearchFilter = presetSearch != null ? presetSearch : "";
        this.animalSearchFilter = animalSearch != null ? animalSearch : "";
        this.dirty = dirty;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/ConfigPanel.ui");

        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) return;

        ConfigManager config = plugin.getConfigManager();
        if (config == null) return;

        // Populate preset settings
        populatePresetSettings(cmd, config);

        // Populate preset list
        List<String> presets = getFilteredPresets(config);
        populatePresetList(cmd, events, presets, config.getActivePreset());

        // Populate animal table
        List<AnimalType> animals = getFilteredAnimals();
        populateAnimalTable(cmd, events, animals, config);

        // Set up action markers and event bindings
        setupEventBindings(cmd, events);

        // Restore search field values so they persist across page rebuilds
        cmd.set("#presetSearch.Value", presetSearchFilter);
        cmd.set("#animalSearch.Value", animalSearchFilter);

        // Dirty state indicator on save button
        cmd.set("#saveBtn.Text", dirty ? "SAVE TO FILE *" : "SAVE TO FILE");
    }

    /**
     * Populate the preset settings section with current values.
     */
    private void populatePresetSettings(UICommandBuilder cmd, ConfigManager config) {
        String activePreset = config.getActivePreset();
        cmd.set("#presetRenameInput.Value", activePreset != null ? activePreset : "none");
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

        for (int i = 0; i < PRESETS_PER_PAGE; i++) {
            int presetIndex = startIndex + i;
            String slotId = "#preset" + i;
            String actionFieldId = "#presetAction" + i;

            if (presetIndex < presets.size()) {
                String presetName = presets.get(presetIndex);
                cmd.set(slotId + ".Text", presetName);
                cmd.set(actionFieldId + ".Value", "SELECT_PRESET:" + presetName);

                events.addEventBinding(CustomUIEventBindingType.Activating, slotId,
                    new EventData()
                        .append("@action", actionFieldId + ".Value")
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            } else {
                cmd.set(slotId + ".Text", "");
                cmd.set(actionFieldId + ".Value", "");
            }
        }

        cmd.set("#presetPageLabel.Text", (presetPage + 1) + "/" + totalPages);

        cmd.set("#actionPresetPrev.Value", "PRESET_PREV");
        cmd.set("#actionPresetNext.Value", "PRESET_NEXT");

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
     * Populate the animal table rows with inline-editable fields.
     */
    private void populateAnimalTable(UICommandBuilder cmd, UIEventBuilder events,
                                      List<AnimalType> animals, ConfigManager config) {
        int totalPages = Math.max(1, (animals.size() + ANIMALS_PER_PAGE - 1) / ANIMALS_PER_PAGE);
        animalPage = Math.min(animalPage, totalPages - 1);

        int startIndex = animalPage * ANIMALS_PER_PAGE;

        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            int animalIndex = startIndex + i;
            String rowPrefix = "#row" + i;

            if (animalIndex < animals.size()) {
                AnimalType animal = animals.get(animalIndex);
                ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);

                // Set name label above icon
                cmd.set(rowPrefix + "Name.Text", getAbbreviatedName(animal));

                // Build icon via appendInline
                cmd.clear(rowPrefix + "Icon");
                String iconPath = getAnimalIconPath(animal);
                StringBuilder iconMarkup = new StringBuilder();
                iconMarkup.append("Group #icon {\n");
                iconMarkup.append("  Anchor: (Width: 48, Height: 48);\n");
                if (iconPath != null) {
                    iconMarkup.append("  Background: (TexturePath: \"").append(iconPath).append("\");\n");
                } else {
                    iconMarkup.append("  Background: ").append(getAnimalBackgroundColor(animal)).append(";\n");
                }
                iconMarkup.append("}");
                cmd.appendInline(rowPrefix + "Icon", iconMarkup.toString());

                // Breeding toggle text
                boolean breedEnabled = animalConfig != null && animalConfig.breedingEnabled;
                cmd.set(rowPrefix + "Toggle.Text", breedEnabled ? "ON" : "OFF");

                // Taming toggle text
                boolean tameEnabled = animalConfig != null && animalConfig.tamingEnabled;
                cmd.set(rowPrefix + "TameToggle.Text", tameEnabled ? "ON" : "OFF");

                // Cooldown value
                double cooldownVal = animalConfig != null ? animalConfig.breedCooldownMinutes : 0;
                cmd.set(rowPrefix + "Cooldown.Value", String.valueOf(cooldownVal));

                // Growth value
                double growthVal = animalConfig != null ? animalConfig.growthTimeMinutes : 0;
                cmd.set(rowPrefix + "Growth.Value", String.valueOf(growthVal));

                // Foods value (comma-separated)
                String foods = "";
                if (animalConfig != null) {
                    List<String> foodList = animalConfig.getEffectiveBreedingFoods();
                    if (foodList != null) {
                        foods = String.join(", ", foodList);
                    }
                }
                cmd.set(rowPrefix + "Foods.Value", foods);

                // Set hidden action fields and bind toggle buttons
                cmd.set("#rowAction" + i + ".Value", "TOGGLE_ROW:" + animal.name());
                events.addEventBinding(CustomUIEventBindingType.Activating, rowPrefix + "Toggle",
                    buildFullEventData("#rowAction" + i + ".Value"));

                cmd.set("#rowTamingAction" + i + ".Value", "TOGGLE_TAMING:" + animal.name());
                events.addEventBinding(CustomUIEventBindingType.Activating, rowPrefix + "TameToggle",
                    buildFullEventData("#rowTamingAction" + i + ".Value"));
            } else {
                // Empty row — clear content (row stays at full height)
                cmd.clear(rowPrefix);
            }
        }

        // Pagination
        cmd.set("#animalPageLabel.Text", (animalPage + 1) + "/" + totalPages);

        cmd.set("#actionAnimalPrev.Value", "ANIMAL_PREV");
        cmd.set("#actionAnimalNext.Value", "ANIMAL_NEXT");

        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalPrevBtn",
            buildFullEventData("#actionAnimalPrev.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalNextBtn",
            buildFullEventData("#actionAnimalNext.Value"));
    }

    /**
     * Build EventData that captures all row fields + preset fields + action.
     * Shared by toggle buttons, save button, pagination, and growth toggle.
     */
    private EventData buildFullEventData(String actionRef) {
        EventData data = new EventData()
            .append("@action", actionRef)
            .append("@growthTime", "#growthTimeInput.Value")
            .append("@cooldown", "#cooldownInput.Value")
            .append("@presetSearch", "#presetSearch.Value")
            .append("@animalSearch", "#animalSearch.Value")
            .append("@presetRename", "#presetRenameInput.Value");
        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            data.append("@row" + i + "Cooldown", "#row" + i + "Cooldown.Value");
            data.append("@row" + i + "Growth", "#row" + i + "Growth.Value");
            data.append("@row" + i + "Foods", "#row" + i + "Foods.Value");
        }
        return data;
    }

    /**
     * Set up hidden action markers and event bindings for non-row buttons.
     */
    private void setupEventBindings(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.set("#actionToggleGrowth.Value", "TOGGLE_GROWTH");
        cmd.set("#actionSave.Value", "SAVE");
        cmd.set("#actionAddPreset.Value", "ADD_PRESET");
        cmd.set("#actionSearchPresets.Value", "SEARCH_PRESETS");
        cmd.set("#actionSearchAnimals.Value", "SEARCH_ANIMALS");
        cmd.set("#actionRenamePreset.Value", "RENAME_PRESET");

        // Growth toggle button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#growthToggleBtn",
            buildFullEventData("#actionToggleGrowth.Value"));

        // Save button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveBtn",
            buildFullEventData("#actionSave.Value"));

        // Add preset button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#addPresetBtn",
            new EventData()
                .append("@action", "#actionAddPreset.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Rename preset button — only capture rename field, not row data
        events.addEventBinding(CustomUIEventBindingType.Activating, "#renamePresetBtn",
            new EventData()
                .append("@action", "#actionRenamePreset.Value")
                .append("@presetRename", "#presetRenameInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Search buttons (GO) — click to apply search filter
        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetSearchBtn",
            new EventData()
                .append("@action", "#actionSearchPresets.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalSearchBtn",
            new EventData()
                .append("@action", "#actionSearchAnimals.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
    }

    /**
     * Apply all row edits from event data to in-memory config.
     * Returns true if any parsing errors occurred.
     */
    private boolean applyRowEdits(ConfigEventData data, ConfigManager config) {
        List<AnimalType> animals = getFilteredAnimals();
        int startIndex = animalPage * ANIMALS_PER_PAGE;
        boolean hasErrors = false;

        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            int animalIndex = startIndex + i;
            if (animalIndex >= animals.size()) break;

            AnimalType animal = animals.get(animalIndex);

            // Parse and apply cooldown
            String cooldownStr = data.rowCooldown[i];
            if (cooldownStr != null && !cooldownStr.isEmpty()) {
                try {
                    double val = Double.parseDouble(cooldownStr.trim());
                    if (val >= 0) {
                        config.setBreedingCooldown(animal, val);
                    } else {
                        hasErrors = true;
                    }
                } catch (NumberFormatException e) {
                    hasErrors = true;
                }
            }

            // Parse and apply growth
            String growthStr = data.rowGrowth[i];
            if (growthStr != null && !growthStr.isEmpty()) {
                try {
                    double val = Double.parseDouble(growthStr.trim());
                    if (val > 0) {
                        config.setGrowthTime(animal, val);
                    } else {
                        hasErrors = true;
                    }
                } catch (NumberFormatException e) {
                    hasErrors = true;
                }
            }

            // Parse and apply foods (comma-separated)
            String foodsStr = data.rowFoods[i];
            if (foodsStr != null) {
                String trimmed = foodsStr.trim();
                if (!trimmed.isEmpty()) {
                    List<String> foods = new ArrayList<>();
                    for (String f : trimmed.split(",")) {
                        String ft = f.trim();
                        if (!ft.isEmpty()) {
                            foods.add(ft);
                        }
                    }
                    if (!foods.isEmpty()) {
                        config.setBreedingFoods(animal, foods);
                    }
                }
            }
        }

        return hasErrors;
    }

    /**
     * Apply global settings (growth time, cooldown) from event data to in-memory config.
     * Returns true if any value was changed.
     */
    private boolean applyGlobalSettings(ConfigEventData data, ConfigManager config) {
        boolean changed = false;
        if (data.growthTime != null && !data.growthTime.isEmpty()) {
            try {
                double val = Double.parseDouble(data.growthTime.trim());
                if (val > 0 && val != config.getDefaultGrowthTimeMinutes()) {
                    config.setDefaultGrowthTime(val);
                    changed = true;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (data.cooldown != null && !data.cooldown.isEmpty()) {
            try {
                double val = Double.parseDouble(data.cooldown.trim());
                if (val >= 0 && val != config.getDefaultBreedCooldownMinutes()) {
                    config.setDefaultBreedCooldown(val);
                    changed = true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return changed;
    }

    /**
     * Preserve current field edits to in-memory config before any page refresh.
     * Checks for differences BEFORE applying edits so dirty tracking works correctly.
     */
    private void preserveEdits(ConfigEventData data, ConfigManager config) {
        boolean changed = false;

        // Check global settings for changes BEFORE applying
        if (data.growthTime != null && !data.growthTime.isEmpty()) {
            try {
                double val = Double.parseDouble(data.growthTime.trim());
                if (val > 0 && val != config.getDefaultGrowthTimeMinutes()) changed = true;
            } catch (NumberFormatException ignored) {}
        }
        if (data.cooldown != null && !data.cooldown.isEmpty()) {
            try {
                double val = Double.parseDouble(data.cooldown.trim());
                if (val >= 0 && val != config.getDefaultBreedCooldownMinutes()) changed = true;
            } catch (NumberFormatException ignored) {}
        }

        // Check row values for changes BEFORE applying
        List<AnimalType> animals = getFilteredAnimals();
        int startIndex = animalPage * ANIMALS_PER_PAGE;
        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            int animalIndex = startIndex + i;
            if (animalIndex >= animals.size()) break;
            AnimalType animal = animals.get(animalIndex);
            ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
            if (ac == null) continue;

            if (data.rowCooldown[i] != null && !data.rowCooldown[i].isEmpty()) {
                try {
                    double val = Double.parseDouble(data.rowCooldown[i].trim());
                    if (val >= 0 && val != ac.breedCooldownMinutes) changed = true;
                } catch (NumberFormatException ignored) {}
            }
            if (data.rowGrowth[i] != null && !data.rowGrowth[i].isEmpty()) {
                try {
                    double val = Double.parseDouble(data.rowGrowth[i].trim());
                    if (val > 0 && val != ac.growthTimeMinutes) changed = true;
                } catch (NumberFormatException ignored) {}
            }
            if (data.rowFoods[i] != null && !data.rowFoods[i].trim().isEmpty()) {
                String current = "";
                List<String> foodList = ac.getEffectiveBreedingFoods();
                if (foodList != null) current = String.join(", ", foodList);
                if (!data.rowFoods[i].trim().equals(current)) changed = true;
            }
        }

        // NOW apply edits after checking
        applyGlobalSettings(data, config);
        applyRowEdits(data, config);

        if (changed) dirty = true;
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

            // --- Search actions (immediate filtering) ---
            if (action.equals("SEARCH_PRESETS")) {
                preserveEdits(data, config);
                presetPage = 0;
                reopenPage(player, ref, store);
                return;
            }
            if (action.equals("SEARCH_ANIMALS")) {
                preserveEdits(data, config);
                animalPage = 0;
                reopenPage(player, ref, store);
                return;
            }

            // --- Preset selection ---
            if (action.startsWith("SELECT_PRESET:")) {
                String presetName = action.substring("SELECT_PRESET:".length());
                if (config.applyPreset(presetName)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Applied preset: " + presetName).color("#55FF55"));
                    }
                }
                dirty = false;
                reopenPage(player, ref, store);
                return;
            }

            // --- Rename preset ---
            if (action.equals("RENAME_PRESET")) {
                String newName = data.presetRename != null ? data.presetRename.trim() : "";
                String oldName = config.getActivePreset();
                log("Rename: old='" + oldName + "' new='" + newName + "'");
                if (newName.isEmpty()) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Enter a new name for the preset.").color("#FF5555"));
                    }
                    reopenPage(player, ref, store);
                    return;
                }
                if (newName.equals(oldName)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Name unchanged ('" + newName + "').").color("#FFAA00"));
                    }
                    reopenPage(player, ref, store);
                    return;
                }
                if (config.isBuiltinPreset(oldName)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Cannot rename built-in preset: " + oldName).color("#FF5555"));
                    }
                    reopenPage(player, ref, store);
                    return;
                }
                if (config.renamePreset(oldName, newName)) {
                    config.saveToFile();
                    if (player != null) {
                        player.sendMessage(Message.raw("Renamed preset: " + oldName + " -> " + newName).color("#55FF55"));
                    }
                } else {
                    if (player != null) {
                        player.sendMessage(Message.raw("Failed to rename. Use only letters, numbers, _ and - (no spaces).").color("#FF5555"));
                    }
                }
                reopenPage(player, ref, store);
                return;
            }

            // --- Row breeding toggle ---
            if (action.startsWith("TOGGLE_ROW:")) {
                String animalName = action.substring("TOGGLE_ROW:".length());
                preserveEdits(data, config);
                try {
                    AnimalType animal = AnimalType.valueOf(animalName);
                    ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
                    if (animalConfig != null) {
                        boolean newState = !animalConfig.breedingEnabled;
                        config.setAnimalEnabled(animal, newState);
                        if (player != null) {
                            player.sendMessage(Message.raw(animal.getModelAssetId() + " breeding " +
                                (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                        }
                    }
                } catch (Exception e) {
                    log("Error toggling animal breeding: " + e.getMessage());
                }
                dirty = true;
                reopenPage(player, ref, store);
                return;
            }

            // --- Row taming toggle ---
            if (action.startsWith("TOGGLE_TAMING:")) {
                String animalName = action.substring("TOGGLE_TAMING:".length());
                preserveEdits(data, config);
                try {
                    AnimalType animal = AnimalType.valueOf(animalName);
                    ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
                    if (animalConfig != null) {
                        boolean newState = !animalConfig.tamingEnabled;
                        config.setTamingEnabled(animal, newState);
                        if (player != null) {
                            player.sendMessage(Message.raw(animal.getModelAssetId() + " taming " +
                                (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                        }
                    }
                } catch (Exception e) {
                    log("Error toggling animal taming: " + e.getMessage());
                }
                dirty = true;
                reopenPage(player, ref, store);
                return;
            }

            // --- Preset pagination ---
            if (action.equals("PRESET_PREV")) {
                preserveEdits(data, config);
                if (presetPage > 0) presetPage--;
                reopenPage(player, ref, store);
                return;
            }
            if (action.equals("PRESET_NEXT")) {
                preserveEdits(data, config);
                List<String> presets = getFilteredPresets(config);
                int totalPages = (presets.size() + PRESETS_PER_PAGE - 1) / PRESETS_PER_PAGE;
                if (presetPage < totalPages - 1) presetPage++;
                reopenPage(player, ref, store);
                return;
            }

            // --- Animal pagination (preserve edits before changing page) ---
            if (action.equals("ANIMAL_PREV")) {
                preserveEdits(data, config);
                if (animalPage > 0) animalPage--;
                reopenPage(player, ref, store);
                return;
            }
            if (action.equals("ANIMAL_NEXT")) {
                preserveEdits(data, config);
                List<AnimalType> animals = getFilteredAnimals();
                int totalPages = (animals.size() + ANIMALS_PER_PAGE - 1) / ANIMALS_PER_PAGE;
                if (animalPage < totalPages - 1) animalPage++;
                reopenPage(player, ref, store);
                return;
            }

            // --- Toggle growth ---
            if (action.equals("TOGGLE_GROWTH")) {
                preserveEdits(data, config);
                boolean newState = !config.isGrowthEnabled();
                config.setGrowthEnabled(newState);
                if (player != null) {
                    player.sendMessage(Message.raw("Baby growth " + (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                }
                dirty = true;
                reopenPage(player, ref, store);
                return;
            }

            // --- Save ---
            if (action.equals("SAVE")) {
                boolean hasErrors = false;

                // Apply global settings
                applyGlobalSettings(data, config);

                // Apply all row edits
                if (applyRowEdits(data, config)) {
                    hasErrors = true;
                }

                // Save to file and sync patches
                config.saveToFile();
                PatchSyncService patchSync = plugin.getPatchSyncService();
                if (patchSync != null) {
                    patchSync.syncAllPatches();
                }
                dirty = false;

                if (player != null) {
                    if (hasErrors) {
                        player.sendMessage(Message.raw("Saved with some invalid values ignored.").color("#FFAA00"));
                    } else {
                        player.sendMessage(Message.raw("Configuration saved and patches synced!").color("#55FF55"));
                    }
                }
                reopenPage(player, ref, store);
                return;
            }

            // --- Add preset ---
            if (action.equals("ADD_PRESET")) {
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
                        presetSearchFilter, animalSearchFilter, dirty));
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

    /**
     * Get abbreviated display name for animal to fit in icon label.
     */
    private String getAbbreviatedName(AnimalType animal) {
        String name = animal.getModelAssetId();

        if (name.contains("_")) {
            String[] parts = name.split("_");
            String base = parts[0].toUpperCase();
            String variant = parts.length > 1 ? parts[1] : "";

            if (base.length() <= 7 && !variant.isEmpty()) {
                return base + " " + variant.substring(0, 1).toUpperCase();
            }
            if (base.length() > 9) {
                return base.substring(0, 8) + "..";
            }
            return base;
        }

        name = name.toUpperCase();
        if (name.length() > 9) {
            return name.substring(0, 8) + "..";
        }
        return name;
    }

    /**
     * Get the icon path for an animal model.
     */
    private String getAnimalIconPath(AnimalType animal) {
        String modelAssetId = animal.getModelAssetId();

        if (modelAssetId.contains("_")) {
            String[] parts = modelAssetId.split("_");
            String suffix = parts[parts.length - 1];

            boolean hasOwnIcon = suffix.equals("Calf") || suffix.equals("Piglet") ||
                suffix.equals("Chick") || suffix.equals("Lamb") || suffix.equals("Foal") ||
                suffix.equals("Kid") || suffix.equals("Undead") || suffix.equals("Ice") ||
                suffix.equals("Void") || suffix.equals("Grizzly") || suffix.equals("Polar") ||
                suffix.equals("Doe") || suffix.equals("Stag") || suffix.equals("Bull") ||
                suffix.equals("Desert") || suffix.equals("Snow") || suffix.equals("Black") ||
                suffix.equals("White") || suffix.equals("Frost") || suffix.equals("Electric");

            if (!hasOwnIcon) {
                String baseName = parts[0];
                return "Pages/Memories/npcs/" + baseName + ".png";
            }
        }

        return "Pages/Memories/npcs/" + modelAssetId + ".png";
    }

    /**
     * Get background color for an animal based on its category.
     */
    private String getAnimalBackgroundColor(AnimalType animal) {
        switch (animal.getCategory()) {
            case LIVESTOCK:
                return "#2a4a2a";
            case MAMMAL:
                return "#4a3a2a";
            case AVIAN:
                return "#2a3a4a";
            case CRITTER:
                return "#4a4a2a";
            case AQUATIC:
                return "#2a4a4a";
            case REPTILE:
                return "#3a4a2a";
            case VERMIN:
            case SCARAK:
                return "#4a2a4a";
            case MYTHIC:
                return "#4a2a3a";
            default:
                return "#2a2a3a";
        }
    }

    private void log(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && HyTamePlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[ConfigPanel] " + message);
        }
    }
}
