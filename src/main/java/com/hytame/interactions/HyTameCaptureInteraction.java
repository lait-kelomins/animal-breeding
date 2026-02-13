package com.hytame.interactions;

import com.hypixel.hytale.builtin.adventure.farming.states.CoopBlock;
import com.hypixel.hytale.builtin.tagset.TagSetPlugin;
import com.hypixel.hytale.builtin.tagset.config.NPCGroup;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import it.unimi.dsi.fastutil.Pair;

import com.hytame.HyTamePlugin;
import com.hytame.managers.TamingManager;
import com.hytame.metadata.HyTameMetadataKeys;
import com.hytame.models.AnimalType;
import com.hytame.models.TamedAnimalData;
import com.hytame.tame.HyTameComponent;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.util.EntityUtil;
import com.hytame.util.NameplateUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Replacement for vanilla UseCaptureCrateInteraction.
 * Replicates vanilla capture/release logic AND stores/restores HyTame metadata
 * on the capture crate ItemStack.
 *
 * On capture: writes HyTame taming data (hytameId, owner, name, type) to the item.
 * On release: reads HyTame metadata and applies it to the spawned entity.
 */
public class HyTameCaptureInteraction extends SimpleBlockInteraction {

    @SuppressWarnings("unchecked")
    public static final BuilderCodec<HyTameCaptureInteraction> CODEC =
            ((BuilderCodec.Builder<HyTameCaptureInteraction>)
                    ((BuilderCodec.Builder<HyTameCaptureInteraction>)
                            ((BuilderCodec.Builder<HyTameCaptureInteraction>)
                                    BuilderCodec.builder(
                                            HyTameCaptureInteraction.class,
                                            HyTameCaptureInteraction::new,
                                            SimpleInteraction.CODEC)
                                    .appendInherited(
                                            new KeyedCodec<String[]>("AcceptedNpcGroups", NPCGroup.CHILD_ASSET_CODEC_ARRAY),
                                            (o, v) -> { o.acceptedNpcGroupIds = v; },
                                            o -> o.acceptedNpcGroupIds,
                                            (o, p) -> { o.acceptedNpcGroupIds = p.acceptedNpcGroupIds; })
                                    .addValidator(NPCGroup.VALIDATOR_CACHE.getArrayValidator())
                                    .add())
                            .appendInherited(
                                    new KeyedCodec<String>("FullIcon", Codec.STRING),
                                    (o, v) -> { o.fullIcon = v; },
                                    o -> o.fullIcon,
                                    (o, p) -> { o.fullIcon = p.fullIcon; })
                            .add())
                    .afterDecode(data -> {
                        if (data.acceptedNpcGroupIds != null) {
                            data.acceptedNpcGroupIndexes = new int[data.acceptedNpcGroupIds.length];
                            for (int i = 0; i < data.acceptedNpcGroupIds.length; i++) {
                                data.acceptedNpcGroupIndexes[i] =
                                        NPCGroup.getAssetMap().getIndex(data.acceptedNpcGroupIds[i]);
                            }
                        }
                    }))
                    .build();

    protected String[] acceptedNpcGroupIds;
    protected int[] acceptedNpcGroupIndexes;
    protected String fullIcon;

    // ==================== CAPTURE (tick0) ====================

