package com.laits.breeding.managers;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages taming state for animals.
 * Handles tamed animal tracking and ownership.
 *
 * Uses dual-indexing for ECS ↔ JSON sync:
 * - tamedAnimals: indexed by animalUuid (current entity UUID, changes on respawn)
 * - tamedByHytameId: indexed by hytameId (stable, never changes)
 */
public class TamingManager {

    // Tamed animals by their current entity UUID (changes on respawn)
    private final Map<UUID, TamedAnimalData> tamedAnimalsByUuid = new ConcurrentHashMap<>();

    // Secondary index by hytameId (stable, never changes) for ECS sync
    private final Map<UUID, TamedAnimalData> tamedByHytameId = new ConcurrentHashMap<>();

    // Reference to persistence manager for dirty marking
    private PersistenceManager persistenceManager;

    // Logger
    private Consumer<String> logger;

    // Grace period tracking for preventing duplication on slow servers
    private volatile long initializationTime = 0;
    private int gracePeriodMs = 15000;  // Default 15 seconds (fallback)

    // Smart loading detection - track expected vs found entities
    private int expectedEntityCount = 0;      // From JSON load
    private int foundEntityCount = 0;         // Incremented as entities link
    private volatile boolean loadingComplete = false;
    private volatile long loadingCompleteTime = 0;
    private static final int POST_LOAD_BUFFER_MS = 5000;  // 5 second buffer after all entities found
    private static final int MAX_GRACE_PERIOD_MS = 30000; // 30 second hard max (fallback for missing entities)

    public TamingManager() {
    }

    /**
     * Set the grace period in milliseconds.
     * During this period after initialization, respawn checks are skipped.
     */
    public void setGracePeriodMs(int ms) {
        this.gracePeriodMs = ms;
    }

    /**
     * Mark the taming manager as initialized.
     * Call this after initial entity scanning is complete.
     */
    public void markInitialized() {
        this.initializationTime = System.currentTimeMillis();
        log("TamingManager initialized - grace period of " + (gracePeriodMs / 1000) + "s started");
    }

    /**
     * Check if we're still in the initialization grace period.
     * During this period, respawn operations should be skipped.
     *
     * Smart detection: Waits until all expected entities are found, then adds a buffer.
     * Fallback: Hard max timeout in case some entities are genuinely missing.
     */
    public boolean isInGracePeriod() {
        if (initializationTime == 0) return true;  // Not yet initialized

        long now = System.currentTimeMillis();

        // Hard max timeout - prevents indefinite waiting if entities are genuinely missing
        if ((now - initializationTime) > MAX_GRACE_PERIOD_MS) {
            if (!loadingComplete) {
                loadingComplete = true;
                loadingCompleteTime = now;
                log("Grace period max timeout reached - " + foundEntityCount + "/" + expectedEntityCount +
                    " entities found. Proceeding anyway.");
            }
            return false;
        }

        // Still scanning for entities
        if (!loadingComplete) return true;

        // Buffer period after loading complete
        return (now - loadingCompleteTime) < POST_LOAD_BUFFER_MS;
    }

    /**
     * Called when an entity is successfully linked to saved tamed animal data.
     * Tracks progress toward loading completion.
     */
    public void onEntityLinked(UUID hytameId) {
        if (loadingComplete) return;

        foundEntityCount++;
        log("Entity linked: " + hytameId + " (" + foundEntityCount + "/" + expectedEntityCount + ")");

        if (foundEntityCount >= expectedEntityCount && !loadingComplete) {
            loadingComplete = true;
            loadingCompleteTime = System.currentTimeMillis();
            log("All " + expectedEntityCount + " tamed entities found - " +
                (POST_LOAD_BUFFER_MS / 1000) + "s buffer started");
        }
    }

