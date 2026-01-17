package com.laits.breeding.models;

/**
 * Enum representing breedable animal types.
 *
 * Dual system support:
 * - LIVESTOCK animals have baby NPC variants (spawn baby, replace with adult when grown)
 * - Other animals use scaling (spawn adult at small scale, scale up when grown)
 *
 * Data from Hytale assets:
 * - Appearance: The model asset ID used for detection
 * - LovedItems: Food that makes them breed
 * - Baby model: The baby's Appearance value (for spawning)
 * - Baby NPC Role: The full NPC role name (e.g., "Cow_Calf")
 */
public enum AnimalType {
    // ===========================================
    // LIVESTOCK - Have baby NPC variants (spawn baby, replace with adult)
    // ===========================================
    // Parent           Category            Model               Default Food                        Baby Model              Baby NPC Role
    COW(               Category.LIVESTOCK,  "Cow",              "Plant_Crop_Cauliflower_Item",      "Calf",                 "Cow_Calf"),
    PIG(               Category.LIVESTOCK,  "Pig",              "Plant_Crop_Mushroom_Cap_Brown",    "Piglet",               "Pig_Piglet"),
    CHICKEN(           Category.LIVESTOCK,  "Chicken",          "Plant_Crop_Corn_Item",             "Chick",                "Chicken_Chick"),
    SHEEP(             Category.LIVESTOCK,  "Sheep",            "Plant_Crop_Lettuce_Item",          "Lamb",                 "Sheep_Lamb"),
    GOAT(              Category.LIVESTOCK,  "Goat",             "Plant_Fruit_Apple",                "Goat_Kid",             "Goat_Kid"),
    HORSE(             Category.LIVESTOCK,  "Horse",            "Plant_Crop_Carrot_Item",           "Horse_Foal",           "Horse_Foal"),
    CAMEL(             Category.LIVESTOCK,  "Camel",            "Plant_Crop_Wheat_Item",            "Camel_Calf",           "Camel_Calf"),
    RAM(               Category.LIVESTOCK,  "Ram",              "Plant_Fruit_Apple",                "Ram_Lamb",             "Ram_Lamb"),
    TURKEY(            Category.LIVESTOCK,  "Turkey",           "Plant_Crop_Corn_Item",             "Turkey_Chick",         "Turkey_Chick"),
    BOAR(              Category.LIVESTOCK,  "Boar",             "Plant_Crop_Mushroom_Cap_Red",      "Boar_Piglet",          "Boar_Piglet"),
    RABBIT(            Category.LIVESTOCK,  "Rabbit",           "Plant_Crop_Carrot_Item",           "Bunny",                "Bunny"),
    BISON(             Category.LIVESTOCK,  "Bison",            "Plant_Crop_Wheat_Item",            "Bison_Calf",           "Bison_Calf"),
    CHICKEN_DESERT(    Category.LIVESTOCK,  "Chicken_Desert",   "Plant_Crop_Corn_Item",             "Chicken_Desert_Chick", "Chicken_Desert_Chick"),
    MOUFLON(           Category.LIVESTOCK,  "Mouflon",          "Plant_Crop_Lettuce_Item",          "Mouflon_Lamb",         "Mouflon_Lamb"),
    PIG_WILD(          Category.LIVESTOCK,  "Pig_Wild",         "Plant_Crop_Mushroom_Cap_Brown",    "Pig_Wild_Piglet",      "Pig_Wild_Piglet"),
    SKRILL(            Category.LIVESTOCK,  "Skrill",           "Plant_Crop_Corn_Item",             "Skrill_Chick",         "Skrill_Chick"),
    WARTHOG(           Category.LIVESTOCK,  "Warthog",          "Plant_Crop_Mushroom_Cap_Red",      "Warthog_Piglet",       "Warthog_Piglet"),

    // ===========================================
    // MAMMALS - No baby variants (spawn adult at small scale, scale up when grown)
    // ===========================================
    WOLF(              Category.MAMMAL,     "Wolf_Black",       "Food_Meat_Cooked",                 null,                   null),
    WOLF_WHITE(        Category.MAMMAL,     "Wolf_White",       "Food_Meat_Cooked",                 null,                   null),
    FOX(               Category.MAMMAL,     "Fox",              "Food_Wildmeat_Raw",                null,                   null),
    BEAR_GRIZZLY(      Category.MAMMAL,     "Bear_Grizzly",     "Plant_Fruit_Apple",                null,                   null),
    BEAR_POLAR(        Category.MAMMAL,     "Bear_Polar",       "Food_Fish_Cooked",                 null,                   null),
    DEER_DOE(          Category.MAMMAL,     "Deer_Doe",         "Plant_Crop_Carrot_Item",           null,                   null),
    DEER_STAG(         Category.MAMMAL,     "Deer_Stag",        "Plant_Crop_Carrot_Item",           null,                   null),
    MOOSE_BULL(        Category.MAMMAL,     "Moose_Bull",       "Plant_Crop_Wheat_Item",            null,                   null),
    MOOSE_COW(         Category.MAMMAL,     "Moose_Cow",        "Plant_Crop_Wheat_Item",            null,                   null),
    HYENA(             Category.MAMMAL,     "Hyena",            "Food_Wildmeat_Raw",                    null,                   null),