    @Override
    protected void tick0(boolean firstRun, float time,
                         @Nonnull InteractionType type,
                         @Nonnull InteractionContext context,
                         @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        ItemStack item = context.getHeldItem();
        if (item == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> playerRef = context.getEntity();
        LivingEntity playerEntity = (LivingEntity) EntityUtils.getEntity(playerRef, commandBuffer);
        Inventory playerInventory = playerEntity.getInventory();
        byte activeHotbarSlot = playerInventory.getActiveHotbarSlot();
        ItemStack inHandItemStack = playerInventory.getActiveHotbarItem();

        // Check if crate already has a captured NPC
        CapturedNPCMetadata existingMeta = item.getFromMetadataOrNull(
                "CapturedEntity", CapturedNPCMetadata.CODEC);


        if (existingMeta == null) {
            // === CAPTURE MODE: Empty crate, try to capture target entity ===
            Ref<EntityStore> targetEntity = context.getTargetEntity();
            if (targetEntity == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            NPCEntity npc = commandBuffer.getComponent(targetEntity, NPCEntity.getComponentType());
            if (npc == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Check NPC group acceptance (same as vanilla)
            TagSetPlugin.TagSetLookup tagSetPlugin = TagSetPlugin.get(NPCGroup.class);
            boolean tagFound = false;
            for (int group : this.acceptedNpcGroupIndexes) {
                if (tagSetPlugin.tagInSet(group, npc.getRoleIndex())) {
                    tagFound = true;
                    break;
                }
            }
            if (!tagFound) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            PersistentModel persistentModel = commandBuffer.getComponent(
                    targetEntity, PersistentModel.getComponentType());
            if (persistentModel == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            // Build CapturedNPCMetadata (vanilla behavior)
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(
                    persistentModel.getModelReference().getModelAssetId());
            CapturedNPCMetadata meta = inHandItemStack.getFromMetadataOrDefault(
                    "CapturedEntity", CapturedNPCMetadata.CODEC);
            if (modelAsset != null) {
                meta.setIconPath(modelAsset.getIcon());
            }
            String npcName = NPCPlugin.get().getName(npc.getRoleIndex());
            if (npcName != null) {
                meta.setNpcNameKey(npcName);
            }
            if (this.fullIcon != null) {
                meta.setFullItemIcon(this.fullIcon);
            }

            // Write CapturedNPCMetadata to item
            ItemStack itemWithNPC = inHandItemStack.withMetadata(
                    CapturedNPCMetadata.KEYED_CODEC, meta);

            // === HyTame: Write taming metadata if animal is tamed ===
            itemWithNPC = writeHyTameMetadata(itemWithNPC, targetEntity, commandBuffer);

            playerInventory.getHotbar().replaceItemStackInSlot(
                    activeHotbarSlot, item, itemWithNPC);
            commandBuffer.removeEntity(targetEntity, RemoveReason.REMOVE);
            return;
        }

        // Crate is filled - delegate to SimpleBlockInteraction (handles block placement)
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }

    // ==================== RELEASE (interactWithBlock) ====================

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler) {

        ItemStack item = context.getHeldItem();
        if (item == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Ref<EntityStore> playerRef = context.getEntity();
        LivingEntity playerEntity = (LivingEntity) EntityUtils.getEntity(playerRef, commandBuffer);
        Inventory playerInventory = playerEntity.getInventory();
        byte activeHotbarSlot = playerInventory.getActiveHotbarSlot();

        CapturedNPCMetadata existingMeta = item.getFromMetadataOrNull(
                "CapturedEntity", CapturedNPCMetadata.CODEC);
        if (existingMeta == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        BlockPosition pos = context.getTargetBlock();
        if (pos == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // === Read HyTame metadata BEFORE wiping item (so we can apply after spawn) ===
        UUID hytameId = item.getFromMetadataOrNull(HyTameMetadataKeys.HYTAME_ID, Codec.UUID_STRING);
        UUID ownerUuid = item.getFromMetadataOrNull(HyTameMetadataKeys.OWNER_UUID, Codec.UUID_STRING);
        String ownerName = item.getFromMetadataOrNull(HyTameMetadataKeys.OWNER_NAME, Codec.STRING);
        String customName = item.getFromMetadataOrNull(HyTameMetadataKeys.CUSTOM_NAME, Codec.STRING);
        String animalTypeStr = item.getFromMetadataOrNull(HyTameMetadataKeys.ANIMAL_TYPE, Codec.STRING);
        Boolean isTamed = item.getFromMetadataOrNull(HyTameMetadataKeys.TAMED, Codec.BOOLEAN);

        // Store HyTame data on metadata for coop persistence (before tryPutResident stores it)
        if (Boolean.TRUE.equals(isTamed) && existingMeta != null) {
            com.hytame.coop.HyTameCoopData coopData = new com.hytame.coop.HyTameCoopData(
                    hytameId, ownerUuid, ownerName, customName, animalTypeStr, true);
            com.hytame.coop.CoopCodecExtender.setHyTameData(existingMeta, coopData);
            log("Stored HyTame data on metadata for coop persistence");
        }

        // Wipe all metadata from item (vanilla behavior)
        ItemStack noMetaItemStack = item.withMetadata(null);

        // Check for coop block (vanilla behavior)
        Object worldChunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        Ref<ChunkStore> blockRef = ((WorldChunk) worldChunk).getBlockComponentEntity(pos.x, pos.y, pos.z);
        if (blockRef == null) {
            blockRef = BlockModule.ensureBlockEntity((WorldChunk) worldChunk, pos.x, pos.y, pos.z);
        }

        if (blockRef != null) {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            CoopBlock coopBlockState = chunkStore.getComponent(blockRef, CoopBlock.getComponentType());
            if (coopBlockState != null) {
                WorldTimeResource worldTimeResource = commandBuffer.getResource(
                        WorldTimeResource.getResourceType());
                if (coopBlockState.tryPutResident(existingMeta, worldTimeResource)) {
                    world.execute(() -> coopBlockState.ensureSpawnResidentsInWorld(
                            world, world.getEntityStore().getStore(),
                            new Vector3d(pos.x, pos.y, pos.z),
                            new Vector3d().assign(Vector3d.FORWARD)));
                    playerInventory.getHotbar().replaceItemStackInSlot(
                            activeHotbarSlot, item, noMetaItemStack);
                } else {
                    context.getState().state = InteractionState.Failed;
                }
                return;
            }
        }

        // === Spawn NPC at position ===
        Vector3d spawnPos = new Vector3d(
                (float) pos.x + 0.5f, pos.y, (float) pos.z + 0.5f);
        if (context.getClientState() != null) {
            BlockFace blockFace = BlockFace.fromProtocolFace(context.getClientState().blockFace);
            if (blockFace != null) {
                spawnPos.add(blockFace.getDirection());
            }
        }

        NPCPlugin npcModule = NPCPlugin.get();
        Store<EntityStore> store = commandBuffer.getStore();
        int roleIndex = NPCPlugin.get().getIndex(existingMeta.getNpcNameKey());

        // Spawn and apply HyTame metadata
        final UUID fHytameId = hytameId;
        final UUID fOwnerUuid = ownerUuid;
        final String fOwnerName = ownerName;
        final String fCustomName = customName;
        final String fAnimalTypeStr = animalTypeStr;
        final boolean fIsTamed = Boolean.TRUE.equals(isTamed);

        commandBuffer.run(_store -> {
            Pair<Ref<EntityStore>, NPCEntity> result = npcModule.spawnEntity(
                    store, roleIndex, spawnPos, Vector3f.ZERO, null, null);

            if (result != null && fIsTamed) {
                Ref<EntityStore> npcRef = result.first();

                // Apply HyTameComponent immediately using ensureAndGetComponent
                // (matches pattern used by HyTameActivateSystem, CoopResidentTracker, TameHelper)
                HyTamePlugin plugin = HyTamePlugin.getInstance();
                if (plugin != null) {
                    var hyTameType = plugin.getHyTameComponentType();
                    if (hyTameType != null) {
                        try {
                            HyTameComponent comp = store.ensureAndGetComponent(npcRef, hyTameType);
                            if (comp != null) {
                                if (fOwnerUuid != null && fOwnerName != null) {
                                    comp.setTamed(fOwnerUuid, fOwnerName);
                                }
                                if (fHytameId != null) {
                                    comp.setHytameId(fHytameId);
                                }
                                log("Applied HyTameComponent: owner=" + fOwnerName + " hytameId=" + fHytameId);
                            }
                        } catch (Exception e) {
                            log("Failed to apply HyTameComponent: " + e.getMessage());
                        }
                    }
                }

                // Schedule nameplate + TamingManager registration on world thread
                // (entity UUID not available during commandBuffer.run, must defer)
                world.execute(() -> {
                    try {
                        // Set nameplate
                        if (fCustomName != null && !fCustomName.isEmpty()) {
                            NameplateUtil.setEntityNameplate(npcRef, fCustomName);
                            log("Restored nameplate: " + fCustomName);
                        }

                        // Register in TamingManager with the entity's real UUID
                        registerInTamingManager(npcRef, fHytameId, fOwnerUuid,
                                fOwnerName, fCustomName, fAnimalTypeStr);
                    } catch (Exception e) {
                        log("Failed deferred restore: " + e.getMessage());
                    }
                });
            }
        });

        playerInventory.getHotbar().replaceItemStackInSlot(
                activeHotbarSlot, item, noMetaItemStack);
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i targetBlock) {
        // No-op (same as vanilla)
    }

    // ==================== HyTame Metadata Helpers ====================

    /**
     * Write HyTame metadata to the capture crate item if the target is tamed.
     */
    private ItemStack writeHyTameMetadata(ItemStack itemStack,
                                           Ref<EntityStore> targetEntity,
                                           CommandBuffer<EntityStore> commandBuffer) {
        try {
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null) return itemStack;

            // Check if entity has HyTameComponent
            var hyTameType = plugin.getHyTameComponentType();
            if (hyTameType == null) {
                log("writeHyTameMetadata: hyTameType is null");
                return itemStack;
            }
            HyTameComponent hyTameComp = commandBuffer.getComponent(targetEntity, hyTameType);
            if (hyTameComp == null) {
                // Fallback: try reading from store directly
                Store<EntityStore> store = commandBuffer.getStore();
                if (store != null) {
                    hyTameComp = store.getComponent(targetEntity, hyTameType);
                }
            }
            if (hyTameComp == null) {
                log("writeHyTameMetadata: HyTameComponent not found on entity");
                return itemStack;
            }
            if (!hyTameComp.isTamed()) {
                log("writeHyTameMetadata: entity is not tamed");
                return itemStack;
            }

            UUID hytameId = hyTameComp.getHytameId();
            UUID ownerUuid = hyTameComp.getTamerUUID();
            String ownerName = hyTameComp.getTamerName();

            // Get custom name and animal type from TamingManager
            String customName = null;
            String animalTypeStr = null;
            TamingManager tamingManager = plugin.getTamingManager();
            if (tamingManager != null && hytameId != null) {
                TamedAnimalData tamedData = tamingManager.findByHytameId(hytameId);
                if (tamedData != null) {
                    customName = tamedData.getCustomName();
                    AnimalType type = tamedData.getAnimalType();
                    if (type != null) {
                        animalTypeStr = type.name();
                    }
                    // Mark as captured so EntityRemoveEvent doesn't trigger respawn
                    tamedData.setCaptured(true);
                    tamingManager.saveImmediately();
                }
            }

            // Write all HyTame metadata keys
            itemStack = itemStack.withMetadata(
                    HyTameMetadataKeys.TAMED, Codec.BOOLEAN, true);

            if (hytameId != null) {
                itemStack = itemStack.withMetadata(
                        HyTameMetadataKeys.HYTAME_ID, Codec.UUID_STRING, hytameId);
            }
            if (ownerUuid != null) {
                itemStack = itemStack.withMetadata(
                        HyTameMetadataKeys.OWNER_UUID, Codec.UUID_STRING, ownerUuid);
            }
            if (ownerName != null) {
                itemStack = itemStack.withMetadata(
                        HyTameMetadataKeys.OWNER_NAME, Codec.STRING, ownerName);
            }
            if (customName != null) {
                itemStack = itemStack.withMetadata(
                        HyTameMetadataKeys.CUSTOM_NAME, Codec.STRING, customName);
            }
            if (animalTypeStr != null) {
                itemStack = itemStack.withMetadata(
                        HyTameMetadataKeys.ANIMAL_TYPE, Codec.STRING, animalTypeStr);
            }

            log("Wrote HyTame metadata to capture crate: hytameId=" + hytameId
                    + " owner=" + ownerName + " name=" + customName + " type=" + animalTypeStr);

        } catch (Exception e) {
            log("Failed to write HyTame metadata: " + e.getMessage());
        }
        return itemStack;
    }

    /**
     * Register a restored tamed animal in TamingManager.
     * Called from world.execute() after the entity is fully initialized.
     */
    private void registerInTamingManager(Ref<EntityStore> npcRef,
                                          UUID hytameId,
                                          UUID ownerUuid,
                                          String ownerName,
                                          String customName,
                                          String animalTypeStr) {
        try {
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null) return;

            TamingManager tamingManager = plugin.getTamingManager();
            if (tamingManager == null) return;

            // Get entity UUID from the store (entity is now fully initialized)
            Store<EntityStore> store = npcRef.getStore();
            if (store == null) {
                log("Cannot register in TamingManager: store is null");
                return;
            }
            UUIDComponent uuidComp = store.getComponent(npcRef, EcsReflectionUtil.UUID_TYPE);
            UUID entityUuid = (uuidComp != null) ? uuidComp.getUuid() : null;

            if (entityUuid == null || ownerUuid == null) {
                log("Cannot register in TamingManager: entityUuid=" + entityUuid
                        + " ownerUuid=" + ownerUuid);
                return;
            }

            AnimalType type = null;
            if (animalTypeStr != null) {
                try {
                    type = AnimalType.valueOf(animalTypeStr);
                } catch (IllegalArgumentException e) {
                    // Custom animal or unknown type
                }
            }

            // Fetch position from entity ref
            Vector3d capPos = EntityUtil.getPositionFromRef(npcRef);
            double capX = capPos != null ? capPos.getX() : 0;
            double capY = capPos != null ? capPos.getY() : 0;
            double capZ = capPos != null ? capPos.getZ() : 0;

            TamedAnimalData data = tamingManager.tameAnimal(
                    hytameId, entityUuid, ownerUuid,
                    customName != null ? customName : (ownerName + "'s pet"),
                    type, npcRef, capX, capY, capZ, null, null);

            if (data != null) {
                data.setCaptured(false);
                tamingManager.saveImmediately();
                log("Restored tamed animal from capture crate: hytameId=" + hytameId
                        + " entityUuid=" + entityUuid + " owner=" + ownerName
                        + " name=" + customName);
            }
        } catch (Exception e) {
            log("Failed to register in TamingManager: " + e.getMessage());
        }
    }

    private static void log(String message) {
        if (!HyTamePlugin.isVerboseLogging()) return;
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[HyTameCapture] " + message);
        }
    }
}
