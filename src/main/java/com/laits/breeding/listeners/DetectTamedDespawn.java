package com.laits.breeding.listeners;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;

public class DetectTamedDespawn extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType(), DespawnComponent.getComponentType());

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
      public void tick(
         float dt,
         int index,
         @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
         @Nonnull Store<EntityStore> store,
         @Nonnull CommandBuffer<EntityStore> commandBuffer
      ) {
        try {
            NPCEntity npcEntity = archetypeChunk.getComponent(index, NPCEntity.getComponentType());

            boolean entityExists = true;
            if (npcEntity == null || npcEntity.isDespawning()) {
                entityExists = false;
            }

            DespawnComponent despawnComp = archetypeChunk.getComponent(index, DespawnComponent.getComponentType());
            if (despawnComp != null) {
                entityExists = false;
            }

            if (entityExists) {
                // Entity is not despawning
                return;
            }

            TransformComponent transformComp = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

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

            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp == null) {
                log("UUIDComponent is null for despawned entity");
                return;
            }
            UUID entityId = uuidComp.getUuid();

            boolean isTamed = tamingManager.isTamed(entityId);
            log("Is entity tamed? " + isTamed);
            if (isTamed) {
                // It's a tamed animal - ensure it's tracked
                // tamingManager.trackTamedEntity(ref, entityId);
                Vector3d position = transformComp.getPosition();
                double x = position.getX();
                double y = position.getY();
                double z = position.getZ();
                
                tamingManager.onTamedAnimalDespawn(entityId, x, y, z);
            }
        } catch (Exception e) {
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
