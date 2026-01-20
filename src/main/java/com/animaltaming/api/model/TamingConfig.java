package com.animaltaming.api.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration for a tameable species.
 * Loaded from JSON and validated at load time.
 *
 * @param speciesId unique identifier for the species (e.g., "wolf")
 * @param dietType the species diet type
 * @param preferredFoods list of food item IDs that can be fed
 * @param canBeMounted whether this species can be ridden for trust gain
 * @param calmingDistance max distance for sneak-calming to work
 * @param calmingTimeTicks ticks required to calm the animal
 * @param calmDurationTicks how long the animal stays calmed
 * @param trustPerFeed trust points gained per feeding
 * @param trustPerMountSecond trust points gained per second while mounted
 * @param requiredTrustLevel trust needed to complete taming
 * @param maxFollowDistance distance before teleporting to owner in FOLLOW mode
 */
public record TamingConfig(
        String speciesId,
        DietType dietType,
        List<String> preferredFoods,
        boolean canBeMounted,
        double calmingDistance,
        int calmingTimeTicks,
        int calmDurationTicks,
        int trustPerFeed,
        int trustPerMountSecond,
        int requiredTrustLevel,
        double maxFollowDistance
) {
    /**
     * Validates all required fields at construction time.
     * Throws IllegalArgumentException if validation fails.
     */
    public TamingConfig {
        Objects.requireNonNull(speciesId, "speciesId is required");
        Objects.requireNonNull(dietType, "dietType is required");
        Objects.requireNonNull(preferredFoods, "preferredFoods is required");

        if (speciesId.isBlank()) {
            throw new IllegalArgumentException("speciesId cannot be blank");
        }
        if (preferredFoods.isEmpty()) {
            throw new IllegalArgumentException("preferredFoods cannot be empty");
        }
        if (calmingDistance <= 0) {
            throw new IllegalArgumentException("calmingDistance must be positive");
        }
        if (calmingTimeTicks <= 0) {
            throw new IllegalArgumentException("calmingTimeTicks must be positive");
        }
        if (calmDurationTicks <= 0) {
            throw new IllegalArgumentException("calmDurationTicks must be positive");
        }
        if (trustPerFeed < 0) {
            throw new IllegalArgumentException("trustPerFeed cannot be negative");
        }
        if (trustPerMountSecond < 0) {
            throw new IllegalArgumentException("trustPerMountSecond cannot be negative");
        }
        if (requiredTrustLevel <= 0) {
            throw new IllegalArgumentException("requiredTrustLevel must be positive");
        }
        if (maxFollowDistance <= 0) {
            throw new IllegalArgumentException("maxFollowDistance must be positive");
        }
        if (canBeMounted && trustPerMountSecond <= 0) {
            throw new IllegalArgumentException("mountable species must have positive trustPerMountSecond");
        }
        if (!canBeMounted && trustPerMountSecond > 0) {
            throw new IllegalArgumentException("non-mountable species cannot have trustPerMountSecond");
        }

        // Make list immutable
        preferredFoods = List.copyOf(preferredFoods);
    }

    /**
     * Check if a food item is accepted by this species.
     *
     * @param foodId the food item ID to check
     * @return true if this species will eat this food
     */
    public boolean acceptsFood(String foodId) {
        return preferredFoods.contains(foodId);
    }
}