    /**
     * Set the logger for output messages.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    /**
     * Set the persistence manager for dirty marking.
     */
    public void setPersistenceManager(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /**
     * Mark data as dirty (needs saving).
     * @param saveImmediately If true, forces an immediate save to disk
     */
    private void markDirty(boolean saveImmediately) {
        if (persistenceManager != null) {
            if (saveImmediately) {
                persistenceManager.forceSave(getAllTamedAnimals());
            } else {
                persistenceManager.markDirty();
            }
        }
    }

    /**
     * Mark data as dirty (will be saved on next auto-save).
     */
    private void markDirty() {
        markDirty(false);
    }

    /**
     * Force an immediate save to disk.
     * Use this for critical data changes that must persist immediately.
     */
    public void saveImmediately() {
        markDirty(true);
    }

    /**
     * Notify that tamed animal data has been modified externally.
     * Call this after modifying TamedAnimalData directly (e.g., copyFromBreedingData).
     */
    public void notifyDataChanged() {
        markDirty(true); // External changes should save immediately
    }

    // ===========================================
    // INITIALIZATION
    // ===========================================

    /**
     * Load tamed animals from persistence.
     * @param savedAnimals List of tamed animal data from save file
     */
    public void loadFromPersistence(List<TamedAnimalData> savedAnimals) {
        tamedAnimalsByUuid.clear();
        tamedByHytameId.clear();
        int migrated = 0;
        int despawnedCount = 0;
        int capturedCount = 0;

        // Reset smart loading detection
        foundEntityCount = 0;
        loadingComplete = false;
        loadingCompleteTime = 0;

        for (TamedAnimalData data : savedAnimals) {
            if (data != null && data.getAnimalUuid() != null) {
                // Migrate old entries without hytameId
                if (data.getHytameId() == null) {
                    data.ensureHytameId();
                    migrated++;
                }

                // IMPORTANT: Keep the isDespawned state from the save file
                // This prevents race conditions where:
                // 1. World loads slowly
                // 2. Plugin sees no entity and marks as despawned
                // 3. World finishes loading and entity appears
                // 4. RespawnManager respawns another copy = duplicate
                // The entity scan will update isDespawned based on actual entity presence
                data.setEntityRef(null); // Will be reattached by animal scan

                if (data.isDespawned()) {
                    despawnedCount++;
                }
                if (data.isCaptured()) {
                    capturedCount++;
                }

                tamedAnimalsByUuid.put(data.getAnimalUuid(), data);
                tamedByHytameId.put(data.getHytameId(), data);
            }
        }

        // Set expected entity count for smart loading detection
        // Only count non-despawned, non-captured, non-dead animals (those expected to exist in world)
        expectedEntityCount = 0;
        for (TamedAnimalData data : tamedAnimalsByUuid.values()) {
            if (!data.isDespawned() && !data.isCaptured() && !data.isDead()) {
                expectedEntityCount++;
            }
        }

        log("Loaded " + tamedAnimalsByUuid.size() + " tamed animals from persistence");
        log("  - " + despawnedCount + " marked as despawned");
        log("  - " + capturedCount + " marked as captured");
        log("  - " + expectedEntityCount + " expected to exist in world (for smart loading)");

        if (migrated > 0) {
            log("Migrated " + migrated + " animals to new hytameId system");
            markDirty(true); // Save migrated data immediately
        }
    }

    /**
     * Get all tamed animal data for persistence.
     */
    public Collection<TamedAnimalData> getAllTamedAnimals() {
        return new ArrayList<>(tamedAnimalsByUuid.values());
    }

    // ===========================================
    // TAMING METHODS
    // ===========================================

    /**
     * Check if an animal is tamed.
     */
    public boolean isTamed(UUID animalId) {
        return animalId != null && tamedAnimalsByUuid.containsKey(animalId);
    }

    /**
     * Get tamed animal data by UUID.
     */
    public TamedAnimalData getTamedData(UUID animalId) {
        return animalId != null ? tamedAnimalsByUuid.get(animalId) : null;
    }

    /**
     * Tame an animal.
     * @param animalId Entity UUID
     * @param ownerUuid Player UUID who is taming
     * @param name Custom name for the animal
     * @param type Animal type (can be null for custom animals)
     * @return The created TamedAnimalData
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type) {
        return tameAnimal(animalId, ownerUuid, name, type, null);
    }

    /**
     * Tame an animal with entity reference for position tracking.
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type, Ref<EntityStore> entityRef) {
        return tameAnimal(animalId, ownerUuid, name, type, entityRef, 0, 0, 0);
    }

    /**
     * Tame an animal with entity reference and initial position.
     * Note: type can be null for custom animals (e.g., Mosshorn).
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type, Ref<EntityStore> entityRef, double x, double y, double z) {
        return tameAnimal(animalId, ownerUuid, name, type, entityRef, x, y, z, GrowthStage.ADULT);
    }

    /**
     * Tame an animal with entity reference, initial position, and growth stage.
     * Use this when taming babies to ensure correct growth stage is saved.
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type, Ref<EntityStore> entityRef, double x, double y, double z, GrowthStage growthStage) {
        return tameAnimal(null, animalId, ownerUuid, name, type, entityRef, x, y, z, growthStage, null);
    }

    /**
     * Tame an animal with entity reference, initial position, growth stage, and world name.
     * Use this overload for multi-world support.
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type, Ref<EntityStore> entityRef, double x, double y, double z, GrowthStage growthStage, String worldName) {
        return tameAnimal(null, animalId, ownerUuid, name, type, entityRef, x, y, z, growthStage, worldName);
    }

    /**
     * Tame an animal with explicit hytameId (for linking to existing ECS TameComponent).
     * This is the core taming method that all others delegate to.
     *
     * @param hytameId Stable ID from ECS TameComponent (null to generate new)
     * @param worldName World name for multi-world support (null uses "default")
     */
    public TamedAnimalData tameAnimal(UUID hytameId, UUID animalId, UUID ownerUuid, String name, AnimalType type, Ref<EntityStore> entityRef, double x, double y, double z, GrowthStage growthStage, String worldName) {
        if (animalId == null || ownerUuid == null || name == null) {
            return null;
        }

        // Check if already tracked by animalUuid
        if (tamedAnimalsByUuid.containsKey(animalId)) {
            log("Animal already tamed: " + animalId);
            TamedAnimalData existing = tamedAnimalsByUuid.get(animalId);
            // Update entityRef if provided and not set
            if (entityRef != null && existing.getEntityRef() == null) {
                existing.setEntityRef(entityRef);
            }
            // Update position if provided
            if (x != 0 || y != 0 || z != 0) {
                existing.setLastPosition(x, y, z);
            }
            // Update growth stage if different
            if (growthStage != null && existing.getGrowthStage() != growthStage) {
                existing.setGrowthStage(growthStage);
            }
            // Update world ID if provided and different
            if (worldName != null && !worldName.isEmpty() && !worldName.equals(existing.getWorldId())) {
                existing.setWorldId(worldName);
            }
            return existing;
        }

        // Check if already tracked by hytameId (respawned entity with new UUID)
        if (hytameId != null && tamedByHytameId.containsKey(hytameId)) {
            log("Found existing data by hytameId: " + hytameId + " (updating entity UUID)");
            TamedAnimalData existing = tamedByHytameId.get(hytameId);
            UUID oldUuid = existing.getAnimalUuid();
            if (oldUuid != null) {
                tamedAnimalsByUuid.remove(oldUuid);
            }
            existing.setAnimalUuid(animalId);
            existing.setDespawned(false);
            if (entityRef != null) {
                existing.setEntityRef(entityRef);
            }
            if (x != 0 || y != 0 || z != 0) {
                existing.setLastPosition(x, y, z);
            }
            // Update world ID if provided
            if (worldName != null && !worldName.isEmpty()) {
                existing.setWorldId(worldName);
            }
            tamedAnimalsByUuid.put(animalId, existing);
            markDirty(true);
            return existing;
        }

        // Create new tamed animal data
        TamedAnimalData data;
        if (hytameId != null) {
            data = new TamedAnimalData(hytameId, animalId, ownerUuid, name, type);
        } else {
            data = new TamedAnimalData(animalId, ownerUuid, name, type);
        }
        if (entityRef != null) {
            data.setEntityRef(entityRef);
        }
        // Set initial position if provided
        if (x != 0 || y != 0 || z != 0) {
            data.setLastPosition(x, y, z);
        }
        // Set growth stage before saving
        if (growthStage != null) {
            data.setGrowthStage(growthStage);
        }
        if (growthStage == GrowthStage.BABY) {
            data.setBirthTime(System.currentTimeMillis());
        }
        // Set world ID for multi-world support
        if (worldName != null && !worldName.isEmpty()) {
            data.setWorldId(worldName);
        }

        tamedAnimalsByUuid.put(animalId, data);
        tamedByHytameId.put(data.getHytameId(), data);
        markDirty(true); // Save immediately - user action

        log("Tamed animal: " + name + " (" + type + ", " + growthStage + ") in world=" + (worldName != null ? worldName : "default") + " at (" +
            String.format("%.1f, %.1f, %.1f", x, y, z) + ") hytameId=" + data.getHytameId() + " owned by " + ownerUuid);
        return data;
    }

    /**
     * Untame an animal (owner only).
     * @param animalId Entity UUID
     * @param playerUuid Player attempting to untame
     * @return true if untamed successfully, false if not owner or not tamed
     */
    public boolean untameAnimal(UUID animalId, UUID playerUuid) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data == null) {
            return false;
        }

