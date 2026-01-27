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
    // MAMMALS - Wild mammals, use scaling
    // ===========================================
    WOLF(              Category.MAMMAL,     "Wolf_Black",       "Food_Wildmeat_Cooked",             null,                   null),
    WOLF_WHITE(        Category.MAMMAL,     "Wolf_White",       "Food_Wildmeat_Cooked",             null,                   null),
    FOX(               Category.MAMMAL,     "Fox",              "Food_Wildmeat_Raw",                null,                   null),
    BEAR_GRIZZLY(      Category.MAMMAL,     "Bear_Grizzly",     "Plant_Fruit_Apple",                null,                   null),
    BEAR_POLAR(        Category.MAMMAL,     "Bear_Polar",       "Food_Fish_Grilled",                null,                   null),
    DEER_DOE(          Category.MAMMAL,     "Deer_Doe",         "Plant_Crop_Carrot_Item",           null,                   null),
    DEER_STAG(         Category.MAMMAL,     "Deer_Stag",        "Plant_Crop_Carrot_Item",           null,                   null),
    MOOSE_BULL(        Category.MAMMAL,     "Moose_Bull",       "Plant_Crop_Wheat_Item",            null,                   null),
    MOOSE_COW(         Category.MAMMAL,     "Moose_Cow",        "Plant_Crop_Wheat_Item",            null,                   null),
    HYENA(             Category.MAMMAL,     "Hyena",            "Food_Wildmeat_Raw",                null,                   null),
    ANTELOPE(          Category.MAMMAL,     "Antelope",         "Plant_Crop_Wheat_Item",            null,                   null),
    ARMADILLO(         Category.MAMMAL,     "Armadillo",        "Plant_Fruit_Berries_Red",          null,                   null),
    LEOPARD_SNOW(      Category.MAMMAL,     "Leopard_Snow",     "Food_Wildmeat_Raw",                null,                   null),
    MOSSHORN(          Category.MAMMAL,     "Mosshorn",         "Plant_Crop_Lettuce_Item",          null,                   null),
    MOSSHORN_PLAIN(    Category.MAMMAL,     "Mosshorn_Plain",   "Plant_Crop_Lettuce_Item",          null,                   null),
    TIGER_SABERTOOTH(  Category.MAMMAL,     "Tiger_Sabertooth", "Food_Wildmeat_Raw",                null,                   null),

    // ===========================================
    // CRITTERS - Small creatures, use scaling
    // ===========================================
    FROG_BLUE(         Category.CRITTER,    "Frog_Blue",        "Plant_Fruit_Berries_Red",          null,                   null),
    FROG_GREEN(        Category.CRITTER,    "Frog_Green",       "Plant_Fruit_Berries_Red",          null,                   null),
    FROG_ORANGE(       Category.CRITTER,    "Frog_Orange",      "Plant_Fruit_Berries_Red",          null,                   null),
    GECKO(             Category.CRITTER,    "Gecko",            "Plant_Fruit_Berries_Red",          null,                   null),
    MEERKAT(           Category.CRITTER,    "Meerkat",          "Food_Wildmeat_Raw",                null,                   null),
    MOUSE(             Category.CRITTER,    "Mouse",            "Plant_Crop_Wheat_Item",            null,                   null),
    SQUIRREL(          Category.CRITTER,    "Squirrel",         "Plant_Fruit_Apple",                null,                   null),

    // ===========================================
    // AVIAN - Birds, use scaling
    // ===========================================
    // Ground birds (Fowl)
    DUCK(              Category.AVIAN,      "Duck",             "Plant_Crop_Corn_Item",             null,                   null),
    PIGEON(            Category.AVIAN,      "Pigeon",           "Plant_Crop_Wheat_Item",            null,                   null),
    // Flying birds (Aerial)
    BAT(               Category.AVIAN,      "Bat",              "Plant_Fruit_Berries_Red",          null,                   null),
    BAT_ICE(           Category.AVIAN,      "Bat_Ice",          "Plant_Fruit_Berries_Red",          null,                   null),
    BLUEBIRD(          Category.AVIAN,      "Bluebird",         "Plant_Crop_Wheat_Item",            null,                   null),
    CROW(              Category.AVIAN,      "Crow",             "Plant_Crop_Corn_Item",             null,                   null),
    FINCH_GREEN(       Category.AVIAN,      "Finch_Green",      "Plant_Crop_Wheat_Item",            null,                   null),
    FLAMINGO(          Category.AVIAN,      "Flamingo",         "Food_Fish_Raw",                    null,                   null),
    OWL_BROWN(         Category.AVIAN,      "Owl_Brown",        "Food_Wildmeat_Raw",                null,                   null),
    OWL_SNOW(          Category.AVIAN,      "Owl_Snow",         "Food_Wildmeat_Raw",                null,                   null),
    PARROT(            Category.AVIAN,      "Parrot",           "Plant_Crop_Corn_Item",             null,                   null),
    PENGUIN(           Category.AVIAN,      "Penguin",          "Food_Fish_Raw",                    null,                   null),
    RAVEN(             Category.AVIAN,      "Raven",            "Plant_Crop_Corn_Item",             null,                   null),
    SPARROW(           Category.AVIAN,      "Sparrow",          "Plant_Crop_Wheat_Item",            null,                   null),
    WOODPECKER(        Category.AVIAN,      "Woodpecker",       "Plant_Fruit_Berries_Red",          null,                   null),
    // Raptors (Birds of Prey)
    HAWK(              Category.AVIAN,      "Hawk",             "Food_Wildmeat_Raw",                null,                   null),
    VULTURE(           Category.AVIAN,      "Vulture",          "Food_Wildmeat_Raw",                null,                   null),
    TETRABIRD(         Category.AVIAN,      "Tetrabird",        "Food_Wildmeat_Raw",                null,                   null),

    // ===========================================
    // REPTILES - Reptiles, use scaling
    // ===========================================
    TORTOISE(          Category.REPTILE,    "Tortoise",         "Plant_Crop_Lettuce_Item",          null,                   null),
    CROCODILE(         Category.REPTILE,    "Crocodile",        "Food_Wildmeat_Raw",                null,                   null),
    LIZARD_SAND(       Category.REPTILE,    "Lizard_Sand",      "Plant_Fruit_Berries_Red",          null,                   null),
    TOAD_RHINO(        Category.REPTILE,    "Toad_Rhino",       "Plant_Fruit_Berries_Red",          null,                   null),
    TOAD_RHINO_MAGMA(  Category.REPTILE,    "Toad_Rhino_Magma", "Plant_Fruit_Berries_Red",          null,                   null),

    // ===========================================
    // VERMIN - Creepy crawlies, use scaling
    // ===========================================
    RAT(               Category.VERMIN,     "Rat",              "Plant_Crop_Wheat_Item",            null,                   null),
    MOLERAT(           Category.VERMIN,     "Molerat",          "Plant_Crop_Carrot_Item",           null,                   null),
    LARVA_SILK(        Category.VERMIN,     "Larva_Silk",       "Plant_Crop_Lettuce_Item",          null,                   null),
    SCORPION(          Category.VERMIN,     "Scorpion",         "Food_Wildmeat_Raw",                null,                   null),
    SLUG_MAGMA(        Category.VERMIN,     "Slug_Magma",       "Plant_Crop_Mushroom_Cap_Red",      null,                   null),
    SNAIL_FROST(       Category.VERMIN,     "Snail_Frost",      "Plant_Crop_Lettuce_Item",          null,                   null),
    SNAIL_MAGMA(       Category.VERMIN,     "Snail_Magma",      "Plant_Crop_Mushroom_Cap_Red",      null,                   null),
    SNAKE_COBRA(       Category.VERMIN,     "Snake_Cobra",      "Food_Wildmeat_Raw",                null,                   null),
    SNAKE_MARSH(       Category.VERMIN,     "Snake_Marsh",      "Food_Wildmeat_Raw",                null,                   null),
    SNAKE_RATTLE(      Category.VERMIN,     "Snake_Rattle",     "Food_Wildmeat_Raw",                null,                   null),
    SPIDER(            Category.VERMIN,     "Spider",           "Food_Wildmeat_Raw",                null,                   null),
    SPIDER_CAVE(       Category.VERMIN,     "Spider_Cave",      "Food_Wildmeat_Raw",                null,                   null),

    // ===========================================
    // AQUATIC - Fish and sea creatures, use scaling
    // ===========================================
    // Abyssal
    EEL_MORAY(         Category.AQUATIC,    "Eel_Moray",        "Food_Fish_Raw",                    null,                   null),
    SHARK_HAMMERHEAD(  Category.AQUATIC,    "Shark_Hammerhead", "Food_Fish_Raw",                    null,                   null),
    SHELLFISH_LAVA(    Category.AQUATIC,    "Shellfish_Lava",   "Food_Fish_Raw",                    null,                   null),
    TRILOBITE(         Category.AQUATIC,    "Trilobite",        "Food_Fish_Raw",                    null,                   null),
    TRILOBITE_BLACK(   Category.AQUATIC,    "Trilobite_Black",  "Food_Fish_Raw",                    null,                   null),
    WHALE_HUMPBACK(    Category.AQUATIC,    "Whale_Humpback",   "Food_Fish_Raw",                    null,                   null),
    // Freshwater
    BLUEGILL(          Category.AQUATIC,    "Bluegill",         "Food_Fish_Raw",                    null,                   null),
    CATFISH(           Category.AQUATIC,    "Catfish",          "Food_Fish_Raw",                    null,                   null),
    FROSTGILL(         Category.AQUATIC,    "Frostgill",        "Food_Fish_Raw",                    null,                   null),
    MINNOW(            Category.AQUATIC,    "Minnow",           "Food_Fish_Raw",                    null,                   null),
    PIKE(              Category.AQUATIC,    "Pike",             "Food_Fish_Raw",                    null,                   null),
    PIRANHA(           Category.AQUATIC,    "Piranha",          "Food_Fish_Raw",                    null,                   null),
    PIRANHA_BLACK(     Category.AQUATIC,    "Piranha_Black",    "Food_Fish_Raw",                    null,                   null),
    SALMON(            Category.AQUATIC,    "Salmon",           "Food_Fish_Raw",                    null,                   null),
    SNAPJAW(           Category.AQUATIC,    "Snapjaw",          "Food_Fish_Raw",                    null,                   null),
    TROUT_RAINBOW(     Category.AQUATIC,    "Trout_Rainbow",    "Food_Fish_Raw",                    null,                   null),
    // Marine
    CLOWNFISH(         Category.AQUATIC,    "Clownfish",        "Food_Fish_Raw",                    null,                   null),
    CRAB(              Category.AQUATIC,    "Crab",             "Food_Fish_Raw",                    null,                   null),
    JELLYFISH_BLUE(    Category.AQUATIC,    "Jellyfish_Blue",   "Food_Fish_Raw",                    null,                   null),
    JELLYFISH_CYAN(    Category.AQUATIC,    "Jellyfish_Cyan",   "Food_Fish_Raw",                    null,                   null),
    JELLYFISH_GREEN(   Category.AQUATIC,    "Jellyfish_Green",  "Food_Fish_Raw",                    null,                   null),
    JELLYFISH_MAN_OF_WAR(Category.AQUATIC,  "Jellyfish_Man_Of_War", "Food_Fish_Raw",                null,                   null),
    JELLYFISH_RED(     Category.AQUATIC,    "Jellyfish_Red",    "Food_Fish_Raw",                    null,                   null),
    JELLYFISH_YELLOW(  Category.AQUATIC,    "Jellyfish_Yellow", "Food_Fish_Raw",                    null,                   null),
    LOBSTER(           Category.AQUATIC,    "Lobster",          "Food_Fish_Raw",                    null,                   null),
    PUFFERFISH(        Category.AQUATIC,    "Pufferfish",       "Food_Fish_Raw",                    null,                   null),
    TANG_BLUE(         Category.AQUATIC,    "Tang_Blue",        "Food_Fish_Raw",                    null,                   null),
    TANG_CHEVRON(      Category.AQUATIC,    "Tang_Chevron",     "Food_Fish_Raw",                    null,                   null),
    TANG_LEMON_PEEL(   Category.AQUATIC,    "Tang_Lemon_Peel",  "Food_Fish_Raw",                    null,                   null),
    TANG_SAILFIN(      Category.AQUATIC,    "Tang_Sailfin",     "Food_Fish_Raw",                    null,                   null),

    // ===========================================
    // MYTHIC - Mythical/fantasy creatures, use scaling
    // ===========================================
    EMBERWULF(         Category.MYTHIC,     "Emberwulf",        "Food_Wildmeat_Cooked",             null,                   null),
    YETI(              Category.MYTHIC,     "Yeti",             "Food_Wildmeat_Raw",                null,                   null),
    FEN_STALKER(       Category.MYTHIC,     "Fen_Stalker",      "Food_Wildmeat_Raw",                null,                   null),
    CACTEE(            Category.MYTHIC,     "Cactee",           "Plant_Cactus_Flower",              null,                   null),
    HATWORM(           Category.MYTHIC,     "Hatworm",          "Plant_Crop_Mushroom_Cap_Brown",    null,                   null),
    SNAPDRAGON(        Category.MYTHIC,     "Snapdragon",       "Food_Wildmeat_Raw",                null,                   null),
    SPARK_LIVING(      Category.MYTHIC,     "Spark_Living",     "Plant_Crop_Chilli_Item",           null,                   null),
    TRILLODON(         Category.MYTHIC,     "Trillodon",        "Food_Wildmeat_Raw",                null,                   null),

    // ===========================================
    // DINOSAUR - Prehistoric creatures, use scaling
    // ===========================================
    RAPTOR_CAVE(       Category.DINOSAUR,   "Raptor_Cave",      "Food_Wildmeat_Cooked",             null,                   null),
    REX_CAVE(          Category.DINOSAUR,   "Rex_Cave",         "Food_Wildmeat_Cooked",             null,                   null),
    ARCHAEOPTERYX(     Category.DINOSAUR,   "Archaeopteryx",    "Food_Wildmeat_Raw",                null,                   null),
    PTERODACTYL(       Category.DINOSAUR,   "Pterodactyl",      "Food_Fish_Raw",                    null,                   null),

    // ===========================================
    // BOSS - Boss creatures (may be too powerful to breed!)
    // ===========================================
    DRAGON_FIRE(       Category.BOSS,       "Dragon_Fire",      "Food_Wildmeat_Cooked",             null,                   null),
    DRAGON_FROST(      Category.BOSS,       "Dragon_Frost",     "Food_Fish_Raw",                    null,                   null),

    // ===========================================
    // UNDEAD - Skeletons, zombies, and undead creatures
    // ===========================================
    SKELETON(          Category.UNDEAD,     "Skeleton",         "Bone",                             null,                   null),
    SKELETON_BURNT(    Category.UNDEAD,     "Skeleton_Burnt",   "Bone",                             null,                   null),
    SKELETON_FROST(    Category.UNDEAD,     "Skeleton_Frost",   "Bone",                             null,                   null),
    SKELETON_SAND(     Category.UNDEAD,     "Skeleton_Sand",    "Bone",                             null,                   null),
    SKELETON_PIRATE(   Category.UNDEAD,     "Skeleton_Pirate",  "Bone",                             null,                   null),
    SKELETON_INCANDESCENT(Category.UNDEAD,  "Skeleton_Incandescent", "Bone",                        null,                   null),
    ZOMBIE(            Category.UNDEAD,     "Zombie",           "Food_Wildmeat_Raw",                null,                   null),
    ZOMBIE_BURNT(      Category.UNDEAD,     "Zombie_Burnt",     "Food_Wildmeat_Raw",                null,                   null),
    ZOMBIE_FROST(      Category.UNDEAD,     "Zombie_Frost",     "Food_Wildmeat_Raw",                null,                   null),
    ZOMBIE_SAND(       Category.UNDEAD,     "Zombie_Sand",      "Food_Wildmeat_Raw",                null,                   null),
    ZOMBIE_ABERRANT(   Category.UNDEAD,     "Zombie_Aberrant",  "Food_Wildmeat_Raw",                null,                   null),
    GHOUL(             Category.UNDEAD,     "Ghoul",            "Food_Wildmeat_Raw",                null,                   null),
    WRAITH(            Category.UNDEAD,     "Wraith",           "Ectoplasm",                        null,                   null),
    WEREWOLF(          Category.UNDEAD,     "Werewolf",         "Food_Wildmeat_Raw",                null,                   null),
    SHADOW_KNIGHT(     Category.UNDEAD,     "Shadow_Knight",    "Bone",                             null,                   null),
    HORSE_SKELETON(    Category.UNDEAD,     "Horse_Skeleton",   "Bone",                             null,                   null),
    HOUND_BLEACHED(    Category.UNDEAD,     "Hound_Bleached",   "Bone",                             null,                   null),
    CHICKEN_UNDEAD(    Category.UNDEAD,     "Chicken_Undead",   "Bone",                             null,                   null),
    COW_UNDEAD(        Category.UNDEAD,     "Cow_Undead",       "Bone",                             null,                   null),
    PIG_UNDEAD(        Category.UNDEAD,     "Pig_Undead",       "Bone",                             null,                   null),

    // ===========================================
    // GOLEM - Elemental golems
    // ===========================================
    GOLEM_CRYSTAL_EARTH(Category.GOLEM,     "Golem_Crystal_Earth",   "Gem_Crystal",               null,                   null),
    GOLEM_CRYSTAL_FLAME(Category.GOLEM,     "Golem_Crystal_Flame",   "Gem_Crystal",               null,                   null),
    GOLEM_CRYSTAL_FROST(Category.GOLEM,     "Golem_Crystal_Frost",   "Gem_Crystal",               null,                   null),
    GOLEM_CRYSTAL_SAND( Category.GOLEM,     "Golem_Crystal_Sand",    "Gem_Crystal",               null,                   null),
    GOLEM_CRYSTAL_THUNDER(Category.GOLEM,   "Golem_Crystal_Thunder", "Gem_Crystal",               null,                   null),
    GOLEM_FIRESTEEL(   Category.GOLEM,      "Golem_Firesteel",       "Ingot_Iron",                null,                   null),
    GOLEM_GUARDIAN_VOID(Category.GOLEM,     "Golem_Guardian_Void",   "Void_Shard",                null,                   null),

    // ===========================================
    // SPIRIT - Elemental spirits
    // ===========================================
    SPIRIT_EMBER(      Category.SPIRIT,     "Spirit_Ember",     "Plant_Crop_Chilli_Item",           null,                   null),
    SPIRIT_FROST(      Category.SPIRIT,     "Spirit_Frost",     "Snowball",                         null,                   null),
    SPIRIT_ROOT(       Category.SPIRIT,     "Spirit_Root",      "Plant_Fruit_Apple",                null,                   null),
    SPIRIT_THUNDER(    Category.SPIRIT,     "Spirit_Thunder",   "Gem_Crystal",                      null,                   null),

    // ===========================================
    // GOBLIN - Goblin enemies
    // ===========================================
    GOBLIN_SCRAPPER(   Category.GOBLIN,     "Goblin_Scrapper",  "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_THIEF(      Category.GOBLIN,     "Goblin_Thief",     "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_MINER(      Category.GOBLIN,     "Goblin_Miner",     "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_LOBBER(     Category.GOBLIN,     "Goblin_Lobber",    "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_SCAVENGER(  Category.GOBLIN,     "Goblin_Scavenger", "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_HERMIT(     Category.GOBLIN,     "Goblin_Hermit",    "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_OGRE(       Category.GOBLIN,     "Goblin_Ogre",      "Food_Wildmeat_Raw",                null,                   null),
    GOBLIN_DUKE(       Category.GOBLIN,     "Goblin_Duke",      "Food_Wildmeat_Cooked",             null,                   null),

    // ===========================================
    // TRORK - Trork enemies
    // ===========================================
    TRORK_BRAWLER(     Category.TRORK,      "Trork_Brawler",    "Food_Wildmeat_Raw",                null,                   null),
    TRORK_WARRIOR(     Category.TRORK,      "Trork_Warrior",    "Food_Wildmeat_Raw",                null,                   null),
    TRORK_HUNTER(      Category.TRORK,      "Trork_Hunter",     "Food_Wildmeat_Raw",                null,                   null),
    TRORK_SENTRY(      Category.TRORK,      "Trork_Sentry",     "Food_Wildmeat_Raw",                null,                   null),
    TRORK_GUARD(       Category.TRORK,      "Trork_Guard",      "Food_Wildmeat_Raw",                null,                   null),
    TRORK_MAULER(      Category.TRORK,      "Trork_Mauler",     "Food_Wildmeat_Raw",                null,                   null),
    TRORK_SHAMAN(      Category.TRORK,      "Trork_Shaman",     "Food_Wildmeat_Raw",                null,                   null),
    TRORK_CHIEFTAIN(   Category.TRORK,      "Trork_Chieftain",  "Food_Wildmeat_Cooked",             null,                   null),
    TRORK_DOCTOR_WITCH(Category.TRORK,      "Trork_Doctor_Witch","Food_Wildmeat_Raw",               null,                   null),

    // ===========================================
    // SCARAK - Bug enemies
    // ===========================================
    SCARAK_LOUSE(      Category.SCARAK,     "Scarak_Louse",     "Food_Wildmeat_Raw",                null,                   null),
    SCARAK_SEEKER(     Category.SCARAK,     "Scarak_Seeker",    "Food_Wildmeat_Raw",                null,                   null),
    SCARAK_FIGHTER(    Category.SCARAK,     "Scarak_Fighter",   "Food_Wildmeat_Raw",                null,                   null),
    SCARAK_DEFENDER(   Category.SCARAK,     "Scarak_Defender",  "Food_Wildmeat_Raw",                null,                   null),
    SCARAK_BROODMOTHER(Category.SCARAK,     "Scarak_Broodmother","Food_Wildmeat_Raw",               null,                   null),

    // ===========================================
    // KWEEBEC - Plant people (neutral)
    // ===========================================
    KWEEBEC_SEEDLING(  Category.KWEEBEC,    "Kweebec_Seedling", "Plant_Fruit_Apple",                null,                   null),
    KWEEBEC_SPROUTLING(Category.KWEEBEC,    "Kweebec_Sproutling","Plant_Fruit_Apple",               null,                   null),
    KWEEBEC_SAPLING(   Category.KWEEBEC,    "Kweebec_Sapling",  "Plant_Fruit_Apple",                null,                   null),
    KWEEBEC_ROOTLING(  Category.KWEEBEC,    "Kweebec_Rootling", "Plant_Fruit_Apple",                null,                   null),
    KWEEBEC_RAZORLEAF( Category.KWEEBEC,    "Kweebec_Razorleaf","Plant_Fruit_Apple",                null,                   null),
    KWEEBEC_ELDER(     Category.KWEEBEC,    "Kweebec_Elder",    "Plant_Fruit_Apple",                null,                   null),

    // ===========================================
    // OUTLANDER - Human bandits and cultists
    // ===========================================
    OUTLANDER_PEON(    Category.OUTLANDER,  "Outlander_Peon",   "Food_Bread",                       null,                   null),
    OUTLANDER_HUNTER(  Category.OUTLANDER,  "Outlander_Hunter", "Food_Bread",                       null,                   null),
    OUTLANDER_MARAUDER(Category.OUTLANDER,  "Outlander_Marauder","Food_Bread",                      null,                   null),
    OUTLANDER_STALKER( Category.OUTLANDER,  "Outlander_Stalker","Food_Bread",                       null,                   null),
    OUTLANDER_BERSERKER(Category.OUTLANDER, "Outlander_Berserker","Food_Bread",                     null,                   null),
    OUTLANDER_BRUTE(   Category.OUTLANDER,  "Outlander_Brute",  "Food_Bread",                       null,                   null),
    OUTLANDER_CULTIST( Category.OUTLANDER,  "Outlander_Cultist","Food_Bread",                       null,                   null),
    OUTLANDER_PRIEST(  Category.OUTLANDER,  "Outlander_Priest", "Food_Bread",                       null,                   null),
    OUTLANDER_SORCERER(Category.OUTLANDER,  "Outlander_Sorcerer","Food_Bread",                      null,                   null),

    // ===========================================
    // VOID - Void creatures
    // ===========================================
    CRAWLER_VOID(      Category.VOID,       "Crawler_Void",     "Void_Shard",                       null,                   null),
    EYE_VOID(          Category.VOID,       "Eye_Void",         "Void_Shard",                       null,                   null),
    LARVA_VOID(        Category.VOID,       "Larva_Void",       "Void_Shard",                       null,                   null),
    SPAWN_VOID(        Category.VOID,       "Spawn_Void",       "Void_Shard",                       null,                   null),
    SPECTRE_VOID(      Category.VOID,       "Spectre_Void",     "Void_Shard",                       null,                   null),

    // ===========================================
    // MISC - Other creatures
    // ===========================================
    HEDERA(            Category.MISC,       "Hedera",           "Plant_Fruit_Berries_Red",          null,                   null);

    /**
     * Animal categories for organization and filtering.
     */
    public enum Category {
        LIVESTOCK,  // Farm animals with baby NPC variants
        MAMMAL,     // Wild mammals (wolves, bears, deer, etc.)
        CRITTER,    // Small creatures (frogs, mice, etc.)
        AVIAN,      // Birds (flying and ground)
        REPTILE,    // Reptiles (tortoises, crocodiles, etc.)
        VERMIN,     // Creepy crawlies (spiders, snakes, rats, etc.)
        AQUATIC,    // Fish and sea creatures
        MYTHIC,     // Mythical/fantasy creatures
        DINOSAUR,   // Prehistoric creatures
        BOSS,       // Boss creatures
        UNDEAD,     // Skeletons, zombies, ghosts
        GOLEM,      // Elemental golems
        SPIRIT,     // Elemental spirits
        GOBLIN,     // Goblin enemies
        TRORK,      // Trork enemies
        SCARAK,     // Bug enemies
        KWEEBEC,    // Plant people
        OUTLANDER,  // Human bandits/cultists
        VOID,       // Void creatures
        MISC        // Other creatures
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
     * Check if this animal is mountable.
     * Mountable animals have a default mount interaction that we must preserve.
     *
     * Currently mountable in Hytale:
     * - Horse, Ram, Skeleton Horse (vanilla)
     *
     * Likely mountable / mod-supported:
     * - Camel, Tetrabird (confirmed future mounts)
     * - Antelope, Bison, Deer, Moose (mountable via mods)
     *
     * Sources:
     * - https://www.bisecthosting.com/blog/how-to-use-mounts-in-hytale-horses-rams-camels
     * - https://www.curseforge.com/hytale/mods/more-mounts
     */
    public boolean isMountable() {
        switch (this) {
            // Vanilla mountable
            case HORSE:
            case CAMEL:
            case RAM:
                return true;
            // Likely mountable / mod-supported
            case TETRABIRD:
            case ANTELOPE:
            case BISON:
            case DEER_DOE:
            case DEER_STAG:
            case MOOSE_BULL:
            case MOOSE_COW:
                return false;
            default:
                return false;
        }
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
