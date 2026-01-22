package com.animaltaming.system;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal context interface for systems.
 * Provides focused access to world state without being monolithic.
 *
 * This is a placeholder that will be adapted to the actual Hytale API.
 */
public interface SystemContext {

    // ==================== TIME ====================

    /**
     * Get the current game tick.
     */
    long getCurrentTick();

    /**
     * Get the server's tick rate (typically 30).
     */
    int getTickRate();

    // ==================== ENTITY EXISTENCE ====================

    /**
     * Check if an entity exists.
     */
    boolean entityExists(long entityId);

    // ==================== POSITION ====================

    /**
     * Get an entity's X position.
     */
    double getEntityX(long entityId);

    /**
     * Get an entity's Y position.
     */
    double getEntityY(long entityId);

    /**
     * Get an entity's Z position.
     */
    double getEntityZ(long entityId);

    /**
     * Get the distance between two entities.
     */
    default double getDistance(long entityA, long entityB) {
        double dx = getEntityX(entityA) - getEntityX(entityB);
        double dy = getEntityY(entityA) - getEntityY(entityB);
        double dz = getEntityZ(entityA) - getEntityZ(entityB);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ==================== PLAYER QUERIES ====================

    /**
     * Get all online players.
     * Used by PlayerLookupService for cache refresh.
     */
    List<PlayerInfo> getAllPlayers();

    /**
     * Get players within a radius.
     */
    List<Long> getPlayersInRadius(double x, double y, double z, double radius);

    /**
     * Check if a player is sneaking.
     * TODO: [HYTALE-API] Implement when player input state is available.
     */
    default boolean isPlayerSneaking(long playerId) {
        return false; // Placeholder
    }

    /**
     * Get the item ID held by a player.
     * TODO: [HYTALE-API] Implement when inventory API is available.
     */
    default Optional<String> getHeldItemId(long playerId) {
        return Optional.empty(); // Placeholder
    }

    /**
     * Consume one item from a player's hand.
     * TODO: [HYTALE-API] Implement when inventory API is available.
     */
    default boolean consumeHeldItem(long playerId) {
        return false; // Placeholder
    }

    // ==================== MOUNT QUERIES ====================

    /**
     * Get entities riding an entity.
     * TODO: [HYTALE-API] Implement when mount API is available.
     */
    default List<Long> getRiders(long entityId) {
        return List.of(); // Placeholder
    }

    // ==================== TAMEABLE ANIMALS ====================

    /**
     * Get all tameable animals in the world.
     */
    List<TameableAnimalInfo> getTameableAnimals();

    /**
     * Get entity ID for a tamed animal by its UUID.
     */
    Optional<Long> getEntityIdForAnimal(UUID animalId);

    // ==================== INTERACTIONS ====================

    /**
     * Get pending player interactions this tick.
     */
    List<InteractionEvent> getPendingInteractions();

    // ==================== ACTIONS ====================

    /**
     * Teleport an entity.
     */
    void teleport(long entityId, double x, double y, double z);

    /**
     * Move an entity toward a target position at a given speed.
     * Uses pathfinding if available, direct movement otherwise.
     *
     * @param entityId the entity to move
     * @param targetX target X coordinate
     * @param targetY target Y coordinate
     * @param targetZ target Z coordinate
     * @param speed movement speed (blocks per second)
     */
    default void moveEntityToward(long entityId, double targetX, double targetY, double targetZ, double speed) {
        // Default no-op - implement in platform-specific context
    }

    /**
     * Spawn a particle effect.
     */
    void spawnParticle(double x, double y, double z, String particleType);

    /**
     * Play a sound effect.
     */
    void playSound(double x, double y, double z, String soundType);

    /**
     * Send a message to a player.
     */
    void sendMessage(long playerId, String message);

    // ==================== DATA TYPES ====================

    /**
     * Information about an online player.
     */
    record PlayerInfo(long entityId, UUID uuid, String name) {}

    /**
     * Information about a tameable animal.
     */
    record TameableAnimalInfo(long entityId, UUID animalId, String speciesId) {}

    /**
     * A player interaction event.
     */
    record InteractionEvent(long playerEntityId, long targetEntityId, String type) {}
}
