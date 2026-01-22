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
    private static final int ANIMALS_PER_PAGE = 15;  // 5 columns x 3 rows

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

        // Populate selected animal config
        populateSelectedAnimalConfig(cmd, config);

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

            if (presetIndex < presets.size()) {
                String presetName = presets.get(presetIndex);
                cmd.set(slotId + ".Text", presetName);

                // Bind click event
                events.addEventBinding(CustomUIEventBindingType.Activating, slotId,
                    new EventData()
                        .append("@action", "SELECT_PRESET:" + presetName)
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            } else {
                // Empty slot
                cmd.set(slotId + ".Text", "");
            }
        }

        // Update pagination label
        cmd.set("#presetPageLabel.Text", (presetPage + 1) + "/" + totalPages);

        // Bind pagination buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetPrevBtn",
            new EventData()
                .append("@action", "PRESET_PREV")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#presetNextBtn",
            new EventData()
                .append("@action", "PRESET_NEXT")
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
     * Populate the animal grid slots.
     */
    private void populateAnimalGrid(UICommandBuilder cmd, UIEventBuilder events, List<AnimalType> animals) {
        int totalPages = Math.max(1, (animals.size() + ANIMALS_PER_PAGE - 1) / ANIMALS_PER_PAGE);
        animalPage = Math.min(animalPage, totalPages - 1);

        int startIndex = animalPage * ANIMALS_PER_PAGE;

        // Populate animal slots
        for (int i = 0; i < ANIMALS_PER_PAGE; i++) {
            int animalIndex = startIndex + i;
            String slotId = "#animal" + i;

            if (animalIndex < animals.size()) {
                AnimalType animal = animals.get(animalIndex);
                // Use abbreviated name to fit in cell
                String displayName = getAbbreviatedName(animal);
                cmd.set(slotId + ".Text", displayName);

                // Bind click event
                events.addEventBinding(CustomUIEventBindingType.Activating, slotId,
                    new EventData()
                        .append("@action", "SELECT_ANIMAL:" + animal.name())
                        .append("@presetSearch", "#presetSearch.Value")
                        .append("@animalSearch", "#animalSearch.Value"));
            } else {
                // Empty slot
                cmd.set(slotId + ".Text", "");
            }
        }

        // Update pagination label
        cmd.set("#animalPageLabel.Text", (animalPage + 1) + "/" + totalPages);

        // Bind pagination buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalPrevBtn",
            new EventData()
                .append("@action", "ANIMAL_PREV")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#animalNextBtn",
            new EventData()
                .append("@action", "ANIMAL_NEXT")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));
    }

    /**
     * Get abbreviated display name for animal (max ~6 chars to fit in cell).
     */
    private String getAbbreviatedName(AnimalType animal) {
        String name = animal.getModelAssetId();
        // Remove common prefixes/suffixes
        name = name.replace("_", " ");
        // Take first word if multiple
        if (name.contains(" ")) {
            String[] parts = name.split(" ");
            name = parts[0];
        }
        // Truncate if still too long
        if (name.length() > 7) {
            name = name.substring(0, 6) + ".";
        }
        return name;
    }

    /**
     * Populate the selected animal config section.
     */
    private void populateSelectedAnimalConfig(UICommandBuilder cmd, ConfigManager config) {
        if (selectedAnimal == null) {
            cmd.set("#selectedAnimalName.Text", "(none selected)");
            cmd.set("#animalSettingsPlaceholder.Text", "Click an animal to edit its settings");
            return;
        }

        try {
            AnimalType animal = AnimalType.valueOf(selectedAnimal);
            cmd.set("#selectedAnimalName.Text", animal.getModelAssetId());

            // Show animal config info
            ConfigManager.AnimalConfig animalConfig = config.getAnimalConfig(animal);
            if (animalConfig != null) {
                String info = (animalConfig.enabled ? "Enabled" : "Disabled") +
                    "\nGrowth: " + animalConfig.growthTimeMinutes + " min" +
                    "\nCooldown: " + animalConfig.breedCooldownMinutes + " min";
                cmd.set("#animalSettingsPlaceholder.Text", info);
            } else {
                cmd.set("#animalSettingsPlaceholder.Text", "No config found");
            }
        } catch (Exception e) {
            cmd.set("#selectedAnimalName.Text", "(invalid)");
            cmd.set("#animalSettingsPlaceholder.Text", "Click an animal to edit its settings");
        }
    }

    /**
     * Set up hidden action markers and event bindings.
     */
    private void setupEventBindings(UICommandBuilder cmd, UIEventBuilder events) {
        // Hidden action markers
        cmd.set("#actionToggleGrowth.Value", "TOGGLE_GROWTH");
        cmd.set("#actionSave.Value", "SAVE");

        // Growth toggle button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#growthToggleBtn",
            new EventData()
                .append("@action", "#actionToggleGrowth.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Save button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveBtn",
            new EventData()
                .append("@action", "#actionSave.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value")
                .append("@presetSearch", "#presetSearch.Value")
                .append("@animalSearch", "#animalSearch.Value"));

        // Add preset button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#addPresetBtn",
            new EventData()
                .append("@action", "ADD_PRESET")
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

                // Parse growth time
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

                // Parse cooldown
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

                // Save to file
                config.saveToFile();

                if (player != null) {
                    if (hasErrors) {
                        player.sendMessage(Message.raw("Saved with some invalid values ignored.").color("#FFAA00"));
                    } else {
                        player.sendMessage(Message.raw("Configuration saved!").color("#55FF55"));
                    }
                }
                // Stay on page
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
