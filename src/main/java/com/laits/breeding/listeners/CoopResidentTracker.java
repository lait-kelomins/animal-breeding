package com.laits.breeding.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.adventure.farming.component.CoopResidentComponent;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.NameplateUtil;
import com.tameableanimals.tame.HyTameComponent;

/**
 * Tracks tamed animals entering/exiting coops and capture crates.
 *
 * This system watches for CoopResidentComponent and:
 * - When added: marks the tamed animal as "in storage" (won't be respawned)
 * - When removed: if not UNLOAD reason, the animal is being released back into world
 *
 * This prevents duplication where:
 * 1. Chicken enters coop at night
 * 2. Game removes entity (stores in coop block data)
 * 3. Plugin would normally mark as despawned and respawn it
 * 4. Chicken exits coop in morning = duplicate
 *
 * By tracking coop storage, we know not to respawn these animals.
 */
public class CoopResidentTracker extends RefSystem<EntityStore> {

    private static final ComponentType<EntityStore, CoopResidentComponent> COOP_RESIDENT_TYPE =
        CoopResidentComponent.getComponentType();

    // Track tamed animals currently in coops/crates
    // Maps animal UUID -> coop block position (for matching on exit)
    private static final Map<UUID, Vector3i> animalsInStorage = new ConcurrentHashMap<>();

    // Also track by coop position for quick lookup when chicken exits
    // Maps "x,y,z" -> list of animal UUIDs stored at that coop
    private static final Map<String, List<UUID>> animalsByCoopPosition = new ConcurrentHashMap<>();

    // Track animals captured by capture crates
    // Maps player UUID -> list of captured animal UUIDs (in order of capture)
    private static final Map<UUID, List<UUID>> capturedByPlayer = new ConcurrentHashMap<>();

    // Track pending captures (player about to capture animal)
    // Maps animal UUID -> player UUID (who is capturing it)
    // Entry is valid for short time window between interaction and entity removal
    private static final Map<UUID, PendingCapture> pendingCaptures = new ConcurrentHashMap<>();
    private static final long PENDING_CAPTURE_TTL_MS = 1000; // 1 second TTL

    // Capture crate item ID
    private static final String CAPTURE_CRATE_ITEM_ID = "Tool_Capture_Crate";

    /**
     * Record for tracking a pending capture.
     */
    private static class PendingCapture {
        final UUID playerUuid;
        final long timestamp;

        PendingCapture(UUID playerUuid) {
            this.playerUuid = playerUuid;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > PENDING_CAPTURE_TTL_MS;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return COOP_RESIDENT_TYPE;
    }

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Entity with CoopResidentComponent was added/spawned
        // This fires when:
        // 1. Chicken spawns naturally near a coop (has component from spawn)
        // 2. Chicken EXITS coop (game spawns new entity from coop data)
        //
        // For case 2, we need to match by coop position and restore taming data

        log("onEntityAdded triggered! reason=" + reason);

        try {
            UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            if (uuidComp == null || uuidComp.getUuid() == null) {
                log("  - No UUID component found");
                return;
            }

            UUID newEntityUuid = uuidComp.getUuid();
            log("  - New entity UUID: " + newEntityUuid);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                log("  - Plugin is null!");
                return;
            }

            // Get the coop position from CoopResidentComponent
            CoopResidentComponent coopComp = store.getComponent(ref, COOP_RESIDENT_TYPE);
            Vector3i coopPos = null;
            if (coopComp != null) {
                coopPos = coopComp.getCoopLocation();
                log("  - Coop position: " + (coopPos != null ? coopPos.x + "," + coopPos.y + "," + coopPos.z : "null"));
            }

