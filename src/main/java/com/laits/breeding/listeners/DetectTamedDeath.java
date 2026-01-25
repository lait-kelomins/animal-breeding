package com.laits.breeding.listeners;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.NPCSystems.OnDeathSystem;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that detects when tamed animals die.
 * Marks them as dead (not despawned) so they don't respawn.
 */
public class DetectTamedDeath extends OnDeathSystem {

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
        log("onComponentAdded called - DeathComponent detected on an entity");
        try {
            // Use deterministic UUID from ref - this matches how TamingManager tracks animals
            // TamingManager uses UUID.nameUUIDFromBytes(entityRef.toString().getBytes())
            UUID entityId = UUID.nameUUIDFromBytes(ref.toString().getBytes());
            log("Dying entity deterministic UUID: " + entityId);

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
            log("Is entity tamed? " + isTamed);

            if (!isTamed) {
                return;
            }

            // Mark as dead - won't respawn
            log("Marking tamed animal as dead: " + entityId);
            tamingManager.onTamedAnimalDeath(entityId);
            log("Successfully marked tamed animal as dead: " + entityId);

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
