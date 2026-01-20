package com.animaltaming.api.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record tracking taming progress for an animal.
 * Used during the taming process before the animal becomes fully tamed.
 *
 * @param animalId unique ID for this taming attempt
 * @param speciesId the species being tamed
 * @param attemptingPlayerId UUID of the player attempting to tame
 * @param state current taming state
 * @param calmingStartTick tick when calming began
 * @param calmExpirationTick tick when calm state expires
 * @param trustLevel current trust accumulated (0 to requiredTrustLevel)
 * @param lastTrustGainTick tick of last trust gain (for decay)
 * @param mountStartTick tick when player started riding (for mount trust)
 */
public record TamingProgress(
        UUID animalId,
        String speciesId,
        UUID attemptingPlayerId,
        TamingState state,
        long calmingStartTick,
        long calmExpirationTick,
        int trustLevel,
        long lastTrustGainTick,
        long mountStartTick
) {
    public TamingProgress {
        Objects.requireNonNull(animalId, "animalId is required");
        Objects.requireNonNull(speciesId, "speciesId is required");
        Objects.requireNonNull(state, "state is required");
    }

    /**
     * Create a new taming progress when a player starts calming.
     */
    public static TamingProgress startCalming(UUID animalId, String speciesId, UUID playerId, long currentTick) {
        return new TamingProgress(
                animalId,
                speciesId,
                playerId,
                TamingState.CALMING,
                currentTick,
                0,
                0,
                0,
                0
        );
    }

    /**
     * Transition to CALMED state.
     */
    public TamingProgress withCalmed(long currentTick, int calmDurationTicks) {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                TamingState.CALMED,
                calmingStartTick,
                currentTick + calmDurationTicks,
                trustLevel,
                lastTrustGainTick,
                0
        );
    }

    /**
     * Transition to BONDING_FEED state.
     */
    public TamingProgress withBondingFeed() {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                TamingState.BONDING_FEED,
                calmingStartTick,
                calmExpirationTick,
                trustLevel,
                lastTrustGainTick,
                0
        );
    }

    /**
     * Transition to BONDING_MOUNT state and record mount start.
     */
    public TamingProgress withBondingMount(long currentTick) {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                TamingState.BONDING_MOUNT,
                calmingStartTick,
                calmExpirationTick,
                trustLevel,
                lastTrustGainTick,
                currentTick
        );
    }

    /**
     * Add trust from feeding.
     */
    public TamingProgress withTrustGain(int amount, long currentTick) {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                state,
                calmingStartTick,
                calmExpirationTick,
                trustLevel + amount,
                currentTick,
                mountStartTick
        );
    }

    /**
     * Update trust level directly.
     */
    public TamingProgress withTrustLevel(int newTrustLevel, long currentTick) {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                state,
                calmingStartTick,
                calmExpirationTick,
                newTrustLevel,
                currentTick,
                mountStartTick
        );
    }

    /**
     * Reset mount tracking (player dismounted).
     */
    public TamingProgress withMountReset() {
        return new TamingProgress(
                animalId,
                speciesId,
                attemptingPlayerId,
                state == TamingState.BONDING_MOUNT ? TamingState.CALMED : state,
                calmingStartTick,
                calmExpirationTick,
                trustLevel,
                lastTrustGainTick,
                0
        );
    }

    /**
     * Check if the calm state has expired.
     */
    public boolean isCalmExpired(long currentTick) {
        return calmExpirationTick > 0 && currentTick >= calmExpirationTick;
    }

    /**
     * Calculate seconds spent mounted.
     */
    public double getMountDurationSeconds(long currentTick, int tickRate) {
        if (mountStartTick <= 0) {
            return 0;
        }
        long ticksRiding = currentTick - mountStartTick;
        return (double) ticksRiding / tickRate;
    }
}
