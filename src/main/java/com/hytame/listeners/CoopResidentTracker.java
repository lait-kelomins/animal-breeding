package com.hytame.listeners;

import java.util.List;
import java.util.UUID;

import com.hypixel.hytale.builtin.adventure.farming.component.CoopResidentComponent;
import com.hypixel.hytale.builtin.adventure.farming.states.CoopBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.reference.PersistentRef;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hytame.HyTamePlugin;
import com.hytame.coop.CoopCodecExtender;
import com.hytame.coop.HyTameCoopData;
import com.hytame.managers.TamingManager;
import com.hytame.models.AnimalType;
import com.hytame.models.TamedAnimalData;
import com.hytame.tame.HyTameComponent;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.util.EntityUtil;
import com.hytame.util.NameplateUtil;

import javax.annotation.Nonnull;

/**
 * Tracks tamed animals entering/exiting coops.
 *
 * Uses CoopCodecExtender's companion map to persist HyTame taming data
 * on CapturedNPCMetadata instances, which are saved/loaded with chunk data.
 *
 * onEntityRemove: When a tamed animal enters coop storage, writes HyTame data
 *   to the matching CoopResident's metadata via the companion map.
 * onEntityAdded: When an animal exits the coop, reads HyTame data from the
 *   companion map and restores taming state.
 */
public class CoopResidentTracker extends RefSystem<EntityStore> {

    private static final ComponentType<EntityStore, CoopResidentComponent> COOP_RESIDENT_TYPE =
            CoopResidentComponent.getComponentType();