            // Check if we have a stored tamed animal at this coop position
            if (coopPos != null) {
                String key = positionKey(coopPos);
                List<UUID> storedAtCoop = animalsByCoopPosition.get(key);
                log("  - Animals stored at this coop: " + (storedAtCoop != null ? storedAtCoop.size() : 0));

                if (storedAtCoop != null && !storedAtCoop.isEmpty()) {
                    // Pop one stored animal and restore its data to this new entity
                    UUID oldUuid = popStoredAnimalAtPosition(coopPos);
                    if (oldUuid != null) {
                        log("  - Matching stored animal UUID: " + oldUuid);

                        TamingManager tamingManager = plugin.getTamingManager();
                        if (tamingManager != null) {
                            TamedAnimalData tamedData = tamingManager.getTamedData(oldUuid);
                            if (tamedData != null) {
                                log("  - Found TamedAnimalData! Name: " + tamedData.getCustomName());

                                // Update with new entity reference
                                tamingManager.markRespawned(oldUuid, newEntityUuid, ref);
                                log("  - Updated TamedAnimalData with new UUID");

                                // Restore HyTameComponent
                                var hyTameType = plugin.getHyTameComponentType();
                                if (hyTameType != null) {
                                    try {
                                        HyTameComponent hyTameComp = store.ensureAndGetComponent(ref, hyTameType);
                                        if (hyTameComp != null) {
                                            UUID ownerUuid = tamedData.getOwnerUuid();
                                            String ownerName = tamedData.getOwnerName();
                                            UUID hytameId = tamedData.getHytameId();

                                            hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                                            if (hytameId != null) {
                                                hyTameComp.setHytameId(hytameId);
                                            }
                                            log("  - Restored HyTameComponent: owner=" + ownerName + ", hytameId=" + hytameId);
                                        }
                                    } catch (Exception e) {
                                        log("  - Failed to restore HyTameComponent: " + e.getMessage());
                                    }
                                }

                                // Restore nameplate
                                String customName = tamedData.getCustomName();
                                if (customName != null && !customName.isEmpty()) {
                                    try {
                                        NameplateUtil.setEntityNameplate(ref, customName);
                                        log("  - Restored nameplate: " + customName);
                                    } catch (Exception e) {
                                        log("  - Failed to restore nameplate: " + e.getMessage());
                                    }
                                }

                                log("  - SUCCESS: Chicken restored from coop!");
                                return;
                            }
                        }
                    }
                }
            }

