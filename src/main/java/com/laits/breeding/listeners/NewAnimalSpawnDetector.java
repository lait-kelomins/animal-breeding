package com.laits.breeding.listeners;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.listeners.CaptureCratePacketListener;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.util.EcsReflectionUtil;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RefSystem that detects newly spawned animals immediately when added to ECS.
 *
 * Uses RefSystem pattern (like Hytale's EntitySystems) to get immediate notification
 * via onEntityAdded() with direct Ref access, instead of polling with EntityTickingSystem.
 *
 * This eliminates the delay from periodic scans - animals are detected the moment
 * they are added to the entity store.
 */
public class NewAnimalSpawnDetector extends RefSystem<EntityStore> {

    // Component types use centralized cache from EcsReflectionUtil
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = EcsReflectionUtil.MODEL_TYPE;
    private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = EcsReflectionUtil.UUID_TYPE;

    // Track processed entities to avoid duplicate processing
    // Key: UUID string, Value: timestamp when processed
    private final Map<String, Long> processedEntities = new ConcurrentHashMap<>();

    // Cache configuration
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL
    private static final int MAX_CACHE_SIZE = 10000;

    // Player UUIDs to exclude from animal detection
    private volatile Set<UUID> playerUuids = ConcurrentHashMap.newKeySet();

    // Statistics
    private static int detectedCount = 0;
    private static long lastDetectionTime = 0;
    private static String lastDetectedAnimal = "none";

    public NewAnimalSpawnDetector() {
        super();
        log("NewAnimalSpawnDetector initialized (RefSystem pattern)");
    }

    public static int getDetectedCount() { return detectedCount; }
    public static long getLastDetectionTime() { return lastDetectionTime; }
    public static String getLastDetectedAnimal() { return lastDetectedAnimal; }

    /**
     * Update the set of player UUIDs to exclude from animal detection.
     */
    public void updatePlayerUuids(Set<UUID> uuids) {
        this.playerUuids = uuids != null ? uuids : ConcurrentHashMap.newKeySet();
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Query for entities with ModelComponent - we'll filter for animals in onEntityAdded
        return MODEL_TYPE;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            if (ref == null || !ref.isValid()) return;

            // Get UUID for deduplication
            UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
            String dedupeKey = (uuidComp != null && uuidComp.getUuid() != null)
                    ? uuidComp.getUuid().toString()
                    : ref.toString();

            // Skip if already processed
            if (processedEntities.containsKey(dedupeKey)) return;

            // Skip player entities
            if (uuidComp != null && uuidComp.getUuid() != null) {
                if (playerUuids.contains(uuidComp.getUuid())) {
                    return;
                }
            }

            // Get model component
            ModelComponent modelComp = store.getComponent(ref, MODEL_TYPE);
            if (modelComp == null) return;

            // Extract model asset ID
            String modelAssetId = extractModelAssetId(modelComp);
            if (modelAssetId == null) return;

            // Check if it's an animal we care about
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            boolean isCustomAnimal = false;

            if (animalType == null) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin != null && plugin.getConfigManager() != null) {
                    isCustomAnimal = plugin.getConfigManager().isCustomAnimal(modelAssetId);
                }
            }

            // Skip if neither built-in nor custom animal
            if (animalType == null && !isCustomAnimal) return;

            // Check if this is a tamed animal exiting a coop/crate or capture crate
            // Try to restore from storage before normal processing
            try {
                // First, check for coop exit (has CoopResidentComponent with exact position)
                if (CoopResidentTracker.getStorageCount() > 0) {
                    var coopComp = store.getComponent(ref, EcsReflectionUtil.COOP_RESIDENT_TYPE);
                    if (coopComp != null) {
                        var coopPos = coopComp.getCoopLocation();
                        if (coopPos != null) {
                            log("Spawn has CoopResidentComponent! Coop at: " + coopPos.x + "," + coopPos.y + "," + coopPos.z);
                            boolean restored = CoopResidentTracker.tryRestoreFromCoopByPosition(store, commandBuffer, ref, coopPos, modelAssetId);
                            if (restored) {
                                processedEntities.put(dedupeKey, System.currentTimeMillis());
                                return;
                            }
                        }
                    }

                    // Fallback: try proximity matching for coops
                    TransformComponent transformComp = store.getComponent(ref, EcsReflectionUtil.TRANSFORM_TYPE);
                    if (transformComp != null) {
                        Vector3d spawnPos = transformComp.getPosition();
                        if (spawnPos != null) {
                            boolean restored = CoopResidentTracker.tryRestoreFromCoop(store, commandBuffer, ref, spawnPos, modelAssetId);
                            if (restored) {
                                processedEntities.put(dedupeKey, System.currentTimeMillis());
                                return;
                            }
                        }
                    }
                }

                // Check for capture crate release
                // First try packet-based detection (more reliable)
                TransformComponent transformComp = store.getComponent(ref, EcsReflectionUtil.TRANSFORM_TYPE);
                if (transformComp != null) {
                    Vector3d spawnPos = transformComp.getPosition();
                    if (spawnPos != null) {
                        // Check if there's a pending release from packet listener
                        var pendingRelease = CaptureCratePacketListener.consumePendingRelease(
                            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
                        if (pendingRelease != null) {
                            log("Capture crate release detected via packet! Player=" + pendingRelease.playerUuid);
                            // Try to restore from that player's captured animals
                            boolean restored = CoopResidentTracker.tryRestoreFromCaptureCrateByPlayer(
                                store, commandBuffer, ref, pendingRelease.playerUuid, modelAssetId);
                            if (restored) {
                                processedEntities.put(dedupeKey, System.currentTimeMillis());
                                return;
                            }
                        }

                        // Fallback: try the old method (match by animal type across all captures)
                        if (!CoopResidentTracker.capturedByPlayerIsEmpty()) {
                            boolean restored = CoopResidentTracker.tryRestoreFromCaptureCrate(
                                store, commandBuffer, ref, spawnPos, modelAssetId);
                            if (restored) {
                                processedEntities.put(dedupeKey, System.currentTimeMillis());
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Continue with normal processing
                log("Exception in coop/capture restore check: " + e.getMessage());
            }

            // Mark as processed
            processedEntities.put(dedupeKey, System.currentTimeMillis());

            // Cleanup old entries periodically
            if (processedEntities.size() > MAX_CACHE_SIZE) {
                cleanupExpiredEntries();
            }

            // Update statistics
            detectedCount++;
            lastDetectionTime = System.currentTimeMillis();
            lastDetectedAnimal = modelAssetId;

            String typeDesc = animalType != null ? animalType.toString() : "CUSTOM";
            log("Detected new animal (RefSystem): " + modelAssetId + " (" + typeDesc + ")");

            // Get the world from commandBuffer for proper multi-world support
            World world = commandBuffer.getExternalData().getWorld();

            // Notify the main plugin to set up interactions
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.onNewAnimalDetected(store, ref, modelAssetId, animalType, world);
            }

        } catch (Exception e) {
            // Silent - don't spam logs
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Cleanup from processed cache when entity is removed
        try {
            UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
            if (uuidComp != null && uuidComp.getUuid() != null) {
                processedEntities.remove(uuidComp.getUuid().toString());
            } else {
                processedEntities.remove(ref.toString());
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Extract modelAssetId from ModelComponent.
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

    /**
     * Clear processed entities cache.
     */
    public void clearProcessedCache() {
        processedEntities.clear();
    }

    /**
     * Remove expired entries from the processed entities cache.
     */
    public int cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var it = processedEntities.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
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
