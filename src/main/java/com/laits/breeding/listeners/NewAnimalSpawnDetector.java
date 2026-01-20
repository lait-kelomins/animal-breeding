package com.laits.breeding.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.AnimalType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hypixel.hytale.server.core.entity.UUIDComponent;

import java.lang.reflect.Field;
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
    private static ComponentType<EntityStore, ?> newSpawnComponentType = null;

    // Track processed entities to avoid duplicate processing
    private final Set<String> processedEntities = ConcurrentHashMap.newKeySet();

    // Player UUIDs to exclude from animal detection (updated by main plugin)
    private volatile Set<UUID> playerUuids = ConcurrentHashMap.newKeySet();

    // Statistics
    private static int detectedCount = 0;
    private static long lastDetectionTime = 0;
    private static String lastDetectedAnimal = "none";

    public NewAnimalSpawnDetector() {
        super();
        initializeNewSpawnComponentType();
    }

    /**
     * Initialize the NewSpawnComponent type via reflection.
     */
    @SuppressWarnings("unchecked")
    private void initializeNewSpawnComponentType() {
        try {
            Class<?> newSpawnClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.entity.component.NewSpawnComponent");
            Object typeObj = newSpawnClass.getMethod("getComponentType").invoke(null);
            newSpawnComponentType = (ComponentType<EntityStore, ?>) typeObj;
            log("NewSpawnComponent type initialized successfully");
        } catch (ClassNotFoundException e) {
            log("NewSpawnComponent class not found - spawn detection will use fallback");
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
            Object newSpawnComp = chunk.getComponent(entityIndex, newSpawnComponentType);
            if (newSpawnComp == null) return;

            // Get entity reference
            Ref<EntityStore> entityRef = chunk.getReferenceTo(entityIndex);
            if (entityRef == null || !entityRef.isValid()) return;

            // Create unique key to avoid reprocessing
            String refKey = entityRef.toString();
            if (processedEntities.contains(refKey)) return;

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

            // Check if it's an animal we care about
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            if (animalType == null) return;

            // Mark as processed
            processedEntities.add(refKey);

            // Update statistics
            detectedCount++;
            lastDetectionTime = System.currentTimeMillis();
            lastDetectedAnimal = modelAssetId;

            log("Detected new animal spawn: " + modelAssetId + " (" + animalType + ")");

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
     * Extract modelAssetId from ModelComponent using reflection.
     */
    private String extractModelAssetId(ModelComponent modelComp) {
        try {
            Field modelField = ModelComponent.class.getDeclaredField("model");
            modelField.setAccessible(true);
            Object model = modelField.get(modelComp);
            if (model == null) return null;

            // Extract from toString: Model{modelAssetId='Cow', scale=1.0, ...}
            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0) return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start) return null;
            return modelStr.substring(start, end);
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