    // ===========================================
    // CRITTERS - Small creatures, use scaling
    // ===========================================
    FROG_BLUE(         Category.CRITTER,    "Frog_Blue",        "Food_Insect",                      null,                   null),
    FROG_GREEN(        Category.CRITTER,    "Frog_Green",       "Food_Insect",                      null,                   null),
    FROG_ORANGE(       Category.CRITTER,    "Frog_Orange",      "Food_Insect",                      null,                   null),
    GECKO(             Category.CRITTER,    "Gecko",            "Food_Insect",                      null,                   null),
    MEERKAT(           Category.CRITTER,    "Meerkat",          "Food_Insect",                      null,                   null),
    MOUSE(             Category.CRITTER,    "Mouse",            "Plant_Crop_Wheat_Item",            null,                   null),
    SQUIRREL(          Category.CRITTER,    "Squirrel",         "Plant_Fruit_Apple",                null,                   null),

    // ===========================================
    // AVIAN - Birds, use scaling
    // ===========================================
    DUCK(              Category.AVIAN,      "Duck",             "Plant_Crop_Corn_Item",             null,                   null),
    PIGEON(            Category.AVIAN,      "Pigeon",           "Plant_Crop_Wheat_Item",            null,                   null),
    PARROT(            Category.AVIAN,      "Parrot",           "Plant_Crop_Corn_Item",             null,                   null),
    CROW(              Category.AVIAN,      "Crow",             "Plant_Crop_Corn_Item",             null,                   null),
    RAVEN(             Category.AVIAN,      "Raven",            "Plant_Crop_Corn_Item",             null,                   null),
    OWL_BROWN(         Category.AVIAN,      "Owl_Brown",        "Food_Wildmeat_Raw",                    null,                   null),
    OWL_SNOW(          Category.AVIAN,      "Owl_Snow",         "Food_Wildmeat_Raw",                    null,                   null),
    FLAMINGO(          Category.AVIAN,      "Flamingo",         "Food_Fish_Raw",                    null,                   null),
    PENGUIN(           Category.AVIAN,      "Penguin",          "Food_Fish_Raw",                    null,                   null),

    // ===========================================
    // REPTILES - Reptiles, use scaling
    // ===========================================
    TORTOISE(          Category.REPTILE,    "Tortoise",         "Plant_Crop_Lettuce_Item",          null,                   null),
    CROCODILE(         Category.REPTILE,    "Crocodile",        "Food_Wildmeat_Raw",                    null,                   null),
    LIZARD_SAND(       Category.REPTILE,    "Lizard_Sand",      "Food_Insect",                      null,                   null),

    // ===========================================
    // MYTHIC - Mythical creatures, use scaling
    // ===========================================
    EMBERWULF(         Category.MYTHIC,     "Emberwulf",        "Food_Meat_Cooked",                 null,                   null),
    YETI(              Category.MYTHIC,     "Yeti",             "Food_Wildmeat_Raw",                    null,                   null),
    FEN_STALKER(       Category.MYTHIC,     "Fen_Stalker",      "Food_Wildmeat_Raw",                    null,                   null);

    /**
     * Animal categories for organization and filtering.
     */
    public enum Category {
        LIVESTOCK,  // Farm animals with baby NPC variants
        MAMMAL,     // Wild mammals (wolves, bears, deer, etc.)
        CRITTER,    // Small creatures (frogs, mice, etc.)
        AVIAN,      // Birds
        REPTILE,    // Reptiles
        MYTHIC      // Mythical creatures (emberwulf, yeti, etc.)
    }

    private final Category category;
    private final String modelAssetId;      // Appearance for parent (detection)
    private final String breedingFood;      // Default breeding food (LovedItems)
    private final String babyModelAssetId;  // Baby's Appearance (for model) - null if uses scaling
    private final String babyNpcRoleId;     // Baby's NPC Role ID (for spawning) - null if uses scaling

    AnimalType(Category category, String modelAssetId, String breedingFood, String babyModelAssetId, String babyNpcRoleId) {
        this.category = category;
        this.modelAssetId = modelAssetId;
        this.breedingFood = breedingFood;
        this.babyModelAssetId = babyModelAssetId;
        this.babyNpcRoleId = babyNpcRoleId;
    }

