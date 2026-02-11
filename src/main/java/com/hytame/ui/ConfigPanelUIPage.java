package com.hytame.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
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
import java.util.Set;

/**
 * Configuration panel for Animal Breeding plugin.
 *
 * Layout:
 * - Left column: Preset list with search/pagination + preset settings
 * - Right column: Scrollable animal list + detail panel for selected animal
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/ConfigPanel.ui
 */
public class ConfigPanelUIPage extends InteractiveCustomUIPage<ConfigPanelUIPage.ConfigEventData> {

    // Known food items for the food picker
    private static final String[][] KNOWN_FOODS = {
        // Crops
        {"Plant_Crop_Wheat_Item", "Wheat"},
        {"Plant_Crop_Carrot_Item", "Carrot"},
        {"Plant_Crop_Potato_Item", "Potato"},
        {"Plant_Crop_Cauliflower_Item", "Cauliflower"},
        {"Plant_Crop_Lettuce_Item", "Lettuce"},
        {"Plant_Crop_Corn_Item", "Corn"},
        {"Plant_Crop_Rice_Item", "Rice"},
        {"Plant_Crop_Chilli_Item", "Chilli"},
        {"Plant_Crop_Mushroom_Cap_Brown", "Brown Mushroom"},
        {"Plant_Crop_Mushroom_Cap_Red", "Red Mushroom"},
        // Fruits & Plants
        {"Plant_Fruit_Apple", "Apple"},
        {"Plant_Fruit_Berries_Red", "Red Berries"},
        {"Plant_Cactus_Flower", "Cactus Flower"},
        // Raw Meats
        {"Food_Beef_Raw", "Raw Beef"},
        {"Food_Chicken_Raw", "Raw Chicken"},
        {"Food_Pork_Raw", "Raw Pork"},
        {"Food_Fish_Raw", "Raw Fish"},
        {"Food_Wildmeat_Raw", "Raw Wildmeat"},
        // Cooked Meats
        {"Food_Fish_Grilled", "Grilled Fish"},
        {"Food_Wildmeat_Cooked", "Cooked Wildmeat"},
        // Other
        {"Food_Bread", "Bread"},
    };

    // State
    private String presetSearchFilter = "";
    private String animalSearchFilter = "";
    private String foodSearchFilter = "";
    private String selectedAnimalName = null;
    private boolean dirty = false;

    // Cached lists matching what's currently in the UI (set during build/search, used by sendUpdate)
    private List<AnimalType> currentFilteredAnimals = new ArrayList<>();
    private List<String> currentFilteredPresets = new ArrayList<>();

    /**
     * Event data received from UI interactions.
     * Contains preset fields + detail panel fields (for selected animal).
     */
    public static class ConfigEventData {
        public String action;
        public String growthTime;
        public String cooldown;
        public String presetSearch;
        public String animalSearch;
        public String presetRename;
        public String detailCooldown;
        public String detailGrowth;
        public String foodSearch;

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
            .append(new KeyedCodec<>("@detailCooldown", new StringCodec()),
                (obj, val) -> obj.detailCooldown = val,
                obj -> obj.detailCooldown)
            .add()
            .append(new KeyedCodec<>("@detailGrowth", new StringCodec()),
                (obj, val) -> obj.detailGrowth = val,
                obj -> obj.detailGrowth)
            .add()
            .append(new KeyedCodec<>("@foodSearch", new StringCodec()),
                (obj, val) -> obj.foodSearch = val,
                obj -> obj.foodSearch)
            .add()
            .build();
    }

    public ConfigPanelUIPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    /**
     * Constructor with state preservation for selected animal and dirty tracking.
     */
    public ConfigPanelUIPage(PlayerRef playerRef,
                             String presetSearch, String animalSearch,
                             String selectedAnimalName, boolean dirty) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
        this.presetSearchFilter = presetSearch != null ? presetSearch : "";
        this.animalSearchFilter = animalSearch != null ? animalSearch : "";
        this.selectedAnimalName = selectedAnimalName;
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

