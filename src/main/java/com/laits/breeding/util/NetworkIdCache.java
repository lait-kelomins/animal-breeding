package com.laits.breeding.util;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches NetworkId -> Ref mapping for O(1) lookup by network ID.
 * This avoids brute-force iteration when looking up entities from packet entityId.
 */
public class NetworkIdCache extends RefSystem<EntityStore> {

    // Cache: networkId -> entity Ref
    private static final Map<Integer, Ref<EntityStore>> networkIdToRef = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return EcsReflectionUtil.NETWORK_ID_TYPE;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            NetworkId networkIdComp = store.getComponent(ref, EcsReflectionUtil.NETWORK_ID_TYPE);
            if (networkIdComp != null) {
                int networkId = networkIdComp.getId();
                networkIdToRef.put(networkId, ref);
            }
        } catch (Exception e) {
            // Silent
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        try {
            NetworkId networkIdComp = store.getComponent(ref, EcsReflectionUtil.NETWORK_ID_TYPE);
            if (networkIdComp != null) {
                int networkId = networkIdComp.getId();
                networkIdToRef.remove(networkId);
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Look up an entity Ref by its network ID.
     * Returns null if not found.
     */
    public static Ref<EntityStore> getByNetworkId(int networkId) {
        Ref<EntityStore> ref = networkIdToRef.get(networkId);
        // Validate ref is still valid
        if (ref != null && ref.isValid()) {
            return ref;
        }
        // Clean up stale entry
        if (ref != null) {
            networkIdToRef.remove(networkId);
        }
        return null;
    }

    /**
     * Get cache size (for debugging).
     */
    public static int getCacheSize() {
        return networkIdToRef.size();
    }

    /**
     * Clear the cache.
     */
    public static void clear() {
        networkIdToRef.clear();
    }

    private void log(String message) {
        if (LaitsBreedingPlugin.isVerboseLogging()) {
            System.out.println("[NetworkIdCache] " + message);
        }
    }
}
