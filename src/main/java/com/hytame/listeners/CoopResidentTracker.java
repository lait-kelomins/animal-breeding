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
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.reference.PersistentRef;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import com.hytame.HyTamePlugin;
import com.hytame.coop.CoopCodecExtender;
import com.hytame.coop.HyTameCoopData;
import com.hytame.managers.TamingManager;
import com.hytame.models.AnimalType;
import com.hytame.models.TamedAnimalData;
import com.hytame.tame.HyTameComponent;
import com.hytame.util.EcsReflectionUtil;
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
        diag("[COOP-ADD] onEntityAdded triggered, reason=" + reason);

        try {
            CoopResidentComponent coopComp = store.getComponent(ref, COOP_RESIDENT_TYPE);
            if (coopComp == null) {
                diag("[COOP-ADD] ABORT: coopComp is null (store.getComponent returned null)");
                return;
            }

            Vector3i coopPos = coopComp.getCoopLocation();
            if (coopPos == null) {
                diag("[COOP-ADD] ABORT: coopPos is null");
                return;
            }

            UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
            diag("[COOP-ADD] entityUuid=" + entityUuid + " coopPos=" + coopPos.x + "," + coopPos.y + "," + coopPos.z);

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
                    diag("[COOP-ADD] EXCEPTION in deferred restore: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            diag("[COOP-ADD] EXCEPTION in onEntityAdded: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Deferred restore: match entity to CoopResident by PersistentRef UUID,
     * read HyTame data from companion map, apply to entity.
     */
    public static void restoreFromCoop(Ref<EntityStore> ref, Vector3i coopPos, World world) {
        diag("[COOP-RESTORE] Starting restore for coopPos=" + coopPos.x + "," + coopPos.y + "," + coopPos.z);

        if (!ref.isValid()) {
            diag("[COOP-RESTORE] ABORT: ref no longer valid");
            return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            diag("[COOP-RESTORE] ABORT: store is null");
            return;
        }

        UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
        UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
        if (entityUuid == null) {
            diag("[COOP-RESTORE] ABORT: no UUID component after defer");
            return;
        }
        diag("[COOP-RESTORE] entityUuid=" + entityUuid);

        // Access CoopBlock at coop position
        CoopBlock coopBlock = getCoopBlockAt(world, coopPos);
        if (coopBlock == null) {
            diag("[COOP-RESTORE] ABORT: no CoopBlock at position");
            return;
        }

        List<CoopBlock.CoopResident> residents = CoopCodecExtender.getResidents(coopBlock);
        if (residents == null || residents.isEmpty()) {
            diag("[COOP-RESTORE] ABORT: no residents (null=" + (residents == null) + ")");
            return;
        }
        diag("[COOP-RESTORE] Found " + residents.size() + " residents in coop");

        // Find matching resident by PersistentRef UUID
        CoopBlock.CoopResident matchedResident = null;
        for (int i = 0; i < residents.size(); i++) {
            CoopBlock.CoopResident resident = residents.get(i);
            PersistentRef persistentRef = resident.getPersistentRef();
            UUID refUuid = (persistentRef != null) ? persistentRef.getUuid() : null;
            boolean hasHyTame = CoopCodecExtender.hasHyTameData(resident.getMetadata());
            diag("[COOP-RESTORE]   resident[" + i + "] persistentRef=" + refUuid
                    + " deployed=" + resident.getDeployedToWorld()
                    + " hasHyTame=" + hasHyTame);
            if (persistentRef != null && entityUuid.equals(refUuid)) {
                matchedResident = resident;
                diag("[COOP-RESTORE]   -> MATCHED by UUID!");
                break;
            }
        }

        if (matchedResident == null) {
            diag("[COOP-RESTORE] ABORT: no resident matched UUID " + entityUuid);
            return;
        }

        CapturedNPCMetadata metadata = matchedResident.getMetadata();
        HyTameCoopData data = CoopCodecExtender.getHyTameData(metadata);
        diag("[COOP-RESTORE] companionMap data=" + data + " (metadata identity=" + System.identityHashCode(metadata) + ")");
        if (data == null || !data.isTamed()) {
            diag("[COOP-RESTORE] ABORT: no HyTame data or not tamed (wild animal)");
            return;
        }

        diag("[COOP-RESTORE] Found HyTame data: " + data);

        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) {
            diag("[COOP-RESTORE] ABORT: plugin is null");
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
                    diag("[COOP-RESTORE] Restored HyTameComponent: owner=" + ownerName + " hytameId=" + hytameId);
                } else {
                    diag("[COOP-RESTORE] WARN: ensureAndGetComponent returned null");
                }
            } catch (Exception e) {
                diag("[COOP-RESTORE] EXCEPTION restoring HyTameComponent: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            diag("[COOP-RESTORE] WARN: hyTameType is null");
        }

        // Restore nameplate
        String customName = data.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            try {
                NameplateUtil.setEntityNameplate(ref, customName);
                diag("[COOP-RESTORE] Restored nameplate: " + customName);
            } catch (Exception e) {
                diag("[COOP-RESTORE] EXCEPTION restoring nameplate: " + e.getMessage());
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
            TamedAnimalData tamedData = tamingManager.tameAnimal(
                    data.getHytameId(), entityUuid, data.getOwnerUuid(),
                    customName != null ? customName : "Tamed Animal",
                    type, ref, 0, 0, 0, null, null);
            if (tamedData != null) {
                tamedData.setOwnerName(data.getOwnerName());
                tamedData.setCaptured(false);
                tamingManager.saveImmediately();
            }
            diag("[COOP-RESTORE] Registered in TamingManager: hytameId=" + data.getHytameId());
        }

        diag("[COOP-RESTORE] SUCCESS: Tamed animal restored from coop!");
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Entity with CoopResidentComponent is being removed - animal entering coop storage
        diag("[COOP-REM] onEntityRemove triggered, reason=" + reason);

        try {
            if (reason == RemoveReason.UNLOAD) {
                diag("[COOP-REM] SKIP: UNLOAD reason");
                return;
            }

            // Try both store and commandBuffer to diagnose which works
            CoopResidentComponent coopComp = store.getComponent(ref, COOP_RESIDENT_TYPE);
            CoopResidentComponent coopCompCB = commandBuffer.getComponent(ref, COOP_RESIDENT_TYPE);
            diag("[COOP-REM] coopComp via store=" + (coopComp != null) + " via cmdBuf=" + (coopCompCB != null));
            if (coopComp == null) coopComp = coopCompCB;
            if (coopComp == null) {
                diag("[COOP-REM] ABORT: coopComp null from both store and commandBuffer");
                return;
            }

            boolean markedForDespawn = coopComp.getMarkedForDespawn();
            diag("[COOP-REM] markedForDespawn=" + markedForDespawn);
            if (!markedForDespawn) {
                diag("[COOP-REM] SKIP: not marked for despawn");
                return;
            }

            Vector3i coopPos = coopComp.getCoopLocation();
            if (coopPos == null) {
                diag("[COOP-REM] ABORT: coopPos is null");
                return;
            }

            // Try both for UUID too
            UUIDComponent uuidComp = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            if (uuidComp == null) uuidComp = commandBuffer.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
            UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;
            diag("[COOP-REM] entityUuid=" + entityUuid + " coopPos=" + coopPos.x + "," + coopPos.y + "," + coopPos.z);

            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null) {
                diag("[COOP-REM] ABORT: plugin is null");
                return;
            }

            var hyTameType = plugin.getHyTameComponentType();
            if (hyTameType == null) {
                diag("[COOP-REM] ABORT: hyTameType is null");
                return;
            }

            // Try both for HyTameComponent
            HyTameComponent hyTameComp = store.getComponent(ref, hyTameType);
            if (hyTameComp == null) hyTameComp = commandBuffer.getComponent(ref, hyTameType);
            diag("[COOP-REM] hyTameComp=" + (hyTameComp != null) + " isTamed=" + (hyTameComp != null ? hyTameComp.isTamed() : "N/A"));
            if (hyTameComp == null || !hyTameComp.isTamed()) {
                diag("[COOP-REM] SKIP: not tamed");
                return;
            }

            // Get TamedAnimalData for additional fields
            TamingManager tamingManager = plugin.getTamingManager();
            TamedAnimalData tamedData = null;
            if (tamingManager != null && entityUuid != null) {
                tamedData = tamingManager.getTamedData(entityUuid);
                diag("[COOP-REM] tamedData=" + (tamedData != null) + " isCaptured=" + (tamedData != null ? tamedData.isCaptured() : "N/A"));
                if (tamedData != null && tamedData.isCaptured()) {
                    diag("[COOP-REM] SKIP: animal already captured in crate");
                    return;
                }
            }

            HyTameCoopData coopData = HyTameCoopData.from(hyTameComp, tamedData);
            diag("[COOP-REM] Built coopData: " + coopData);

            // Access CoopBlock at coop position
            World world = commandBuffer.getExternalData().getWorld();
            CoopBlock coopBlock = getCoopBlockAt(world, coopPos);
            if (coopBlock == null) {
                diag("[COOP-REM] ABORT: no CoopBlock at " + coopPos.x + "," + coopPos.y + "," + coopPos.z);
                return;
            }

            List<CoopBlock.CoopResident> residents = CoopCodecExtender.getResidents(coopBlock);
            if (residents == null || residents.isEmpty()) {
                diag("[COOP-REM] ABORT: no residents (null=" + (residents == null) + ")");
                return;
            }

            // Get entity's role index for matching
            var npcComp = store.getComponent(ref, EcsReflectionUtil.NPC_TYPE);
            if (npcComp == null) npcComp = commandBuffer.getComponent(ref, EcsReflectionUtil.NPC_TYPE);
            int entityRoleIndex = (npcComp != null) ? npcComp.getRoleIndex() : -1;
            diag("[COOP-REM] entityRoleIndex=" + entityRoleIndex + " npcComp=" + (npcComp != null) + " residents=" + residents.size());

            // Find the matching CoopResident
            CoopBlock.CoopResident matchedResident = null;
            for (int i = 0; i < residents.size(); i++) {
                CoopBlock.CoopResident resident = residents.get(i);
                CapturedNPCMetadata meta = resident.getMetadata();
                boolean deployed = resident.getDeployedToWorld();
                PersistentRef pRef = resident.getPersistentRef();
                boolean hasHyTame = CoopCodecExtender.hasHyTameData(meta);
                int metaRole = meta.getRoleIndex();
                diag("[COOP-REM]   resident[" + i + "] deployed=" + deployed
                        + " persistentRef=" + (pRef != null ? pRef.getUuid() : "null")
                        + " hasHyTame=" + hasHyTame
                        + " metaRoleIndex=" + metaRole
                        + " metaIdentity=" + System.identityHashCode(meta));

                if (!deployed && pRef == null && !hasHyTame) {
                    if (entityRoleIndex >= 0 && metaRole == entityRoleIndex) {
                        matchedResident = resident;
                        diag("[COOP-REM]   -> MATCHED!");
                        break;
                    }
                }
            }

            if (matchedResident == null) {
                diag("[COOP-REM] ABORT: no matching resident found");
                return;
            }

            // Write HyTame data to the resident's metadata via companion map
            CoopCodecExtender.setHyTameData(matchedResident.getMetadata(), coopData);
            diag("[COOP-REM] Wrote HyTame data to metadata (identity=" + System.identityHashCode(matchedResident.getMetadata()) + ")");

            // Mark in TamingManager as in-storage
            if (tamingManager != null && tamedData != null) {
                tamedData.setCaptured(true);
                tamingManager.saveImmediately();
                diag("[COOP-REM] Marked as captured in TamingManager");
            }

            diag("[COOP-REM] SUCCESS: data persisted via companion map");

        } catch (Exception e) {
            diag("[COOP-REM] EXCEPTION: " + e.getMessage());
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
            diag("Failed to get CoopBlock at " + pos.x + "," + pos.y + "," + pos.z + ": " + e.getMessage());
            return null;
        }
    }

    /** Always-on diagnostic logging for coop flow debugging. */
    private static void diag(String message) {
        System.out.println(message);
    }

    private static void log(String message) {
        if (!HyTamePlugin.isVerboseLogging()) return;
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[CoopTracker] " + message);
        }
    }
}
