package com.laits.breeding.util;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.models.GrowthStage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages plugin configuration from JSON file.
 * Supports runtime changes, persistence, multiple breeding foods, and presets.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config version for migrations
    private static final int CURRENT_CONFIG_VERSION = 2;
    private int configVersion = 1;  // Will be upgraded on load if needed

    // Config data
    private final Map<AnimalType, AnimalConfig> animalConfigs = new EnumMap<>(AnimalType.class);
    private final Map<String, CustomAnimalConfig> customAnimals = new HashMap<>();  // key = modelAssetId
    private double defaultGrowthTimeMinutes = 30.0;
    private double defaultBreedCooldownMinutes = 5.0;
    private boolean debugMode = false;
    private boolean growthEnabled = true;  // Can be disabled to freeze baby growth
    private String activePreset = "default_extended";

    // Tameable animal groups (from tameable-animals integration)
    private Set<String> tameableAnimalGroups = new HashSet<>(Arrays.asList(
        "PreyBig", "PreySmall", "Livestock", "Critters"
    ));

    // File path for persistence
    private Path configFilePath;
    private Path presetsDirectory;
    private Consumer<String> logger;

    // ==================== SAFE JSON EXTRACTION HELPERS ====================

    /**
     * Safely extract a double from a JsonObject with a default value.
     * Handles null, missing keys, wrong types, and malformed values.
     */
    private static double safeGetDouble(JsonObject json, String key, double defaultValue) {
        try {
            if (json == null || !json.has(key)) return defaultValue;
            JsonElement elem = json.get(key);
            if (elem == null || elem.isJsonNull()) return defaultValue;
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
                return elem.getAsDouble();
            }
            // Try parsing string as double
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                return Double.parseDouble(elem.getAsString());
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Safely extract a boolean from a JsonObject with a default value.
     * Handles null, missing keys, wrong types, and malformed values.
     */
    private static boolean safeGetBoolean(JsonObject json, String key, boolean defaultValue) {
        try {
            if (json == null || !json.has(key)) return defaultValue;
            JsonElement elem = json.get(key);
            if (elem == null || elem.isJsonNull()) return defaultValue;
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isBoolean()) {
                return elem.getAsBoolean();
            }
            // Try parsing string as boolean
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                String str = elem.getAsString().toLowerCase();
                return "true".equals(str) || "1".equals(str) || "yes".equals(str);
            }
            // Try parsing number as boolean (0 = false, non-0 = true)
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) {
                return elem.getAsInt() != 0;
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Safely extract a String from a JsonObject with a default value.
     * Handles null, missing keys, and non-string values.
     */
    private static String safeGetString(JsonObject json, String key, String defaultValue) {
        try {
            if (json == null || !json.has(key)) return defaultValue;
            JsonElement elem = json.get(key);
            return safeGetString(elem, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Safely extract a String from a JsonElement with a default value.
     * Handles null and non-string values.
     */
    private static String safeGetString(JsonElement elem, String defaultValue) {
        try {
            if (elem == null || elem.isJsonNull()) return defaultValue;
            if (elem.isJsonPrimitive()) {
                return elem.getAsString();
            }
            return defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== END SAFE JSON HELPERS ====================

    /**
     * Configuration for a single animal type.
     *
     * Food system (v2+):
     * - baseFoods: Foods that can tame, breed, AND heal (default fallback)
     * - tamingFoods: Override for taming only (defaults to baseFoods if not set)
     * - breedingFoods: Override for breeding only (defaults to baseFoods if not set)
     * - Healing always uses union of all configured foods
     */
    public static class AnimalConfig {
        public boolean enabled = true;
        public List<String> baseFoods = new ArrayList<>();      // v2: tame + breed + heal
        public List<String> tamingFoods = null;                 // v2: override for taming only
        public List<String> breedingFoods = new ArrayList<>();  // v1 compat + v2 override
        public double growthTimeMinutes;
        public double breedCooldownMinutes;

        public AnimalConfig() {}

        public AnimalConfig(boolean enabled, List<String> breedingFoods, double growthTimeMinutes, double breedCooldownMinutes) {
            this.enabled = enabled;
            this.breedingFoods = breedingFoods != null ? new ArrayList<>(breedingFoods) : new ArrayList<>();
            this.baseFoods = new ArrayList<>(this.breedingFoods); // v2 migration: copy to baseFoods
            this.growthTimeMinutes = growthTimeMinutes;
            this.breedCooldownMinutes = breedCooldownMinutes;
        }

        public AnimalConfig(boolean enabled, String breedingFood, double growthTimeMinutes, double breedCooldownMinutes) {
            this.enabled = enabled;
            this.breedingFoods = new ArrayList<>();
            this.baseFoods = new ArrayList<>();
            if (breedingFood != null) {
                this.breedingFoods.add(breedingFood);
                this.baseFoods.add(breedingFood);
            }
            this.growthTimeMinutes = growthTimeMinutes;
            this.breedCooldownMinutes = breedCooldownMinutes;
        }

        /**
         * Get effective taming foods (tamingFoods ?? baseFoods).
         */
        public List<String> getEffectiveTamingFoods() {
            if (tamingFoods != null && !tamingFoods.isEmpty()) {
                return tamingFoods;
            }
            if (baseFoods != null && !baseFoods.isEmpty()) {
                return baseFoods;
            }
            return breedingFoods; // v1 fallback
        }

        /**
         * Get effective breeding foods (breedingFoods ?? baseFoods).
         */
        public List<String> getEffectiveBreedingFoods() {
            if (breedingFoods != null && !breedingFoods.isEmpty()) {
                return breedingFoods;
            }
            return baseFoods != null ? baseFoods : new ArrayList<>();
        }

        /**
         * Get all healing foods (union of all configured foods).
         */
        public Set<String> getAllHealingFoods() {
            Set<String> all = new HashSet<>();
            if (baseFoods != null) all.addAll(baseFoods);
            if (tamingFoods != null) all.addAll(tamingFoods);
            if (breedingFoods != null) all.addAll(breedingFoods);
            return all;
        }
    }

    /**
     * Result of looking up an animal by ID (unified for built-in and custom animals).
     */
    public static class AnimalLookupResult {
        private final AnimalType builtInType;
        private final CustomAnimalConfig customConfig;

        public AnimalLookupResult(AnimalType builtInType, CustomAnimalConfig customConfig) {
            this.builtInType = builtInType;
            this.customConfig = customConfig;
        }

        public boolean isBuiltIn() { return builtInType != null; }
        public boolean isCustom() { return customConfig != null; }
        public AnimalType getBuiltInType() { return builtInType; }
        public CustomAnimalConfig getCustomConfig() { return customConfig; }

        public String getId() {
            if (builtInType != null) return builtInType.name();
            if (customConfig != null) return customConfig.getModelAssetId();
            return null;
        }

        public String getDisplayName() {
            if (builtInType != null) return builtInType.getModelAssetId();
            if (customConfig != null) return customConfig.getDisplayName();
            return null;
        }
    }

    public ConfigManager() {
        loadDefaults();
    }

    /**
     * Set the logger for debug output.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    /**
     * Load default configuration values from AnimalType enum.
     * Livestock animals are enabled, others are disabled.
     */
    private void loadDefaults() {
        for (AnimalType type : AnimalType.values()) {
            boolean enabledByDefault = type.isLivestock();
            List<String> defaultFoods = new ArrayList<>();
            defaultFoods.add(type.getDefaultBreedingFood());

            animalConfigs.put(type, new AnimalConfig(
                enabledByDefault,
                defaultFoods,
                defaultGrowthTimeMinutes,
                defaultBreedCooldownMinutes
            ));
        }
    }

    /**
     * Load configuration from a JSON file.
     * @param configPath Path to the config file
     */
    public void loadFromFile(Path configPath) {
        this.configFilePath = configPath;
        this.presetsDirectory = configPath.getParent().resolve("presets");

        // Initialize presets directory and files
        initializePresets();

        if (!Files.exists(configPath)) {
            log("Config file not found, creating default: " + configPath);
            saveToFile();
            return;
        }

        try {
            String json = Files.readString(configPath);
            loadFromJson(json);
            log("Loaded config from: " + configPath);
        } catch (Exception e) {
            log("Error loading config: " + e.getMessage() + ", using defaults");
            loadDefaults();
        }
    }

    /**
     * Initialize the presets directory, create missing preset files, and update existing ones with new animals.
     */
    private void initializePresets() {
        try {
            if (!Files.exists(presetsDirectory)) {
                Files.createDirectories(presetsDirectory);
                log("Created presets directory: " + presetsDirectory);
            }

            // Built-in presets to manage
            String[] builtinPresets = {"default", "default_extended", "lait_curated", "zoo", "all"};

            for (String presetName : builtinPresets) {
                Path presetFile = presetsDirectory.resolve(presetName + ".json");
                if (!Files.exists(presetFile)) {
                    // Create new preset file
                    saveBuiltinPresetToFile(presetName, presetFile);
                    log("Created " + presetName + " preset file: " + presetFile);
                } else {
                    // Update existing preset with any missing animals
                    int added = updatePresetWithMissingAnimals(presetName, presetFile);
                    if (added > 0) {
                        log("Updated " + presetName + " preset: added " + added + " new animals");
                    }
                }
            }
        } catch (Exception e) {
            log("Error initializing presets: " + e.getMessage());
        }
    }

    /**
     * Update an existing preset file with any missing animals using the preset's default values.
     * @return number of animals added
     */
    private int updatePresetWithMissingAnimals(String presetName, Path presetFile) {
        try {
            // Read existing preset
            String json = Files.readString(presetFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject animals = root.has("animals") ? root.getAsJsonObject("animals") : new JsonObject();

            // Get the preset's default values for missing animals
            Map<AnimalType, AnimalConfig> presetDefaults = getBuiltinPresetConfigs(presetName);

            int addedCount = 0;
            for (AnimalType type : AnimalType.values()) {
                String key = type.name();
                if (!animals.has(key)) {
                    // Animal is missing - add it with preset defaults
                    AnimalConfig defaultConfig = presetDefaults.get(type);
                    if (defaultConfig != null) {
                        JsonObject animalJson = new JsonObject();
                        animalJson.addProperty("enabled", defaultConfig.enabled);

                        JsonArray foodsArray = new JsonArray();
                        for (String food : defaultConfig.breedingFoods) {
                            foodsArray.add(food);
                        }
                        animalJson.add("breedingFoods", foodsArray);

                        animalJson.addProperty("growthTimeMinutes", defaultConfig.growthTimeMinutes);
                        animalJson.addProperty("breedCooldownMinutes", defaultConfig.breedCooldownMinutes);

                        animals.add(key, animalJson);
                        addedCount++;
                    }
                }
            }

            if (addedCount > 0) {
                // Save updated preset
                root.add("animals", animals);
                Files.writeString(presetFile, GSON.toJson(root));
            }

            return addedCount;
        } catch (Exception e) {
            log("Error updating preset " + presetName + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * Get the built-in preset configurations for all animals.
     */
    private Map<AnimalType, AnimalConfig> getBuiltinPresetConfigs(String presetName) {
        // Save current state
        Map<AnimalType, AnimalConfig> originalConfigs = new EnumMap<>(AnimalType.class);
        for (Map.Entry<AnimalType, AnimalConfig> entry : animalConfigs.entrySet()) {
            AnimalConfig copy = new AnimalConfig();
            copy.enabled = entry.getValue().enabled;
            copy.breedingFoods = new ArrayList<>(entry.getValue().breedingFoods);
            copy.growthTimeMinutes = entry.getValue().growthTimeMinutes;
            copy.breedCooldownMinutes = entry.getValue().breedCooldownMinutes;
            originalConfigs.put(entry.getKey(), copy);
        }
        double originalGrowth = defaultGrowthTimeMinutes;
        double originalCooldown = defaultBreedCooldownMinutes;

        // Apply the preset to get its values
        switch (presetName) {
            case "default": applyBuiltinDefaultPreset(); break;
            case "default_extended": applyBuiltinDefaultExtendedPreset(); break;
            case "lait_curated": applyBuiltinLaitCuratedPreset(); break;
            case "zoo": applyBuiltinZooPreset(); break;
            case "all": applyBuiltinAllPreset(); break;
        }

        // Copy the preset configs
        Map<AnimalType, AnimalConfig> presetConfigs = new EnumMap<>(AnimalType.class);
        for (Map.Entry<AnimalType, AnimalConfig> entry : animalConfigs.entrySet()) {
            AnimalConfig copy = new AnimalConfig();
            copy.enabled = entry.getValue().enabled;
            copy.breedingFoods = new ArrayList<>(entry.getValue().breedingFoods);
            copy.growthTimeMinutes = entry.getValue().growthTimeMinutes;
            copy.breedCooldownMinutes = entry.getValue().breedCooldownMinutes;
            presetConfigs.put(entry.getKey(), copy);
        }

        // Restore original state
        animalConfigs.clear();
        animalConfigs.putAll(originalConfigs);
        defaultGrowthTimeMinutes = originalGrowth;
        defaultBreedCooldownMinutes = originalCooldown;

        return presetConfigs;
    }

    /**
     * Restore a built-in preset to its default values (overwrites the preset file).
     * @param presetName The preset name to restore
     * @return true if restored successfully
     */
    public boolean restorePreset(String presetName) {
        if (!isBuiltinPreset(presetName)) {
            log("Cannot restore non-builtin preset: " + presetName);
            return false;
        }

        try {
            Path presetFile = presetsDirectory.resolve(presetName + ".json");
            saveBuiltinPresetToFile(presetName, presetFile);
            log("Restored " + presetName + " preset to default values");
            return true;
        } catch (Exception e) {
            log("Error restoring preset: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a preset name is a built-in preset.
     */
    public boolean isBuiltinPreset(String presetName) {
        return presetName.equals("default") ||
               presetName.equals("default_extended") ||
               presetName.equals("lait_curated") ||
               presetName.equals("zoo") ||
               presetName.equals("all");
    }

    /**
     * Save a built-in preset to a JSON file.
     */
    private void saveBuiltinPresetToFile(String presetName, Path filePath) throws IOException {
        // Temporarily apply the preset to generate its JSON
        Map<AnimalType, AnimalConfig> originalConfigs = new EnumMap<>(AnimalType.class);
        for (Map.Entry<AnimalType, AnimalConfig> entry : animalConfigs.entrySet()) {
            AnimalConfig copy = new AnimalConfig();
            copy.enabled = entry.getValue().enabled;
            copy.breedingFoods = new ArrayList<>(entry.getValue().breedingFoods);
            copy.growthTimeMinutes = entry.getValue().growthTimeMinutes;
            copy.breedCooldownMinutes = entry.getValue().breedCooldownMinutes;
            originalConfigs.put(entry.getKey(), copy);
        }
        double originalGrowth = defaultGrowthTimeMinutes;
        double originalCooldown = defaultBreedCooldownMinutes;

        // Apply the built-in preset
        if (presetName.equals("default")) {
            applyBuiltinDefaultPreset();
        } else if (presetName.equals("default_extended")) {
            applyBuiltinDefaultExtendedPreset();
        } else if (presetName.equals("lait_curated")) {
            applyBuiltinLaitCuratedPreset();
        } else if (presetName.equals("zoo")) {
            applyBuiltinZooPreset();
        } else if (presetName.equals("all")) {
            applyBuiltinAllPreset();
        }

        // Generate JSON and save
        String json = toJson();
        Files.writeString(filePath, json);

        // Restore original config
        animalConfigs.clear();
        animalConfigs.putAll(originalConfigs);
        defaultGrowthTimeMinutes = originalGrowth;
        defaultBreedCooldownMinutes = originalCooldown;
    }

    /**
     * Load configuration from embedded resource.
     * @param resourcePath Path to resource (e.g., "/config.json")
     */
    public void loadFromResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log("Config resource not found: " + resourcePath + ", using defaults");
                return;
            }
            String json = new String(is.readAllBytes());
            loadFromJson(json);
            log("Loaded config from resource: " + resourcePath);
        } catch (Exception e) {
            log("Error loading config resource: " + e.getMessage() + ", using defaults");
        }
    }

    /**
     * Load configuration from JSON string.
     */
    public void loadFromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Load active preset name (using safe extraction)
            activePreset = safeGetString(root, "activePreset", activePreset);

            // Load defaults (using safe extraction)
            if (root.has("defaults") && root.get("defaults").isJsonObject()) {
                JsonObject defaults = root.getAsJsonObject("defaults");
                defaultGrowthTimeMinutes = safeGetDouble(defaults, "growthTimeMinutes", defaultGrowthTimeMinutes);
                defaultBreedCooldownMinutes = safeGetDouble(defaults, "breedCooldownMinutes", defaultBreedCooldownMinutes);
                growthEnabled = safeGetBoolean(defaults, "growthEnabled", growthEnabled);
            }

            // Load animal configs (using safe extraction)
            if (root.has("animals") && root.get("animals").isJsonObject()) {
                JsonObject animals = root.getAsJsonObject("animals");
                for (AnimalType type : AnimalType.values()) {
                    String key = type.name();
                    if (animals.has(key) && animals.get(key).isJsonObject()) {
                        JsonObject animalJson = animals.getAsJsonObject(key);
                        AnimalConfig config = animalConfigs.get(type);
                        if (config == null) {
                            config = new AnimalConfig();
                            animalConfigs.put(type, config);
                        }

                        config.enabled = safeGetBoolean(animalJson, "enabled", config.enabled);

                        // Support both single food (legacy) and multiple foods
                        if (animalJson.has("breedingFoods") && animalJson.get("breedingFoods").isJsonArray()) {
                            config.breedingFoods.clear();
                            JsonArray foodsArray = animalJson.getAsJsonArray("breedingFoods");
                            for (JsonElement elem : foodsArray) {
                                String food = safeGetString(elem, null);
                                if (food != null) {
                                    config.breedingFoods.add(food);
                                }
                            }
                        } else if (animalJson.has("breedingFood")) {
                            // Legacy single food support
                            String food = safeGetString(animalJson, "breedingFood", null);
                            if (food != null) {
                                config.breedingFoods.clear();
                                config.breedingFoods.add(food);
                            }
                        }

                        config.growthTimeMinutes = safeGetDouble(animalJson, "growthTimeMinutes", config.growthTimeMinutes);
                        config.breedCooldownMinutes = safeGetDouble(animalJson, "breedCooldownMinutes", config.breedCooldownMinutes);
                    }
                }
            }

            // Load custom animals (for mod support)
            if (root.has("customAnimals") && root.get("customAnimals").isJsonObject()) {
                customAnimals.clear();
                JsonObject customAnimalsJson = root.getAsJsonObject("customAnimals");
                for (String modelAssetId : customAnimalsJson.keySet()) {
                    try {
                        JsonObject customJson = customAnimalsJson.getAsJsonObject(modelAssetId);

                        // Parse breeding foods
                        List<String> foods = new ArrayList<>();
                        if (customJson.has("breedingFoods")) {
                            for (JsonElement elem : customJson.getAsJsonArray("breedingFoods")) {
                                foods.add(elem.getAsString());
                            }
                        }

                        // Parse other fields with defaults using safe extraction
                        String displayName = safeGetString(customJson, "displayName", modelAssetId);
                        double growthTime = safeGetDouble(customJson, "growthTimeMinutes", defaultGrowthTimeMinutes);
                        double breedCooldown = safeGetDouble(customJson, "breedCooldownMinutes", defaultBreedCooldownMinutes);
                        String babyNpcRole = safeGetString(customJson, "babyNpcRoleId", null);
                        String adultNpcRole = safeGetString(customJson, "adultNpcRoleId", modelAssetId);
                        boolean mountable = safeGetBoolean(customJson, "mountable", false);
                        boolean enabled = safeGetBoolean(customJson, "enabled", true);

                        CustomAnimalConfig customConfig = new CustomAnimalConfig(
                            modelAssetId, displayName, foods, growthTime, breedCooldown,
                            babyNpcRole, adultNpcRole, mountable, enabled
                        );
                        customAnimals.put(modelAssetId, customConfig);
                        log("Loaded custom animal: " + modelAssetId);
                    } catch (Exception e) {
                        log("Error parsing custom animal " + modelAssetId + ": " + e.getMessage());
                    }
                }
                log("Loaded " + customAnimals.size() + " custom animals");
            }
        } catch (Exception e) {
            log("Error parsing config JSON: " + e.getMessage());
        }
    }

    /**
     * Save current configuration to file.
     */
    public void saveToFile() {
        if (configFilePath == null) {
            log("No config file path set, cannot save");
            return;
        }

        try {
            String json = toJson();
            Files.createDirectories(configFilePath.getParent());
            Files.writeString(configFilePath, json);
            log("Saved config to: " + configFilePath);
        } catch (Exception e) {
            log("Error saving config: " + e.getMessage());
        }
    }

    /**
     * Convert current configuration to JSON string.
     */
    public String toJson() {
        JsonObject root = new JsonObject();

        // Active preset
        root.addProperty("activePreset", activePreset);

        // Defaults
        JsonObject defaults = new JsonObject();
        defaults.addProperty("growthTimeMinutes", defaultGrowthTimeMinutes);
        defaults.addProperty("breedCooldownMinutes", defaultBreedCooldownMinutes);
        defaults.addProperty("growthEnabled", growthEnabled);
        root.add("defaults", defaults);

        // Animals (grouped by category)
        JsonObject animals = new JsonObject();
        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config != null) {
                JsonObject animalJson = new JsonObject();
                animalJson.addProperty("enabled", config.enabled);

                // Multiple breeding foods
                JsonArray foodsArray = new JsonArray();
                for (String food : config.breedingFoods) {
                    foodsArray.add(food);
                }
                animalJson.add("breedingFoods", foodsArray);

                animalJson.addProperty("growthTimeMinutes", config.growthTimeMinutes);
                animalJson.addProperty("breedCooldownMinutes", config.breedCooldownMinutes);
                animals.add(type.name(), animalJson);
            }
        }
        root.add("animals", animals);

        // Custom animals (for mod support)
        if (!customAnimals.isEmpty()) {
            JsonObject customAnimalsJson = new JsonObject();
            for (CustomAnimalConfig custom : customAnimals.values()) {
                JsonObject customJson = new JsonObject();
                customJson.addProperty("enabled", custom.isEnabled());
                customJson.addProperty("displayName", custom.getDisplayName());

                JsonArray foodsArray = new JsonArray();
                for (String food : custom.getBreedingFoods()) {
                    foodsArray.add(food);
                }
                customJson.add("breedingFoods", foodsArray);

                customJson.addProperty("growthTimeMinutes", custom.getGrowthTimeMinutes());
                customJson.addProperty("breedCooldownMinutes", custom.getBreedCooldownMinutes());

                if (custom.getBabyNpcRoleId() != null) {
                    customJson.addProperty("babyNpcRoleId", custom.getBabyNpcRoleId());
                }
                if (!custom.getAdultNpcRoleId().equals(custom.getModelAssetId())) {
                    customJson.addProperty("adultNpcRoleId", custom.getAdultNpcRoleId());
                }
                if (custom.isMountable()) {
                    customJson.addProperty("mountable", true);
                }

                customAnimalsJson.add(custom.getModelAssetId(), customJson);
            }
            root.add("customAnimals", customAnimalsJson);
        }

        return GSON.toJson(root);
    }

    // ===========================================
    // PRESET SYSTEM
    // ===========================================

    /**
     * Get list of available preset names by scanning the presets directory.
     */
    public List<String> getAvailablePresets() {
        List<String> presets = new ArrayList<>();
        if (presetsDirectory != null && Files.exists(presetsDirectory)) {
            try {
                Files.list(presetsDirectory)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        name = name.substring(0, name.length() - 5); // Remove .json
                        presets.add(name);
                    });
            } catch (Exception e) {
                log("Error listing presets: " + e.getMessage());
            }
        }
        // Ensure default presets are always available
        if (!presets.contains("all")) presets.add("all");
        if (!presets.contains("default")) presets.add("default");
        if (!presets.contains("default_extended")) presets.add("default_extended");
        if (!presets.contains("lait_curated")) presets.add("lait_curated");
        if (!presets.contains("zoo")) presets.add("zoo");
        Collections.sort(presets);
        return presets;
    }

    /**
     * Get the currently active preset name.
     */
    public String getActivePreset() {
        return activePreset;
    }

    /**
     * Apply a preset by name. Loads from preset file if it exists.
     * @param presetName The preset name
     * @return true if preset was applied successfully
     */
    public boolean applyPreset(String presetName) {
        Path presetFile = presetsDirectory != null
            ? presetsDirectory.resolve(presetName + ".json")
            : null;

        // Try to load from file first
        if (presetFile != null && Files.exists(presetFile)) {
            try {
                String json = Files.readString(presetFile);
                loadFromJson(json);
                activePreset = presetName;
                log("Applied preset from file: " + presetFile);
                return true;
            } catch (Exception e) {
                log("Error loading preset file: " + e.getMessage());
            }
        }

        // Fall back to built-in presets
        switch (presetName.toLowerCase()) {
            case "default":
                applyBuiltinDefaultPreset();
                activePreset = "default";
                return true;
            case "default_extended":
                applyBuiltinDefaultExtendedPreset();
                activePreset = "default_extended";
                return true;
            case "lait_curated":
                applyBuiltinLaitCuratedPreset();
                activePreset = "lait_curated";
                return true;
            case "zoo":
                applyBuiltinZooPreset();
                activePreset = "zoo";
                return true;
            case "all":
                applyBuiltinAllPreset();
                activePreset = "all";
                return true;
            default:
                log("Preset not found: " + presetName);
                return false;
        }
    }

    // Pattern for valid preset names: alphanumeric, underscores, hyphens only
    private static final java.util.regex.Pattern VALID_PRESET_NAME =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Validate a preset name to prevent path traversal attacks.
     * Valid names contain only alphanumeric characters, underscores, and hyphens.
     * @param presetName The name to validate
     * @return true if the name is valid and safe
     */
    public boolean isValidPresetName(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return false;
        }
        // Check length (prevent excessively long names)
        if (presetName.length() > 64) {
            return false;
        }
        // Check for path traversal patterns
        if (presetName.contains("..") || presetName.contains("/") || presetName.contains("\\")) {
            return false;
        }
        // Only allow safe characters
        return VALID_PRESET_NAME.matcher(presetName).matches();
    }

    /**
     * Save the current configuration as a new preset.
     * @param presetName The name for the new preset
     * @return true if saved successfully
     */
    public boolean saveAsPreset(String presetName) {
        if (presetsDirectory == null) {
            log("Presets directory not initialized");
            return false;
        }
        // Validate preset name to prevent path traversal
        if (!isValidPresetName(presetName)) {
            log("Invalid preset name: " + presetName + " (must be alphanumeric with underscores/hyphens only)");
            return false;
        }
        try {
            Path presetFile = presetsDirectory.resolve(presetName + ".json");
            // Double-check that resolved path is within presets directory
            if (!presetFile.normalize().startsWith(presetsDirectory.normalize())) {
                log("Security error: preset path escapes presets directory");
                return false;
            }
            String json = toJson();
            Files.writeString(presetFile, json);
            log("Saved preset: " + presetFile);
            return true;
        } catch (Exception e) {
            log("Error saving preset: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the path to the presets directory.
     */
    public Path getPresetsDirectory() {
        return presetsDirectory;
    }

    /**
     * Apply the built-in default preset - original game values.
     */
    private void applyBuiltinDefaultPreset() {
        defaultGrowthTimeMinutes = 30.0;
        defaultBreedCooldownMinutes = 5.0;

        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config == null) {
                config = new AnimalConfig();
                animalConfigs.put(type, config);
            }

            // Only livestock enabled by default
            config.enabled = type.isLivestock();

            // Single default food
            config.breedingFoods.clear();
            config.breedingFoods.add(type.getDefaultBreedingFood());

            config.growthTimeMinutes = defaultGrowthTimeMinutes;
            config.breedCooldownMinutes = defaultBreedCooldownMinutes;
        }
    }

    /**
     * Apply the default_extended preset - default timings with lait_curated foods.
     * This is the recommended preset for most players.
     */
    private void applyBuiltinDefaultExtendedPreset() {
        // Use default timings
        defaultGrowthTimeMinutes = 30.0;
        defaultBreedCooldownMinutes = 5.0;

        // First apply lait_curated foods
        applyBuiltinLaitCuratedPreset();

        // Then override with default timings
        defaultGrowthTimeMinutes = 30.0;
        defaultBreedCooldownMinutes = 5.0;

        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config != null) {
                // Only livestock enabled
                config.enabled = type.isLivestock();
                // Default timings
                config.growthTimeMinutes = defaultGrowthTimeMinutes;
                config.breedCooldownMinutes = defaultBreedCooldownMinutes;
            }
        }
    }

    /**
     * Apply Lait's curated preset - more logical and diverse food options.
     */
    private void applyBuiltinLaitCuratedPreset() {
        defaultGrowthTimeMinutes = 20.0;  // Slightly faster growth
        defaultBreedCooldownMinutes = 3.0;  // Shorter cooldown

        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config == null) {
                config = new AnimalConfig();
                animalConfigs.put(type, config);
            }

            // Only livestock enabled
            config.enabled = type.isLivestock();
            config.breedingFoods.clear();

            // Set curated foods per animal with multiple options
            switch (type) {
                // === LIVESTOCK ===
                case COW:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",       // Wheat - classic cow food
                        "Plant_Crop_Cauliflower_Item", // Original
                        "Plant_Crop_Lettuce_Item"      // Leafy greens
                    ));
                    config.growthTimeMinutes = 25.0;
                    break;

                case PIG:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Carrot_Item",          // Carrots
                        "Plant_Crop_Potato_Item",          // Potatoes
                        "Plant_Crop_Mushroom_Cap_Brown",   // Original
                        "Plant_Fruit_Apple"                // Apples - pigs love fruit
                    ));
                    config.growthTimeMinutes = 15.0;  // Pigs grow fast
                    break;

                case CHICKEN:
                case CHICKEN_DESERT:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",    // Corn - original
                        "Plant_Crop_Wheat_Item",   // Wheat seeds
                        "Plant_Crop_Rice_Item"     // Rice
                    ));
                    config.growthTimeMinutes = 10.0;  // Chickens mature quickly
                    break;

                case TURKEY:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",    // Corn - original
                        "Plant_Crop_Wheat_Item"    // Wheat
                    ));
                    config.growthTimeMinutes = 15.0;
                    break;

                case SHEEP:
                case MOUFLON:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",       // Wheat - classic
                        "Plant_Crop_Lettuce_Item",     // Original
                        "Plant_Crop_Cauliflower_Item"  // Vegetables
                    ));
                    config.growthTimeMinutes = 20.0;
                    break;

                case RAM:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Fruit_Apple"   // Original
                    ));
                    config.growthTimeMinutes = 20.0;
                    break;

                case GOAT:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Fruit_Apple",           // Original - goats love fruit
                        "Plant_Crop_Wheat_Item",       // Wheat
                        "Plant_Crop_Carrot_Item"       // Carrots
                    ));
                    config.growthTimeMinutes = 18.0;
                    break;

                case HORSE:
                    config.enabled = false;
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Carrot_Item",  // Original - horses love carrots
                        "Plant_Fruit_Apple",       // Apples
                        "Plant_Crop_Wheat_Item"    // Hay/wheat
                    ));
                    config.growthTimeMinutes = 30.0;  // Horses take time to mature
                    break;

                case CAMEL:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",   // Original
                        "Plant_Cactus_Flower"      // Desert plant
                    ));
                    config.growthTimeMinutes = 35.0;  // Large animal, slow growth
                    break;

                case BISON:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Corn_Item"
                    ));
                    config.growthTimeMinutes = 35.0;  // Large animal
                    break;

                case RABBIT:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Carrot_Item",  // Original - rabbits love carrots
                        "Plant_Crop_Lettuce_Item", // Leafy greens
                        "Plant_Fruit_Apple"        // Fruit
                    ));
                    config.growthTimeMinutes = 8.0;  // Rabbits mature very fast
                    config.breedCooldownMinutes = 1.0;  // Breed like... rabbits
                    break;

                case BOAR:
                case WARTHOG:
                case PIG_WILD:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Mushroom_Cap_Red",    // Original for boar
                        "Plant_Crop_Mushroom_Cap_Brown",  // Brown mushrooms
                        "Plant_Fruit_Apple"               // Fallen fruit
                    ));
                    config.growthTimeMinutes = 18.0;
                    break;

                case SKRILL:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",
                        "Food_Wildmeat_Raw"    // Skrils might eat meat too
                    ));
                    config.growthTimeMinutes = 20.0;
                    break;

                // === MAMMALS (disabled by default but configured) ===
                case WOLF:
                case WOLF_WHITE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 25.0;
                    break;

                case FOX:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Raw",
                        "Food_Fish_Raw"
                    ));
                    config.growthTimeMinutes = 15.0;
                    break;

                case BEAR_GRIZZLY:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Fruit_Apple",
                        "Food_Fish_Raw",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 45.0;  // Bears are huge
                    break;

                case BEAR_POLAR:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Fish_Grilled",
                        "Food_Fish_Raw",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 45.0;
                    break;

                case DEER_DOE:
                case DEER_STAG:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Carrot_Item",
                        "Plant_Fruit_Apple",
                        "Plant_Crop_Wheat_Item"
                    ));
                    config.growthTimeMinutes = 25.0;
                    break;

                case MOOSE_BULL:
                case MOOSE_COW:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Lettuce_Item"
                    ));
                    config.growthTimeMinutes = 40.0;
                    break;

                // === CRITTERS ===
                case FROG_BLUE:
                case FROG_GREEN:
                case FROG_ORANGE:
                case GECKO:
                case LIZARD_SAND:
                    config.breedingFoods.add("Plant_Fruit_Berries_Red");
                    config.growthTimeMinutes = 5.0;  // Small, fast growth
                    break;

                case MEERKAT:
                    config.breedingFoods.add("Food_Wildmeat_Raw");  // Meerkats eat small prey
                    config.growthTimeMinutes = 5.0;
                    break;

                case MOUSE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Corn_Item"
                    ));
                    config.growthTimeMinutes = 3.0;
                    config.breedCooldownMinutes = 0.5;  // Very fast breeding
                    break;

                case SQUIRREL:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Fruit_Apple",
                        "Plant_Crop_Corn_Item"
                    ));
                    config.growthTimeMinutes = 5.0;
                    break;

                // === BIRDS ===
                case DUCK:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",
                        "Plant_Crop_Wheat_Item"
                    ));
                    config.growthTimeMinutes = 12.0;
                    break;

                case PIGEON:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Corn_Item"
                    ));
                    config.growthTimeMinutes = 10.0;
                    break;

                case PENGUIN:
                case FLAMINGO:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Fish_Raw"
                    ));
                    config.growthTimeMinutes = 20.0;
                    break;

                case PARROT:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",
                        "Plant_Fruit_Apple"
                    ));
                    config.growthTimeMinutes = 15.0;
                    break;

                case CROW:
                case RAVEN:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Corn_Item",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 12.0;
                    break;

                case OWL_BROWN:
                case OWL_SNOW:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 18.0;
                    break;

                // === REPTILES ===
                case TORTOISE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Lettuce_Item",
                        "Plant_Crop_Cauliflower_Item"
                    ));
                    config.growthTimeMinutes = 60.0;  // Tortoises are slow
                    break;

                case CROCODILE:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 50.0;
                    break;

                // === MYTHIC ===
                case EMBERWULF:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 40.0;
                    break;

                case YETI:
                case FEN_STALKER:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 50.0;
                    break;

                // === DINOSAURS ===
                case RAPTOR_CAVE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 35.0;  // Medium predator
                    break;

                case REX_CAVE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 60.0;  // Large dinosaur, slow growth
                    config.breedCooldownMinutes = 10.0;  // Long breeding cooldown
                    break;

                case ARCHAEOPTERYX:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Raw",
                        "Plant_Fruit_Berries_Red"  // Small bird/dino eats berries and small prey
                    ));
                    config.growthTimeMinutes = 20.0;  // Small flying dino
                    break;

                case PTERODACTYL:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Fish_Raw",
                        "Food_Fish_Grilled",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 40.0;  // Large flying dinosaur
                    break;

                // === NEW MAMMALS (v1.2.0) ===
                case HYENA:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 20.0;
                    break;

                case ANTELOPE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Lettuce_Item"
                    ));
                    config.growthTimeMinutes = 20.0;
                    break;

                case ARMADILLO:
                    config.breedingFoods.add("Plant_Fruit_Berries_Red");
                    config.growthTimeMinutes = 15.0;
                    break;

                case LEOPARD_SNOW:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Raw",
                        "Food_Wildmeat_Cooked"
                    ));
                    config.growthTimeMinutes = 35.0;
                    break;

                case MOSSHORN:
                case MOSSHORN_PLAIN:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Lettuce_Item"
                    ));
                    config.growthTimeMinutes = 40.0;  // Large creature
                    break;

                case TIGER_SABERTOOTH:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Raw",
                        "Food_Wildmeat_Cooked"
                    ));
                    config.growthTimeMinutes = 45.0;  // Apex predator
                    break;

                // === NEW BIRDS (v1.2.0) ===
                case BAT:
                case BAT_ICE:
                    config.breedingFoods.add("Plant_Fruit_Berries_Red");
                    config.growthTimeMinutes = 8.0;
                    break;

                case BLUEBIRD:
                case FINCH_GREEN:
                case SPARROW:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Fruit_Berries_Red"
                    ));
                    config.growthTimeMinutes = 8.0;  // Small birds
                    break;

                case WOODPECKER:
                    config.breedingFoods.add("Plant_Fruit_Berries_Red");
                    config.growthTimeMinutes = 10.0;
                    break;

                case HAWK:
                case VULTURE:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 20.0;
                    break;

                case TETRABIRD:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Raw",
                        "Food_Fish_Raw"
                    ));
                    config.growthTimeMinutes = 25.0;
                    break;

                // === NEW REPTILES (v1.2.0) ===
                case TOAD_RHINO:
                    config.breedingFoods.add("Plant_Fruit_Berries_Red");
                    config.growthTimeMinutes = 30.0;
                    break;

                case TOAD_RHINO_MAGMA:
                    config.breedingFoods.add("Food_Wildmeat_Cooked");
                    config.growthTimeMinutes = 35.0;
                    break;

                // === NEW MYTHIC (v1.2.0) ===
                case CACTEE:
                    config.breedingFoods.add("Plant_Cactus_Flower");
                    config.growthTimeMinutes = 20.0;
                    break;

                case HATWORM:
                    config.breedingFoods.add("Plant_Crop_Mushroom_Cap_Brown");
                    config.growthTimeMinutes = 15.0;
                    break;

                case SNAPDRAGON:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 25.0;
                    break;

                case SPARK_LIVING:
                    config.breedingFoods.add("Plant_Crop_Corn_Item");
                    config.growthTimeMinutes = 10.0;
                    break;

                case TRILLODON:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 40.0;
                    break;

                // === VERMIN (v1.2.0) ===
                case RAT:
                case MOLERAT:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Plant_Crop_Wheat_Item",
                        "Plant_Crop_Corn_Item"
                    ));
                    config.growthTimeMinutes = 3.0;
                    config.breedCooldownMinutes = 0.5;  // Fast breeders
                    break;

                case LARVA_SILK:
                    config.breedingFoods.add("Plant_Crop_Lettuce_Item");
                    config.growthTimeMinutes = 5.0;
                    break;

                case SCORPION:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 10.0;
                    break;

                case SLUG_MAGMA:
                case SNAIL_MAGMA:
                    config.breedingFoods.add("Food_Wildmeat_Cooked");
                    config.growthTimeMinutes = 8.0;
                    break;

                case SNAIL_FROST:
                    config.breedingFoods.add("Plant_Crop_Lettuce_Item");
                    config.growthTimeMinutes = 8.0;
                    break;

                case SNAKE_COBRA:
                case SNAKE_MARSH:
                case SNAKE_RATTLE:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 12.0;
                    break;

                case SPIDER:
                case SPIDER_CAVE:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 10.0;
                    break;

                // === AQUATIC (v1.2.0) ===
                // Freshwater fish
                case BLUEGILL:
                case CATFISH:
                case FROSTGILL:
                case MINNOW:
                case PIKE:
                case SALMON:
                case SNAPJAW:
                case TROUT_RAINBOW:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 10.0;
                    break;

                case PIRANHA:
                case PIRANHA_BLACK:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 8.0;
                    break;

                // Marine fish
                case CLOWNFISH:
                case TANG_BLUE:
                case TANG_CHEVRON:
                case TANG_LEMON_PEEL:
                case TANG_SAILFIN:
                case PUFFERFISH:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 12.0;
                    break;

                // Crustaceans
                case CRAB:
                case LOBSTER:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 15.0;
                    break;

                // Jellyfish
                case JELLYFISH_BLUE:
                case JELLYFISH_CYAN:
                case JELLYFISH_GREEN:
                case JELLYFISH_RED:
                case JELLYFISH_YELLOW:
                case JELLYFISH_MAN_OF_WAR:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 10.0;
                    break;

                // Deep sea / Abyssal
                case EEL_MORAY:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 20.0;
                    break;

                case SHARK_HAMMERHEAD:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Fish_Raw",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 60.0;  // Large predator
                    config.breedCooldownMinutes = 10.0;
                    break;

                case SHELLFISH_LAVA:
                    config.breedingFoods.add("Food_Wildmeat_Cooked");
                    config.growthTimeMinutes = 15.0;
                    break;

                case TRILOBITE:
                case TRILOBITE_BLACK:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 20.0;
                    break;

                case WHALE_HUMPBACK:
                    config.breedingFoods.add("Food_Fish_Raw");
                    config.growthTimeMinutes = 120.0;  // Massive creature
                    config.breedCooldownMinutes = 30.0;
                    break;

                // === BOSSES (v1.2.0) ===
                case DRAGON_FIRE:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Wildmeat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 180.0;  // Epic creature
                    config.breedCooldownMinutes = 60.0;
                    break;

                case DRAGON_FROST:
                    config.breedingFoods.addAll(Arrays.asList(
                        "Food_Fish_Raw",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 180.0;  // Epic creature
                    config.breedCooldownMinutes = 60.0;
                    break;

                default:
                    // For any not explicitly configured, use default food
                    config.breedingFoods.add(type.getDefaultBreedingFood());
                    config.growthTimeMinutes = defaultGrowthTimeMinutes;
                    break;
            }

            // Set cooldown if not already set
            if (config.breedCooldownMinutes == 0) {
                config.breedCooldownMinutes = defaultBreedCooldownMinutes;
            }
        }
    }

    /**
     * Apply zoo preset - real animals enabled for a zoo experience.
     * Uses lait_curated food/timing values but enables wild animals.
     * Includes: LIVESTOCK, MAMMAL, CRITTER, AVIAN, REPTILE, DINOSAUR, AQUATIC
     * Excludes: MYTHIC (fantasy), VERMIN (pests), BOSS (epic creatures)
     */
    private void applyBuiltinZooPreset() {
        // Start with lait_curated values for foods and timing
        applyBuiltinLaitCuratedPreset();

        // Enable animals appropriate for a zoo
        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config != null) {
                AnimalType.Category category = type.getCategory();
                // Enable real animals, exclude fantasy/dangerous/pests
                config.enabled = category != AnimalType.Category.MYTHIC &&
                                 category != AnimalType.Category.VERMIN &&
                                 category != AnimalType.Category.BOSS;
            }
        }
    }

    /**
     * Apply all preset - EVERYTHING enabled.
     * Uses lait_curated food/timing values and enables all 119 animals.
     * Includes: All categories (LIVESTOCK, MAMMAL, CRITTER, AVIAN, REPTILE,
     *           VERMIN, AQUATIC, MYTHIC, DINOSAUR, BOSS)
     */
    private void applyBuiltinAllPreset() {
        // Start with lait_curated values for foods and timing
        applyBuiltinLaitCuratedPreset();

        // Enable ALL animals
        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config != null) {
                config.enabled = true;
            }
        }
    }

    // ===========================================
    // GETTERS
    // ===========================================

    /**
     * Check if an animal type is enabled for breeding.
     */
    public boolean isAnimalEnabled(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        return config != null && config.enabled;
    }

    // ===========================================
    // CUSTOM ANIMAL GETTERS
    // ===========================================

    /**
     * Get a custom animal config by model asset ID.
     * @return CustomAnimalConfig or null if not found
     */
    public CustomAnimalConfig getCustomAnimal(String modelAssetId) {
        return customAnimals.get(modelAssetId);
    }

    /**
     * Check if a model asset ID is a registered custom animal.
     */
    public boolean isCustomAnimal(String modelAssetId) {
        return customAnimals.containsKey(modelAssetId);
    }

    /**
     * Check if a custom animal is enabled for breeding.
     */
    public boolean isCustomAnimalEnabled(String modelAssetId) {
        CustomAnimalConfig custom = customAnimals.get(modelAssetId);
        return custom != null && custom.isEnabled();
    }

    /**
     * Get all registered custom animals.
     */
    public Map<String, CustomAnimalConfig> getCustomAnimals() {
        return Collections.unmodifiableMap(customAnimals);
    }

    // ===========================================
    // UNIFIED ANIMAL LOOKUP (built-in + custom)
    // ===========================================

    /**
     * Look up any animal by ID (built-in or custom).
     * Tries built-in AnimalType first (case-insensitive), then custom animals.
     * @param id The animal identifier (e.g., "COW", "cow", "MyCustomCreature")
     * @return AnimalLookupResult or null if not found
     */
    public AnimalLookupResult lookupAnimal(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        // Try built-in first (case-insensitive)
        AnimalType type = AnimalType.fromModelAssetId(id);
        if (type != null) {
            return new AnimalLookupResult(type, null);
        }

        // Try custom animal (exact match first, then case-insensitive)
        CustomAnimalConfig custom = customAnimals.get(id);
        if (custom != null) {
            return new AnimalLookupResult(null, custom);
        }

        // Try case-insensitive custom animal lookup
        for (Map.Entry<String, CustomAnimalConfig> entry : customAnimals.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(id)) {
                return new AnimalLookupResult(null, entry.getValue());
            }
        }

        return null; // Not found
    }

    /**
     * Set growth time for any animal (built-in or custom).
     */
    public boolean setAnyAnimalGrowthTime(String id, double minutes) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            setGrowthTime(result.getBuiltInType(), minutes);
        } else {
            setCustomAnimalGrowthTime(result.getCustomConfig().getModelAssetId(), minutes);
        }
        return true;
    }

    /**
     * Set breeding cooldown for any animal (built-in or custom).
     */
    public boolean setAnyAnimalCooldown(String id, double minutes) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            setBreedingCooldown(result.getBuiltInType(), minutes);
        } else {
            setCustomAnimalCooldown(result.getCustomConfig().getModelAssetId(), minutes);
        }
        return true;
    }

    /**
     * Set primary breeding food for any animal (built-in or custom).
     */
    public boolean setAnyAnimalFood(String id, String food) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            setBreedingFood(result.getBuiltInType(), food);
        } else {
            // For custom animals, clear existing and add new
            String modelId = result.getCustomConfig().getModelAssetId();
            CustomAnimalConfig existing = customAnimals.get(modelId);
            if (existing != null) {
                List<String> newFoods = new ArrayList<>();
                newFoods.add(food);
                customAnimals.put(modelId, new CustomAnimalConfig(
                    existing.getModelAssetId(),
                    existing.getDisplayName(),
                    newFoods,
                    existing.getGrowthTimeMinutes(),
                    existing.getBreedCooldownMinutes(),
                    existing.getBabyNpcRoleId(),
                    existing.getAdultNpcRoleId(),
                    existing.isMountable(),
                    existing.isEnabled()
                ));
            }
        }
        return true;
    }

    /**
     * Add a breeding food to any animal (built-in or custom).
     */
    public boolean addAnyAnimalFood(String id, String food) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            addBreedingFood(result.getBuiltInType(), food);
        } else {
            addCustomAnimalFood(result.getCustomConfig().getModelAssetId(), food);
        }
        return true;
    }

    /**
     * Remove a breeding food from any animal (built-in or custom).
     */
    public boolean removeAnyAnimalFood(String id, String food) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            removeBreedingFood(result.getBuiltInType(), food);
        } else {
            removeCustomAnimalFood(result.getCustomConfig().getModelAssetId(), food);
        }
        return true;
    }

    /**
     * Enable or disable any animal (built-in or custom).
     */
    public boolean setAnyAnimalEnabled(String id, boolean enabled) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            setAnimalEnabled(result.getBuiltInType(), enabled);
        } else {
            setCustomAnimalEnabled(result.getCustomConfig().getModelAssetId(), enabled);
        }
        return true;
    }

    /**
     * Get breeding foods for any animal (built-in or custom).
     */
    public List<String> getAnyAnimalFoods(String id) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return Collections.emptyList();

        if (result.isBuiltIn()) {
            return getBreedingFoods(result.getBuiltInType());
        } else {
            return result.getCustomConfig().getBreedingFoods();
        }
    }

    /**
     * Check if any animal is enabled (built-in or custom).
     */
    public boolean isAnyAnimalEnabled(String id) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return false;

        if (result.isBuiltIn()) {
            return isAnimalEnabled(result.getBuiltInType());
        } else {
            return result.getCustomConfig().isEnabled();
        }
    }

    /**
     * Get growth time in minutes for any animal (built-in or custom).
     */
    public double getAnyAnimalGrowthTimeMinutes(String id) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return defaultGrowthTimeMinutes;

        if (result.isBuiltIn()) {
            AnimalConfig config = animalConfigs.get(result.getBuiltInType());
            return config != null ? config.growthTimeMinutes : defaultGrowthTimeMinutes;
        } else {
            return result.getCustomConfig().getGrowthTimeMinutes();
        }
    }

    /**
     * Get cooldown in minutes for any animal (built-in or custom).
     */
    public double getAnyAnimalCooldownMinutes(String id) {
        AnimalLookupResult result = lookupAnimal(id);
        if (result == null) return defaultBreedCooldownMinutes;

        if (result.isBuiltIn()) {
            AnimalConfig config = animalConfigs.get(result.getBuiltInType());
            return config != null ? config.breedCooldownMinutes : defaultBreedCooldownMinutes;
        } else {
            return result.getCustomConfig().getBreedCooldownMinutes();
        }
    }

    /**
     * Get breeding cooldown for a custom animal in milliseconds.
     */
    public long getCustomAnimalBreedingCooldown(String modelAssetId) {
        CustomAnimalConfig custom = customAnimals.get(modelAssetId);
        if (custom != null) {
            return (long) (custom.getBreedCooldownMinutes() * 60 * 1000);
        }
        return (long) (defaultBreedCooldownMinutes * 60 * 1000);
    }

    /**
     * Get growth time for a custom animal in milliseconds.
     */
    public long getCustomAnimalGrowthTime(String modelAssetId) {
        CustomAnimalConfig custom = customAnimals.get(modelAssetId);
        if (custom != null) {
            return (long) (custom.getGrowthTimeMinutes() * 60 * 1000);
        }
        return (long) (defaultGrowthTimeMinutes * 60 * 1000);
    }

    /**
     * Get the primary breeding food for an animal type (first in list).
     */
    public String getBreedingFood(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null && !config.breedingFoods.isEmpty()) {
            return config.breedingFoods.get(0);
        }
        return type.getDefaultBreedingFood();
    }

    /**
     * Get all breeding foods for an animal type.
     */
    public List<String> getBreedingFoods(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null && !config.breedingFoods.isEmpty()) {
            return Collections.unmodifiableList(config.breedingFoods);
        }
        return Collections.singletonList(type.getDefaultBreedingFood());
    }

    /**
     * Check if the given item is valid breeding food for the animal.
     */
    public boolean isBreedingFood(AnimalType type, String itemId) {
        if (itemId == null || type == null) return false;

        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            List<String> foods = config.getEffectiveBreedingFoods();
            for (String food : foods) {
                if (itemId.equalsIgnoreCase(food)) {
                    return true;
                }
            }
            return false;
        }
        // Fall back to default
        return itemId.equalsIgnoreCase(type.getDefaultBreedingFood());
    }

    // ===========================================
    // TAMING FOOD METHODS (v2+)
    // ===========================================

    /**
     * Get taming foods for an animal type.
     * Returns tamingFoods if set, otherwise baseFoods, otherwise breedingFoods.
     */
    public List<String> getTamingFoods(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            return config.getEffectiveTamingFoods();
        }
        return Collections.singletonList(type.getDefaultBreedingFood());
    }

    /**
     * Check if the given item is valid taming food for the animal.
     */
    public boolean isTamingFood(AnimalType type, String itemId) {
        if (itemId == null || type == null) return false;

        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            List<String> foods = config.getEffectiveTamingFoods();
            for (String food : foods) {
                if (itemId.equalsIgnoreCase(food)) {
                    return true;
                }
            }
            return false;
        }
        // Fall back to default
        return itemId.equalsIgnoreCase(type.getDefaultBreedingFood());
    }

    /**
     * Get all healing foods for an animal type (union of all food types).
     */
    public Set<String> getHealingFoods(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            return config.getAllHealingFoods();
        }
        Set<String> defaultFoods = new HashSet<>();
        defaultFoods.add(type.getDefaultBreedingFood());
        return defaultFoods;
    }

    /**
     * Check if the given item is valid healing food for the animal.
     * Any configured food can heal.
     */
    public boolean isHealingFood(AnimalType type, String itemId) {
        if (itemId == null || type == null) return false;

        Set<String> healingFoods = getHealingFoods(type);
        for (String food : healingFoods) {
            if (itemId.equalsIgnoreCase(food)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the set of tameable animal groups.
     */
    public Set<String> getTameableAnimalGroups() {
        return Collections.unmodifiableSet(tameableAnimalGroups);
    }

    /**
     * Check if an animal group is tameable.
     */
    public boolean isGroupTameable(String groupName) {
        return groupName != null && tameableAnimalGroups.contains(groupName);
    }

    /**
     * Get current config version.
     */
    public int getConfigVersion() {
        return configVersion;
    }

    /**
     * Get breeding cooldown for an animal type in milliseconds.
     */
    public long getBreedingCooldown(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        double minutes = (config != null) ? config.breedCooldownMinutes : defaultBreedCooldownMinutes;
        return (long) (minutes * 60 * 1000);
    }

    /**
     * Get growth time for an animal type in milliseconds.
     */
    public long getGrowthTime(AnimalType type) {
        AnimalConfig config = animalConfigs.get(type);
        double minutes = (config != null) ? config.growthTimeMinutes : defaultGrowthTimeMinutes;
        return (long) (minutes * 60 * 1000);
    }

    /**
     * Get growth stage duration (total growth time divided by 2 stages).
     */
    public long getGrowthStageDuration(GrowthStage stage) {
        if (stage == GrowthStage.ADULT) return 0L;
        // Divide total growth time by 2 (BABY and JUVENILE stages)
        return (long) (defaultGrowthTimeMinutes * 60 * 1000 / 2);
    }

    /**
     * Get growth stage duration for a specific animal.
     */
    public long getGrowthStageDuration(AnimalType type, GrowthStage stage) {
        if (stage == GrowthStage.ADULT) return 0L;
        long totalGrowthTime = getGrowthTime(type);
        return totalGrowthTime / 2; // Divide by 2 stages
    }

    /**
     * Get gestation period (instant for now, but configurable later).
     */
    public long getGestationPeriod(AnimalType type) {
        return 0L; // Instant breeding
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    /**
     * Check if baby growth is enabled.
     */
    public boolean isGrowthEnabled() {
        return growthEnabled;
    }

    /**
     * Enable or disable baby growth globally.
     * When disabled, babies will not age and grow into adults.
     */
    public void setGrowthEnabled(boolean enabled) {
        this.growthEnabled = enabled;
    }

    // ===========================================
    // SETTERS (for runtime config changes)
    // ===========================================

    /**
     * Enable or disable an animal type.
     */
    public void setAnimalEnabled(AnimalType type, boolean enabled) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.enabled = enabled;
        }
    }

    /**
     * Set breeding food for an animal type (replaces all foods with single food).
     */
    public void setBreedingFood(AnimalType type, String food) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.breedingFoods.clear();
            config.breedingFoods.add(food);
        }
    }

    /**
     * Set multiple breeding foods for an animal type.
     */
    public void setBreedingFoods(AnimalType type, List<String> foods) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.breedingFoods.clear();
            config.breedingFoods.addAll(foods);
        }
    }

    /**
     * Add a breeding food to an animal type.
     */
    public void addBreedingFood(AnimalType type, String food) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null && !config.breedingFoods.contains(food)) {
            config.breedingFoods.add(food);
        }
    }

    /**
     * Remove a breeding food from an animal type.
     */
    public void removeBreedingFood(AnimalType type, String food) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.breedingFoods.remove(food);
        }
    }

    /**
     * Set breeding cooldown for an animal type.
     */
    public void setBreedingCooldown(AnimalType type, double minutes) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.breedCooldownMinutes = minutes;
        }
    }

    /**
     * Set growth time for an animal type.
     */
    public void setGrowthTime(AnimalType type, double minutes) {
        AnimalConfig config = animalConfigs.get(type);
        if (config != null) {
            config.growthTimeMinutes = minutes;
        }
    }

    /**
     * Set default growth time for all animals.
     */
    public void setDefaultGrowthTime(double minutes) {
        this.defaultGrowthTimeMinutes = minutes;
        // Apply to all animals that use default
        for (AnimalConfig config : animalConfigs.values()) {
            config.growthTimeMinutes = minutes;
        }
    }

    /**
     * Set default breeding cooldown for all animals.
     */
    public void setDefaultBreedCooldown(double minutes) {
        this.defaultBreedCooldownMinutes = minutes;
        // Apply to all animals that use default
        for (AnimalConfig config : animalConfigs.values()) {
            config.breedCooldownMinutes = minutes;
        }
    }

    /**
     * Get animal config for display purposes.
     */
    public AnimalConfig getAnimalConfig(AnimalType type) {
        return animalConfigs.get(type);
    }

    /**
     * Get all animal configs.
     */
    public Map<AnimalType, AnimalConfig> getAllAnimalConfigs() {
        return new EnumMap<>(animalConfigs);
    }

    // ===========================================
    // CUSTOM ANIMAL SETTERS
    // ===========================================

    /**
     * Add a new custom animal or update an existing one.
     * @param modelAssetId The exact model asset ID of the creature
     * @param breedingFoods List of valid breeding foods
     * @return The created/updated CustomAnimalConfig
     */
    public CustomAnimalConfig addCustomAnimal(String modelAssetId, List<String> breedingFoods) {
        CustomAnimalConfig config = new CustomAnimalConfig(
            modelAssetId,
            modelAssetId,  // displayName defaults to modelAssetId
            breedingFoods,
            defaultGrowthTimeMinutes,
            defaultBreedCooldownMinutes,
            null,  // no baby NPC role
            modelAssetId,  // adult NPC role
            false,  // not mountable by default
            true   // enabled by default
        );
        customAnimals.put(modelAssetId, config);
        log("Added custom animal: " + modelAssetId + " with foods: " + breedingFoods);
        return config;
    }

    /**
     * Add a custom animal with full configuration.
     */
    public CustomAnimalConfig addCustomAnimal(
            String modelAssetId,
            String displayName,
            List<String> breedingFoods,
            double growthTimeMinutes,
            double breedCooldownMinutes,
            boolean mountable
    ) {
        CustomAnimalConfig config = new CustomAnimalConfig(
            modelAssetId,
            displayName,
            breedingFoods,
            growthTimeMinutes,
            breedCooldownMinutes,
            null,  // no baby NPC role
            modelAssetId,
            mountable,
            true
        );
        customAnimals.put(modelAssetId, config);
        log("Added custom animal: " + modelAssetId);
        return config;
    }

    /**
     * Remove a custom animal.
     * @return true if removed, false if not found
     */
    public boolean removeCustomAnimal(String modelAssetId) {
        CustomAnimalConfig removed = customAnimals.remove(modelAssetId);
        if (removed != null) {
            log("Removed custom animal: " + modelAssetId);
            return true;
        }
        return false;
    }

    /**
     * Enable or disable a custom animal.
     */
    public void setCustomAnimalEnabled(String modelAssetId, boolean enabled) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            // Recreate with new enabled status
            customAnimals.put(modelAssetId, new CustomAnimalConfig(
                existing.getModelAssetId(),
                existing.getDisplayName(),
                existing.getBreedingFoods(),
                existing.getGrowthTimeMinutes(),
                existing.getBreedCooldownMinutes(),
                existing.getBabyNpcRoleId(),
                existing.getAdultNpcRoleId(),
                existing.isMountable(),
                enabled
            ));
        }
    }

    /**
     * Add a breeding food to a custom animal.
     */
    public void addCustomAnimalFood(String modelAssetId, String food) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            List<String> foods = new ArrayList<>(existing.getBreedingFoods());
            if (!foods.contains(food)) {
                foods.add(food);
                customAnimals.put(modelAssetId, new CustomAnimalConfig(
                    existing.getModelAssetId(),
                    existing.getDisplayName(),
                    foods,
                    existing.getGrowthTimeMinutes(),
                    existing.getBreedCooldownMinutes(),
                    existing.getBabyNpcRoleId(),
                    existing.getAdultNpcRoleId(),
                    existing.isMountable(),
                    existing.isEnabled()
                ));
            }
        }
    }

    /**
     * Remove a breeding food from a custom animal.
     */
    public void removeCustomAnimalFood(String modelAssetId, String food) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            List<String> foods = new ArrayList<>(existing.getBreedingFoods());
            if (foods.remove(food)) {
                customAnimals.put(modelAssetId, new CustomAnimalConfig(
                    existing.getModelAssetId(),
                    existing.getDisplayName(),
                    foods,
                    existing.getGrowthTimeMinutes(),
                    existing.getBreedCooldownMinutes(),
                    existing.getBabyNpcRoleId(),
                    existing.getAdultNpcRoleId(),
                    existing.isMountable(),
                    existing.isEnabled()
                ));
            }
        }
    }

    /**
     * Set the NPC role ID for a custom animal (used for spawning babies).
     * @param modelAssetId The model asset ID of the custom animal
     * @param roleId The NPC role ID to use when spawning this animal
     */
    public void setCustomAnimalNpcRole(String modelAssetId, String roleId) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            customAnimals.put(modelAssetId, new CustomAnimalConfig(
                existing.getModelAssetId(),
                existing.getDisplayName(),
                existing.getBreedingFoods(),
                existing.getGrowthTimeMinutes(),
                existing.getBreedCooldownMinutes(),
                existing.getBabyNpcRoleId(),
                roleId,  // Set the new adult NPC role ID
                existing.isMountable(),
                existing.isEnabled()
            ));
            log("Set NPC role for " + modelAssetId + " to: " + roleId);
        }
    }

    /**
     * Set the baby NPC role for a custom animal.
     * This allows spawning dedicated baby NPCs instead of using scaling.
     * @param modelAssetId The model asset ID of the custom animal
     * @param babyRoleId The NPC role ID for spawning babies (null to use scaling)
     */
    public void setCustomAnimalBabyRole(String modelAssetId, String babyRoleId) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            customAnimals.put(modelAssetId, new CustomAnimalConfig(
                existing.getModelAssetId(),
                existing.getDisplayName(),
                existing.getBreedingFoods(),
                existing.getGrowthTimeMinutes(),
                existing.getBreedCooldownMinutes(),
                babyRoleId,  // Set the new baby NPC role ID
                existing.getAdultNpcRoleId(),
                existing.isMountable(),
                existing.isEnabled()
            ));
            log("Set baby NPC role for " + modelAssetId + " to: " + babyRoleId);
        }
    }

    /**
     * Set the growth time for a custom animal in minutes.
     * @param modelAssetId The model asset ID of the custom animal
     * @param growthTimeMinutes The growth time in minutes
     */
    public void setCustomAnimalGrowthTime(String modelAssetId, double growthTimeMinutes) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            customAnimals.put(modelAssetId, new CustomAnimalConfig(
                existing.getModelAssetId(),
                existing.getDisplayName(),
                existing.getBreedingFoods(),
                growthTimeMinutes,  // Set the new growth time
                existing.getBreedCooldownMinutes(),
                existing.getBabyNpcRoleId(),
                existing.getAdultNpcRoleId(),
                existing.isMountable(),
                existing.isEnabled()
            ));
            log("Set growth time for " + modelAssetId + " to: " + growthTimeMinutes + " min");
        }
    }

    /**
     * Set the breeding cooldown for a custom animal in minutes.
     * @param modelAssetId The model asset ID of the custom animal
     * @param cooldownMinutes The cooldown time in minutes
     */
    public void setCustomAnimalCooldown(String modelAssetId, double cooldownMinutes) {
        CustomAnimalConfig existing = customAnimals.get(modelAssetId);
        if (existing != null) {
            customAnimals.put(modelAssetId, new CustomAnimalConfig(
                existing.getModelAssetId(),
                existing.getDisplayName(),
                existing.getBreedingFoods(),
                existing.getGrowthTimeMinutes(),
                cooldownMinutes,  // Set the new cooldown
                existing.getBabyNpcRoleId(),
                existing.getAdultNpcRoleId(),
                existing.isMountable(),
                existing.isEnabled()
            ));
            log("Set cooldown for " + modelAssetId + " to: " + cooldownMinutes + " min");
        }
    }

    // ===========================================
    // LEGACY MESSAGE METHODS (kept for compatibility)
    // ===========================================

    public String getBreedingStartedMessage() {
        return formatMessage("&aBreeding started!");
    }

    public String getOnCooldownMessage() {
        return formatMessage("&cThis animal needs to rest before breeding again.");
    }

    public String getNotAdultMessage() {
        return formatMessage("&cThis animal is not old enough to breed.");
    }

    public String getWrongFoodMessage() {
        return formatMessage("&cThis animal doesn't want that food.");
    }

    public String getBirthMessage(AnimalType animalType) {
        return formatMessage("&bA baby " + animalType.getId() + " was born!");
    }

    public String getGrowthStageMessage(AnimalType animalType, GrowthStage stage) {
        return formatMessage("&e" + animalType.getId() + " has grown to " + stage.getDisplayName() + "!");
    }

    private String formatMessage(String message) {
        return message.replace("&", "\u00A7");
    }
}
