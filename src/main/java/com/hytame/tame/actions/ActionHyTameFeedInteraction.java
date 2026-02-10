package com.hytame.tame.actions;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytame.HyTamePlugin;
import com.hytame.managers.BreedingManager;
import com.hytame.managers.TamingManager;
import com.hytame.models.AnimalType;
import com.hytame.models.BreedingData;
import com.hytame.models.CustomAnimalConfig;
import com.hytame.models.GrowthStage;
import com.hytame.models.TamedAnimalData;
import com.hytame.util.ConfigManager;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.util.NameplateUtil;
import com.hytame.util.TameHelper;
import com.hytame.tame.HyTameComponent;
import com.hytame.tame.utils.Debug;

import io.netty.handler.logging.LogLevel;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Action that routes feeding to taming or breeding based on state.
 *
 * Routing Logic:
 * - If animal NOT tamed AND food is taming food -> executeTaming
 * - If animal IS tamed AND food is breeding food -> executeBreeding
 * - Otherwise return false (wrong food for state)
 *
 * Per-Entity State:
 * - HyTameComponent.isActionReady() controls whether action can execute
 * - Disabled during breeding cooldown or love mode
 * - HyTameTickSystem manages this state
 */
public class ActionHyTameFeedInteraction extends ActionBase {
    // Heart particle system ID for breeding (red hearts)
    // Custom particle with shorter duration (extends vanilla Hearts)
    // Asset location: Server/Particles/BreedingHearts.particlesystem
    private static final String HEARTS_PARTICLE = "BreedingHearts";

    // Taming particle system ID (blue hearts)
    // Asset location: Server/Particles/TamingStars.particlesystem
    private static final String TAMING_PARTICLE = "TameHearts";

    // Breeding distance - animals must be within this range to breed
    private static final double BREEDING_DISTANCE = 5.0;

    // protected final Set<String> tamingFood;
    // protected final Set<String> breedingFood;

    public ActionHyTameFeedInteraction(@Nonnull BuilderActionHyTameFeedInteraction builder, @Nonnull BuilderSupport support) {
        super(builder);
        // this.tamingFood = new
        // HashSet<>(Arrays.asList(builder.getTamingFood(support)));
        // this.breedingFood = new
        // HashSet<>(Arrays.asList(builder.getBreedingFood(support)));
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();

        Debug.log("CanExecute called for HyTameFeedInteraction", Level.INFO);
        ModelComponent modelComponent = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
        String modelAssetId = modelComponent.getModel().getModelAssetId();
        AnimalType animalType = modelAssetId != null ? AnimalType.fromModelAssetId(modelAssetId) : null;
        CustomAnimalConfig customAnimal = null;
        ConfigManager configManager = plugin.getConfigManager();

        // If not a known animal type, check for custom animal
        if (animalType == null && modelAssetId != null && configManager != null) {
            customAnimal = configManager.getCustomAnimal(modelAssetId);
            if (customAnimal != null) {
            }
        }
        if (animalType == null) {
            customAnimal = configManager.getCustomAnimal(modelAssetId);
        }

        // Skip if neither breeding nor taming is enabled
        if (animalType != null && (configManager.isBreedingEnabled(animalType)
                || configManager.isTamingEnabled(animalType))) {
            Debug.log("Can execute tame or feed", Level.INFO);
            return true;
        }
        if (customAnimal != null && (customAnimal.isBreedingEnabled() || customAnimal.isTamingEnabled())) {
            Debug.log("Can execute tame or feed", Level.INFO);
            return true;
        }

        Debug.log("Can't execute tame or feed", Level.INFO);
        return false;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        ModelComponent modelComponent = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
        String modelAssetId = modelComponent.getModel().getModelAssetId();
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        ConfigManager configManager = plugin.getConfigManager();
        CustomAnimalConfig customAnimal = null;
        AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);

        if (animalType == null) {
            customAnimal = configManager.getCustomAnimal(modelAssetId);
        }

