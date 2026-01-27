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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.util.EcsReflectionUtil;

public class DetectTamedDespawn extends EntityTickingSystem<EntityStore> {
    private final Query<EntityStore> QUERY = Query.and(EcsReflectionUtil.NPC_TYPE, EcsReflectionUtil.DESPAWN_TYPE);

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
            NPCEntity npcEntity = archetypeChunk.getComponent(index, EcsReflectionUtil.NPC_TYPE);

            boolean entityExists = true;
            if (npcEntity == null || npcEntity.isDespawning()) {
                entityExists = false;
            }

            if (archetypeChunk.getComponent(index, EcsReflectionUtil.DESPAWN_TYPE) != null) {
                entityExists = false;
            }

            if (entityExists) {
                // Entity is not despawning
                return;
            }

            TransformComponent transformComp = archetypeChunk.getComponent(index, EcsReflectionUtil.TRANSFORM_TYPE);
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

            var uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
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