        // Only owner can untame
        if (!data.isOwnedBy(playerUuid)) {
            return false;
        }

        tamedAnimalsByUuid.remove(animalId);
        if (data.getHytameId() != null) {
            tamedByHytameId.remove(data.getHytameId());
        }
        markDirty(true); // Save immediately - user action

        log("Untamed animal: " + data.getCustomName() + " by " + playerUuid);
        return true;
    }

    /**
     * Rename a tamed animal (owner only).
     * @param animalId Entity UUID
     * @param playerUuid Player attempting to rename
     * @param newName New name for the animal
     * @return true if renamed successfully
     */
    public boolean renameAnimal(UUID animalId, UUID playerUuid, String newName) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data == null) {
            return false;
        }

        // Only owner can rename
        if (!data.isOwnedBy(playerUuid)) {
            return false;
        }

        String oldName = data.getCustomName();
        data.setCustomName(newName);
        markDirty(true); // Save immediately - user action

        log("Renamed animal: " + oldName + " -> " + newName);
        return true;
    }

    // ===========================================
    // OWNERSHIP & INTERACTION
    // ===========================================

    /**
     * Check if a player can interact with an animal.
     * Returns true if: not tamed, or player is owner, or allowInteraction is true.
     */
    public boolean canPlayerInteract(UUID animalId, UUID playerUuid) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data == null) {
            return true; // Not tamed, anyone can interact
        }
        return data.canInteract(playerUuid);
    }

    /**
     * Get the owner of a tamed animal.
     * @return Owner UUID or null if not tamed
     */
    public UUID getOwner(UUID animalId) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        return data != null ? data.getOwnerUuid() : null;
    }

    /**
     * Check if a player owns the animal.
     */
    public boolean isOwner(UUID animalId, UUID playerUuid) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        return data != null && data.isOwnedBy(playerUuid);
    }

    /**
     * Toggle allow interaction setting for a player's animals.
     * @param ownerUuid The owner to toggle for
     * @return The new state (true = allow, false = deny)
     */
    public boolean toggleAllowInteraction(UUID ownerUuid) {
        // Find first animal owned by this player to determine current state
        boolean currentState = true;
        for (TamedAnimalData data : tamedAnimalsByUuid.values()) {
            if (data.isOwnedBy(ownerUuid)) {
                currentState = data.isAllowInteraction();
                break;
            }
        }

        // Toggle all animals owned by this player
        boolean newState = !currentState;
        for (TamedAnimalData data : tamedAnimalsByUuid.values()) {
            if (data.isOwnedBy(ownerUuid)) {
                data.setAllowInteraction(newState);
            }
        }
        markDirty();

        log("Player " + ownerUuid + " set allowInteraction to " + newState);
        return newState;
    }

    /**
     * Set allow interaction for a specific animal.
     */
    public void setAllowInteraction(UUID animalId, boolean allow) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null) {
            data.setAllowInteraction(allow);
            markDirty();
        }
    }

    // ===========================================
    // ENTITY LIFECYCLE
    // ===========================================

    /**
     * Called when a tamed animal despawns.
     * Marks it for respawn and saves position.
     */
    public void onTamedAnimalDespawn(UUID animalId, double x, double y, double z) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null) {
            data.setLastPosition(x, y, z);
            data.setDespawned(true);
            data.setEntityRef(null);
            markDirty();

            log("Tamed animal despawned: " + data.getCustomName() + " at (" +
                String.format("%.1f, %.1f, %.1f", x, y, z) + ")");
        }
    }

    /**
     * Handle when a tamed animal dies. Marks as dead (won't respawn, but kept for record).
     */
    public void onTamedAnimalDeath(UUID animalId) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null) {
            data.setDead(true);
            data.setEntityRef(null);
            markDirty(true); // Save immediately - death is critical data
            log("Tamed animal died: " + data.getCustomName() + " (marked as dead, not removed)");
        }
    }

    /**
     * Update position of a tamed animal (called periodically).
     */
    public void updatePosition(UUID animalId, double x, double y, double z) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null && !data.isDespawned()) {
            data.setLastPosition(x, y, z);
            // Don't mark dirty for every position update - save on despawn
        }
    }

    /**
     * Update entity reference for a tamed animal.
     */
    public void updateEntityRef(UUID animalId, Ref<EntityStore> entityRef) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null) {
            data.setEntityRef(entityRef);
        }
    }

    /**
     * Get despawned tamed animals within range of a position.
     * @param x Center X
     * @param z Center Z
     * @param radius Search radius
     * @return List of despawned tamed animals within range
     */
    public List<TamedAnimalData> getDespawnedAnimalsInRegion(double x, double z, double radius) {
        double radiusSq = radius * radius;
        return tamedAnimalsByUuid.values().stream()
                .filter(TamedAnimalData::isDespawned)
                .filter(data -> !data.isDead()) // Dead animals don't respawn
                .filter(data -> {
                    double dx = data.getLastX() - x;
                    double dz = data.getLastZ() - z;
                    return (dx * dx + dz * dz) <= radiusSq;
                })
                .collect(Collectors.toList());
    }

    /**
     * Mark an animal as respawned with new entity reference and UUID.
     * @param oldUuid The old UUID (from save data)
     * @param newUuid The new UUID after respawn
     * @param entityRef The new entity reference
     */
    public void markRespawned(UUID oldUuid, UUID newUuid, Ref<EntityStore> entityRef) {
        TamedAnimalData data = tamedAnimalsByUuid.remove(oldUuid);
        if (data != null) {
            data.setAnimalUuid(newUuid);
            data.setDespawned(false);
            data.setEntityRef(entityRef);
            tamedAnimalsByUuid.put(newUuid, data);
            markDirty();

            log("Respawned tamed animal: " + data.getCustomName() + " (new UUID: " + newUuid + ")");
        }
    }

    /**
     * Copy breeding state from BreedingData to TamedAnimalData.
     */
    public void syncFromBreedingData(UUID animalId, BreedingData breedingData) {
        TamedAnimalData data = tamedAnimalsByUuid.get(animalId);
        if (data != null && breedingData != null) {
            data.copyFromBreedingData(breedingData);
        }
    }

    // ===========================================
    // STATS & QUERIES
    // ===========================================

    /**
     * Get total count of tamed animals.
     */
    public int getTamedCount() {
        return tamedAnimalsByUuid.size();
    }

    /**
     * Get count of despawned animals awaiting respawn.
     */
    public int getDespawnedCount() {
        return (int) tamedAnimalsByUuid.values().stream()
                .filter(TamedAnimalData::isDespawned)
                .count();
    }

    /**
     * Get count of animals owned by a specific player.
     */
    public int getPlayerTamedCount(UUID playerUuid) {
        return (int) tamedAnimalsByUuid.values().stream()
                .filter(data -> data.isOwnedBy(playerUuid))
                .count();
    }

    /**
     * Get list of animals owned by a player.
     */
    public List<TamedAnimalData> getPlayerAnimals(UUID playerUuid) {
        return tamedAnimalsByUuid.values().stream()
                .filter(data -> data.isOwnedBy(playerUuid))
                .collect(Collectors.toList());
    }

    /**
     * Find tamed animal by name (case-insensitive).
     * @param ownerUuid Owner to search for (or null for all)
     * @param name Name to search for
     * @return The tamed animal data or null
     */
    public TamedAnimalData findByName(UUID ownerUuid, String name) {
        return tamedAnimalsByUuid.values().stream()
                .filter(data -> ownerUuid == null || data.isOwnedBy(ownerUuid))
                .filter(data -> data.getCustomName() != null &&
                        data.getCustomName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Clean up stale data (animals despawned for too long).
     * @param maxDespawnedAgeMillis Maximum age for despawned animals
     * @return Number of entries cleaned up
     */
    public int cleanupStaleData(long maxDespawnedAgeMillis) {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, TamedAnimalData> entry : tamedAnimalsByUuid.entrySet()) {
            TamedAnimalData data = entry.getValue();
            if (data.isDespawned()) {
                // Check if despawned for too long
                // Note: We don't track despawn time, so this is based on tamed time
                // This could be enhanced to track actual despawn time
            }
        }

        for (UUID uuid : toRemove) {
            TamedAnimalData removed = tamedAnimalsByUuid.remove(uuid);
            if (removed != null && removed.getHytameId() != null) {
                tamedByHytameId.remove(removed.getHytameId());
            }
        }

        if (!toRemove.isEmpty()) {
            markDirty();
            log("Cleaned up " + toRemove.size() + " stale tamed animal entries");
        }

        return toRemove.size();
    }

    // ===========================================
    // ECS SYNC METHODS
    // ===========================================

    /**
     * Find tamed animal data by hytameId.
     * Used when syncing ECS TameComponent with JSON data.
     */
    public TamedAnimalData findByHytameId(UUID hytameId) {
        return hytameId != null ? tamedByHytameId.get(hytameId) : null;
    }

    /**
     * Find a captured animal by type (for restore after server restart).
     * Returns the first animal that is marked as captured and matches the type.
     */
    public TamedAnimalData findCapturedByType(AnimalType type) {
        if (type == null) return null;

        for (TamedAnimalData data : tamedByHytameId.values()) {
            if (data.isCaptured() && data.getAnimalType() == type) {
                log("Found captured animal: hytameId=" + data.getHytameId() + " name=" + data.getCustomName());
                return data;
            }
        }
        return null;
    }

    /**
     * Get all tamed animals indexed by hytameId.
     * Useful for bulk ECS sync operations.
     */
    public Map<UUID, TamedAnimalData> getTamedByHytameId() {
        return new HashMap<>(tamedByHytameId);
    }

    /**
     * Represents the result of syncing an entity with the JSON store.
     */
    public enum SyncResult {
        /** Entity and JSON are now linked */
        SYNCED,
        /** Entity has TameComponent but no JSON entry - created new JSON entry */
        CREATED,
        /** JSON entry exists but entity doesn't - marked for respawn */
        NEEDS_RESPAWN,
        /** JSON says dead but entity exists - should remove entity */
        SHOULD_REMOVE,
        /** No action needed (untamed or already synced) */
        NO_ACTION
    }

    /**
     * Sync a single entity's TameComponent with JSON store.
     * Call this when an entity is loaded or discovered.
     *
     * @param entityUuid Current entity UUID
     * @param hytameId HytameId from TameComponent (null if not tamed)
     * @param isTamed Whether TameComponent says entity is tamed
     * @param tamerUuid Owner UUID from TameComponent
     * @param tamerName Owner name from TameComponent
     * @param entityRef Entity reference for position tracking
     * @param x Entity X position
     * @param y Entity Y position
     * @param z Entity Z position
     * @return SyncResult indicating what action was taken
     */
    public SyncResult syncEntity(UUID entityUuid, UUID hytameId, boolean isTamed,
                                  UUID tamerUuid, String tamerName, Ref<EntityStore> entityRef,
                                  double x, double y, double z) {
        if (!isTamed || hytameId == null) {
            return SyncResult.NO_ACTION;
        }

        // Check if we have JSON data for this hytameId
        TamedAnimalData jsonData = findByHytameId(hytameId);

        if (jsonData != null) {
            // JSON entry exists - check state
            if (jsonData.isDead()) {
                // JSON says dead but entity exists - shouldn't happen normally
                log("WARN: Entity with hytameId=" + hytameId + " exists but JSON says dead");
                return SyncResult.SHOULD_REMOVE;
            }

            // Update JSON with current entity state
            UUID oldUuid = jsonData.getAnimalUuid();
            if (!entityUuid.equals(oldUuid)) {
                // Entity UUID changed (respawn case)
                if (oldUuid != null) {
                    tamedAnimalsByUuid.remove(oldUuid);
                }
                jsonData.setAnimalUuid(entityUuid);
                tamedAnimalsByUuid.put(entityUuid, jsonData);
            }

            jsonData.setEntityRef(entityRef);
            jsonData.setDespawned(false);
            jsonData.setLastPosition(x, y, z);

            // Notify smart loading detection
            onEntityLinked(hytameId);

            log("Synced entity " + entityUuid + " with hytameId=" + hytameId +
                " (name: " + jsonData.getCustomName() + ")");
            return SyncResult.SYNCED;
        }

        // No JSON entry - create one (entity was tamed but we lost the JSON data)
        // This is a recovery scenario or first-time setup
        TamedAnimalData newData = tameAnimal(hytameId, entityUuid, tamerUuid,
            "Unknown", null, entityRef, x, y, z, GrowthStage.ADULT, null);
        if (newData != null) {
            log("Created JSON entry for existing tamed entity hytameId=" + hytameId);
            return SyncResult.CREATED;
        }

        return SyncResult.NO_ACTION;
    }

    /**
     * Get animals that are in JSON but have no entity reference.
     * These may need to be respawned.
     *
     * @return List of tamed animals without entity references that aren't dead
     */
    public List<TamedAnimalData> getAnimalsNeedingRespawn() {
        return tamedAnimalsByUuid.values().stream()
            .filter(data -> data.getEntityRef() == null)
            .filter(data -> !data.isDead())
            .filter(data -> data.isDespawned())
            .collect(Collectors.toList());
    }

    /**
     * Cleanup orphaned flags after loading is complete.
     * Clears isCaptured flag for animals that:
     * - Have no entity reference
     * - Are not despawned (meaning we expected them to exist but they don't)
     *
     * Call this after the grace period expires and entity scan completes.
     *
     * @return Number of orphaned flags cleared
     */
    public int cleanupOrphanedFlags() {
        int cleaned = 0;
        for (TamedAnimalData data : tamedByHytameId.values()) {
            if (data.isCaptured() && data.getEntityRef() == null && !data.isDespawned()) {
                log("Clearing orphaned isCaptured flag for: " + data.getHytameId() +
                    " (" + data.getCustomName() + ")");
                data.setCaptured(false);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            markDirty(true);
            log("Cleaned up " + cleaned + " orphaned captured flags");
        }
        return cleaned;
    }

    /**
     * Clear entity references for all animals.
     * Call this when world is unloading or on shutdown.
     */
    public void clearAllEntityRefs() {
        for (TamedAnimalData data : tamedAnimalsByUuid.values()) {
            if (data.getEntityRef() != null) {
                data.setEntityRef(null);
                data.setDespawned(true);
            }
        }
        markDirty();
        log("Cleared all entity references (world unload/shutdown)");
    }

}
