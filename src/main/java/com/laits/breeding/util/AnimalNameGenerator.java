package com.laits.breeding.util;

import com.laits.breeding.models.AnimalType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Utility class for generating suggested animal names and validating user input.
 */
public class AnimalNameGenerator {

    private static final Random random = new Random();

    // Maximum name length (lenient limit)
    public static final int MAX_NAME_LENGTH = 48;

    // Generic cute names that work for any animal
    private static final List<String> GENERIC_NAMES = Arrays.asList(
        "Buddy", "Max", "Bella", "Charlie", "Luna", "Milo", "Coco", "Rocky",
        "Daisy", "Duke", "Sadie", "Bear", "Molly", "Tucker", "Bailey", "Maggie",
        "Jack", "Sophie", "Oliver", "Lucy", "Buster", "Chloe", "Teddy", "Penny",
        "Zeus", "Zoey", "Gus", "Lily", "Winston", "Gracie", "Oscar", "Ruby"
    );

    // Farm animal names
    private static final List<String> FARM_NAMES = Arrays.asList(
        "Bessie", "Clover", "Buttercup", "Daisy", "Patches", "Spot", "Brownie",
        "Cookie", "Ginger", "Honey", "Maple", "Mocha", "Pepper", "Sugar"
    );

    // Cow-specific names
    private static final List<String> COW_NAMES = Arrays.asList(
        "Bessie", "Buttercup", "Daisy", "Milkshake", "Moo-Moo", "Clarabelle",
        "Bovina", "Cowbell", "Cream Puff", "Patches", "Spot", "Brownie"
    );

    // Pig-specific names
    private static final List<String> PIG_NAMES = Arrays.asList(
        "Hamlet", "Bacon", "Wilbur", "Piglet", "Truffles", "Porky", "Snuffles",
        "Oink", "Muddy", "Babe", "Pinky", "Waddles", "Hammy", "Curly"
    );

    // Chicken-specific names
    private static final List<String> CHICKEN_NAMES = Arrays.asList(
        "Clucky", "Henny", "Pecky", "Nugget", "Feathers", "Drumstick", "Sunny",
        "Yolky", "Eggbert", "Chickpea", "Clucker", "Scrambles", "Omelet"
    );

    // Sheep-specific names
    private static final List<String> SHEEP_NAMES = Arrays.asList(
        "Woolly", "Fluffy", "Cotton", "Cloud", "Snowball", "Lamb Chop", "Fuzzy",
        "Fleece", "Shearlock", "Baa-Baa", "Marshmallow", "Cashmere", "Angora"
    );

    // Horse-specific names
    private static final List<String> HORSE_NAMES = Arrays.asList(
        "Thunder", "Spirit", "Midnight", "Storm", "Blaze", "Shadow", "Star",
        "Lucky", "Copper", "Dusty", "Apollo", "Maverick", "Trigger", "Cinnamon"
    );

    // Rabbit-specific names
    private static final List<String> RABBIT_NAMES = Arrays.asList(
        "Thumper", "Cottontail", "Flopsy", "Hopscotch", "Bunbun", "Snowball",
        "Carrot", "Clover", "Nibbles", "Velvet", "Whiskers", "Binky", "Honey"
    );

    // Predator/wild animal names
    private static final List<String> PREDATOR_NAMES = Arrays.asList(
        "Fang", "Shadow", "Hunter", "Storm", "Blaze", "Thunder", "Midnight",
        "Raven", "Ghost", "Dusk", "Prowler", "Striker", "Fury", "Tempest"
    );

    /**
     * Get 3 suggested names for an animal type.
     * @param animalType The type of animal (can be null for generic names)
     * @return List of 3 unique suggested names
     */
    public static List<String> getSuggestedNames(AnimalType animalType) {
        List<String> pool = getNamePoolForType(animalType);
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);

        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, shuffled.size()); i++) {
            result.add(shuffled.get(i));
        }

        // Pad with generic names if needed
        if (result.size() < 3) {
            List<String> genericShuffled = new ArrayList<>(GENERIC_NAMES);
            Collections.shuffle(genericShuffled, random);
            for (String name : genericShuffled) {
                if (!result.contains(name)) {
                    result.add(name);
                    if (result.size() >= 3) break;
                }
            }
        }

        return result;
    }

    /**
     * Get the appropriate name pool for an animal type.
     */
    private static List<String> getNamePoolForType(AnimalType type) {
        if (type == null) {
            return GENERIC_NAMES;
        }

        switch (type) {
            case COW:
                return COW_NAMES;
            case PIG:
                return PIG_NAMES;
            case CHICKEN:
                return CHICKEN_NAMES;
            case SHEEP:
                return SHEEP_NAMES;
            case HORSE:
                return HORSE_NAMES;
            case RABBIT:
                return RABBIT_NAMES;
            case WOLF:
            case FOX:
                return PREDATOR_NAMES;
            case GOAT:
            case DUCK:
            case TURKEY:
            case CAMEL:
                return FARM_NAMES;
            default:
                return GENERIC_NAMES;
        }
    }

    /**
     * Validate and sanitize a name input.
     * - Trims whitespace
     * - Removes control characters (keeps emoji/unicode)
     * - Enforces max length
     *
     * @param name The raw name input
     * @return Sanitized name, or null if invalid/empty
     */
    public static String validateName(String name) {
        if (name == null) {
            return null;
        }

        // Trim whitespace
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Remove control characters (U+0000 to U+001F and U+007F to U+009F)
        // but keep everything else including emoji
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            // Skip control characters
            if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                continue;
            }
            sb.append(c);
        }

        String sanitized = sb.toString().trim();
        if (sanitized.isEmpty()) {
            return null;
        }

        // Enforce max length
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }

        return sanitized;
    }

    /**
     * Check if a name would be valid.
     * @param name The name to check
     * @return true if the name is valid
     */
    public static boolean isValidName(String name) {
        return validateName(name) != null;
    }
}
