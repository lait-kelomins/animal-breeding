package com.laits.breeding.handlers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.effects.EffectsManager;
import com.laits.breeding.managers.InteractionSetupManager;
import com.laits.breeding.listeners.CoopResidentTracker;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.util.EntityUtil;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles mouse click and player interaction events for breeding.
 * Extracted from LaitsBreedingPlugin to reduce plugin size.
 */
public class MouseInteractionHandler {

    private final ConfigManager configManager;
    private final BreedingManager breedingManager;
    private final EffectsManager effectsManager;
    private final InteractionSetupManager interactionSetupManager;
    private TamingManager tamingManager;

    private boolean verboseLogging = false;
    private Consumer<String> logger = msg -> {};

    public MouseInteractionHandler(ConfigManager configManager, BreedingManager breedingManager,
                                   EffectsManager effectsManager, InteractionSetupManager interactionSetupManager) {
        this.configManager = configManager;
        this.breedingManager = breedingManager;
        this.effectsManager = effectsManager;
        this.interactionSetupManager = interactionSetupManager;
    }

    public void setTamingManager(TamingManager tamingManager) {
        this.tamingManager = tamingManager;
    }

    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String message) {
        if (verboseLogging) {
            logger.accept(message);
        }
    }

    /**
     * Handle mouse button events. Call from plugin's event handler.
     */
    public void onMouseButton(PlayerMouseButtonEvent event) {
        try {
            handleMouseClick(event);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Handle player interact events. Call from plugin's event handler.
     * Sets up interactions immediately when player interacts with an animal.
     * Also detects capture crate usage on tamed animals.
     */
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            Entity targetEntity = event.getTargetEntity();
            if (targetEntity == null)
                return;

            // Skip if target is a Player
            if (targetEntity instanceof Player) {
                return;
            }

            Player player = event.getPlayer();
            UUID entityId = EntityUtil.getEntityUUID(targetEntity);

            // Check for capture crate usage on tamed animal
            if (player != null && entityId != null && tamingManager != null) {
                if (tamingManager.isTamed(entityId)) {
                    // Check if player is holding a capture crate
                    try {
                        var inventory = player.getInventory();
                        if (inventory != null) {
                            var heldItem = inventory.getActiveHotbarItem();
                            if (heldItem != null) {
                                String itemId = heldItem.getItemId();
                                if (CoopResidentTracker.getCaptureCrateItemId().equals(itemId)) {
                                    // Register pending capture - will be consumed in EntityRemoveEvent
                                    UUID playerUuid = EntityUtil.getEntityUUID(player);
                                    if (playerUuid != null) {
                                        CoopResidentTracker.registerPendingCapture(entityId, playerUuid);
                                        log("[CaptureCrate] Registered pending capture: animal=" + entityId + " player=" + playerUuid);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Silent - capture detection is best-effort
                    }
                }
            }

            String entityName = EntityUtil.getEntityModelId(targetEntity);
            AnimalType animalType = AnimalType.fromEntityTypeId(entityName);
            if (animalType == null)
                return;

            // Check if enabled
            if (!configManager.isAnimalEnabled(animalType))
                return;

            // Set up interaction for this animal
            Object entityRef = EntityUtil.getEntityRef(targetEntity);
            if (entityRef != null && entityRef instanceof Ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                World world = targetEntity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    interactionSetupManager.setupEntityInteractions(store, ref, animalType);
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Handle mouse clicks for breeding and taming.
     */
    private void handleMouseClick(PlayerMouseButtonEvent event) {
        Player player = event.getPlayer();

        // Only handle right-click
        if (event.getMouseButton().mouseButtonType != MouseButtonType.Right) {
            return;
        }

        // Check if interacting with an entity
        Entity targetEntity = event.getTargetEntity();
        if (targetEntity == null) {
            return;
        }

        // Skip if target is a Player (prevents treating players with animal models as
        // animals)
        if (targetEntity instanceof Player) {
            return;
        }

        // Debug log
        log("[TamingDebug] handleMouseClick triggered on entity");

        // Get held item early for taming check
        Item heldItem = event.getItemInHand();
        String itemId = heldItem != null ? heldItem.getId() : null;

        // Note: Taming is now handled via NameAnimalInteraction with UI (using Name Tag
        // item)

        // Get entity model ID to determine type (via ECS ModelComponent)
        String entityName = EntityUtil.getEntityModelId(targetEntity);

        // Try to identify animal type from entity name
        AnimalType animalType = AnimalType.fromEntityTypeId(entityName);
        if (animalType == null) {
            return; // Not a breedable animal
        }

        // Ensure interaction is set up for this animal (in case periodic scan hasn't
        // run yet)
        try {
            Object entityRef = EntityUtil.getEntityRef(targetEntity);
            if (entityRef != null && entityRef instanceof Ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                World world = targetEntity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    interactionSetupManager.setupEntityInteractions(store, ref, animalType);
                }
            }
        } catch (Exception e) {
            // Silent - interaction setup is best-effort
        }

        // Check held item (already got it earlier for taming)
        if (heldItem == null) {
            return;
        }

        // Get UUID for this entity (via ECS UUIDComponent)
        UUID entityId = EntityUtil.getEntityUUID(targetEntity);

        // Try to feed the animal
        BreedingManager.FeedResult result = breedingManager.tryFeed(entityId, animalType, itemId);

        // Store the entity ref for position tracking (needed for distance-based
        // breeding)
        if (result == BreedingManager.FeedResult.SUCCESS || result == BreedingManager.FeedResult.ALREADY_IN_LOVE) {
            Ref<EntityStore> entityRef = EntityUtil.getEntityRef(targetEntity);
            if (entityRef != null) {
                BreedingData data = breedingManager.getData(entityId);
                if (data != null && data.getEntityRef() == null) {
                    data.setEntityRef(entityRef);
                }
            }
        }

        // Send chat feedback to player
        log("Feed result for " + animalType.getId() + ": " + result);

        if (player != null) {
            switch (result) {
                case SUCCESS:
                    player.sendMessage(Message
                            .raw("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) + " is now in love!"));
                    // Note: Sound and item consumption handled by FeedAnimal interaction
                    effectsManager.spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case ALREADY_IN_LOVE:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is already in love!"));
                    effectsManager.spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case WRONG_FOOD:
                    java.util.List<String> validFoods = configManager.getBreedingFoods(animalType);
                    String foodList = String.join(", ", validFoods);
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] Wrong food! " + capitalize(animalType.getId())
                            + " needs: " + foodList));
                    break;
                case DISABLED:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] Breeding is disabled for " + animalType.getId() + "s"));
                    break;
                case NOT_ADULT:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is too young to breed"));
                    break;
                case ON_COOLDOWN:
                    BreedingData data = breedingManager.getData(entityId);
                    if (data != null) {
                        long remaining = data.getCooldownRemaining(configManager.getBreedingCooldown(animalType));
                        player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId()
                                + " needs to rest (" + (remaining / 1000) + "s)"));
                    } else {
                        player.sendMessage(
                                Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " needs to rest"));
                    }
                    break;
            }
        }
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
