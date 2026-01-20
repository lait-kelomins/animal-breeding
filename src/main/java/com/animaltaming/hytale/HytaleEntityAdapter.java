package com.animaltaming.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages bidirectional mapping between plugin's long entityId
 * and Hytale's Entity objects.
 *
 * Thread-safe for concurrent access from game tick and event handlers.
 */
public class HytaleEntityAdapter {

    private final Map<Long, Entity> entityMap = new ConcurrentHashMap<>();
    private final Map<Entity, Long> reverseMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> uuidToId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    /**
     * Register an entity and get its plugin entity ID.
     * If already registered, returns existing ID.
     *
     * @param entity the Hytale entity
     * @return the plugin entity ID
     */
    public long registerEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }

        // Return existing ID if already registered
        Long existingId = reverseMap.get(entity);
        if (existingId != null) {
            return existingId;
        }

        long id = nextId.getAndIncrement();
        entityMap.put(id, entity);
        reverseMap.put(entity, id);

        UUID uuid = entity.getUuid();
        if (uuid != null) {
            uuidToId.put(uuid, id);
        }

        return id;
    }

    /**
     * Get an entity by its plugin ID.
     *
     * @param entityId the plugin entity ID
     * @return optional containing the entity if found
     */
    public Optional<Entity> getEntity(long entityId) {
        return Optional.ofNullable(entityMap.get(entityId));
    }

    /**
     * Get a living entity by its plugin ID.
     *
     * @param entityId the plugin entity ID
     * @return optional containing the living entity if found and is a LivingEntity
     */
    public Optional<LivingEntity> getLivingEntity(long entityId) {
        return getEntity(entityId)
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e);
    }

    /**
     * Get a player by its plugin ID.
     *
     * @param entityId the plugin entity ID
     * @return optional containing the player if found and is a Player
     */
    public Optional<Player> getPlayer(long entityId) {
        return getEntity(entityId)
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e);
    }

    /**
     * Get the plugin ID for an entity.
     *
     * @param entity the Hytale entity
     * @return optional containing the plugin ID if registered
     */
    public Optional<Long> getEntityId(Entity entity) {
        return Optional.ofNullable(reverseMap.get(entity));
    }

    /**
     * Get the plugin ID for an entity by its UUID.
     *
     * @param uuid the entity UUID
     * @return optional containing the plugin ID if registered
     */
    public Optional<Long> getEntityIdByUuid(UUID uuid) {
        return Optional.ofNullable(uuidToId.get(uuid));
    }

    /**
     * Unregister an entity by its plugin ID.
     *
     * @param entityId the plugin entity ID
     */
    public void unregisterEntity(long entityId) {
        Entity entity = entityMap.remove(entityId);
        if (entity != null) {
            reverseMap.remove(entity);
            UUID uuid = entity.getUuid();
            if (uuid != null) {
                uuidToId.remove(uuid);
            }
        }
    }

    /**
     * Unregister an entity directly.
     *
     * @param entity the entity to unregister
     */
    public void unregisterEntity(Entity entity) {
        Long id = reverseMap.remove(entity);
        if (id != null) {
            entityMap.remove(id);
            UUID uuid = entity.getUuid();
            if (uuid != null) {
                uuidToId.remove(uuid);
            }
        }
    }

    /**
     * Check if an entity is registered.
     *
     * @param entityId the plugin entity ID
     * @return true if registered
     */
    public boolean isRegistered(long entityId) {
        return entityMap.containsKey(entityId);
    }

    /**
     * Get all registered entity IDs.
     *
     * @return set of registered entity IDs
     */
    public Set<Long> getAllEntityIds() {
        return Set.copyOf(entityMap.keySet());
    }

    /**
     * Get all registered entity mappings for iteration.
     * Used by RefUtils to find entityId by Ref.
     *
     * @return iterable of all entity ID to entity mappings
     */
    public Set<Map.Entry<Long, Entity>> getAllMappings() {
        return Set.copyOf(entityMap.entrySet());
    }

    /**
     * Find the plugin entity ID for a given Ref by scanning all registered entities.
     * Used when converting MountedByComponent passengers back to plugin IDs.
     *
     * @param targetRef the Ref to find
     * @return optional containing the plugin ID if found
     */
    public Optional<Long> findEntityIdByRef(Ref<EntityStore> targetRef) {
        if (targetRef == null || !targetRef.isValid()) {
            return Optional.empty();
        }

        for (Map.Entry<Long, Entity> entry : entityMap.entrySet()) {
            Entity entity = entry.getValue();
            Ref<EntityStore> ref = entity.getReference();
            if (ref != null && ref.equals(targetRef)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * Get the count of registered entities.
     *
     * @return number of registered entities
     */
    public int size() {
        return entityMap.size();
    }

    /**
     * Clear all registered entities.
     */
    public void clear() {
        entityMap.clear();
        reverseMap.clear();
        uuidToId.clear();
    }
}