        // Populate scrollable animal list
        List<AnimalType> animals = getFilteredAnimals();
        populateAnimalTable(cmd, events, animals, config);

        // Populate detail panel for selected animal
        populateDetailPanel(cmd, config);

        // Populate food picker for selected animal
        populateFoodPicker(cmd, events, config);

        // Set up action markers and event bindings
        setupEventBindings(cmd, events);

        // Restore search field values so they persist across page rebuilds
        cmd.set("#presetSearch.Value", presetSearchFilter);
        cmd.set("#animalSearch.Value", animalSearchFilter);
        cmd.set("#foodSearch.Value", foodSearchFilter);

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
        cmd.set("#growthToggleBtn.Background", config.isGrowthEnabled() ? "#2a6a2a" : "#6a2a2a");
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
     * Populate the preset list with dynamic rows.
     */
    private void populatePresetList(UICommandBuilder cmd, UIEventBuilder events,
                                    List<String> presets, String activePreset) {
        cmd.clear("#presetList");
        this.currentFilteredPresets = new ArrayList<>(presets);

        for (int i = 0; i < presets.size(); i++) {
            String presetName = presets.get(i);
            String sel = "#presetList[" + i + "]";

            cmd.append("#presetList", "Pages/ConfigPresetRow.ui");
            cmd.set(sel + " #presetBtn.Text", presetName);
            cmd.set(sel + " #presetAction.Value", "SELECT_PRESET:" + presetName);

            // Highlight active preset
            if (presetName.equals(activePreset)) {
                cmd.set(sel + ".Background", "#3a5a8a");
            }

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #presetBtn",
                new EventData()
                    .append("@action", sel + " #presetAction.Value")
                    .append("@presetSearch", "#presetSearch.Value")
                    .append("@animalSearch", "#animalSearch.Value"));
        }
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
     * Populate the scrollable animal list with dynamic rows.
     */
    private void populateAnimalTable(UICommandBuilder cmd, UIEventBuilder events,
                                      List<AnimalType> animals, ConfigManager config) {
        cmd.clear("#animalRows");

        // Cache the filtered list so sendUpdate actions can find indices
        this.currentFilteredAnimals = new ArrayList<>(animals);

        for (int i = 0; i < animals.size(); i++) {
            AnimalType animal = animals.get(i);
            ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
            String sel = "#animalRows[" + i + "]";

            // Append a row template
            cmd.append("#animalRows", "Pages/AnimalRow.ui");

            // Set name on select button (full name since button stretches)
            cmd.set(sel + " #selectBtn.Text", animal.getModelAssetId());

            // Build icon via appendInline
            String iconPath = getAnimalIconPath(animal);
            StringBuilder iconMarkup = new StringBuilder();
            iconMarkup.append("Group {\n");
            iconMarkup.append("  Anchor: (Width: 36, Height: 36);\n");
            if (iconPath != null) {
                iconMarkup.append("  Background: (TexturePath: \"").append(iconPath).append("\");\n");
            } else {
                iconMarkup.append("  Background: ").append(getAnimalBackgroundColor(animal)).append(";\n");
            }
            iconMarkup.append("}");
            cmd.appendInline(sel + " #icon", iconMarkup.toString());

            // Breeding toggle
            boolean breedEnabled = animalConfig != null && animalConfig.breedingEnabled;
            cmd.set(sel + " #breedToggle.Text", breedEnabled ? "ON" : "OFF");
            cmd.set(sel + " #breedToggle.Background", breedEnabled ? "#2a6a2a" : "#6a2a2a");

            // Taming toggle
            boolean tameEnabled = animalConfig != null && animalConfig.tamingEnabled;
            cmd.set(sel + " #tameToggle.Text", tameEnabled ? "ON" : "OFF");
            cmd.set(sel + " #tameToggle.Background", tameEnabled ? "#2a6a2a" : "#6a2a2a");

            // Highlight selected row
            if (animal.name().equals(selectedAnimalName)) {
                cmd.set(sel + ".Background", "#3a5a8a");
            }

            // Set hidden action field values for this row
            cmd.set(sel + " #selectAction.Value", "SELECT_ANIMAL:" + animal.name());
            cmd.set(sel + " #breedAction.Value", "TOGGLE_BREED:" + animal.name());
            cmd.set(sel + " #tameAction.Value", "TOGGLE_TAMING:" + animal.name());

            // Event bindings — each references its own hidden action field
            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #selectBtn",
                buildDetailEventData(sel + " #selectAction.Value"));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #breedToggle",
                buildDetailEventData(sel + " #breedAction.Value"));

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #tameToggle",
                buildDetailEventData(sel + " #tameAction.Value"));
        }
    }

    /**
     * Populate the detail panel with the selected animal's values.
     */
    private void populateDetailPanel(UICommandBuilder cmd, ConfigManager config) {
        if (selectedAnimalName == null) {
            cmd.set("#detailTitle.Text", "Select an animal");
            cmd.set("#detailCooldown.Value", "");
            cmd.set("#detailGrowth.Value", "");
            return;
        }

        try {
            AnimalType animal = AnimalType.valueOf(selectedAnimalName);
            ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);

            cmd.set("#detailTitle.Text", animal.getModelAssetId());

            if (animalConfig != null) {
                cmd.set("#detailCooldown.Value", String.valueOf(animalConfig.breedCooldownMinutes));
                cmd.set("#detailGrowth.Value", String.valueOf(animalConfig.growthTimeMinutes));
            } else {
                cmd.set("#detailCooldown.Value", "0");
                cmd.set("#detailGrowth.Value", "0");
            }
        } catch (Exception e) {
            cmd.set("#detailTitle.Text", "Unknown animal");
            cmd.set("#detailCooldown.Value", "");
            cmd.set("#detailGrowth.Value", "");
        }
    }

    /**
     * Populate the food picker list for the selected animal.
     * Enabled foods pinned at top with highlighted background.
     */
    private void populateFoodPicker(UICommandBuilder cmd, UIEventBuilder events, ConfigManager config) {
        cmd.clear("#foodList");

        if (selectedAnimalName == null) return;

        List<String> enabledFoods;
        try {
            AnimalType animal = AnimalType.valueOf(selectedAnimalName);
            ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
            enabledFoods = ac != null ? ac.getEffectiveBreedingFoods() : new ArrayList<>();
        } catch (Exception e) {
            enabledFoods = new ArrayList<>();
        }

        List<String[]> filtered = getFilteredFoods(enabledFoods);

        for (int i = 0; i < filtered.size(); i++) {
            String[] food = filtered.get(i);
            String foodId = food[0];
            String displayName = food[1];
            boolean enabled = enabledFoods.contains(foodId);
            String sel = "#foodList[" + i + "]";

            cmd.append("#foodList", "Pages/FoodRow.ui");

            // Food icon via TexturePath (appendInline with simple string Background)
            String foodIconPath = getFoodIconPath(foodId);
            if (foodIconPath != null) {
                cmd.appendInline(sel + " #foodIcon",
                    "Group { Anchor: (Width: 24, Height: 24); Background: \"" + foodIconPath + "\"; }");
            } else {
                cmd.set(sel + " #foodIcon.Background", enabled ? "#2a6a2a" : "#1a1a2a");
            }

            String label = displayName + (enabled ? "  [ON]" : "  [OFF]");
            cmd.set(sel + " #foodBtn.Text", label);
            cmd.set(sel + " #foodAction.Value", "TOGGLE_FOOD:" + foodId);

            if (enabled) {
                cmd.set(sel + ".Background", "#3a5a8a");
            }

            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #foodBtn",
                new EventData()
                    .append("@action", sel + " #foodAction.Value")
                    .append("@detailCooldown", "#detailCooldown.Value")
                    .append("@detailGrowth", "#detailGrowth.Value")
                    .append("@foodSearch", "#foodSearch.Value")
                    .append("@growthTime", "#growthTimeInput.Value")
                    .append("@cooldown", "#cooldownInput.Value")
                    .append("@presetSearch", "#presetSearch.Value")
                    .append("@animalSearch", "#animalSearch.Value")
                    .append("@presetRename", "#presetRenameInput.Value"));
        }
    }

    /**
     * Get known foods filtered by foodSearchFilter, sorted with enabled first then alphabetical.
     */
    private List<String[]> getFilteredFoods(List<String> enabledFoods) {
        List<String[]> enabled = new ArrayList<>();
        List<String[]> disabled = new ArrayList<>();

        String filter = foodSearchFilter != null ? foodSearchFilter.toLowerCase() : "";

        for (String[] food : KNOWN_FOODS) {
            String foodId = food[0];
            String displayName = food[1];

            // Apply search filter
            if (!filter.isEmpty() &&
                !displayName.toLowerCase().contains(filter) &&
                !foodId.toLowerCase().contains(filter)) {
                continue;
            }

            if (enabledFoods.contains(foodId)) {
                enabled.add(food);
            } else {
                disabled.add(food);
            }
        }

        // Sort each group alphabetically by display name
        enabled.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        disabled.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));

        // Enabled first, then disabled
        List<String[]> result = new ArrayList<>(enabled);
        result.addAll(disabled);
        return result;
    }

    /**
     * Build EventData that captures detail panel + global fields + action.
     * Used for all row interactions (select, toggle breed, toggle tame).
     * @param actionRef UI element path to read action from (e.g., "#animalRows[0] #selectAction.Value")
     */
    private EventData buildDetailEventData(String actionRef) {
        return new EventData()
            .append("@action", actionRef)
            .append("@detailCooldown", "#detailCooldown.Value")
            .append("@detailGrowth", "#detailGrowth.Value")
            .append("@foodSearch", "#foodSearch.Value")
            .append("@growthTime", "#growthTimeInput.Value")
            .append("@cooldown", "#cooldownInput.Value")
            .append("@presetSearch", "#presetSearch.Value")
            .append("@animalSearch", "#animalSearch.Value")
            .append("@presetRename", "#presetRenameInput.Value");
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
        cmd.set("#actionSearchFoods.Value", "SEARCH_FOODS");
        cmd.set("#actionDetailChanged.Value", "DETAIL_CHANGED");

        // Growth toggle button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#growthToggleBtn",
            new EventData()
                .append("@action", "#actionToggleGrowth.Value")
                .append("@detailCooldown", "#detailCooldown.Value")
                .append("@detailGrowth", "#detailGrowth.Value")
                .append("@foodSearch", "#foodSearch.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value")
                .append("@presetRename", "#presetRenameInput.Value"));

        // Save button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveBtn",
            new EventData()
                .append("@action", "#actionSave.Value")
                .append("@detailCooldown", "#detailCooldown.Value")
                .append("@detailGrowth", "#detailGrowth.Value")
                .append("@foodSearch", "#foodSearch.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value")
                .append("@presetRename", "#presetRenameInput.Value"));

        // Add preset button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#addPresetBtn",
            new EventData()
                .append("@action", "#actionAddPreset.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Rename preset button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#renamePresetBtn",
            new EventData()
                .append("@action", "#actionRenamePreset.Value")
                .append("@presetRename", "#presetRenameInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Search buttons (GO)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetSearchBtn",
            buildDetailEventData("#actionSearchPresets.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalSearchBtn",
            buildDetailEventData("#actionSearchAnimals.Value"));

        // Live search — ValueChanged fires on every keystroke
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#presetSearch",
            buildDetailEventData("#actionSearchPresets.Value"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#animalSearch",
            buildDetailEventData("#actionSearchAnimals.Value"));

        // Food search — live filter
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#foodSearch",
            buildDetailEventData("#actionSearchFoods.Value"));

        // Detail field dirty tracking — fire on every keystroke in cooldown/growth
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#detailCooldown",
            buildDetailEventData("#actionDetailChanged.Value"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#detailGrowth",
            buildDetailEventData("#actionDetailChanged.Value"));
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
     * Preserve detail panel edits to the selected animal's config before page refresh.
     */
    private void preserveDetailEdits(ConfigEventData data, ConfigManager config) {
        if (selectedAnimalName == null) return;

        boolean changed = false;

        // Apply global settings
        if (applyGlobalSettings(data, config)) {
            changed = true;
        }

        // Apply detail panel edits to selected animal
        try {
            AnimalType animal = AnimalType.valueOf(selectedAnimalName);
            ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
            if (ac == null) return;

            // Cooldown
            if (data.detailCooldown != null && !data.detailCooldown.isEmpty()) {
                try {
                    double val = Double.parseDouble(data.detailCooldown.trim());
                    if (val >= 0 && val != ac.breedCooldownMinutes) {
                        config.setBreedingCooldown(animal, val);
                        changed = true;
                    }
                } catch (NumberFormatException ignored) {}
            }

            // Growth
            if (data.detailGrowth != null && !data.detailGrowth.isEmpty()) {
                try {
                    double val = Double.parseDouble(data.detailGrowth.trim());
                    if (val > 0 && val != ac.growthTimeMinutes) {
                        config.setGrowthTime(animal, val);
                        changed = true;
                    }
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}

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
            if (data.foodSearch != null) {
                foodSearchFilter = data.foodSearch;
            }

            String action = data.action;
            if (action == null) {
                action = "";
            }
            action = action.trim();

            log("Config panel action: " + action);

            // --- Search actions (live filtering via sendPartialUpdate) ---
            if (action.equals("SEARCH_PRESETS")) {
                preserveDetailEdits(data, config);
                presetSearchFilter = data.presetSearch != null ? data.presetSearch : "";
                List<String> presets = getFilteredPresets(config);

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                populatePresetList(cmd, ev, presets, config.getActivePreset());
                cmd.set("#saveBtn.Text", dirty ? "SAVE TO FILE *" : "SAVE TO FILE");

                sendPartialUpdate(cmd, ev);
                return;
            }
            if (action.equals("SEARCH_ANIMALS")) {
                preserveDetailEdits(data, config);
                animalSearchFilter = data.animalSearch != null ? data.animalSearch : "";
                List<AnimalType> animals = getFilteredAnimals();

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                populateAnimalTable(cmd, ev, animals, config);
                populateDetailPanel(cmd, config);
                populateFoodPicker(cmd, ev, config);
                cmd.set("#saveBtn.Text", dirty ? "SAVE TO FILE *" : "SAVE TO FILE");

                sendPartialUpdate(cmd, ev);
                return;
            }

            // --- Food search (live filtering) ---
            if (action.equals("SEARCH_FOODS")) {
                foodSearchFilter = data.foodSearch != null ? data.foodSearch : "";

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();
                populateFoodPicker(cmd, ev, config);

                sendPartialUpdate(cmd, ev);
                return;
            }

            // --- Detail field dirty tracking (cooldown/growth keystroke) ---
            if (action.equals("DETAIL_CHANGED")) {
                if (selectedAnimalName != null) {
                    try {
                        AnimalType animal = AnimalType.valueOf(selectedAnimalName);
                        ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
                        if (ac != null) {
                            boolean fieldDirty = false;
                            if (data.detailCooldown != null && !data.detailCooldown.isEmpty()) {
                                try {
                                    double val = Double.parseDouble(data.detailCooldown.trim());
                                    if (val != ac.breedCooldownMinutes) fieldDirty = true;
                                } catch (NumberFormatException ignored) {}
                            }
                            if (data.detailGrowth != null && !data.detailGrowth.isEmpty()) {
                                try {
                                    double val = Double.parseDouble(data.detailGrowth.trim());
                                    if (val != ac.growthTimeMinutes) fieldDirty = true;
                                } catch (NumberFormatException ignored) {}
                            }
                            if (fieldDirty && !dirty) {
                                dirty = true;
                                UICommandBuilder cmd = new UICommandBuilder();
                                cmd.set("#saveBtn.Text", "SAVE TO FILE *");
                                sendUpdate(cmd);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return;
            }

            // --- Toggle food in picker ---
            if (action.startsWith("TOGGLE_FOOD:")) {
                String foodId = action.substring("TOGGLE_FOOD:".length());
                preserveDetailEdits(data, config);

                if (selectedAnimalName != null) {
                    try {
                        AnimalType animal = AnimalType.valueOf(selectedAnimalName);
                        ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
                        if (ac != null) {
                            List<String> foods = new ArrayList<>(ac.getEffectiveBreedingFoods());
                            if (foods.contains(foodId)) {
                                foods.remove(foodId);
                            } else {
                                foods.add(foodId);
                            }
                            config.setBreedingFoods(animal, foods);
                            dirty = true;

                            // Rebuild food list (order changes when toggling)
                            UICommandBuilder cmd = new UICommandBuilder();
                            UIEventBuilder ev = new UIEventBuilder();
                            populateFoodPicker(cmd, ev, config);
                            cmd.set("#saveBtn.Text", "SAVE TO FILE *");

                            sendPartialUpdate(cmd, ev);
                        }
                    } catch (Exception e) {
                        log("Error toggling food: " + e.getMessage());
                    }
                }
                return;
            }

            // --- Select animal (show in detail panel) ---
            // Uses sendUpdate() to preserve scroll position
            if (action.startsWith("SELECT_ANIMAL:")) {
                String animalName = action.substring("SELECT_ANIMAL:".length());
                preserveDetailEdits(data, config);

                int oldIdx = findAnimalIndex(selectedAnimalName);
                int newIdx = findAnimalIndex(animalName);
                selectedAnimalName = animalName;
                foodSearchFilter = "";  // Reset food search on animal change

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();

                // Remove old highlight
                if (oldIdx >= 0) {
                    cmd.set("#animalRows[" + oldIdx + "].Background", "#00000000");
                }
                // Add new highlight
                if (newIdx >= 0) {
                    cmd.set("#animalRows[" + newIdx + "].Background", "#3a5a8a");
                }
                // Update detail panel
                populateDetailPanel(cmd, config);
                // Rebuild food picker for new animal
                populateFoodPicker(cmd, ev, config);
                cmd.set("#foodSearch.Value", "");
                // Update save button (preserveDetailEdits may have set dirty)
                cmd.set("#saveBtn.Text", dirty ? "SAVE TO FILE *" : "SAVE TO FILE");

                sendPartialUpdate(cmd, ev);
                return;
            }

            // --- Breed toggle ---
            // Uses sendUpdate() to preserve scroll position
            if (action.startsWith("TOGGLE_BREED:")) {
                String animalName = action.substring("TOGGLE_BREED:".length());
                preserveDetailEdits(data, config);
                try {
                    AnimalType animal = AnimalType.valueOf(animalName);
                    ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
                    if (animalConfig != null) {
                        boolean newState = !animalConfig.breedingEnabled;
                        config.setAnimalEnabled(animal, newState);

                        dirty = true;
                        int idx = findAnimalIndex(animalName);
                        UICommandBuilder cmd = new UICommandBuilder();
                        if (idx >= 0) {
                            cmd.set("#animalRows[" + idx + "] #breedToggle.Text", newState ? "ON" : "OFF");
                            cmd.set("#animalRows[" + idx + "] #breedToggle.Background", newState ? "#2a6a2a" : "#6a2a2a");
                        }
                        cmd.set("#saveBtn.Text", "SAVE TO FILE *");
                        sendUpdate(cmd);

                        if (player != null) {
                            player.sendMessage(Message.raw(animal.getModelAssetId() + " breeding " +
                                (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                        }
                    }
                } catch (Exception e) {
                    log("Error toggling animal breeding: " + e.getMessage());
                }
                return;
            }

            // --- Taming toggle ---
            // Uses sendUpdate() to preserve scroll position
            if (action.startsWith("TOGGLE_TAMING:")) {
                String animalName = action.substring("TOGGLE_TAMING:".length());
                preserveDetailEdits(data, config);
                try {
                    AnimalType animal = AnimalType.valueOf(animalName);
                    ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
                    if (animalConfig != null) {
                        boolean newState = !animalConfig.tamingEnabled;
                        config.setTamingEnabled(animal, newState);

                        dirty = true;
                        int idx = findAnimalIndex(animalName);
                        UICommandBuilder cmd = new UICommandBuilder();
                        if (idx >= 0) {
                            cmd.set("#animalRows[" + idx + "] #tameToggle.Text", newState ? "ON" : "OFF");
                            cmd.set("#animalRows[" + idx + "] #tameToggle.Background", newState ? "#2a6a2a" : "#6a2a2a");
                        }
                        cmd.set("#saveBtn.Text", "SAVE TO FILE *");
                        sendUpdate(cmd);

                        if (player != null) {
                            player.sendMessage(Message.raw(animal.getModelAssetId() + " taming " +
                                (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                        }
                    }
                } catch (Exception e) {
                    log("Error toggling animal taming: " + e.getMessage());
                }
                return;
            }

            // --- Preset selection ---
            // Uses sendUpdate() to preserve animal list scroll position
            if (action.startsWith("SELECT_PRESET:")) {
                String presetName = action.substring("SELECT_PRESET:".length());
                String oldPreset = config.getActivePreset();
                if (config.applyPreset(presetName)) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Applied preset: " + presetName).color("#55FF55"));
                    }
                }
                dirty = false;

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder ev = new UIEventBuilder();

                // Update preset highlights
                int oldPresetIdx = findPresetIndex(oldPreset);
                int newPresetIdx = findPresetIndex(presetName);
                if (oldPresetIdx >= 0) {
                    cmd.set("#presetList[" + oldPresetIdx + "].Background", "#00000000");
                }
                if (newPresetIdx >= 0) {
                    cmd.set("#presetList[" + newPresetIdx + "].Background", "#2a3a5a");
                }

                // Update preset settings section
                populatePresetSettings(cmd, config);

                // Update all animal toggles (preset changes everything)
                for (int i = 0; i < currentFilteredAnimals.size(); i++) {
                    AnimalType animal = currentFilteredAnimals.get(i);
                    ConfigManager.AnimalConfig ac = config.getAnimalConfig(animal);
                    boolean breedEnabled = ac != null && ac.breedingEnabled;
                    boolean tameEnabled = ac != null && ac.tamingEnabled;
                    cmd.set("#animalRows[" + i + "] #breedToggle.Text", breedEnabled ? "ON" : "OFF");
                    cmd.set("#animalRows[" + i + "] #breedToggle.Background", breedEnabled ? "#2a6a2a" : "#6a2a2a");
                    cmd.set("#animalRows[" + i + "] #tameToggle.Text", tameEnabled ? "ON" : "OFF");
                    cmd.set("#animalRows[" + i + "] #tameToggle.Background", tameEnabled ? "#2a6a2a" : "#6a2a2a");
                }

                // Update detail panel, food picker, and save button
                populateDetailPanel(cmd, config);
                populateFoodPicker(cmd, ev, config);
                cmd.set("#saveBtn.Text", "SAVE TO FILE");

                sendPartialUpdate(cmd, ev);
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

            // --- Toggle growth ---
            if (action.equals("TOGGLE_GROWTH")) {
                preserveDetailEdits(data, config);
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
                // Apply global settings
                applyGlobalSettings(data, config);

                // Apply detail panel edits for selected animal
                preserveDetailEdits(data, config);

                // Save to file and sync patches
                config.saveToFile();
                PatchSyncService patchSync = plugin.getPatchSyncService();
                if (patchSync != null) {
                    patchSync.syncAllPatches();
                }
                dirty = false;

                if (player != null) {
                    player.sendMessage(Message.raw("Configuration saved and patches synced!").color("#55FF55"));
                }
                reopenPage(player, ref, store);
                return;
            }

            // --- Add preset ---
            if (action.equals("ADD_PRESET")) {
                preserveDetailEdits(data, config);
                String baseName = "custom";
                List<String> existing = config.getAvailablePresets();
                String newName = baseName;
                int counter = 1;
                while (existing.contains(newName)) {
                    newName = baseName + "_" + counter;
                    counter++;
                }
                if (config.saveAsPreset(newName)) {
                    config.applyPreset(newName);
                    dirty = false;
                    if (player != null) {
                        player.sendMessage(Message.raw("Created preset: " + newName).color("#55FF55"));
                    }
                } else {
                    if (player != null) {
                        player.sendMessage(Message.raw("Failed to create preset.").color("#FF5555"));
                    }
                }
                reopenPage(player, ref, store);
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
                    new ConfigPanelUIPage(playerRef,
                        presetSearchFilter, animalSearchFilter,
                        selectedAnimalName, dirty));
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

    // Animals without a Memories icon — show colored fallback instead of red X
    private static final Set<String> NO_MEMORY_ICON = Set.of(
        "Pig_Wild", "Mosshorn_Plain", "Frog_Blue", "Frog_Orange", "Dragon_Fire",
        "Skeleton", "Skeleton_Burnt", "Skeleton_Frost", "Skeleton_Sand",
        "Skeleton_Pirate", "Skeleton_Incandescent", "Golem_Guardian_Void",
        "Kweebec_Razorleaf", "Kweebec_Elder", "Hatworm"
    );

    /**
     * Get the icon path for an animal model.
     * Returns null for animals without a Memories icon.
     */
    private String getAnimalIconPath(AnimalType animal) {
        String id = animal.getModelAssetId();
        if (NO_MEMORY_ICON.contains(id)) return null;
        return "Pages/Memories/npcs/" + id + ".png";
    }

    /**
     * Get the icon path for a food item.
     * Item icons live at Common/Icons/ItemsGenerated/{id}.png
     * UI TexturePath resolves from Common/UI/Custom/ — try relative escape first.
     * Change FOOD_ICON_FORMAT to test different path formats.
     */
    private static final int FOOD_ICON_FORMAT = 5;
    private String getFoodIconPath(String foodId) {
        // Strip "_Item" suffix if present (e.g. "Plant_Crop_Lettuce_Item" -> "Plant_Crop_Lettuce")
        String iconId = foodId.endsWith("_Item") ? foodId.substring(0, foodId.length() - 5) : foodId;
        switch (FOOD_ICON_FORMAT) {
            case 1: return "../../Icons/ItemsGenerated/" + iconId + ".png";
            case 2: return "Icons/ItemsGenerated/" + iconId + ".png";
            case 3: return "../../../Common/Icons/ItemsGenerated/" + iconId + ".png";
            case 4: return "Pages/Memories/npcs/Sheep.png"; // TEST: known-working animal path
            case 5: return "Pages/Icons/" + iconId + ".png"; // Bundled in mod resources
            default: return null; // solid color fallback
        }
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

    /**
     * Find the index of an animal in the cached filtered list.
     * Returns -1 if not found.
     */
    private int findAnimalIndex(String animalName) {
        if (animalName == null || currentFilteredAnimals == null) return -1;
        for (int i = 0; i < currentFilteredAnimals.size(); i++) {
            if (currentFilteredAnimals.get(i).name().equals(animalName)) return i;
        }
        return -1;
    }

    /**
     * Find the index of a preset in the cached filtered list.
     */
    private int findPresetIndex(String presetName) {
        if (presetName == null || currentFilteredPresets == null) return -1;
        for (int i = 0; i < currentFilteredPresets.size(); i++) {
            if (currentFilteredPresets.get(i).equals(presetName)) return i;
        }
        return -1;
    }

    /**
     * Send a partial update with both commands AND event bindings, without clearing the page.
     * Unlike sendUpdate(), this includes event bindings (needed when rebuilding dynamic rows).
     */
    private void sendPartialUpdate(UICommandBuilder cmd, UIEventBuilder events) {
        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null) return;
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        playerComponent.getPageManager().updateCustomPage(
            new CustomPage(this.getClass().getName(), false, false, this.getLifetime(),
                cmd.getCommands(), events.getEvents()));
    }

    private void log(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && HyTamePlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[ConfigPanel] " + message);
        }
    }
}
