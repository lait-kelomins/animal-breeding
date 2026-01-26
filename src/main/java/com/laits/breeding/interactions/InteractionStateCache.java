package com.laits.breeding.interactions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.OriginalInteractionState;
import com.laits.breeding.util.EcsReflectionUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton cache for storing original entity interaction states.
 * Used to track the original interaction ID and hint before we override them
 * with our custom feed/tame interactions, allowing restoration when needed.
 */
public final class InteractionStateCache {

    private static final InteractionStateCache INSTANCE = new InteractionStateCache();

    // Store original interaction state (ID + hint) before we override them
    // Key is entity UUID string (stable across different Ref objects for same entity)
    private final Map<String, OriginalInteractionState> originalStates = new ConcurrentHashMap<>();

    private InteractionStateCache() {
        // Private constructor for singleton
    }

    /**
     * Get the singleton instance.
     *
     * @return The InteractionStateCache instance
     */
    public static InteractionStateCache getInstance() {
        return INSTANCE;
    }

    /**
     * Get the original interaction ID for an entity (before we set Root_FeedAnimal).
     * Used by FeedAnimalInteraction to fall back to default behavior (e.g., mounting).
     *
     * @param entityRef The entity reference
     * @return The original interaction ID, or null if not stored
     */
    public String getOriginalInteractionId(Ref<EntityStore> entityRef) {
        return getOriginalInteractionId(entityRef, null);
    }

    /**
     * Get the original interaction ID for an entity.
     * Only returns an ID if we actually saved the original interaction when setting up the entity.
     * Does NOT assume a fallback like "Root_Mount" as it may not exist in all versions.
     *
     * @param entityRef  The entity reference
     * @param animalType The animal type (unused, kept for API compatibility)
     * @return The original interaction ID, or null if not stored
     */
    public String getOriginalInteractionId(Ref<EntityStore> entityRef, AnimalType animalType) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key != null) {
            OriginalInteractionState stored = originalStates.get(key);
            if (stored != null && stored.hasInteraction()) {
                return stored.getInteractionId();
            }
        }
        // No fallback - if we didn't save the original, we don't know what it was
        return null;
    }

    /**
     * Get the full original interaction state for an entity.
     *
     * @param entityRef The entity reference
     * @return The original state, or null if not stored
     */
    public OriginalInteractionState getOriginalState(Ref<EntityStore> entityRef) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key != null) {
            return originalStates.get(key);
        }
        return null;
    }

    /**
     * Store the original interaction state (ID + hint) for an entity.
     * ALWAYS stores, even if interactionId is null - that's the correct original
     * state for horses (null Use interaction allows mounting to work via default behavior).
     *
     * @param entityRef     The entity reference
     * @param interactionId The original interaction ID (may be null)
     * @param hint          The original interaction hint (may be null)
     * @param animalType    The animal type (unused, kept for API compatibility)
     */
    public void storeOriginalState(Ref<EntityStore> entityRef, String interactionId, String hint,
                                   AnimalType animalType) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key == null)
            return;

        // Always store the original state, even if interactionId is null
        // For horses, null is the correct original state - it allows mounting to work
        originalStates.put(key, new OriginalInteractionState(interactionId, hint));
    }

    /**
     * Legacy overload for backwards compatibility - stores interaction ID without hint.
     *
     * @param entityRef     The entity reference
     * @param interactionId The original interaction ID
     * @param animalType    The animal type
     */
    public void storeOriginalInteractionId(Ref<EntityStore> entityRef, String interactionId,
                                           AnimalType animalType) {
        storeOriginalState(entityRef, interactionId, null, animalType);
    }

    /**
     * Legacy overload for backwards compatibility.
     *
     * @param entityRef     The entity reference
     * @param interactionId The original interaction ID
     */
    public void storeOriginalInteractionId(Ref<EntityStore> entityRef, String interactionId) {
        storeOriginalState(entityRef, interactionId, null, null);
    }

    /**
     * Clean up stale entries in the cache.
     * Removes index-based keys (ephemeral) and validates UUID-based keys.
     *
     * @return Number of entries removed
     */
    public int cleanupStaleEntries() {
        int removed = 0;
        Iterator<Map.Entry<String, OriginalInteractionState>> it = originalStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OriginalInteractionState> entry = it.next();
            String key = entry.getKey();
            // Index-based keys are ephemeral and should be cleaned up periodically
            if (key.startsWith("idx:")) {
                it.remove();
                removed++;
            }
            // UUID-based keys could be validated, but for simplicity we rely on EntityRemoveEvent
        }
        return removed;
    }

    /**
     * Remove an entry from the cache.
     *
     * @param entityRef The entity reference
     * @return The removed state, or null if not present
     */
    public OriginalInteractionState remove(Ref<EntityStore> entityRef) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key != null) {
            return originalStates.remove(key);
        }
        return null;
    }

    /**
     * Remove an entry from the cache by key.
     *
     * @param key The cache key
     * @return The removed state, or null if not present
     */
    public OriginalInteractionState removeByKey(String key) {
        if (key != null) {
            return originalStates.remove(key);
        }
        return null;
    }

    /**
     * Get the current size of the cache (for debugging).
     *
     * @return The number of cached entries
     */
    public int getCacheSize() {
        return originalStates.size();
    }

    /**
     * Clear all entries from the cache.
     */
    public void clear() {
        originalStates.clear();
    }
}