    @Override
    public Query<EntityStore> getQuery() {
        return COOP_RESIDENT_TYPE;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Entity with CoopResidentComponent was spawned - animal exiting coop
        try {
            CoopResidentComponent coopComp = store.getComponent(ref, COOP_RESIDENT_TYPE);
            if (coopComp == null) {
                return;
            }

            Vector3i coopPos = coopComp.getCoopLocation();
            if (coopPos == null) {
                return;
            }

            UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;

            // Defer restoration to world.execute() because PersistentRef is not yet
            // set at the time onEntityAdded fires (set at line 262-263 of CoopBlock.ensureSpawnResidentsInWorld,
            // after addComponent at line 260). We need PersistentRef to match the CoopResident.
            World world = commandBuffer.getExternalData().getWorld();
            final Ref<EntityStore> entityRef = ref;
            final Vector3i fCoopPos = coopPos;

            world.execute(() -> {
                try {
                    restoreFromCoop(entityRef, fCoopPos, world);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Deferred restore: match entity to CoopResident by PersistentRef UUID,
     * read HyTame data from companion map, apply to entity.
     */
    public static void restoreFromCoop(Ref<EntityStore> ref, Vector3i coopPos, World world) {
        if (!ref.isValid()) {
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }

        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
        UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
        if (entityUuid == null) {
            return;
        }

        // Access CoopBlock at coop position
        CoopBlock coopBlock = getCoopBlockAt(world, coopPos);
        if (coopBlock == null) {
            return;
        }

        List<CoopBlock.CoopResident> residents = CoopCodecExtender.getResidents(coopBlock);
        if (residents == null || residents.isEmpty()) {
            return;
        }

        // Find matching resident by PersistentRef UUID
        CoopBlock.CoopResident matchedResident = null;
        for (int i = 0; i < residents.size(); i++) {
            CoopBlock.CoopResident resident = residents.get(i);
            PersistentRef persistentRef = resident.getPersistentRef();
            UUID refUuid = (persistentRef != null) ? persistentRef.getUuid() : null;
            boolean hasHyTame = CoopCodecExtender.hasHyTameData(resident.getMetadata());
            if (persistentRef != null && entityUuid.equals(refUuid)) {
                matchedResident = resident;
                break;
            }
        }

        if (matchedResident == null) {
            return;
        }

        CapturedNPCMetadata metadata = matchedResident.getMetadata();
        HyTameCoopData data = CoopCodecExtender.getHyTameData(metadata);
        if (data == null || !data.isTamed()) {
            return;
        }

        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) {
            return;
        }

        // Restore HyTameComponent
        var hyTameType = plugin.getHyTameComponentType();
        if (hyTameType != null) {
            try {
                HyTameComponent hyTameComp = store.ensureAndGetComponent(ref, hyTameType);
                if (hyTameComp != null) {
                    UUID ownerUuid = data.getOwnerUuid();
                    String ownerName = data.getOwnerName();
                    hyTameComp.setTamed(
                            ownerUuid != null ? ownerUuid : UUID.randomUUID(),
                            ownerName != null ? ownerName : "Unknown");
                    UUID hytameId = data.getHytameId();
                    if (hytameId != null) {
                        hyTameComp.setHytameId(hytameId);
                    }
                } else {
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
        }

        // Restore nameplate
        String customName = data.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            try {
                NameplateUtil.setEntityNameplate(ref, customName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Register in TamingManager
        TamingManager tamingManager = plugin.getTamingManager();
        if (tamingManager != null) {
            AnimalType type = null;
            if (data.getAnimalType() != null) {
                try {
                    type = AnimalType.valueOf(data.getAnimalType());
                } catch (IllegalArgumentException e) {
                    // Custom or unknown type
                }
            }
            // Fetch position from entity ref
            Vector3d entityPos = EntityUtil.getPositionFromRef(ref);
            double coopX = entityPos != null ? entityPos.getX() : 0;
            double coopY = entityPos != null ? entityPos.getY() : 0;
            double coopZ = entityPos != null ? entityPos.getZ() : 0;

            TamedAnimalData tamedData = tamingManager.tameAnimal(
                    data.getHytameId(), entityUuid, data.getOwnerUuid(),
                    customName != null ? customName : "Tamed Animal",
                    type, ref, coopX, coopY, coopZ, null, null);
            if (tamedData != null) {
                tamedData.setOwnerName(data.getOwnerName());
                tamedData.setCaptured(false);
                tamingManager.saveImmediately();
            }
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Entity with CoopResidentComponent is being removed - animal entering coop storage

        try {
            if (reason == RemoveReason.UNLOAD) {
                return;
            }

            // Try both store and commandBuffer to diagnose which works
            CoopResidentComponent coopComp = store.getComponent(ref, COOP_RESIDENT_TYPE);
            CoopResidentComponent coopCompCB = commandBuffer.getComponent(ref, COOP_RESIDENT_TYPE);
            if (coopComp == null) coopComp = coopCompCB;
            if (coopComp == null) {
                return;
            }

            boolean markedForDespawn = coopComp.getMarkedForDespawn();
            if (!markedForDespawn) {
                return;
            }

            Vector3i coopPos = coopComp.getCoopLocation();
            if (coopPos == null) {
                return;
            }

            // Try both for UUID too
            UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            if (uuidComp == null) uuidComp = commandBuffer.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;

            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null) {
                return;
            }

            var hyTameType = plugin.getHyTameComponentType();
            if (hyTameType == null) {
                return;
            }

            // Try both for HyTameComponent
            HyTameComponent hyTameComp = store.getComponent(ref, hyTameType);
            if (hyTameComp == null) hyTameComp = commandBuffer.getComponent(ref, hyTameType);
            if (hyTameComp == null || !hyTameComp.isTamed()) {
                return;
            }

            // Get TamedAnimalData for additional fields
            TamingManager tamingManager = plugin.getTamingManager();
            TamedAnimalData tamedData = null;
            if (tamingManager != null && entityUuid != null) {
                tamedData = tamingManager.getTamedData(entityUuid);
                if (tamedData != null && tamedData.isCaptured()) {
                    return;
                }
            }

            HyTameCoopData coopData = HyTameCoopData.from(hyTameComp, tamedData);

            // Access CoopBlock at coop position
            World world = commandBuffer.getExternalData().getWorld();
            CoopBlock coopBlock = getCoopBlockAt(world, coopPos);
            if (coopBlock == null) {
                return;
            }

            List<CoopBlock.CoopResident> residents = CoopCodecExtender.getResidents(coopBlock);
            if (residents == null || residents.isEmpty()) {
                return;
            }

            // Get entity's role index for matching
            var npcComp = store.getComponent(ref, EcsReflectionUtil.NPC_TYPE);
            if (npcComp == null) npcComp = commandBuffer.getComponent(ref, EcsReflectionUtil.NPC_TYPE);
            int entityRoleIndex = (npcComp != null) ? npcComp.getRoleIndex() : -1;

            // Find the matching CoopResident
            CoopBlock.CoopResident matchedResident = null;
            for (int i = 0; i < residents.size(); i++) {
                CoopBlock.CoopResident resident = residents.get(i);
                CapturedNPCMetadata meta = resident.getMetadata();
                boolean deployed = resident.getDeployedToWorld();
                PersistentRef pRef = resident.getPersistentRef();
                boolean hasHyTame = CoopCodecExtender.hasHyTameData(meta);
                int metaRole = NPCPlugin.get().getIndex(meta.getNpcNameKey());

                if (!deployed && pRef == null && !hasHyTame) {
                    if (entityRoleIndex >= 0 && metaRole == entityRoleIndex) {
                        matchedResident = resident;
                        break;
                    }
                }
            }

            if (matchedResident == null) {
                return;
            }

            // Write HyTame data to the resident's metadata via companion map
            CoopCodecExtender.setHyTameData(matchedResident.getMetadata(), coopData);

            // Mark in TamingManager as in-storage
            if (tamingManager != null && tamedData != null) {
                tamedData.setCaptured(true);
                tamingManager.saveImmediately();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Utility ---

    /**
     * Get the CoopBlock component at a world position.
     */
    private static CoopBlock getCoopBlockAt(World world, Vector3i pos) {
        try {
            Object worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
            if (worldChunk == null) return null;

            Ref<ChunkStore> blockRef = ((WorldChunk) worldChunk).getBlockComponentEntity(pos.x, pos.y, pos.z);
            if (blockRef == null) {
                blockRef = BlockModule.ensureBlockEntity((WorldChunk) worldChunk, pos.x, pos.y, pos.z);
            }
            if (blockRef == null) return null;

            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            return chunkStore.getComponent(blockRef, CoopBlock.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    private static void log(String message) {
        if (!HyTamePlugin.isVerboseLogging()) return;
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[CoopTracker] " + message);
        }
    }
}