        Debug.log("ActionHyTameFeedInteraction: execute called", Level.INFO);
        // Get player context
        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) {
            Debug.log("ActionHyTameFeedInteraction: playerRef is null", Level.INFO);
            return false;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent playerUUID = store.getComponent(playerRef, UUIDComponent.getComponentType());
        PlayerRef playerMsgRef = store.getComponent(playerRef, PlayerRef.getComponentType());

        if (player == null || playerUUID == null) {
            Debug.log("ActionHyTameFeedInteraction: player or playerUUID is null", Level.INFO);
            return false;
        }

        // Get HyTameComponent
        HyTameComponent hyTame = store.ensureAndGetComponent(ref, HyTameComponent.getComponentType());
        if (hyTame == null) {
            Debug.log("ActionHyTameFeedInteraction: HyTameComponent not found", Level.INFO);
            return false;
        }

        // Check if action is ready (per-entity state)
        if (!hyTame.isActionReady()) {
            Debug.msg(playerMsgRef, "This animal is busy", Level.INFO);
            return false;
        }

        // Get held item
        String itemId = getHeldItemId(player);
        boolean isTamed = hyTame.isTamed();

        // === BABY CHECK ===
        // Skip babies - they can't be tamed or bred
        if (modelAssetId != null && AnimalType.isBabyVariant(modelAssetId)) {
            Debug.log("Target is a baby variant, skipping feed interaction", Level.INFO);
            return false;
        }
        UUID animalUuid = EcsReflectionUtil.getUuidFromRef(ref);
        if (animalUuid != null) {
            com.hytame.managers.GrowthManager growthMgr = plugin.getGrowthManager();
            if (growthMgr != null && !growthMgr.isFullyGrown(animalUuid)) {
                Debug.log("Target is a growing baby, skipping feed interaction", Level.INFO);
                return false;
            }
            BreedingData babyData = plugin.getBreedingManager() != null
                    ? plugin.getBreedingManager().getData(animalUuid) : null;
            if (babyData != null && babyData.getGrowthStage() != null
                    && babyData.getGrowthStage() != GrowthStage.ADULT) {
                Debug.log("Target is a scaled baby, skipping feed interaction", Level.INFO);
                return false;
            }
        }

        // === ROUTING LOGIC ===
        if (animalType != null) {
            if (!isTamed && configManager.isTamingFood(animalType, itemId)) {
                Debug.log("Executing taming", Level.INFO);
                return executeTaming(ref, role, store, hyTame, playerUUID, player, playerMsgRef);
            } else if (isTamed && configManager.isBreedingFood(animalType, itemId)) {
                Debug.log("Executing breeding", Level.INFO);
                return executeBreeding(ref, store, hyTame, itemId, playerMsgRef);
            }
        } else if (customAnimal != null) {
            if (!isTamed && customAnimal.isTamingEnabled() && customAnimal.isBreedingFood(itemId)) {
                Debug.log("Executing custom animal taming", Level.INFO);
                return executeTaming(ref, role, store, hyTame, playerUUID, player, playerMsgRef);
            } else if (isTamed && customAnimal.isBreedingEnabled() && customAnimal.isBreedingFood(itemId)) {
                Debug.log("Executing custom animal breeding", Level.INFO);
                return executeCustomBreeding(ref, store, modelAssetId, playerMsgRef);
            }
        }

        // Wrong food for current state - don't show message, just fail silently
        // The NPC behavior tree will handle fallback
        Debug.log("ActionHyTameFeedInteraction: wrong food for state (tamed=" + isTamed + ", item=" + itemId + ")",
                Level.INFO);
        return false;
    }

    /**
     * Get the item ID of the player's held item.
     */
    private String getHeldItemId(Player player) {
        try {
            Inventory inventory = player.getInventory();
            if (inventory == null)
                return null;

            byte slot = inventory.getActiveHotbarSlot();
            ItemStack itemStack = inventory.getHotbar().getItemStack(slot);
            if (itemStack == null)
                return null;

            return itemStack.getItemId();
        } catch (Exception e) {
            Debug.log("Error getting held item: " + e.getMessage(), Level.WARNING);
            return null;
        }
    }

    /**
     * Check if the item is valid taming food (case-insensitive).
     */
    // private boolean isTamingFood(String itemId) {
    // if (itemId == null)
    // return false;
    // return tamingFood.stream().anyMatch(f -> f.equalsIgnoreCase(itemId));
    // }

    /**
     * Check if the item is valid breeding food (case-insensitive).
     */
    // private boolean isBreedingFood(String itemId) {
    // if (itemId == null)
    // return false;
    // return breedingFood.stream().anyMatch(f -> f.equalsIgnoreCase(itemId));
    // }

    /**
     * Execute taming logic for a wild animal.
     */
    private boolean executeTaming(Ref<EntityStore> ref, Role role, Store<EntityStore> store,
            HyTameComponent hyTame, UUIDComponent playerUUID,
            Player player, PlayerRef playerMsgRef) {
        // Get NPC entity for spawn tracking
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        NPCEntity npcEntity = store.getComponent(ref, npcType);
        if (npcEntity == null) {
            Debug.msg(playerMsgRef, "Failed to tame: NPC entity not found", Level.WARNING);
            return false;
        }

        // Mark as tamed
        hyTame.setTamed(playerUUID.getUuid(), player.getDisplayName());

        // Set attitude to REVERED (tamed animals are friendly to owner)
        WorldSupport worldSupport = role.getWorldSupport();
        try {
            HyTamePlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);
        } catch (IllegalAccessException e) {
            Debug.msg(playerMsgRef, "Failed to set attitude", Level.SEVERE);
            Debug.log("Attitude set error: " + e.getMessage(), Level.SEVERE);
            return false;
        }

        // Stop spawn tracking (tamed animals don't count toward limits)
        boolean oldState = npcEntity.updateSpawnTrackingState(false);
        if (oldState) {
            Debug.log("Stopped tracking entity " + npcEntity.getRoleName(), Level.INFO);
        }

        // Register with TamingManager for persistence
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && plugin.getTamingManager() != null) {
            UUIDComponent animalUUID = store.getComponent(ref, UUIDComponent.getComponentType());
            if (animalUUID != null) {
                AnimalType animalType = getAnimalTypeFromRef(ref, store);

                // Get world name for multi-world support
                String worldName = null;
                World world = null;
                try {
                    for (java.util.Map.Entry<String, World> entry : com.hypixel.hytale.server.core.universe.Universe
                            .get().getWorlds().entrySet()) {
                        world = entry.getValue();
                        if (world == null)
                            continue;
                        try {
                            Store<EntityStore> worldStore = world.getEntityStore().getStore();
                            if (worldStore == store) {
                                worldName = entry.getKey();
                                Debug.log("[MultiWorld] Taming animal in world: " + worldName, Level.INFO);
                                break;
                            }
                        } catch (Exception e) {
                            // Skip if we can't access this world's store
                        }
                    }
                } catch (Exception e) {
                    Debug.log("Could not get world name for taming: " + e.getMessage(), Level.WARNING);
                }

                // Use tameAnimal with full signature for proper persistence
                plugin.getTamingManager().tameAnimal(
                        hyTame.getHytameId(), // hytameId
                        animalUUID.getUuid(), // animalId
                        playerUUID.getUuid(), // ownerUuid
                        NameplateUtil.UNDEFINED_NAME, // name (unnamed until player uses nametag)
                        animalType, // type
                        ref, // entityRef
                        0, 0, 0, // position (will be updated later)
                        com.hytame.models.GrowthStage.ADULT, // growthStage
                        worldName // worldName for multi-world support
                );
                String tamerName = getPlayerName(playerUUID.getUuid().toString());

                // Capture variables for callback
                final UUID finalTamerUuid = playerUUID.getUuid();
                final String finalTamerName = tamerName;
                final AnimalType finalAnimalType = animalType;
                final Ref<EntityStore> finalTargetRef = ref;
                TamingManager tamingManager = plugin.getTamingManager();

                TameHelper.tameAnimalWithRoleChange(ref, playerUUID.getUuid(), tamerName, world,
                        (hyTameComp) -> {
                            if (hyTameComp != null) {
                                log("Animal tamed successfully via HyTameComponent");
                                // Also register with TamingManager for persistence
                                UUID entityUuid = getUuidFromRef(finalTargetRef);
                                String animalName = NameplateUtil.UNDEFINED_NAME;
                                Vector3d pos = getPositionFromRef(finalTargetRef);
                                double posX = pos != null ? pos.getX() : 0;
                                double posY = pos != null ? pos.getY() : 0;
                                double posZ = pos != null ? pos.getZ() : 0;

                                // Get world name for multi-world support
                                String finalWorldName = getWorldNameFromRef(finalTargetRef);

                                TamedAnimalData tamedData = tamingManager.tameAnimal(
                                        hyTameComp.getHytameId(),
                                        entityUuid,
                                        finalTamerUuid,
                                        animalName,
                                        finalAnimalType,
                                        finalTargetRef,
                                        posX, posY, posZ,
                                        GrowthStage.ADULT,
                                        finalWorldName);

                                // Store owner name for respawn
                                if (tamedData != null) {
                                    tamedData.setOwnerName(finalTamerName);
                                }

                                log("Successfully tamed " + finalAnimalType + " for player "
                                        + finalTamerName);
                            } else {
                                log("Failed to tame animal - HyTameComponent is null");
                            }
                        });

                // Success feedback (show immediately regardless of deferred)
                spawnTamingParticles(ref);
                playFeedingSoundAtPosition(ref);
            }
        }

        Debug.msg(playerMsgRef, npcEntity.getRoleName() + " successfully tamed!", Level.INFO);
        return true;
    }

    /**
     * Get position from an entity ref via TransformComponent.
     */
    private Vector3d getPositionFromRef(Ref<EntityStore> ref) {
        if (ref == null)
            return null;
        try {
            Store<EntityStore> store = ref.getStore();
            if (store == null)
                return null;
            TransformComponent transform = store.getComponent(ref, EcsReflectionUtil.TRANSFORM_TYPE);
            return transform != null ? transform.getPosition() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the player's display name from the interaction context.
     * Since context.getEntity() returns a Ref, we look up the player by UUID.
     * Searches all worlds for multi-world support.
     */
    private String getPlayerName(String playerUuid) {
        try {
            if (playerUuid == null) {
                log("getPlayerName: playerUuid is null");
                return null;
            }

            // Look up the player by UUID from all worlds
            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null)
                    continue;

                for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                    // Compare UUIDs
                    UUID pUuid = getPlayerUuidFromPlayer(player);
                    if (playerUuid.equals(pUuid)) {
                        String name = player.getDisplayName();
                        log("getPlayerName: found player " + name + " in world " + entry.getKey());
                        return name;
                    }
                }
            }
            log("getPlayerName: player not found in any world");
        } catch (Exception e) {
            log("getPlayerName error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get UUID from a Player entity.
     */
    @SuppressWarnings("unchecked")
    private UUID getPlayerUuidFromPlayer(com.hypixel.hytale.server.core.entity.entities.Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                Store<EntityStore> store = ((Ref<EntityStore>) entityRef).getStore();
                if (store != null) {
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef,
                            EcsReflectionUtil.UUID_TYPE);
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

    /**
     * Execute breeding logic for a tamed animal.
     */
    private boolean executeBreeding(Ref<EntityStore> ref, Store<EntityStore> store,
            HyTameComponent hyTame, String itemId, PlayerRef playerMsgRef) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) {
            Debug.log("Plugin is null", Level.WARNING);
            return false;
        }

        BreedingManager breeding = plugin.getBreedingManager();
        if (breeding == null) {
            Debug.log("BreedingManager is null", Level.WARNING);
            return false;
        }

        // Get animal UUID
        UUIDComponent animalUUIDComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (animalUUIDComp == null) {
            Debug.log("Animal UUIDComponent is null", Level.WARNING);
            return false;
        }
        UUID animalId = animalUUIDComp.getUuid();

        // Get AnimalType from ModelComponent
        AnimalType animalType = getAnimalTypeFromRef(ref, store);
        if (animalType == null) {
            Debug.msg(playerMsgRef, "Unknown animal type", Level.WARNING);
            return false;
        }

        // Get world name for multi-world support
        // Search all worlds to find which one contains this entity's store
        String worldName = null;
        try {
            Store<EntityStore> entityStore = store;
            for (java.util.Map.Entry<String, World> entry : com.hypixel.hytale.server.core.universe.Universe.get()
                    .getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null)
                    continue;
                try {
                    Store<EntityStore> worldStore = world.getEntityStore().getStore();
                    if (worldStore == entityStore) {
                        worldName = entry.getKey();
                        Debug.log("[MultiWorld] Found entity in world: " + worldName, Level.INFO);
                        break;
                    }
                } catch (Exception e) {
                    // Skip if we can't access this world's store
                }
            }
            if (worldName == null) {
                Debug.log("[MultiWorld] Entity not found in any world, will use default", Level.WARNING);
            }
        } catch (Exception e) {
            Debug.log("Could not get world name: " + e.getMessage(), Level.WARNING);
        }

        // Delegate to BreedingManager (handles cooldown check internally)
        // Pass the entity ref for heart particle spawning
        BreedingManager.FeedResult result = breeding.tryFeed(animalId, animalType, itemId, ref, worldName);

        
        Debug.log("Tried breeding", Level.INFO);
        Debug.log(result.toString(), Level.INFO);
        switch (result) {
            case SUCCESS:
                spawnHeartParticles(ref);
                checkForMateAndBreedInstantly(breeding, animalId, animalType, ref);
                playFeedingSoundAtPosition(ref);
                break;

            case DISABLED:
            case NOT_ADULT:
            case ON_COOLDOWN:
            case ALREADY_IN_LOVE:
            case WRONG_FOOD:
                return false;
        }

        return true;
    }

    /**
     * Execute breeding logic for a custom animal.
     * Uses BreedingManager's custom animal love mode tracking.
     */
    private boolean executeCustomBreeding(Ref<EntityStore> ref, Store<EntityStore> store,
            String modelAssetId, PlayerRef playerMsgRef) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin == null) return false;

        BreedingManager breeding = plugin.getBreedingManager();
        if (breeding == null) return false;

        UUIDComponent animalUUIDComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (animalUUIDComp == null) return false;
        UUID animalId = animalUUIDComp.getUuid();

        String worldName = getWorldNameFromRef(ref);
        BreedingManager.FeedResult result = breeding.tryFeedCustomAnimal(animalId, modelAssetId, ref, worldName);

        Debug.log("Tried custom breeding: " + result, Level.INFO);
        switch (result) {
            case SUCCESS:
                spawnHeartParticles(ref);
                checkForCustomMateAndBreedInstantly(breeding, animalId, modelAssetId, ref);
                playFeedingSoundAtPosition(ref);
                break;
            default:
                return false;
        }

        return true;
    }

    /**
     * Check if there's a nearby custom animal of the same type in love mode,
     * and breed them instantly if found.
     */
    @SuppressWarnings("unchecked")
    private void checkForCustomMateAndBreedInstantly(
            BreedingManager breeding, UUID animalId, String modelAssetId, Ref<EntityStore> targetRef) {
        Vector3d thisPos = getEntityPosition(targetRef);
        if (thisPos == null) {
            Debug.log("Custom mate check: thisPos is null", Level.INFO);
            return;
        }

        int candidateCount = 0;
        for (BreedingManager.CustomAnimalLoveData otherData : breeding.getCustomAnimalsInLove()) {
            candidateCount++;
            UUID otherId = otherData.getAnimalId();
            if (otherId.equals(animalId)) continue;
            if (!modelAssetId.equals(otherData.getModelAssetId())) {
                Debug.log("Custom mate check: model mismatch - ours: " + modelAssetId + " theirs: " + otherData.getModelAssetId(), Level.INFO);
                continue;
            }

            Ref<EntityStore> otherRef = otherData.getEntityRef();
            if (otherRef == null) continue;

            Vector3d otherPos = getEntityPosition(otherRef);
            if (otherPos == null) continue;

            double distance = calculateDistance(thisPos, otherPos);
            Debug.log("Custom mate check: found mate at distance " + distance + " (max: " + BREEDING_DISTANCE + ")", Level.INFO);
            if (distance > BREEDING_DISTANCE) continue;

            boolean bred = breeding.tryBreedCustomAnimals(animalId, otherId, modelAssetId);
            Debug.log("Custom mate check: tryBreedCustomAnimals result: " + bred, Level.INFO);
            return;
        }
        Debug.log("Custom mate check: no mate found among " + candidateCount + " candidates in love", Level.INFO);
    }

    private void playFeedingSoundAtPosition(Ref<EntityStore> targetRef) {
        try {
            Vector3d pos = getEntityPosition(targetRef);
            if (pos == null) {
                log("playFeedingSoundAtPosition: pos is null");
                return;
            }

            // Use the entity's store directly (already in the correct world)
            Store<EntityStore> store = targetRef.getStore();
            if (store == null) {
                log("playFeedingSoundAtPosition: store is null");
                return;
            }

            int soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
            if (soundId < 0) {
                log("playFeedingSoundAtPosition: soundId < 0, aborting");
                return;
            }

            SoundUtil.playSoundEvent3d(soundId, pos.getX(), pos.getY(), pos.getZ(),
                    p -> true, store);
        } catch (Exception e) {
            log("playFeedingSoundAtPosition error: " + e.getMessage());
        }
    }

    private void spawnHeartParticles(Ref<EntityStore> targetRef) {
        spawnParticles(targetRef, HEARTS_PARTICLE);
    }

    private void spawnTamingParticles(Ref<EntityStore> targetRef) {
        spawnParticles(targetRef, TAMING_PARTICLE);
    }

    private void spawnParticles(Ref<EntityStore> targetRef, String particleId) {
        try {
            Vector3d position = getEntityPosition(targetRef);
            if (position == null)
                return;

            double x = position.getX();
            double y = position.getY() + 1.5;
            double z = position.getZ();

            Store<EntityStore> store = targetRef.getStore();
            Vector3d particlePos = new Vector3d(x, y, z);

            ParticleUtil.spawnParticleEffect(particleId, particlePos, store);
        } catch (Exception e) {
            // Silent
        }
    }

    private void checkForMateAndBreedInstantly(
            BreedingManager breeding,
            UUID animalId,
            AnimalType animalType,
            Ref<EntityStore> targetRef) {
        Vector3d thisPos = getEntityPosition(targetRef);
        if (thisPos == null)
            return;

        BreedingData currentData = breeding.getData(animalId);
        if (currentData != null && currentData.getEntityRef() == null) {
            currentData.setEntityRef(targetRef);
        }

        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        for (UUID otherId : breeding.getTrackedAnimalIds()) {
            if (otherId.equals(animalId))
                continue;

            BreedingData otherData = breeding.getData(otherId);
            if (otherData != null &&
                    otherData.getAnimalType() == animalType &&
                    otherData.isInLove() &&
                    !otherData.isPregnant()) {

                @SuppressWarnings("unchecked")
                Ref<EntityStore> otherRef = (Ref<EntityStore>) otherData.getEntityRef();
                if (otherRef == null)
                    continue;

                Vector3d otherPos = getEntityPosition(otherRef);
                if (otherPos == null) {
                    toRemove.add(otherId);
                    continue;
                }

                double distance = calculateDistance(thisPos, otherPos);
                if (distance > BREEDING_DISTANCE)
                    continue;

                BreedingData animalData = breeding.getData(animalId);
                if (animalData != null) {
                    animalData.completeBreeding();
                }
                otherData.completeBreeding();

                // Set breed cooldown flag for alarm-based system
                if (HyTamePlugin.isAlarmBasedBreedCooldown()) {
                    try {
                        var hyTameType = HyTameComponent.getComponentType();
                        if (hyTameType != null) {
                            HyTameComponent parentTame = targetRef.getStore().getComponent(targetRef, hyTameType);
                            if (parentTame != null) parentTame.setNeedsBreedCooldown(true);
                            HyTameComponent otherTame = otherRef.getStore().getComponent(otherRef, hyTameType);
                            if (otherTame != null) otherTame.setNeedsBreedCooldown(true);
                        }
                    } catch (Exception e) {
                        // Fail safely - cooldown will fall back to Java-based
                    }
                }

                // Spawn baby at midpoint between the two parents
                Vector3d midpoint = new Vector3d(
                        (thisPos.getX() + otherPos.getX()) / 2.0,
                        (thisPos.getY() + otherPos.getY()) / 2.0,
                        (thisPos.getZ() + otherPos.getZ()) / 2.0);
                // Use spawning manager for baby spawning with parent UUIDs for auto-taming
                HyTamePlugin pluginInstance = HyTamePlugin.getInstance();
                if (pluginInstance != null && pluginInstance.getSpawningManager() != null) {
                    // Get world name from parent entity for multi-world support
                    String worldName = getWorldNameFromRef(targetRef);
                    pluginInstance.getSpawningManager().spawnBabyAnimal(animalType, midpoint, animalId, otherId,
                            worldName);
                }
                return;
            }
        }

        for (UUID id : toRemove) {
            breeding.removeData(id);
        }
    }

    private double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private Vector3d getEntityPosition(Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            if (store == null)
                return null;

            TransformComponent transform = store.getComponent(ref, EcsReflectionUtil.TRANSFORM_TYPE);
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Silent - entity may have been removed
        }
        return null;
    }

    /**
     * Get the world name by searching all worlds for the given entity.
     * This is more reliable than trying to get world from store's external data.
     */
    private String getWorldNameFromRef(Ref<EntityStore> ref) {
        if (ref == null)
            return null;

        try {
            UUID entityUuid = getUuidFromRef(ref);
            if (entityUuid == null) {
                log("[WorldDebug] Could not get UUID from ref");
                return null;
            }

            log("[WorldDebug] Searching all worlds for entity UUID: " + entityUuid);

            // Search all worlds for this entity
            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                String worldName = entry.getKey();
                World world = entry.getValue();

                if (world == null)
                    continue;

                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    if (store == null)
                        continue;

                    // Check if this entity exists in this world's store
                    // by comparing the store reference
                    if (ref.getStore() == store) {
                        log("[WorldDebug] Found entity in world: " + worldName);
                        return worldName;
                    }
                } catch (Exception e) {
                    // Skip this world if we can't access its store
                }
            }

            log("[WorldDebug] Entity not found in any world");
        } catch (Exception e) {
            log("getWorldNameFromRef error: " + e.getMessage());
        }
        return null;
    }

    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        // Delegate to EcsReflectionUtil for consistent UUID handling across all systems
        // This uses UUIDComponent first (stable), falling back to ref-based UUID
        return ref != null ? EcsReflectionUtil.getUuidFromRef(ref) : null;
    }

    private void log(String msg) {
        // Only log if verbose logging is enabled
        if (!HyTamePlugin.isVerboseLogging())
            return;

        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[FeedAnimal] " + msg);
        }
    }

    /**
     * Get the AnimalType from an entity reference.
     */
    private AnimalType getAnimalTypeFromRef(Ref<EntityStore> ref, Store<EntityStore> store) {
        String modelAssetId = getModelAssetIdFromRef(ref, store);
        return modelAssetId != null ? AnimalType.fromModelAssetId(modelAssetId) : null;
    }

    /**
     * Get the model asset ID from an entity reference.
     */
    private String getModelAssetIdFromRef(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            ModelComponent modelComp = store.getComponent(ref, ModelComponent.getComponentType());
            if (modelComp == null)
                return null;

            Model model = modelComp.getModel();
            if (model != null) {
                return model.getModelAssetId();
            }
        } catch (Exception e) {
            Debug.log("Error getting model asset ID: " + e.getMessage(), Level.INFO);
        }
        return null;
    }
}