            log("  - No stored tamed animal to restore at this position");
        } catch (Exception e) {
            log("  - Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Entity with CoopResidentComponent is being removed
        // IMPORTANT: This fires when chicken ENTERS the coop (entity removed to storage)
        // If reason is UNLOAD, chunk is unloading
        // If reason is REMOVE, chicken is entering coop storage

        log("onEntityRemove triggered! reason=" + reason);

        try {
            UUIDComponent uuidComp = commandBuffer.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            if (uuidComp == null || uuidComp.getUuid() == null) {
                log("  - No UUID component found");
                return;
            }

            UUID entityUuid = uuidComp.getUuid();
            log("  - Entity UUID: " + entityUuid);

            if (reason == RemoveReason.UNLOAD) {
                // Chunk unloading - not entering coop
                log("  - UNLOAD reason, ignoring");
                return;
            }

            // reason == REMOVE means chicken is ENTERING coop storage
            // Get the coop position from CoopResidentComponent
            CoopResidentComponent coopComp = commandBuffer.getComponent(ref, COOP_RESIDENT_TYPE);
            Vector3i coopPos = null;
            if (coopComp != null) {
                coopPos = coopComp.getCoopLocation();
                log("  - Coop position: " + (coopPos != null ? coopPos.x + "," + coopPos.y + "," + coopPos.z : "null"));
            }

            // Check if this is a tamed animal
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                log("  - Plugin is null!");
                return;
            }

            TamingManager tamingManager = plugin.getTamingManager();
            if (tamingManager == null) {
                log("  - TamingManager is null!");
                return;
            }

            boolean isTamed = tamingManager.isTamed(entityUuid);
            log("  - Is tamed: " + isTamed);

            if (isTamed && coopPos != null) {
                // Check if this animal is being captured by a capture crate
                // If so, don't add to coop storage - the capture crate system will handle it

                // First check: is there a pending capture by network ID?
                // This catches the case where capture was detected but world.execute() hasn't run yet
                try {
                    var networkIdComp = commandBuffer.getComponent(ref, EcsReflectionUtil.NETWORK_ID_TYPE);
                    if (networkIdComp != null) {
                        int networkId = networkIdComp.getId();
                        if (CaptureCratePacketListener.hasPendingCapture(networkId)) {
                            log("  - Pending capture detected by networkId=" + networkId + " - NOT adding to coop storage");
                            return;
                        }
                    }
                } catch (Exception e) {
                    // Continue with other checks
                }

                // Second check: is the animal already marked as captured?
                // This catches the case where world.execute() has already run
                TamedAnimalData tamedData = tamingManager.getTamedData(entityUuid);
                if (tamedData != null && tamedData.isCaptured()) {
                    log("  - Animal is marked as CAPTURED (capture crate) - NOT adding to coop storage");
                    return;
                }

                // Mark as in storage with position - prevents respawn and enables matching on exit
                addToStorage(entityUuid, coopPos);
                log("  - ADDED to storage (chicken entering coop). Storage count: " + animalsInStorage.size());
            }
        } catch (Exception e) {
            log("  - Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a tamed animal is currently in coop/crate storage.
     * Used by RespawnManager to avoid respawning animals that are stored.
     */
    public static boolean isInStorage(UUID entityUuid) {
        return entityUuid != null && animalsInStorage.containsKey(entityUuid);
    }

    /**
     * Get count of animals currently in storage (for debugging).
     */
    public static int getStorageCount() {
        return animalsInStorage.size();
    }

    /**
     * Clear storage tracking (for cleanup on shutdown).
     */
    public static void clearStorage() {
        animalsInStorage.clear();
        animalsByCoopPosition.clear();
        capturedByPlayer.clear();
    }

    /**
     * Get the capture crate item ID.
     */
    public static String getCaptureCrateItemId() {
        return CAPTURE_CRATE_ITEM_ID;
    }

    /**
     * Register a pending capture - called when player interacts with tamed animal while holding capture crate.
     * This creates a short-lived entry that gets consumed when EntityRemoveEvent fires.
     */
    public static void registerPendingCapture(UUID animalUuid, UUID playerUuid) {
        // Clean up expired entries first
        cleanupExpiredPendingCaptures();
        pendingCaptures.put(animalUuid, new PendingCapture(playerUuid));
        logStatic("Registered pending capture: animal=" + animalUuid + " player=" + playerUuid);
    }

    /**
     * Register a pending capture by hytameId - more reliable than entity UUID.
     * The hytameId is stable across entity respawns/captures.
     * Also marks the animal as captured in TamedAnimalData for persistence across server restarts.
     */
    public static void registerPendingCaptureByHytameId(UUID hytameId, UUID playerUuid) {
        if (hytameId == null || playerUuid == null) {
            logStatic("registerPendingCaptureByHytameId: null hytameId or playerUuid");
            return;
        }

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return;

        // Look up TamedAnimalData by hytameId
        TamedAnimalData tamedData = tamingManager.findByHytameId(hytameId);
        if (tamedData == null) {
            logStatic("registerPendingCaptureByHytameId: no TamedAnimalData for hytameId=" + hytameId);
            return;
        }

        // Mark as captured in TamedAnimalData (persists across server restart)
        tamedData.setCaptured(true);
        tamingManager.saveImmediately();  // Persist immediately

        // Track this capture using hytameId (which is stable)
        // Store in capturedByPlayer using hytameId as the key
        capturedByPlayer.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(hytameId);
        logStatic("Registered capture by hytameId: player=" + playerUuid + " hytameId=" + hytameId +
            " name=" + tamedData.getCustomName() + " type=" + tamedData.getAnimalType());
    }

    /**
     * Check if there's a valid pending capture for an animal and consume it.
     * Returns the player UUID if found and not expired, null otherwise.
     */
    public static UUID consumePendingCapture(UUID animalUuid) {
        PendingCapture pending = pendingCaptures.remove(animalUuid);
        if (pending != null && !pending.isExpired()) {
            logStatic("Consumed pending capture: animal=" + animalUuid + " player=" + pending.playerUuid);
            return pending.playerUuid;
        }
        return null;
    }

    /**
     * Clean up expired pending captures.
     */
    private static void cleanupExpiredPendingCaptures() {
        pendingCaptures.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Track a tamed animal captured by a player using a capture crate.
     */
    public static void trackCapture(UUID playerUuid, UUID animalUuid) {
        capturedByPlayer.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(animalUuid);
        logStatic("Tracked capture: player=" + playerUuid + " animal=" + animalUuid);
    }

    /**
     * Try to get a captured animal UUID for a player (FIFO - first captured, first released).
     * Returns null if the player has no captured animals.
     */
    public static UUID popCapturedAnimal(UUID playerUuid) {
        List<UUID> captured = capturedByPlayer.get(playerUuid);
        if (captured != null && !captured.isEmpty()) {
            UUID animalUuid = captured.remove(0);
            if (captured.isEmpty()) {
                capturedByPlayer.remove(playerUuid);
            }
            logStatic("Popped captured animal: player=" + playerUuid + " animal=" + animalUuid);
            return animalUuid;
        }
        return null;
    }

    /**
     * Check if a player has any captured animals.
     */
    public static boolean hasCaptures(UUID playerUuid) {
        List<UUID> captured = capturedByPlayer.get(playerUuid);
        return captured != null && !captured.isEmpty();
    }

    /**
     * Get count of animals captured by a player.
     */
    public static int getCaptureCount(UUID playerUuid) {
        List<UUID> captured = capturedByPlayer.get(playerUuid);
        return captured != null ? captured.size() : 0;
    }

    /**
     * Check if a player is holding a capture crate item.
     */
    public static boolean isHoldingCaptureCrate(com.hypixel.hytale.server.core.entity.entities.Player player) {
        try {
            var inventory = player.getInventory();
            if (inventory == null) return false;

            var heldItem = inventory.getActiveHotbarItem();
            if (heldItem == null) return false;

            String itemId = heldItem.getItemId();
            return CAPTURE_CRATE_ITEM_ID.equals(itemId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if there are any captured animals pending restoration.
     */
    public static boolean capturedByPlayerIsEmpty() {
        return capturedByPlayer.isEmpty();
    }

    /**
     * Convert Vector3i to string key for map lookup.
     */
    private static String positionKey(Vector3i pos) {
        return pos.x + "," + pos.y + "," + pos.z;
    }

    /**
     * Add an animal to storage at a specific coop position.
     */
    private static void addToStorage(UUID uuid, Vector3i coopPos) {
        animalsInStorage.put(uuid, coopPos);

        String key = positionKey(coopPos);
        animalsByCoopPosition.computeIfAbsent(key, k -> new ArrayList<>()).add(uuid);
    }

    /**
     * Remove an animal from storage tracking.
     */
    private static boolean removeFromStorage(UUID uuid) {
        Vector3i pos = animalsInStorage.remove(uuid);
        if (pos != null) {
            String key = positionKey(pos);
            List<UUID> list = animalsByCoopPosition.get(key);
            if (list != null) {
                list.remove(uuid);
                if (list.isEmpty()) {
                    animalsByCoopPosition.remove(key);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Get a stored tamed animal UUID for a coop position (and remove it from tracking).
     * Returns null if no tamed animals are stored at that position.
     */
    private static UUID popStoredAnimalAtPosition(Vector3i coopPos) {
        String key = positionKey(coopPos);
        List<UUID> list = animalsByCoopPosition.get(key);
        if (list != null && !list.isEmpty()) {
            UUID uuid = list.remove(0);
            animalsInStorage.remove(uuid);
            if (list.isEmpty()) {
                animalsByCoopPosition.remove(key);
            }
            return uuid;
        }
        return null;
    }

    /**
     * Try to restore a spawned animal from a specific player's capture crate.
     * Called when packet listener detected which player released the animal.
     *
     * @param playerUuid The player who released the animal
     * @return true if the animal was restored
     */
    public static boolean tryRestoreFromCaptureCrateByPlayer(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            UUID playerUuid,
            String modelAssetId
    ) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return false;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return false;

        // Convert modelAssetId to AnimalType for matching
        com.laits.breeding.models.AnimalType spawnedType =
            com.laits.breeding.models.AnimalType.fromModelAssetId(modelAssetId);

        // First try the in-memory captured list (for same-session releases)
        List<UUID> capturedList = capturedByPlayer.get(playerUuid);
        if (capturedList != null && !capturedList.isEmpty()) {
            logStatic("tryRestoreFromCaptureCrateByPlayer: player=" + playerUuid +
                " model=" + modelAssetId + " type=" + spawnedType +
                " (player has " + capturedList.size() + " captured animals in memory)");

            // Find first matching animal type in player's captured list
            // Use iterator to safely remove stale entries while iterating
            java.util.Iterator<UUID> iterator = capturedList.iterator();
            while (iterator.hasNext()) {
                UUID hytameId = iterator.next();
                TamedAnimalData tamedData = tamingManager.findByHytameId(hytameId);

                if (tamedData == null) {
                    // Entry has no data - remove stale entry
                    logStatic("  - Removing stale entry (no data): hytameId=" + hytameId);
                    iterator.remove();
                    continue;
                }

                // CRITICAL FIX: Verify the animal is actually captured
                // This prevents race conditions where world.execute() adds to the list
                // AFTER the animal was already released via the fallback path
                if (!tamedData.isCaptured()) {
                    logStatic("  - Removing stale entry (not captured): hytameId=" + hytameId +
                        " name=" + tamedData.getCustomName());
                    iterator.remove();
                    continue;
                }

                com.laits.breeding.models.AnimalType capturedType = tamedData.getAnimalType();

                if (capturedType != null && capturedType == spawnedType) {
                    logStatic("  - Found matching captured animal! hytameId=" + hytameId +
                        " type=" + capturedType + " name=" + tamedData.getCustomName());

                    // Remove from captured list
                    iterator.remove();
                    if (capturedList.isEmpty()) {
                        capturedByPlayer.remove(playerUuid);
                    }

                    // Restore the animal using hytameId
                    return restoreCapturedAnimalByHytameId(store, commandBuffer, ref, hytameId, tamedData, plugin);
                }
            }

            // Clean up empty list if all entries were stale
            if (capturedList.isEmpty()) {
                capturedByPlayer.remove(playerUuid);
            }
        }

        // Fallback: search ALL captured animals by type (for after server restart)
        logStatic("tryRestoreFromCaptureCrateByPlayer: no in-memory captures, searching persisted data for type=" + spawnedType);
        TamedAnimalData capturedAnimal = tamingManager.findCapturedByType(spawnedType);
        if (capturedAnimal != null) {
            logStatic("  - Found persisted captured animal! hytameId=" + capturedAnimal.getHytameId() +
                " type=" + capturedAnimal.getAnimalType() + " name=" + capturedAnimal.getCustomName());
            return restoreCapturedAnimalByHytameId(store, commandBuffer, ref,
                capturedAnimal.getHytameId(), capturedAnimal, plugin);
        }

        logStatic("  - No matching captured animal found");
        return false;
    }

    /**
     * Restore a captured animal using hytameId (the stable identifier).
     */
    private static boolean restoreCapturedAnimalByHytameId(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            UUID hytameId,
            TamedAnimalData tamedData,
            LaitsBreedingPlugin plugin
    ) {
        TamingManager tamingManager = plugin.getTamingManager();

        // Get new entity UUID
        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
        UUID newUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
        if (newUuid == null) {
            logStatic("  - New entity has no UUID!");
            return false;
        }

        // Get the old entity UUID from tamedData (may be stale, but needed for markRespawned)
        UUID oldEntityUuid = tamedData.getAnimalUuid();

        logStatic("  - Restoring by hytameId: hytameId=" + hytameId +
            " oldEntityUUID=" + oldEntityUuid +
            " newEntityUUID=" + newUuid + " name=" + tamedData.getCustomName());

        // Update TamingManager with new entity reference
        if (oldEntityUuid != null) {
            tamingManager.markRespawned(oldEntityUuid, newUuid, ref);
        }

        // Restore HyTameComponent
        var hyTameType = plugin.getHyTameComponentType();
        if (hyTameType != null) {
            try {
                HyTameComponent hyTameComp = commandBuffer.ensureAndGetComponent(ref, hyTameType);
                if (hyTameComp != null) {
                    UUID ownerUuid = tamedData.getOwnerUuid();
                    String ownerName = tamedData.getOwnerName();

                    hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                    hyTameComp.setHytameId(hytameId);
                    logStatic("  - Restored HyTameComponent with hytameId=" + hytameId);
                }
            } catch (Exception e) {
                logStatic("  - Failed to restore HyTameComponent: " + e.getMessage());
            }
        }

        // Restore nameplate
        String customName = tamedData.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            final String nameToRestore = customName;
            final Ref<EntityStore> entityRef = ref;
            try {
                World world = commandBuffer.getExternalData().getWorld();
                world.execute(() -> {
                    try {
                        NameplateUtil.setEntityNameplate(entityRef, nameToRestore);
                        logStatic("  - Restored nameplate: " + nameToRestore);
                    } catch (Exception e) {
                        logStatic("  - Failed to restore nameplate: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logStatic("  - Failed to schedule nameplate restore: " + e.getMessage());
            }
        }

        // Clear captured flag and persist
        tamedData.setCaptured(false);
        tamingManager.saveImmediately();

        logStatic("  - SUCCESS: Animal restored from capture crate (by hytameId)!");
        return true;
    }

    /**
     * Common method to restore a captured animal's data to a new entity.
     */
    private static boolean restoreCapturedAnimal(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            UUID capturedAnimalUuid,
            TamedAnimalData tamedData,
            LaitsBreedingPlugin plugin
    ) {
        TamingManager tamingManager = plugin.getTamingManager();

        // Get new entity UUID
        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
        UUID newUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
        if (newUuid == null) {
            logStatic("  - New entity has no UUID!");
            return false;
        }

        logStatic("  - Restoring: oldUUID=" + capturedAnimalUuid +
            " newUUID=" + newUuid + " name=" + tamedData.getCustomName());

        // Update with new entity reference
        tamingManager.markRespawned(capturedAnimalUuid, newUuid, ref);

        // Restore HyTameComponent
        var hyTameType = plugin.getHyTameComponentType();
        if (hyTameType != null) {
            try {
                HyTameComponent hyTameComp = commandBuffer.ensureAndGetComponent(ref, hyTameType);
                if (hyTameComp != null) {
                    UUID ownerUuid = tamedData.getOwnerUuid();
                    String ownerName = tamedData.getOwnerName();
                    UUID hytameId = tamedData.getHytameId();

                    hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                    if (hytameId != null) {
                        hyTameComp.setHytameId(hytameId);
                    }
                    logStatic("  - Restored HyTameComponent");
                }
            } catch (Exception e) {
                logStatic("  - Failed to restore HyTameComponent: " + e.getMessage());
            }
        }

        // Restore nameplate
        String customName = tamedData.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            final String nameToRestore = customName;
            final Ref<EntityStore> entityRef = ref;
            try {
                World world = commandBuffer.getExternalData().getWorld();
                world.execute(() -> {
                    try {
                        NameplateUtil.setEntityNameplate(entityRef, nameToRestore);
                        logStatic("  - Restored nameplate: " + nameToRestore);
                    } catch (Exception e) {
                        logStatic("  - Failed to restore nameplate: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logStatic("  - Failed to schedule nameplate restore: " + e.getMessage());
            }
        }

        logStatic("  - SUCCESS: Animal restored from capture crate!");
        return true;
    }

    /**
     * Try to restore a spawned animal from a capture crate.
     * Checks if any nearby player has captured animals of the same type.
     *
     * @return true if the animal was restored from capture crate
     */
    public static boolean tryRestoreFromCaptureCrate(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            Vector3d spawnPos,
            String modelAssetId
    ) {
        if (capturedByPlayer.isEmpty()) {
            return false;
        }

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return false;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return false;

        // Convert modelAssetId to AnimalType for matching
        com.laits.breeding.models.AnimalType spawnedType =
            com.laits.breeding.models.AnimalType.fromModelAssetId(modelAssetId);

        logStatic("tryRestoreFromCaptureCrate: checking spawn at " +
            String.format("%.1f,%.1f,%.1f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()) +
            " model=" + modelAssetId + " type=" + spawnedType);

        // Find nearby players who have captured animals
        World world = commandBuffer.getExternalData().getWorld();
        double searchRadius = 10.0; // Players within 10 blocks

        for (Map.Entry<UUID, List<UUID>> entry : capturedByPlayer.entrySet()) {
            UUID playerUuid = entry.getKey();
            List<UUID> capturedList = entry.getValue();

            if (capturedList == null || capturedList.isEmpty()) continue;

            // Check if this player is nearby
            // We need to find the player entity and check distance
            // For now, just try to match by animal type since release happens right after capture
            for (int i = 0; i < capturedList.size(); i++) {
                UUID capturedAnimalUuid = capturedList.get(i);
                TamedAnimalData tamedData = tamingManager.getTamedData(capturedAnimalUuid);

                if (tamedData != null) {
                    com.laits.breeding.models.AnimalType capturedType = tamedData.getAnimalType();

                    if (capturedType != null && capturedType == spawnedType) {
                        logStatic("  - Found matching captured animal! UUID=" + capturedAnimalUuid +
                            " type=" + capturedType + " player=" + playerUuid);

                        // Remove from captured list
                        capturedList.remove(i);
                        if (capturedList.isEmpty()) {
                            capturedByPlayer.remove(playerUuid);
                        }

                        // Get new entity UUID
                        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
                        UUID newUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
                        if (newUuid == null) {
                            logStatic("  - New entity has no UUID!");
                            return false;
                        }

                        logStatic("  - Restoring: oldUUID=" + capturedAnimalUuid +
                            " newUUID=" + newUuid + " name=" + tamedData.getCustomName());

                        // Update with new entity reference
                        tamingManager.markRespawned(capturedAnimalUuid, newUuid, ref);

                        // Restore HyTameComponent
                        var hyTameType = plugin.getHyTameComponentType();
                        if (hyTameType != null) {
                            try {
                                HyTameComponent hyTameComp = commandBuffer.ensureAndGetComponent(ref, hyTameType);
                                if (hyTameComp != null) {
                                    UUID ownerUuid = tamedData.getOwnerUuid();
                                    String ownerName = tamedData.getOwnerName();
                                    UUID hytameId = tamedData.getHytameId();

                                    hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                                    if (hytameId != null) {
                                        hyTameComp.setHytameId(hytameId);
                                    }
                                    logStatic("  - Restored HyTameComponent");
                                }
                            } catch (Exception e) {
                                logStatic("  - Failed to restore HyTameComponent: " + e.getMessage());
                            }
                        }

                        // Restore nameplate
                        String customName = tamedData.getCustomName();
                        if (customName != null && !customName.isEmpty()) {
                            final String nameToRestore = customName;
                            final Ref<EntityStore> entityRef = ref;
                            try {
                                world.execute(() -> {
                                    try {
                                        NameplateUtil.setEntityNameplate(entityRef, nameToRestore);
                                        logStatic("  - Restored nameplate: " + nameToRestore);
                                    } catch (Exception e) {
                                        logStatic("  - Failed to restore nameplate: " + e.getMessage());
                                    }
                                });
                            } catch (Exception e) {
                                logStatic("  - Failed to schedule nameplate restore: " + e.getMessage());
                            }
                        }

                        logStatic("  - SUCCESS: Animal restored from capture crate!");
                        return true;
                    }
                }
            }
        }

        logStatic("  - No matching captured animal found");
        return false;
    }

    /**
     * Try to restore a spawned animal from coop storage using the EXACT coop position.
     * This is the most reliable method - used when the entity has CoopResidentComponent.
     *
     * @return true if the animal was restored from coop storage
     */
    public static boolean tryRestoreFromCoopByPosition(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            Vector3i coopPos,
            String modelAssetId
    ) {
        if (coopPos == null || animalsInStorage.isEmpty()) {
            return false;
        }

        logStatic("tryRestoreFromCoopByPosition: exact coop at " +
            coopPos.x + "," + coopPos.y + "," + coopPos.z +
            " model=" + modelAssetId + " (storage has " + animalsInStorage.size() + " animals)");

        // Look for stored animals at this exact coop position
        String key = positionKey(coopPos);
        List<UUID> storedAtCoop = animalsByCoopPosition.get(key);
        logStatic("  - Animals at this exact coop position: " + (storedAtCoop != null ? storedAtCoop.size() : 0));

        if (storedAtCoop == null || storedAtCoop.isEmpty()) {
            logStatic("  - No stored animals at this coop position");
            return false;
        }

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return false;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return false;

        // Convert modelAssetId to AnimalType for matching
        com.laits.breeding.models.AnimalType spawnedType =
            com.laits.breeding.models.AnimalType.fromModelAssetId(modelAssetId);

        // Find a matching animal type at this coop
        for (UUID storedUuid : new ArrayList<>(storedAtCoop)) {
            TamedAnimalData tamedData = tamingManager.getTamedData(storedUuid);
            if (tamedData != null) {
                com.laits.breeding.models.AnimalType storedType = tamedData.getAnimalType();
                if (storedType != null && storedType == spawnedType) {
                    logStatic("  - Found matching animal! UUID=" + storedUuid + " type=" + storedType);

                    // Remove from storage
                    removeFromStorage(storedUuid);

                    // Get new entity UUID
                    UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
                    UUID newUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
                    if (newUuid == null) {
                        logStatic("  - New entity has no UUID!");
                        return false;
                    }

                    logStatic("  - Restoring: oldUUID=" + storedUuid + " newUUID=" + newUuid + " name=" + tamedData.getCustomName());

                    // Update with new entity reference
                    tamingManager.markRespawned(storedUuid, newUuid, ref);

                    // Restore HyTameComponent using CommandBuffer (safe for system callbacks)
                    var hyTameType = plugin.getHyTameComponentType();
                    if (hyTameType != null) {
                        try {
                            HyTameComponent hyTameComp = commandBuffer.ensureAndGetComponent(ref, hyTameType);
                            if (hyTameComp != null) {
                                UUID ownerUuid = tamedData.getOwnerUuid();
                                String ownerName = tamedData.getOwnerName();
                                UUID hytameId = tamedData.getHytameId();

                                hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                                if (hytameId != null) {
                                    hyTameComp.setHytameId(hytameId);
                                }
                                logStatic("  - Restored HyTameComponent");
                            }
                        } catch (Exception e) {
                            logStatic("  - Failed to restore HyTameComponent: " + e.getMessage());
                        }
                    }

                    // Restore nameplate - schedule on world thread to be safe
                    String customName = tamedData.getCustomName();
                    if (customName != null && !customName.isEmpty()) {
                        final String nameToRestore = customName;
                        final Ref<EntityStore> entityRef = ref;
                        try {
                            World world = commandBuffer.getExternalData().getWorld();
                            world.execute(() -> {
                                try {
                                    NameplateUtil.setEntityNameplate(entityRef, nameToRestore);
                                    logStatic("  - Restored nameplate: " + nameToRestore);
                                } catch (Exception e) {
                                    logStatic("  - Failed to restore nameplate: " + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            logStatic("  - Failed to schedule nameplate restore: " + e.getMessage());
                        }
                    }

                    logStatic("  - SUCCESS: Animal restored from coop (exact position match)!");
                    return true;
                }
            }
        }

        logStatic("  - No matching animal type found at this coop");
        return false;
    }

    /**
     * Try to match a newly spawned animal to a stored coop animal by proximity.
     * Checks if the spawn position is within MATCH_RADIUS blocks of any stored coop position.
     *
     * @param spawnPos The position where the animal spawned
     * @param modelAssetId The model asset ID of the spawned animal
     * @return The UUID of a matching stored animal, or null if no match
     */
    private static final double MATCH_RADIUS = 16.0; // Blocks radius to match

    public static UUID tryMatchByProximity(Vector3d spawnPos, String modelAssetId) {
        if (spawnPos == null || animalsInStorage.isEmpty()) {
            return null;
        }

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return null;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return null;

        // Convert modelAssetId to AnimalType for matching
        com.laits.breeding.models.AnimalType spawnedType =
            com.laits.breeding.models.AnimalType.fromModelAssetId(modelAssetId);

        // Check each stored animal
        for (Map.Entry<UUID, Vector3i> entry : animalsInStorage.entrySet()) {
            UUID storedUuid = entry.getKey();
            Vector3i coopPos = entry.getValue();

            // Calculate distance
            double dx = spawnPos.getX() - coopPos.x;
            double dy = spawnPos.getY() - coopPos.y;
            double dz = spawnPos.getZ() - coopPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance <= MATCH_RADIUS) {
                // Check if the stored animal matches the type
                TamedAnimalData tamedData = tamingManager.getTamedData(storedUuid);
                if (tamedData != null) {
                    // Check if animal type matches (e.g., both are chickens)
                    com.laits.breeding.models.AnimalType storedType = tamedData.getAnimalType();
                    if (storedType != null && storedType == spawnedType) {
                        logStatic("Proximity match found! Spawn at " +
                            String.format("%.1f,%.1f,%.1f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()) +
                            " matched to coop at " + coopPos.x + "," + coopPos.y + "," + coopPos.z +
                            " (distance: " + String.format("%.1f", distance) + ", type: " + storedType + ")");

                        // Remove from storage and return
                        removeFromStorage(storedUuid);
                        return storedUuid;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Try to restore a spawned animal from coop storage.
     * Called by NewAnimalSpawnDetector when a new animal spawns.
     *
     * @return true if the animal was restored from coop storage
     */
    public static boolean tryRestoreFromCoop(
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            Vector3d spawnPos,
            String modelAssetId
    ) {
        if (animalsInStorage.isEmpty()) {
            return false;
        }

        logStatic("tryRestoreFromCoop: checking spawn at " +
            String.format("%.1f,%.1f,%.1f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()) +
            " model=" + modelAssetId + " (storage has " + animalsInStorage.size() + " animals)");

        UUID oldUuid = tryMatchByProximity(spawnPos, modelAssetId);
        if (oldUuid == null) {
            logStatic("  - No proximity match found");
            return false;
        }

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) return false;

        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager == null) return false;

        TamedAnimalData tamedData = tamingManager.getTamedData(oldUuid);
        if (tamedData == null) {
            logStatic("  - TamedAnimalData not found for " + oldUuid);
            return false;
        }

        // Get new entity UUID
        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
        UUID newUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
        if (newUuid == null) {
            logStatic("  - New entity has no UUID");
            return false;
        }

        logStatic("  - Restoring: oldUUID=" + oldUuid + " newUUID=" + newUuid + " name=" + tamedData.getCustomName());

        // Update with new entity reference
        tamingManager.markRespawned(oldUuid, newUuid, ref);

        // Restore HyTameComponent using CommandBuffer (safe for system callbacks)
        var hyTameType = plugin.getHyTameComponentType();
        if (hyTameType != null) {
            try {
                HyTameComponent hyTameComp = commandBuffer.ensureAndGetComponent(ref, hyTameType);
                if (hyTameComp != null) {
                    UUID ownerUuid = tamedData.getOwnerUuid();
                    String ownerName = tamedData.getOwnerName();
                    UUID hytameId = tamedData.getHytameId();

                    hyTameComp.setTamed(ownerUuid, ownerName != null ? ownerName : "Unknown");
                    if (hytameId != null) {
                        hyTameComp.setHytameId(hytameId);
                    }
                    logStatic("  - Restored HyTameComponent");
                }
            } catch (Exception e) {
                logStatic("  - Failed to restore HyTameComponent: " + e.getMessage());
            }
        }

        // Restore nameplate - schedule on world thread to be safe
        String customName = tamedData.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            final String nameToRestore = customName;
            final Ref<EntityStore> entityRef = ref;
            try {
                World world = commandBuffer.getExternalData().getWorld();
                world.execute(() -> {
                    try {
                        NameplateUtil.setEntityNameplate(entityRef, nameToRestore);
                        logStatic("  - Restored nameplate: " + nameToRestore);
                    } catch (Exception e) {
                        logStatic("  - Failed to restore nameplate: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logStatic("  - Failed to schedule nameplate restore: " + e.getMessage());
            }
        }

        logStatic("  - SUCCESS: Animal restored from coop!");
        return true;
    }

    private static void logStatic(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[CoopTracker] " + message);
        }
    }

    private void log(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[CoopTracker] " + message);
        }
    }
}
