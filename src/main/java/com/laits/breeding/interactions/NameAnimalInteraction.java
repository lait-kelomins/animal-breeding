package com.laits.breeding.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.ui.NametagUIPage;
import com.laits.breeding.util.TameHelper;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.lang.reflect.Field;
import java.util.Random;
import java.util.UUID;

/**
 * Interaction for taming animals with a Name Tag item.
 * Uses pending name from /breed tame command, or generates a random name.
 */
public class NameAnimalInteraction extends SimpleInteraction {

    public static final BuilderCodec<NameAnimalInteraction> CODEC =
        BuilderCodec.builder(NameAnimalInteraction.class, NameAnimalInteraction::new, SimpleInteraction.CODEC)
            .build();

    // Cached component types
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = UUIDComponent.getComponentType();

    // Random names for animals without a pending name
    private static final String[] RANDOM_NAMES = {
        "Fluffy", "Spot", "Buddy", "Max", "Bella", "Charlie", "Luna", "Milo",
        "Coco", "Rocky", "Daisy", "Duke", "Sadie", "Bear", "Molly", "Tucker",
        "Bailey", "Maggie", "Jack", "Sophie", "Oliver", "Lucy", "Buster", "Chloe",
        "Teddy", "Penny", "Zeus", "Zoey", "Gus", "Lily", "Winston", "Gracie"
    };

    private static final Random random = new Random();

    public NameAnimalInteraction() {
        super();
    }