    /**
     * Get the category this animal belongs to.
     */
    public Category getCategory() {
        return category;
    }

    /**
     * Check if this is a livestock animal (farm animals with baby variants).
     */
    public boolean isLivestock() {
        return category == Category.LIVESTOCK;
    }

    /**
     * Get the modelAssetId used in the ECS system.
     */
    public String getModelAssetId() {
        return modelAssetId;
    }

    /**
     * Get lowercase ID for display purposes.
     */
    public String getId() {
        return modelAssetId.toLowerCase();
    }

    /**
     * Get the default breeding food for this animal.
     * Used by ConfigManager for initialization.
     */
    public String getDefaultBreedingFood() {
        return breedingFood;
    }

    /**
     * Get the breeding food (alias for getDefaultBreedingFood for backwards compat).
     */
    public String getBreedingFood() {
        return breedingFood;
    }

    /**
     * Get the baby variant's modelAssetId (Appearance value).
     * @return Baby model asset ID or null if this animal uses scaling
     */
    public String getBabyModelAssetId() {
        return babyModelAssetId;
    }

    /**
     * Get the baby variant's NPC Role ID for spawning.
     * @return Baby NPC Role ID or null if this animal uses scaling instead
     */
    public String getBabyNpcRoleId() {
        return babyNpcRoleId;
    }

    /**
     * Get the NPC Role ID for spawning this animal's adult form.
     * For livestock: derives from model asset ID (e.g., "Cow")
     * For wild animals: uses model asset ID (e.g., "Wolf_Black", "Fox")
     * @return The NPC role ID to use when spawning this animal
     */
    public String getAdultNpcRoleId() {
        // For most animals, the model asset ID is also the NPC role
        // Special cases can be handled here if needed
        return modelAssetId;
    }

    /**
     * Check if this animal has a dedicated baby NPC variant.
     * If true: spawn baby NPC, replace with adult when grown.
     * If false: spawn adult at small scale, scale up when grown.
     */
    public boolean hasBabyVariant() {
        return babyNpcRoleId != null;
    }

    /**
     * Get the scale for a baby/juvenile of this animal type.
     * Only used for creatures WITHOUT baby variants.
     * @param stage The growth stage
     * @return Scale factor (0.0-1.0)
     */
    public float getScaleForStage(GrowthStage stage) {
        switch (stage) {
            case BABY:
                return 0.4f;  // 40% size
            case JUVENILE:
                return 0.7f;  // 70% size
            case ADULT:
            default:
                return 1.0f;  // Full size
        }
    }

    /**
     * Get AnimalType from exact modelAssetId (e.g., "Cow", "Pig", "Chicken").
     * @param modelAssetId The model asset ID from ECS
     * @return The matching AnimalType or null if not found
     */
    public static AnimalType fromModelAssetId(String modelAssetId) {
        if (modelAssetId == null) {
            return null;
        }
        for (AnimalType type : values()) {
            // Check adult modelAssetId
            if (type.modelAssetId.equalsIgnoreCase(modelAssetId)) {
                return type;
            }
            // Check baby modelAssetId
            if (type.babyModelAssetId != null && type.babyModelAssetId.equalsIgnoreCase(modelAssetId)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if the modelAssetId is a baby variant.
     */
    public static boolean isBabyVariant(String modelAssetId) {
        if (modelAssetId == null) {
            return false;
        }
        for (AnimalType type : values()) {
            if (type.babyModelAssetId != null && type.babyModelAssetId.equalsIgnoreCase(modelAssetId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get AnimalType from entity type ID string (legacy, for backwards compat).
     * @param entityTypeId The entity type identifier
     * @return The matching AnimalType or null if not a breedable animal
     */
    public static AnimalType fromEntityTypeId(String entityTypeId) {
        if (entityTypeId == null) {
            return null;
        }
        // Try exact match first
        AnimalType exact = fromModelAssetId(entityTypeId);
        if (exact != null) {
            return exact;
        }
        // Fall back to contains check
        String lowerId = entityTypeId.toLowerCase();
        for (AnimalType type : values()) {
            if (lowerId.contains(type.modelAssetId.toLowerCase())) {
                return type;
            }
        }
        return null;
    }

    /**
     * Check if the given item ID is valid breeding food for this animal.
     * @param itemId The item identifier
     * @return true if the item can be used to breed this animal
     */
    public boolean isBreedingFood(String itemId) {
        if (itemId == null) {
            return false;
        }
        // Exact match (case-insensitive)
        return itemId.equalsIgnoreCase(breedingFood);
    }
}
