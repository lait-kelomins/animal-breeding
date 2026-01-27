package com.laits.breeding.listeners;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.NPCSystems.OnDeathSystem;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.util.EcsReflectionUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ECS system that detects when tamed animals die.
 * Marks them as dead (not despawned) so they don't respawn.
 */
public class DetectTamedDeath extends OnDeathSystem {

    // Track last detected death UUIDs for debugging (max 10)
    private static final List<UUID> lastDetectedDeaths = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_TRACKED = 10;

    /**
     * Get the last detected death UUIDs (most recent first).
     */
    public static List<UUID> getLastDetectedDeaths() {
        synchronized (lastDetectedDeaths) {
            return new ArrayList<>(lastDetectedDeaths);
        }
    }

    /**
     * Clear the tracked death UUIDs.
     */
    public static void clearTrackedDeaths() {
        lastDetectedDeaths.clear();
    }

    private static void trackDetectedDeath(UUID uuid) {
        synchronized (lastDetectedDeaths) {
            // Add at beginning (most recent first)
            lastDetectedDeaths.add(0, uuid);
            // Keep only max entries
            while (lastDetectedDeaths.size() > MAX_TRACKED) {
                lastDetectedDeaths.remove(lastDetectedDeaths.size() - 1);
            }
        }
    }

    // Match all entities with DeathComponent (no additional requirements)
    @Nonnull
    private static final Query<EntityStore> QUERY = Archetype.of(new ComponentType[]{});

    public DetectTamedDeath() {
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent deathComponent,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Use deterministic UUID from ref - this matches how TamingManager tracks animals
            // TamingManager uses UUID.nameUUIDFromBytes(entityRef.toString().getBytes())
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());

            if (uuidComp == null) {
                log("UUIDComponent is null for entity with DeathComponent");
                return;
            }

            UUID entityId = uuidComp.getUuid();

            // Track all death UUIDs for debugging
            trackDetectedDeath(entityId);

            // Check if this is a tamed animal
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                log("Plugin instance is null");
                return;
            }

            TamingManager tamingManager = plugin.getTamingManager();
            if (tamingManager == null) {
                log("TamingManager is null");
                return;
            }

            boolean isTamed = tamingManager.isTamed(entityId);

            if (!isTamed) {
                return;
            }

            // Mark as dead - won't respawn
            tamingManager.onTamedAnimalDeath(entityId);
        } catch (Exception e) {
            log("Exception in onComponentAdded: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void log(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[DetectTamedDeath] " + message);
        }
    }
}
