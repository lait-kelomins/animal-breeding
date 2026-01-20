package com.animaltaming.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record for a fully tamed animal.
 * This replaces TamingProgress when taming completes.
 * Persisted to disk and survives server restarts.
 *
 * @param id unique identifier for this tamed animal
 * @param ownerId UUID of the player who owns this animal
 * @param ownerName cached display name of the owner
 * @param speciesId the species of the animal
 * @param mode current behavior mode (FOLLOW or STAY)
 * @param homeX X coordinate of home/stay position
 * @param homeY Y coordinate of home/stay position
 * @param homeZ Z coordinate of home/stay position
 * @param maxFollowDistance distance before teleporting to owner
 * @param tamedTimestamp Unix timestamp (ms) when tamed
 * @param customName optional custom name (null if not named)
 */
public record TamedAnimal(
        UUID id,
        UUID ownerId,
        String ownerName,
        String speciesId,
        BehaviorMode mode,
        double homeX,
        double homeY,
        double homeZ,
        double maxFollowDistance,
        long tamedTimestamp,
        String customName
) {
    public TamedAnimal {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(ownerName, "ownerName is required");
        Objects.requireNonNull(speciesId, "speciesId is required");
        Objects.requireNonNull(mode, "mode is required");

        if (maxFollowDistance <= 0) {
            throw new IllegalArgumentException("maxFollowDistance must be positive");
        }
    }

    /**
     * Create a new TamedAnimal when taming completes.
     */
    public static TamedAnimal create(
            UUID id,
            UUID ownerId,
            String ownerName,
            String speciesId,
            double x, double y, double z,
            double maxFollowDistance
    ) {
        return new TamedAnimal(
                id,
                ownerId,
                ownerName,
                speciesId,
                BehaviorMode.FOLLOW,
                x, y, z,
                maxFollowDistance,
                System.currentTimeMillis(),
                null
        );
    }

    /**
     * Change the behavior mode.
     */
    public TamedAnimal withMode(BehaviorMode newMode) {
        return new TamedAnimal(
                id, ownerId, ownerName, speciesId,
                newMode,
                homeX, homeY, homeZ,
                maxFollowDistance, tamedTimestamp, customName
        );
    }

    /**
     * Toggle between FOLLOW and STAY modes.
     */
    public TamedAnimal withToggledMode() {
        return withMode(mode.toggle());
    }

    /**
     * Update the home/stay position.
     */
    public TamedAnimal withHome(double x, double y, double z) {
        return new TamedAnimal(
                id, ownerId, ownerName, speciesId,
                mode,
                x, y, z,
                maxFollowDistance, tamedTimestamp, customName
        );
    }

    /**
     * Set a custom name.
     */
    public TamedAnimal withCustomName(String name) {
        return new TamedAnimal(
                id, ownerId, ownerName, speciesId,
                mode,
                homeX, homeY, homeZ,
                maxFollowDistance, tamedTimestamp, name
        );
    }

    /**
     * Check if this animal is owned by the given player.
     */
    public boolean isOwnedBy(UUID playerId) {
        return ownerId.equals(playerId);
    }

    /**
     * Get the display name (custom name or species).
     */
    public String getDisplayName() {
        return customName != null ? customName : speciesId;
    }

    /**
     * Check if the animal is in follow mode.
     */
    public boolean isFollowing() {
        return mode == BehaviorMode.FOLLOW;
    }

    /**
     * Check if the animal is in stay mode.
     */
    public boolean isStaying() {
        return mode == BehaviorMode.STAY;
    }

    /**
     * Get home position as array [x, y, z].
     */
    public double[] getHomePosition() {
        return new double[]{homeX, homeY, homeZ};
    }

    /**
     * Calculate how long this animal has been tamed in milliseconds.
     */
    public long getTamedDuration() {
        return System.currentTimeMillis() - tamedTimestamp;
    }
}
