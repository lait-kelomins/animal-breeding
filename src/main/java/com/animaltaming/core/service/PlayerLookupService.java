package com.animaltaming.core.service;

import com.animaltaming.system.SystemContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for O(1) player lookups.
 * The cache is refreshed once per tick to avoid O(n) searches.
 *
 * Usage pattern:
 * 1. Call refreshCache() once at the start of each tick
 * 2. Use getEntityId()/getPlayerUUID() for O(1) lookups throughout the tick
 */
public interface PlayerLookupService {

    /**
     * Refresh the player cache from the current world state.
     * Must be called once at the start of each tick.
     *
     * @param context the system context providing player data
     */
    void refreshCache(SystemContext context);

    /**
     * Get the entity ID for a player UUID.
     * O(1) lookup from cache.
     *
     * @param playerUUID the player's UUID
     * @return the entity ID, or empty if player not online
     */
    Optional<Long> getEntityId(UUID playerUUID);

    /**
     * Get the player UUID for an entity ID.
     * O(1) lookup from cache.
     *
     * @param entityId the entity ID
     * @return the player UUID, or empty if not a player
     */
    Optional<UUID> getPlayerUUID(long entityId);

    /**
     * Check if a player is currently online.
     *
     * @param playerUUID the player's UUID
     * @return true if online
     */
    boolean isOnline(UUID playerUUID);

    /**
     * Check if an entity is a player.
     *
     * @param entityId the entity ID
     * @return true if this entity is a player
     */
    boolean isPlayer(long entityId);

    /**
     * Get the number of online players.
     *
     * @return player count
     */
    int getOnlineCount();
}
