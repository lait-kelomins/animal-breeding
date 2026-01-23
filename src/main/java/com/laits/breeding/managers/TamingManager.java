package com.laits.breeding.managers;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages taming state for animals.
 * Handles pending name tags, tamed animal tracking, and ownership.
 */
public class TamingManager {

    // Tamed animals by their UUID
    private final Map<UUID, TamedAnimalData> tamedAnimals = new ConcurrentHashMap<>();

    // Pending name tags: playerUUID -> name they want to apply
    private final Map<UUID, String> pendingNameTags = new ConcurrentHashMap<>();

    // Pending name tags by player name (for command thread safety)
    private final Map<String, String> pendingNameTagsByName = new ConcurrentHashMap<>();

    // Pending untame requests: playerUUID -> true (wants to untame next click)
    private final Map<UUID, Boolean> pendingUntame = new ConcurrentHashMap<>();

    // Pending untame by player name (for command thread safety)
    private final Map<String, Boolean> pendingUntameByName = new ConcurrentHashMap<>();

    // Timestamps for pending entries (for timeout-based cleanup)
    private final Map<Object, Long> pendingTimestamps = new ConcurrentHashMap<>();

    // Timeout for pending entries (5 minutes)
    private static final long PENDING_TIMEOUT_MS = 5 * 60 * 1000;

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

