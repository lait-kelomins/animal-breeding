package com.animaltaming.api;

import com.animaltaming.api.model.TamedAnimal;
import com.animaltaming.api.model.TamingProgress;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Main service interface for the taming system.
 * Provides the public API for taming operations.
 * All implementations must be thread-safe.
 */
public interface TamingService {

    /**
     * Start the calming process for an animal.
     *
     * @param animalEntityId the entity ID of the animal
     * @param animalId the unique ID of the animal
     * @param speciesId the species identifier
     * @param playerId the UUID of the player attempting to tame
     * @return the new taming progress, or empty if calming cannot start
     */
    Optional<TamingProgress> startCalming(long animalEntityId, UUID animalId, String speciesId, UUID playerId);

    /**
     * Feed an animal to gain trust.
     *
     * @param animalEntityId the entity ID of the animal
     * @param playerId the UUID of the feeding player
     * @param foodId the food item being fed
     * @return updated taming progress, or empty if feeding failed
     */
    Optional<TamingProgress> feed(long animalEntityId, UUID playerId, String foodId);

    /**
     * Complete the taming process and create a TamedAnimal.
     *
     * @param animalEntityId the entity ID of the animal
     * @param ownerName display name of the owner
     * @param x current X position
     * @param y current Y position
     * @param z current Z position
     * @return the new TamedAnimal, or empty if taming cannot complete
     */
    Optional<TamedAnimal> completeTaming(long animalEntityId, String ownerName, double x, double y, double z);

    /**
     * Toggle a tamed animal's behavior mode between FOLLOW and STAY.
     *
     * @param animalId the unique ID of the tamed animal
     * @param playerId the UUID of the player toggling (must be owner)
     * @param x current X position (for new home if switching to STAY)
     * @param y current Y position
     * @param z current Z position
     * @return true if mode was toggled
     */
    boolean toggleBehaviorMode(UUID animalId, UUID playerId, double x, double y, double z);

    /**
     * Get a tamed animal by its ID.
     *
     * @param animalId the unique ID
     * @return the tamed animal, or empty if not found
     */
    Optional<TamedAnimal> getTamedAnimal(UUID animalId);

    /**
     * Get all animals owned by a player.
     *
     * @param playerId the player's UUID
     * @return set of tamed animals (never null, may be empty)
     */
    Set<TamedAnimal> getAnimalsOwnedBy(UUID playerId);

    /**
     * Get the current taming progress for an animal.
     *
     * @param animalEntityId the entity ID
     * @return the progress, or empty if not being tamed
     */
    Optional<TamingProgress> getTamingProgress(long animalEntityId);

    /**
     * Remove taming progress for an animal (e.g., when taming fails or is interrupted).
     *
     * @param animalEntityId the entity ID
     */
    void removeTamingProgress(long animalEntityId);

    /**
     * Release a tamed animal (remove ownership).
     *
     * @param animalId the unique ID of the animal
     * @param playerId the UUID of the player releasing (must be owner)
     * @return true if released successfully
     */
    boolean releaseTamedAnimal(UUID animalId, UUID playerId);

    /**
     * Update a tamed animal's data.
     *
     * @param animal the updated animal data
     */
    void updateTamedAnimal(TamedAnimal animal);
}