    private boolean shouldFail = false;

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        InteractionType type,
        InteractionContext context,
        CooldownHandler cooldownHandler
    ) {
        if (firstRun) {
            shouldFail = false;

            try {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null) {
                    shouldFail = true;
                    return;
                }

                TamingManager tamingManager = plugin.getTamingManager();
                BreedingManager breedingManager = plugin.getBreedingManager();
                if (tamingManager == null || breedingManager == null) {
                    shouldFail = true;
                    return;
                }

                Ref<EntityStore> targetRef = context.getTargetEntity();
                if (targetRef == null) {
                    log("No target entity");
                    shouldFail = true;
                    return;
                }

                // Get animal UUID
                UUID animalUuid = getUuidFromRef(targetRef);
                if (!tamingManager.isTamed(animalUuid))
                {
                    log("Animal is not tamed");
                    shouldFail = true;
                    return;
                }

                // Skip players
                if (isPlayerEntity(targetRef)) {
                    log("Target is a player, skipping");
                    shouldFail = true;
                    return;
                }

                // Get model asset ID
                String modelAssetId = getModelAssetIdFromEntity(targetRef);
                if (modelAssetId == null) {
                    log("Could not get model asset ID");
                    shouldFail = true;
                    return;
                }

                // Check if it's an animal
                AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
                if (animalType == null) {
                    // Check for custom animal
                    if (plugin.getConfigManager() == null ||
                        !plugin.getConfigManager().isCustomAnimal(modelAssetId)) {
                        sendPlayerMessage(context, "This is not an animal!", "#FF5555");
                        shouldFail = true;
                        return;
                    }
                }


                // Get player info
                UUID playerUuid = getPlayerUuid(context);
                String playerName = getPlayerName(context);

                if (playerUuid == null) {
                    log("Could not get player UUID");
                    shouldFail = true;
                    return;
                }
                if (animalUuid == null) {
                    log("Could not get animal UUID");
                    shouldFail = true;
                    return;
                }

                // Check ECS TameComponent state
                boolean isEcsTamed = TameHelper.isTamed(targetRef);
                UUID ecsOwner = TameHelper.getOwnerUuid(targetRef);

                // Check if already tamed by someone else
                TamedAnimalData existingTamed = tamingManager.getTamedData(animalUuid);
                if (existingTamed != null && !existingTamed.isOwnedBy(playerUuid)) {
                    sendPlayerMessage(context, "This animal belongs to someone else!", "#FF5555");
                    shouldFail = true;
                    return;
                }

                // Also check ECS tame state (may differ from TamingManager)
                if (isEcsTamed && ecsOwner != null && !ecsOwner.equals(playerUuid)) {
                    sendPlayerMessage(context, "This animal belongs to someone else!", "#FF5555");
                    shouldFail = true;
                    return;
                }

                // If animal is tamed via ECS but not in TamingManager, sync them
                if (isEcsTamed && existingTamed == null && ecsOwner != null && ecsOwner.equals(playerUuid)) {
                    // Sync: create TamingManager entry from ECS state
                    UUID hytameId = TameHelper.getHytameId(targetRef);
                    String tamerName = TameHelper.getHyTameComponent(targetRef) != null ?
                        TameHelper.getHyTameComponent(targetRef).getTamerName() : playerName;
                    String defaultName = (animalType != null ? animalType.name() : modelAssetId) + "_" + animalUuid.toString().substring(0, 4);

                    tamingManager.tameAnimal(
                        hytameId,
                        animalUuid,
                        playerUuid,
                        defaultName,
                        animalType,
                        targetRef,
                        0, 0, 0, // Position will be updated later
                        com.laits.breeding.models.GrowthStage.ADULT
                    );
                    log("Synced ECS tame state to TamingManager for " + animalUuid);
                    existingTamed = tamingManager.getTamedData(animalUuid);
                }

                // Open the nametag UI for the player to enter a name
                com.hypixel.hytale.server.core.entity.entities.Player player = getPlayerFromContext(context);
                if (player == null) {
                    log("Could not get player object");
                    shouldFail = true;
                    return;
                }

                try {
                    // Get PlayerRef from player
                    PlayerRef playerRef = player.getPlayerRef();

                    // Determine display name for UI title
                    String animalDisplayName = animalType != null ? animalType.name() : modelAssetId;

                    // Get existing name if renaming
                    String existingName = existingTamed != null ? existingTamed.getCustomName() : null;

                    // Create and open the nametag UI
                    Ref<EntityStore> playerEntityRef = context.getEntity();
                    Store<EntityStore> store = playerEntityRef.getStore();

                    NametagUIPage nametagPage = new NametagUIPage(playerRef, targetRef, playerUuid, animalDisplayName, existingName);
                    player.getPageManager().openCustomPage(playerEntityRef, store, nametagPage);

                    log("Opened nametag UI for " + modelAssetId);
                } catch (Exception e) {
                    log("Failed to open nametag UI: " + e.getMessage());
                    // Fallback to random name if UI fails
                    String name = RANDOM_NAMES[random.nextInt(RANDOM_NAMES.length)];

                    // Detect growth stage from BreedingManager or model ID
                    com.laits.breeding.models.GrowthStage growthStage = com.laits.breeding.models.GrowthStage.ADULT;
                    BreedingData breedingData = breedingManager.getData(animalUuid);
                    if (breedingData != null && breedingData.getGrowthStage() != null) {
                        growthStage = breedingData.getGrowthStage();
                    } else if (modelAssetId != null) {
                        // Detect baby from model ID
                        if (modelAssetId.contains("_Calf") || modelAssetId.contains("_Piglet") ||
                            modelAssetId.contains("_Chick") || modelAssetId.contains("_Lamb") ||
                            modelAssetId.contains("_Foal") || modelAssetId.contains("_Bunny")) {
                            growthStage = com.laits.breeding.models.GrowthStage.BABY;
                        }
                    }

                    // Get position for taming
                    Vector3d pos = getEntityPosition(targetRef);
                    double x = pos != null ? pos.getX() : 0;
                    double y = pos != null ? pos.getY() : 0;
                    double z = pos != null ? pos.getZ() : 0;

                    TamedAnimalData tamedData = tamingManager.tameAnimal(animalUuid, playerUuid, name, animalType, targetRef, x, y, z, growthStage);
                    if (tamedData != null) {
                        consumePlayerHeldItem(context);
                        sendPlayerMessage(context, name + " is now yours!", "#55FF55");
                        playTamingSound(targetRef);
                        spawnHeartParticles(targetRef);
                    }
                }

                shouldFail = true; // Interaction complete
                return;

            } catch (Exception e) {
                log("Error in NameAnimalInteraction: " + e.getMessage());
                shouldFail = true;
                return;
            }
        }

        if (!shouldFail) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
        }
    }

    private void log(String message) {
        LaitsBreedingPlugin p = LaitsBreedingPlugin.getInstance();
        if (p != null && LaitsBreedingPlugin.isVerboseLogging()) {
            p.getLogger().atInfo().log("[NameAnimal] " + message);
        }
    }

    private boolean isPlayerEntity(Ref<EntityStore> ref) {
        try {
            UUID entityUuid = getUuidFromRef(ref);
            if (entityUuid == null) return false;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return false;

            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                UUID playerUuid = getPlayerUuidFromPlayer(player);
                if (entityUuid.equals(playerUuid)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return false;
    }

    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
                if (uuidComp != null && uuidComp.getUuid() != null) {
                    return uuidComp.getUuid();
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return UUID.nameUUIDFromBytes(ref.toString().getBytes());
    }

    private UUID getPlayerUuidFromPlayer(com.hypixel.hytale.server.core.entity.entities.Player player) {
        try {
            Ref<EntityStore> entityRef = player.getReference();
            if (entityRef instanceof Ref) {
                Store<EntityStore> store = ((Ref<EntityStore>) entityRef).getStore();
                if (store != null) {
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, UUID_TYPE);
                    if (uuidComp != null) {
                        return uuidComp.getUuid();
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    private String getModelAssetIdFromEntity(Ref<EntityStore> targetRef) {
        try {
            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return null;

            ModelComponent modelComp = store.getComponent(targetRef, MODEL_TYPE);
            if (modelComp == null) return null;

            Model model = modelComp.getModel();
            if (model == null) return null;
            
            return model.getModelAssetId();
        } catch (Exception e) {
            return null;
        }
    }

    private UUID getPlayerUuid(InteractionContext context) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return null;
            return getUuidFromRef(entityRef);
        } catch (Exception e) {
            return null;
        }
    }

    private String getPlayerName(InteractionContext context) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return null;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return null;

            UUID playerUuid = getUuidFromRef(entityRef);
            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                UUID pUuid = getPlayerUuidFromPlayer(player);
                if (playerUuid != null && playerUuid.equals(pUuid)) {
                    return player.getDisplayName();
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    private com.hypixel.hytale.server.core.entity.entities.Player getPlayerFromContext(InteractionContext context) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return null;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return null;

            UUID playerUuid = getUuidFromRef(entityRef);
            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                UUID pUuid = getPlayerUuidFromPlayer(player);
                if (playerUuid != null && playerUuid.equals(pUuid)) {
                    return player;
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    private Vector3d getEntityPosition(Ref<EntityStore> targetRef) {
        try {
            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return null;

            TransformComponent transform = store.getComponent(targetRef, TRANSFORM_TYPE);
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    private void sendPlayerMessage(InteractionContext context, String message, String color) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            UUID playerUuid = getUuidFromRef(entityRef);
            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                UUID pUuid = getPlayerUuidFromPlayer(player);
                if (playerUuid != null && playerUuid.equals(pUuid)) {
                    player.sendMessage(Message.raw(message).color(color));
                    return;
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    private void consumePlayerHeldItem(InteractionContext context) {
        try {
            ItemStack heldItem = context.getHeldItem();
            if (heldItem == null) return;

            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            UUID playerUuid = getUuidFromRef(entityRef);
            for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                UUID pUuid = getPlayerUuidFromPlayer(player);
                if (playerUuid != null && playerUuid.equals(pUuid)) {
                    var inventory = ((com.hypixel.hytale.server.core.entity.LivingEntity) player).getInventory();
                    if (inventory != null) {
                        byte activeSlot = inventory.getActiveHotbarSlot();
                        inventory.getHotbar().removeItemStackFromSlot((short) activeSlot, 1);
                        inventory.markChanged();
                        player.sendInventory();
                    }
                    return;
                }
            }
        } catch (Exception e) {
            log("Error consuming item: " + e.getMessage());
        }
    }

    private void playTamingSound(Ref<EntityStore> targetRef) {
        try {
            Vector3d pos = getEntityPosition(targetRef);
            if (pos == null) return;

            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return;

            int soundId = SoundEvent.getAssetMap().getIndex("SFX_UI_Level_Up");
            if (soundId >= 0) {
                SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX,
                    pos.getX(), pos.getY(), pos.getZ(), store);
            }
        } catch (Exception e) {
            // Silent
        }
    }

    private void spawnHeartParticles(Ref<EntityStore> targetRef) {
        try {
            Vector3d position = getEntityPosition(targetRef);
            if (position == null) return;

            double x = position.getX();
            double y = position.getY() + 1.5;
            double z = position.getZ();

            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return;

            Vector3d heartsPos = new Vector3d(x, y, z);
            
            ParticleUtil.spawnParticleEffect("BreedingHearts", heartsPos, store);
        } catch (Exception e) {
            // Silent
        }
    }
}
