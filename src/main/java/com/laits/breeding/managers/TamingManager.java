package com.laits.breeding.managers;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages taming state for animals.
 * Handles tamed animal tracking and ownership.
 */
public class TamingManager {

    // Tamed animals by their UUID
    private final Map<UUID, TamedAnimalData> tamedAnimals = new ConcurrentHashMap<>();

    // Reference to persistence manager for dirty marking
    private PersistenceManager persistenceManager;

    // Logger
    private Consumer<String> logger;

    public TamingManager() {
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
        tamedAnimals.clear();
        for (TamedAnimalData data : savedAnimals) {
            if (data != null && data.getAnimalUuid() != null) {
                // Reset despawned flag on load - the world save has the actual entities
                // If they're truly gone, EntityRemoveEvent will set isDespawned = true
                // data.setDespawned(false);
                data.setEntityRef(null); // Will be reattached by animal scan
                tamedAnimals.put(data.getAnimalUuid(), data);
            }
        }
        log("Loaded " + tamedAnimals.size() + " tamed animals from persistence (all marked as not despawned)");
    }

    /**
     * Get all tamed animal data for persistence.
     */
    public Collection<TamedAnimalData> getAllTamedAnimals() {
        return new ArrayList<>(tamedAnimals.values());
    }

    // ===========================================
    // TAMING METHODS
    // ===========================================

    /**
     * Check if an animal is tamed.
     */
    public boolean isTamed(UUID animalId) {
        return animalId != null && tamedAnimals.containsKey(animalId);
    }

    /**
     * Get tamed animal data by UUID.
     */
    public TamedAnimalData getTamedData(UUID animalId) {
        return animalId != null ? tamedAnimals.get(animalId) : null;
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
        if (animalId == null || ownerUuid == null || name == null) {
            return null;
        }

        // Check if already tamed
        if (tamedAnimals.containsKey(animalId)) {
            log("Animal already tamed: " + animalId);
            TamedAnimalData existing = tamedAnimals.get(animalId);
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
            return existing;
        }

        TamedAnimalData data = new TamedAnimalData(animalId, ownerUuid, name, type);
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
        tamedAnimals.put(animalId, data);
        markDirty(true); // Save immediately - user action

        log("Tamed animal: " + name + " (" + type + ", " + growthStage + ") at (" +
            String.format("%.1f, %.1f, %.1f", x, y, z) + ") owned by " + ownerUuid);
        return data;
    }

    /**
     * Untame an animal (owner only).
     * @param animalId Entity UUID
     * @param playerUuid Player attempting to untame
     * @return true if untamed successfully, false if not owner or not tamed
     */
    public boolean untameAnimal(UUID animalId, UUID playerUuid) {
        TamedAnimalData data = tamedAnimals.get(animalId);
        if (data == null) {
            return false;
        }

        // Only owner can untame
        if (!data.isOwnedBy(playerUuid)) {
            return false;
        }

        tamedAnimals.remove(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
        return data != null ? data.getOwnerUuid() : null;
    }

    /**
     * Check if a player owns the animal.
     */
    public boolean isOwner(UUID animalId, UUID playerUuid) {
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        for (TamedAnimalData data : tamedAnimals.values()) {
            if (data.isOwnedBy(ownerUuid)) {
                currentState = data.isAllowInteraction();
                break;
            }
        }

        // Toggle all animals owned by this player
        boolean newState = !currentState;
        for (TamedAnimalData data : tamedAnimals.values()) {
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
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        TamedAnimalData data = tamedAnimals.get(animalId);
        if (data != null && !data.isDespawned()) {
            data.setLastPosition(x, y, z);
            // Don't mark dirty for every position update - save on despawn
        }
    }

    /**
     * Update entity reference for a tamed animal.
     */
    public void updateEntityRef(UUID animalId, Ref<EntityStore> entityRef) {
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        return tamedAnimals.values().stream()
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
        TamedAnimalData data = tamedAnimals.remove(oldUuid);
        if (data != null) {
            data.setAnimalUuid(newUuid);
            data.setDespawned(false);
            data.setEntityRef(entityRef);
            tamedAnimals.put(newUuid, data);
            markDirty();

            log("Respawned tamed animal: " + data.getCustomName() + " (new UUID: " + newUuid + ")");
        }
    }

    /**
     * Copy breeding state from BreedingData to TamedAnimalData.
     */
    public void syncFromBreedingData(UUID animalId, BreedingData breedingData) {
        TamedAnimalData data = tamedAnimals.get(animalId);
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
        return tamedAnimals.size();
    }

    /**
     * Get count of despawned animals awaiting respawn.
     */
    public int getDespawnedCount() {
        return (int) tamedAnimals.values().stream()
                .filter(TamedAnimalData::isDespawned)
                .count();
    }

    /**
     * Get count of animals owned by a specific player.
     */
    public int getPlayerTamedCount(UUID playerUuid) {
        return (int) tamedAnimals.values().stream()
                .filter(data -> data.isOwnedBy(playerUuid))
                .count();
    }

    /**
     * Get list of animals owned by a player.
     */
    public List<TamedAnimalData> getPlayerAnimals(UUID playerUuid) {
        return tamedAnimals.values().stream()
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
        return tamedAnimals.values().stream()
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

        for (Map.Entry<UUID, TamedAnimalData> entry : tamedAnimals.entrySet()) {
            TamedAnimalData data = entry.getValue();
            if (data.isDespawned()) {
                // Check if despawned for too long
                // Note: We don't track despawn time, so this is based on tamed time
                // This could be enhanced to track actual despawn time
            }
        }

        for (UUID uuid : toRemove) {
            tamedAnimals.remove(uuid);
        }

        if (!toRemove.isEmpty()) {
            markDirty();
            log("Cleaned up " + toRemove.size() + " stale tamed animal entries");
        }

        return toRemove.size();
    }

}
