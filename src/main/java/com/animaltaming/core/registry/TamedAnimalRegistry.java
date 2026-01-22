package com.animaltaming.core.registry;

import com.animaltaming.api.model.TamedAnimal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for active tamed animals.
 * Provides O(1) lookup by animalId and by ownerId.
 * Thread-safe for concurrent access.
 */
public class TamedAnimalRegistry {

    // Primary index: animalId -> TamedAnimal
    private final Map<UUID, TamedAnimal> byAnimalId = new ConcurrentHashMap<>();

    // Secondary index: ownerId -> Set<animalId>
    private final Map<UUID, Set<UUID>> byOwnerId = new ConcurrentHashMap<>();

    // Entity ID mapping: entityId -> animalId (for in-world lookups)
    private final Map<Long, UUID> entityToAnimalId = new ConcurrentHashMap<>();

    /**
     * Register a tamed animal.
     *
     * @param animal the tamed animal to register
     * @param entityId the current entity ID in the world
     */
    public void register(TamedAnimal animal, long entityId) {
        Objects.requireNonNull(animal, "animal is required");

        byAnimalId.put(animal.id(), animal);
        entityToAnimalId.put(entityId, animal.id());

        byOwnerId.computeIfAbsent(animal.ownerId(), k -> ConcurrentHashMap.newKeySet())
                 .add(animal.id());
    }

    /**
     * Update a tamed animal's data.
     * The entity ID mapping is preserved.
     *
     * @param animal the updated animal data
     */
    public void update(TamedAnimal animal) {
        Objects.requireNonNull(animal, "animal is required");

        TamedAnimal existing = byAnimalId.get(animal.id());
        if (existing == null) {
            throw new IllegalStateException("Animal not registered: " + animal.id());
        }

        // If owner changed, update secondary index
        if (!existing.ownerId().equals(animal.ownerId())) {
            Set<UUID> oldOwnerAnimals = byOwnerId.get(existing.ownerId());
            if (oldOwnerAnimals != null) {
                oldOwnerAnimals.remove(animal.id());
            }
            byOwnerId.computeIfAbsent(animal.ownerId(), k -> ConcurrentHashMap.newKeySet())
                     .add(animal.id());
        }

        byAnimalId.put(animal.id(), animal);
    }

    /**
     * Unregister a tamed animal.
     *
     * @param animalId the animal ID to remove
     */
    public void unregister(UUID animalId) {
        TamedAnimal animal = byAnimalId.remove(animalId);
        if (animal != null) {
            Set<UUID> ownerAnimals = byOwnerId.get(animal.ownerId());
            if (ownerAnimals != null) {
                ownerAnimals.remove(animalId);
            }
        }

        // Remove entity mapping by finding the key with this value
        entityToAnimalId.entrySet().removeIf(entry -> entry.getValue().equals(animalId));
    }

    /**
     * Update the entity ID mapping when an animal respawns.
     *
     * @param animalId the animal ID
     * @param newEntityId the new entity ID
     */
    public void updateEntityId(UUID animalId, long newEntityId) {
        // Remove old mapping by finding the key with this value
        entityToAnimalId.entrySet().removeIf(entry -> entry.getValue().equals(animalId));
        // Add new mapping
        entityToAnimalId.put(newEntityId, animalId);
    }

    /**
     * Get a tamed animal by its unique ID.
     *
     * @param animalId the animal ID
     * @return the animal, or empty if not found
     */
    public Optional<TamedAnimal> getByAnimalId(UUID animalId) {
        return Optional.ofNullable(byAnimalId.get(animalId));
    }

    /**
     * Get a tamed animal by its current entity ID.
     *
     * @param entityId the entity ID
     * @return the animal, or empty if not found
     */
    public Optional<TamedAnimal> getByEntityId(long entityId) {
        UUID animalId = entityToAnimalId.get(entityId);
        if (animalId == null) {
            return Optional.empty();
        }
        return getByAnimalId(animalId);
    }

    /**
     * Get the animal ID for an entity ID.
     *
     * @param entityId the entity ID
     * @return the animal ID, or empty if not a tamed animal
     */
    public Optional<UUID> getAnimalIdForEntity(long entityId) {
        return Optional.ofNullable(entityToAnimalId.get(entityId));
    }

    /**
     * Get all tamed animals owned by a player.
     *
     * @param ownerId the owner's UUID
     * @return set of tamed animals (never null, may be empty)
     */
    public Set<TamedAnimal> getByOwnerId(UUID ownerId) {
        Set<UUID> animalIds = byOwnerId.get(ownerId);
        if (animalIds == null || animalIds.isEmpty()) {
            return Set.of();
        }

        Set<TamedAnimal> result = new HashSet<>();
        for (UUID animalId : animalIds) {
            TamedAnimal animal = byAnimalId.get(animalId);
            if (animal != null) {
                result.add(animal);
            }
        }
        return result;
    }

    /**
     * Check if an entity is a tamed animal.
     *
     * @param entityId the entity ID
     * @return true if this entity is a registered tamed animal
     */
    public boolean isTamedAnimal(long entityId) {
        return entityToAnimalId.containsKey(entityId);
    }

    /**
     * Get all registered tamed animals.
     *
     * @return unmodifiable collection
     */
    public Collection<TamedAnimal> getAll() {
        return Collections.unmodifiableCollection(byAnimalId.values());
    }

    /**
     * Get the total number of tamed animals.
     *
     * @return count
     */
    public int size() {
        return byAnimalId.size();
    }

    /**
     * Clear all registrations.
     */
    public void clear() {
        byAnimalId.clear();
        byOwnerId.clear();
        entityToAnimalId.clear();
    }
}
