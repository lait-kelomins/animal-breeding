package com.tameableanimals.actions;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.models.AnimalType;
import com.tameableanimals.tame.HyTameComponent;
import com.tameableanimals.utils.Debug;

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
public class ActionHyTameOrFeed extends ActionBase {
    protected final Set<String> tamingFood;
    protected final Set<String> breedingFood;

    public ActionHyTameOrFeed(@Nonnull BuilderActionHyTameOrFeed builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.tamingFood = new HashSet<>(Arrays.asList(builder.getTamingFood(support)));
        this.breedingFood = new HashSet<>(Arrays.asList(builder.getBreedingFood(support)));
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Debug.log("ActionHyTameOrFeed: execute called", Level.INFO);
        // Get player context
        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) {
            Debug.log("ActionHyTameOrFeed: playerRef is null", Level.INFO);
            return false;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent playerUUID = store.getComponent(playerRef, UUIDComponent.getComponentType());
        PlayerRef playerMsgRef = store.getComponent(playerRef, PlayerRef.getComponentType());

        if (player == null || playerUUID == null) {
            Debug.log("ActionHyTameOrFeed: player or playerUUID is null", Level.INFO);
            return false;
        }

        // Get HyTameComponent
        HyTameComponent hyTame = store.getComponent(ref, HyTameComponent.getComponentType());
        if (hyTame == null) {
            Debug.log("ActionHyTameOrFeed: HyTameComponent not found", Level.INFO);
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

        // === ROUTING LOGIC ===
        if (!isTamed && isTamingFood(itemId)) {
            return executeTaming(ref, role, store, hyTame, playerUUID, player, playerMsgRef);
        } else if (isTamed && isBreedingFood(itemId)) {
            return executeBreeding(ref, store, hyTame, playerMsgRef);
        } else {
            // Wrong food for current state - don't show message, just fail silently
            // The NPC behavior tree will handle fallback
            Debug.log("ActionHyTameOrFeed: wrong food for state (tamed=" + isTamed + ", item=" + itemId + ")",
                    Level.INFO);
            return false;
        }
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
    private boolean isTamingFood(String itemId) {
        if (itemId == null)
            return false;
        return tamingFood.stream().anyMatch(f -> f.equalsIgnoreCase(itemId));
    }

    /**
     * Check if the item is valid breeding food (case-insensitive).
     */
    private boolean isBreedingFood(String itemId) {
        if (itemId == null)
            return false;
        return breedingFood.stream().anyMatch(f -> f.equalsIgnoreCase(itemId));
    }

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

        // Set attitude to REVERED
        WorldSupport worldSupport = role.getWorldSupport();
        try {
            LaitsBreedingPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);
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
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && plugin.getTamingManager() != null) {
            UUIDComponent animalUUID = store.getComponent(ref, UUIDComponent.getComponentType());
            if (animalUUID != null) {
                AnimalType animalType = getAnimalTypeFromRef(ref, store);

                // Get world name for multi-world support
                String worldName = null;
                try {
                    for (java.util.Map.Entry<String, World> entry : com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().entrySet()) {
                        World world = entry.getValue();
                        if (world == null) continue;
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
                        npcEntity.getRoleName(), // name (use role name as default)
                        animalType, // type
                        ref, // entityRef
                        0, 0, 0, // position (will be updated later)
                        com.laits.breeding.models.GrowthStage.ADULT, // growthStage
                        worldName // worldName for multi-world support
                );
            }
        }

        Debug.msg(playerMsgRef, npcEntity.getRoleName() + " successfully tamed!", Level.INFO);
        return true;
    }

    /**
     * Execute breeding logic for a tamed animal.
     */
    private boolean executeBreeding(Ref<EntityStore> ref, Store<EntityStore> store,
            HyTameComponent hyTame, PlayerRef playerMsgRef) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) {
            Debug.log("Plugin is null", Level.WARNING);
            return false;
        }

        BreedingManager manager = plugin.getBreedingManager();
        if (manager == null) {
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
            for (java.util.Map.Entry<String, World> entry : com.hypixel.hytale.server.core.universe.Universe.get().getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null) continue;
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
        BreedingManager.FeedResult result = manager.tryFeed(animalId, animalType, "food", ref, worldName);

        switch (result) {
            case SUCCESS:
                // Disable action while in love mode
                hyTame.setActionReady(false);
                Debug.msg(playerMsgRef, "Animal is in love!", Level.INFO);
                return true;

            case ON_COOLDOWN:
                // Shouldn't happen if HyTameTickSystem is working, but handle gracefully
                Debug.msg(playerMsgRef, "Animal needs to rest first", Level.INFO);
                return false;

            case ALREADY_IN_LOVE:
                Debug.msg(playerMsgRef, "Animal is already in love!", Level.INFO);
                return false;

            case NOT_ADULT:
                Debug.msg(playerMsgRef, "Baby animals cannot breed", Level.INFO);
                return false;

            case DISABLED:
                Debug.msg(playerMsgRef, "Breeding is disabled for this animal", Level.INFO);
                return false;

            case WRONG_FOOD:
                // Shouldn't happen since we checked food type already
                return false;

            default:
                return false;
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