    private void markDirty() {
        if (persistenceManager != null) {
            persistenceManager.markDirty();
        }
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
                tamedAnimals.put(data.getAnimalUuid(), data);
            }
        }
        log("Loaded " + tamedAnimals.size() + " tamed animals from persistence");
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
     * @param type Animal type
     * @return The created TamedAnimalData
     */
    public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type) {
        if (animalId == null || ownerUuid == null || name == null || type == null) {
            return null;
        }

        // Check if already tamed
        if (tamedAnimals.containsKey(animalId)) {
            log("Animal already tamed: " + animalId);
            return tamedAnimals.get(animalId);
        }

        TamedAnimalData data = new TamedAnimalData(animalId, ownerUuid, name, type);
        tamedAnimals.put(animalId, data);
        markDirty();

        log("Tamed animal: " + name + " (" + type + ") owned by " + ownerUuid);
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
        markDirty();

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
        markDirty();

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
    // PENDING NAME TAG METHODS
    // ===========================================

    /**
     * Set a pending name tag for a player.
     * The name will be applied when they right-click an animal.
     */
    public void setPendingNameTag(UUID playerUuid, String name) {
        if (playerUuid != null && name != null) {
            pendingNameTags.put(playerUuid, name);
            pendingTimestamps.put(playerUuid, System.currentTimeMillis());
            log("Set pending name tag for " + playerUuid + ": " + name);
        }
    }

    /**
     * Get the pending name tag for a player without consuming it.
     */
    public String getPendingNameTag(UUID playerUuid) {
        return playerUuid != null ? pendingNameTags.get(playerUuid) : null;
    }

    /**
     * Get and remove the pending name tag for a player.
     */
    public String consumePendingNameTag(UUID playerUuid) {
        return playerUuid != null ? pendingNameTags.remove(playerUuid) : null;
    }

    /**
     * Check if a player has a pending name tag.
     */
    public boolean hasPendingNameTag(UUID playerUuid) {
        return playerUuid != null && pendingNameTags.containsKey(playerUuid);
    }

    /**
     * Clear the pending name tag for a player.
     */
    public void clearPendingNameTag(UUID playerUuid) {
        if (playerUuid != null) {
            pendingNameTags.remove(playerUuid);
        }
    }

    // --- Name-based methods (for command thread safety) ---

    /**
     * Set a pending name tag for a player by their display name.
     * Use this from commands where UUID is not accessible.
     */
    public void setPendingNameTagByName(String playerName, String name) {
        if (playerName != null && name != null) {
            String key = playerName.toLowerCase();
            pendingNameTagsByName.put(key, name);
            pendingTimestamps.put("name:" + key, System.currentTimeMillis());
            log("Set pending name tag for " + playerName + ": " + name);
        }
    }

    /**
     * Get the pending name tag for a player by display name.
     */
    public String getPendingNameTagByName(String playerName) {
        return playerName != null ? pendingNameTagsByName.get(playerName.toLowerCase()) : null;
    }

    /**
     * Get and remove the pending name tag for a player by display name.
     */
    public String consumePendingNameTagByName(String playerName) {
        return playerName != null ? pendingNameTagsByName.remove(playerName.toLowerCase()) : null;
    }

    /**
     * Check if a player has a pending name tag by display name.
     */
    public boolean hasPendingNameTagByName(String playerName) {
        return playerName != null && pendingNameTagsByName.containsKey(playerName.toLowerCase());
    }

    // ===========================================
    // PENDING UNTAME METHODS
    // ===========================================

    /**
     * Set pending untame for a player.
     */
    public void setPendingUntame(UUID playerUuid) {
        if (playerUuid != null) {
            pendingUntame.put(playerUuid, true);
            pendingTimestamps.put("untame:" + playerUuid, System.currentTimeMillis());
        }
    }

    /**
     * Check if a player has pending untame.
     */
    public boolean hasPendingUntame(UUID playerUuid) {
        return playerUuid != null && pendingUntame.containsKey(playerUuid);
    }

    /**
     * Consume pending untame for a player.
     */
    public boolean consumePendingUntame(UUID playerUuid) {
        return playerUuid != null && pendingUntame.remove(playerUuid) != null;
    }

    // --- Name-based untame methods (for command thread safety) ---

    /**
     * Set pending untame for a player by display name.
     */
    public void setPendingUntameByName(String playerName) {
        if (playerName != null) {
            String key = playerName.toLowerCase();
            pendingUntameByName.put(key, true);
            pendingTimestamps.put("untameName:" + key, System.currentTimeMillis());
        }
    }

    /**
     * Check if a player has pending untame by display name.
     */
    public boolean hasPendingUntameByName(String playerName) {
        return playerName != null && pendingUntameByName.containsKey(playerName.toLowerCase());
    }

    /**
     * Consume pending untame for a player by display name.
     */
    public boolean consumePendingUntameByName(String playerName) {
        return playerName != null && pendingUntameByName.remove(playerName.toLowerCase()) != null;
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
     * Handle when a tamed animal dies. Removes from tracking (won't respawn).
     */
    public void onTamedAnimalDeath(UUID animalId) {
        TamedAnimalData data = tamedAnimals.remove(animalId);
        if (data != null) {
            markDirty();
            log("Tamed animal died and was unregistered: " + data.getCustomName());
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
    public void updateEntityRef(UUID animalId, Object entityRef) {
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
    public void markRespawned(UUID oldUuid, UUID newUuid, Object entityRef) {
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

    /**
     * Clean up expired pending entries (name tags and untame requests).
     * Entries expire after PENDING_TIMEOUT_MS (5 minutes).
     * @return Number of expired entries removed
     */
    public int cleanupExpiredPending() {
        long now = System.currentTimeMillis();
        int removed = 0;

        // Clean up pendingNameTags
        Iterator<Map.Entry<UUID, String>> nameTagIt = pendingNameTags.entrySet().iterator();
        while (nameTagIt.hasNext()) {
            UUID key = nameTagIt.next().getKey();
            Long timestamp = pendingTimestamps.get(key);
            if (timestamp != null && (now - timestamp) > PENDING_TIMEOUT_MS) {
                nameTagIt.remove();
                pendingTimestamps.remove(key);
                removed++;
            }
        }

        // Clean up pendingNameTagsByName
        Iterator<Map.Entry<String, String>> nameTagByNameIt = pendingNameTagsByName.entrySet().iterator();
        while (nameTagByNameIt.hasNext()) {
            String key = nameTagByNameIt.next().getKey();
            Long timestamp = pendingTimestamps.get("name:" + key);
            if (timestamp != null && (now - timestamp) > PENDING_TIMEOUT_MS) {
                nameTagByNameIt.remove();
                pendingTimestamps.remove("name:" + key);
                removed++;
            }
        }

        // Clean up pendingUntame
        Iterator<Map.Entry<UUID, Boolean>> untameIt = pendingUntame.entrySet().iterator();
        while (untameIt.hasNext()) {
            UUID key = untameIt.next().getKey();
            Long timestamp = pendingTimestamps.get("untame:" + key);
            if (timestamp != null && (now - timestamp) > PENDING_TIMEOUT_MS) {
                untameIt.remove();
                pendingTimestamps.remove("untame:" + key);
                removed++;
            }
        }

        // Clean up pendingUntameByName
        Iterator<Map.Entry<String, Boolean>> untameByNameIt = pendingUntameByName.entrySet().iterator();
        while (untameByNameIt.hasNext()) {
            String key = untameByNameIt.next().getKey();
            Long timestamp = pendingTimestamps.get("untameName:" + key);
            if (timestamp != null && (now - timestamp) > PENDING_TIMEOUT_MS) {
                untameByNameIt.remove();
                pendingTimestamps.remove("untameName:" + key);
                removed++;
            }
        }

        // Clean up orphaned timestamps
        pendingTimestamps.entrySet().removeIf(e -> (now - e.getValue()) > PENDING_TIMEOUT_MS);

        if (removed > 0) {
            log("Cleaned up " + removed + " expired pending taming entries");
        }

        return removed;
    }

    /**
     * Get count of pending entries (for debugging).
     */
    public int getPendingCount() {
        return pendingNameTags.size() + pendingNameTagsByName.size() +
               pendingUntame.size() + pendingUntameByName.size();
    }
}
