package com.animaltaming.core.service;

import com.animaltaming.system.SystemContext;

import java.util.*;

/**
 * Implementation of PlayerLookupService using HashMap caches.
 * Provides O(1) lookup after cache refresh.
 *
 * The cache is cleared and rebuilt each tick from SystemContext.getAllPlayers().
 * This ensures accurate data while avoiding O(n) searches during the tick.
 */
public class CachedPlayerLookupService implements PlayerLookupService {

    private Map<UUID, Long> uuidToEntityId = new HashMap<>();
    private Map<Long, UUID> entityIdToUuid = new HashMap<>();

    @Override
    public void refreshCache(SystemContext context) {
        // Clear existing cache
        uuidToEntityId = new HashMap<>();
        entityIdToUuid = new HashMap<>();

        // Rebuild from current players
        for (SystemContext.PlayerInfo player : context.getAllPlayers()) {
            uuidToEntityId.put(player.uuid(), player.entityId());
            entityIdToUuid.put(player.entityId(), player.uuid());
        }
    }

    @Override
    public Optional<Long> getEntityId(UUID playerUUID) {
        if (playerUUID == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(uuidToEntityId.get(playerUUID));
    }

    @Override
    public Optional<UUID> getPlayerUUID(long entityId) {
        return Optional.ofNullable(entityIdToUuid.get(entityId));
    }

    @Override
    public boolean isOnline(UUID playerUUID) {
        return playerUUID != null && uuidToEntityId.containsKey(playerUUID);
    }

    @Override
    public boolean isPlayer(long entityId) {
        return entityIdToUuid.containsKey(entityId);
    }

    @Override
    public int getOnlineCount() {
        return uuidToEntityId.size();
    }
}
