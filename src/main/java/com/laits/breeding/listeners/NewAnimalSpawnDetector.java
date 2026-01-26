package com.laits.breeding.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.NewSpawnComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.AnimalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS ticking system that detects newly spawned animals.
 *
 * This system queries for entities with NewSpawnComponent and ModelComponent,
 * detecting animals as soon as they spawn rather than waiting for periodic scans.
 *
 * The NewSpawnComponent is added by the engine when entities are created and
 * removed after they've been processed by internal systems.
 */
public class NewAnimalSpawnDetector extends EntityTickingSystem<EntityStore> {

    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = UUIDComponent.getComponentType();

    // NewSpawnComponent type - obtained via reflection since it may not be public API
    private static ComponentType<EntityStore, NewSpawnComponent> newSpawnComponentType = null;

    // Track processed entities with timestamps to avoid duplicate processing and enable TTL cleanup
    // Key: entity ref string, Value: timestamp when processed
    private final Map<String, Long> processedEntities = new ConcurrentHashMap<>();

    // Cache configuration
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL
    private static final int MAX_CACHE_SIZE = 10000; // Maximum entries before forced cleanup
    private long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // Cleanup every minute

    // Player UUIDs to exclude from animal detection (updated by main plugin)
    private volatile Set<UUID> playerUuids = ConcurrentHashMap.newKeySet();

    // Statistics
    private static int detectedCount = 0;
    private static long lastDetectionTime = 0;
    private static String lastDetectedAnimal = "none";

    public NewAnimalSpawnDetector() {
        super();
        initializeReflectionCache();
    }

    /**
     * Initialize all reflection caches once at construction.
     */
    private void initializeReflectionCache() {
        // Initialize NewSpawnComponent type
        try {
            newSpawnComponentType = NewSpawnComponent.getComponentType();
            log("NewSpawnComponent type initialized successfully");
        } catch (Exception e) {
            log("Failed to initialize NewSpawnComponent: " + e.getMessage());
        }
    }

    public static int getDetectedCount() { return detectedCount; }
    public static long getLastDetectionTime() { return lastDetectionTime; }
    public static String getLastDetectedAnimal() { return lastDetectedAnimal; }

    /**
     * Update the set of player UUIDs to exclude from animal detection.
     * Called periodically by the main plugin.
     */
    public void updatePlayerUuids(Set<UUID> uuids) {
        this.playerUuids = uuids != null ? uuids : ConcurrentHashMap.newKeySet();
    }

    @Override
    public void tick(
            float deltaTime,
            int entityIndex,
            @NotNull ArchetypeChunk<EntityStore> chunk,
            @NotNull Store<EntityStore> store,
            @NotNull CommandBuffer<EntityStore> buffer
    ) {
        try {
            // Skip if NewSpawnComponent couldn't be initialized
            if (newSpawnComponentType == null) return;

            // Check if entity has NewSpawnComponent
            NewSpawnComponent newSpawnComp = chunk.getComponent(entityIndex, newSpawnComponentType);
            if (newSpawnComp == null) return;

            // Get entity reference
            Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
            if (entityRef == null || !entityRef.isValid()) return;

            // Create unique key to avoid reprocessing
            String refKey = entityRef.toString();
            if (processedEntities.containsKey(refKey)) return;

            // Periodic cache cleanup (don't do it every tick for performance)
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime > CLEANUP_INTERVAL_MS || processedEntities.size() > MAX_CACHE_SIZE) {
                cleanupExpiredEntries();
                lastCleanupTime = now;
            }

            // Check if this is a player entity (skip players with animal models)
            try {
                UUIDComponent uuidComp = store.getComponent(entityRef, UUID_TYPE);
                if (uuidComp != null && uuidComp.getUuid() != null) {
                    if (playerUuids.contains(uuidComp.getUuid())) {
                        return; // Skip player entities
                    }
                }
            } catch (Exception e) {
                // Ignore - entity ref may be invalid
            }

            // Check if entity has a model (indicates it's a visual entity)
            ModelComponent modelComp = chunk.getComponent(entityIndex, MODEL_TYPE);
            if (modelComp == null) return;

            // Extract model asset ID to identify the entity type
            String modelAssetId = extractModelAssetId(modelComp);
            if (modelAssetId == null) return;

            // Check if it's an animal we care about (built-in OR custom)
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            boolean isCustomAnimal = false;

            // If not a built-in type, check if it's a registered custom animal
            if (animalType == null) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin != null && plugin.getConfigManager() != null) {
                    isCustomAnimal = plugin.getConfigManager().isCustomAnimal(modelAssetId);
                }
            }

            // Skip if neither built-in nor custom animal
            if (animalType == null && !isCustomAnimal) return;

            // Mark as processed with timestamp
            processedEntities.put(refKey, System.currentTimeMillis());

            // Update statistics
            detectedCount++;
            lastDetectionTime = System.currentTimeMillis();
            lastDetectedAnimal = modelAssetId;

            String typeDesc = animalType != null ? animalType.toString() : "CUSTOM";
            log("Detected new animal spawn: " + modelAssetId + " (" + typeDesc + ")");

            // Notify the main plugin to set up interactions
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.onNewAnimalDetected(store, entityRef, modelAssetId, animalType);
            }

        } catch (Exception e) {
            // Silent - don't spam logs on every tick
        }
    }

    /**
     * Extract modelAssetId from ModelComponent using cached reflection.
     */
    private String extractModelAssetId(ModelComponent modelComp) {
        try {
            Model model = modelComp.getModel();
            if (model == null) return null;

            return model.getModelAssetId();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        // Query for entities with NewSpawnComponent
        // The system will only tick on entities matching this query
        if (newSpawnComponentType != null) {
            return newSpawnComponentType;
        }
        // Fallback: query for ModelComponent (less efficient but works)
        return MODEL_TYPE;
    }

    /**
     * Clear processed entities cache (call periodically to prevent memory leak).
     */
    public void clearProcessedCache() {
        processedEntities.clear();
    }

    /**
     * Remove expired entries from the processed entities cache.
     * Entries older than CACHE_TTL_MS are removed.
     * @return Number of entries removed
     */
    public int cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        int removed = 0;

        Iterator<Map.Entry<String, Long>> it = processedEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > CACHE_TTL_MS) {
                it.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log("Cleaned up " + removed + " expired entries from spawn detector cache");
        }

        return removed;
    }

    /**
     * Get the number of entities in the processed cache.
     */
    public int getProcessedCacheSize() {
        return processedEntities.size();
    }

    private void log(String message) {
        if (LaitsBreedingPlugin.isVerboseLogging()) {
            System.out.println("[NewAnimalSpawnDetector] " + message);
        }
    }
}
