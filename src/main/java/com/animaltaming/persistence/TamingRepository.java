package com.animaltaming.persistence;

import com.animaltaming.api.model.TamedAnimal;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository interface for tamed animal persistence.
 * Implementations handle storage and retrieval.
 */
public interface TamingRepository {

    /**
     * Save a tamed animal.
     *
     * @param animal the animal to save
     */
    void save(TamedAnimal animal);

    /**
     * Load a tamed animal by ID.
     *
     * @param animalId the animal's unique ID
     * @return the animal, or empty if not found
     */
    Optional<TamedAnimal> load(UUID animalId);

    /**
     * Delete a tamed animal.
     *
     * @param animalId the animal's unique ID
     * @return true if deleted, false if not found
     */
    boolean delete(UUID animalId);

    /**
     * Find all animals owned by a player.
     *
     * @param ownerId the owner's UUID
     * @return set of animal IDs
     */
    Set<UUID> findByOwner(UUID ownerId);

    /**
     * Load all saved animals.
     *
     * @return collection of all animals
     */
    Collection<TamedAnimal> loadAll();

    /**
     * Save multiple animals.
     *
     * @param animals the animals to save
     */
    default void saveAll(Collection<TamedAnimal> animals) {
        for (TamedAnimal animal : animals) {
            save(animal);
        }
    }

    /**
     * Check if an animal exists in storage.
     *
     * @param animalId the animal's unique ID
     * @return true if exists
     */
    boolean exists(UUID animalId);

    /**
     * Get the count of stored animals.
     *
     * @return animal count
     */
    int count();
}
