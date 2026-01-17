package com.laits.breeding.util;

import com.laits.breeding.models.AnimalType;
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
import java.util.*;
import java.util.function.Consumer;

/**
 * Manages plugin configuration from JSON file.
 * Supports runtime changes, persistence, multiple breeding foods, and presets.
 */
public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config data
    private final Map<AnimalType, AnimalConfig> animalConfigs = new EnumMap<>(AnimalType.class);
    private double defaultGrowthTimeMinutes = 30.0;
    private double defaultBreedCooldownMinutes = 5.0;
    private boolean debugMode = false;
    private String activePreset = "default";

    // File path for persistence
    private Path configFilePath;
    private Path presetsDirectory;
    private Consumer<String> logger;

    /**
     * Configuration for a single animal type.
     */
    public static class AnimalConfig {
        public boolean enabled = true;
        public List<String> breedingFoods = new ArrayList<>();
        public double growthTimeMinutes;
        public double breedCooldownMinutes;

        public AnimalConfig() {}

        public AnimalConfig(boolean enabled, List<String> breedingFoods, double growthTimeMinutes, double breedCooldownMinutes) {
            this.enabled = enabled;
            this.breedingFoods = breedingFoods != null ? new ArrayList<>(breedingFoods) : new ArrayList<>();
            this.growthTimeMinutes = growthTimeMinutes;
            this.breedCooldownMinutes = breedCooldownMinutes;
        }

        public AnimalConfig(boolean enabled, String breedingFood, double growthTimeMinutes, double breedCooldownMinutes) {
            this.enabled = enabled;
            this.breedingFoods = new ArrayList<>();
            if (breedingFood != null) {
                this.breedingFoods.add(breedingFood);
            }
            this.growthTimeMinutes = growthTimeMinutes;
            this.breedCooldownMinutes = breedCooldownMinutes;
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
     * Initialize the presets directory and create default preset files if they don't exist.
     */
    private void initializePresets() {
        try {
            if (!Files.exists(presetsDirectory)) {
                Files.createDirectories(presetsDirectory);
                log("Created presets directory: " + presetsDirectory);
            }

            // Create default preset file if it doesn't exist
            Path defaultPreset = presetsDirectory.resolve("default.json");
            if (!Files.exists(defaultPreset)) {
                saveBuiltinPresetToFile("default", defaultPreset);
                log("Created default preset file: " + defaultPreset);
            }

            // Create lait_curated preset file if it doesn't exist
            Path laitPreset = presetsDirectory.resolve("lait_curated.json");
            if (!Files.exists(laitPreset)) {
                saveBuiltinPresetToFile("lait_curated", laitPreset);
                log("Created lait_curated preset file: " + laitPreset);
            }

            // Create zoo preset file if it doesn't exist
            Path zooPreset = presetsDirectory.resolve("zoo.json");
            if (!Files.exists(zooPreset)) {
                saveBuiltinPresetToFile("zoo", zooPreset);
                log("Created zoo preset file: " + zooPreset);
            }
        } catch (Exception e) {
            log("Error initializing presets: " + e.getMessage());
        }
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
        } else if (presetName.equals("lait_curated")) {
            applyBuiltinLaitCuratedPreset();
        } else if (presetName.equals("zoo")) {
            applyBuiltinZooPreset();
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

            // Load active preset name
            if (root.has("activePreset")) {
                activePreset = root.get("activePreset").getAsString();
            }

            // Load defaults
            if (root.has("defaults")) {
                JsonObject defaults = root.getAsJsonObject("defaults");
                if (defaults.has("growthTimeMinutes")) {
                    defaultGrowthTimeMinutes = defaults.get("growthTimeMinutes").getAsDouble();
                }
                if (defaults.has("breedCooldownMinutes")) {
                    defaultBreedCooldownMinutes = defaults.get("breedCooldownMinutes").getAsDouble();
                }
            }

            // Load animal configs
            if (root.has("animals")) {
                JsonObject animals = root.getAsJsonObject("animals");
                for (AnimalType type : AnimalType.values()) {
                    String key = type.name();
                    if (animals.has(key)) {
                        JsonObject animalJson = animals.getAsJsonObject(key);
                        AnimalConfig config = animalConfigs.get(type);
                        if (config == null) {
                            config = new AnimalConfig();
                            animalConfigs.put(type, config);
                        }

                        if (animalJson.has("enabled")) {
                            config.enabled = animalJson.get("enabled").getAsBoolean();
                        }

                        // Support both single food (legacy) and multiple foods
                        if (animalJson.has("breedingFoods")) {
                            config.breedingFoods.clear();
                            JsonArray foodsArray = animalJson.getAsJsonArray("breedingFoods");
                            for (JsonElement elem : foodsArray) {
                                config.breedingFoods.add(elem.getAsString());
                            }
                        } else if (animalJson.has("breedingFood")) {
                            // Legacy single food support
                            config.breedingFoods.clear();
                            config.breedingFoods.add(animalJson.get("breedingFood").getAsString());
                        }

                        if (animalJson.has("growthTimeMinutes")) {
                            config.growthTimeMinutes = animalJson.get("growthTimeMinutes").getAsDouble();
                        }
                        if (animalJson.has("breedCooldownMinutes")) {
                            config.breedCooldownMinutes = animalJson.get("breedCooldownMinutes").getAsDouble();
                        }
                    }
                }
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
        if (!presets.contains("default")) presets.add("default");
        if (!presets.contains("lait_curated")) presets.add("lait_curated");
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
            case "lait_curated":
                applyBuiltinLaitCuratedPreset();
                activePreset = "lait_curated";
                return true;
            case "zoo":
                applyBuiltinZooPreset();
                activePreset = "zoo";
                return true;
            default:
                log("Preset not found: " + presetName);
                return false;
        }
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
        try {
            Path presetFile = presetsDirectory.resolve(presetName + ".json");
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
                        "Plant_Crop_Cactus_Fruit"  // Desert fruit
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
                        "Food_Meat_Cooked",
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
                        "Food_Fish_Cooked",
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
                case MEERKAT:
                case LIZARD_SAND:
                    config.breedingFoods.add("Food_Insect");
                    config.growthTimeMinutes = 5.0;  // Small, fast growth
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
                        "Food_Meat_Cooked",
                        "Food_Wildmeat_Raw"
                    ));
                    config.growthTimeMinutes = 40.0;
                    break;

                case YETI:
                case FEN_STALKER:
                    config.breedingFoods.add("Food_Wildmeat_Raw");
                    config.growthTimeMinutes = 50.0;
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
     * Apply zoo preset - all real animals enabled, mythic creatures disabled.
     * Uses lait_curated food/timing values but enables wild animals for a zoo experience.
     */
    private void applyBuiltinZooPreset() {
        // Start with lait_curated values for foods and timing
        applyBuiltinLaitCuratedPreset();

        // Enable all animals EXCEPT mythic creatures
        for (AnimalType type : AnimalType.values()) {
            AnimalConfig config = animalConfigs.get(type);
            if (config != null) {
                // Enable everything except MYTHIC (Emberwulf, Yeti, Fen Stalker)
                config.enabled = type.getCategory() != AnimalType.Category.MYTHIC;
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
        if (config != null && !config.breedingFoods.isEmpty()) {
            for (String food : config.breedingFoods) {
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
