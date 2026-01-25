package com.laits.breeding.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Custom interaction that fires when player feeds an animal.
 * Implements instant breeding with heart particles and baby spawning.
 */
public class FeedAnimalInteraction extends SimpleInteraction {

    public static final BuilderCodec<FeedAnimalInteraction> CODEC =
        BuilderCodec.builder(FeedAnimalInteraction.class, FeedAnimalInteraction::new, SimpleInteraction.CODEC)
            .build();

    // Heart particle system ID
    // Custom particle with shorter duration (extends vanilla Hearts)
    // Asset location: Server/Particles/BreedingHearts.particlesystem
    private static final String HEARTS_PARTICLE = "BreedingHearts";

    // Breeding distance - animals must be within this range to breed
    private static final double BREEDING_DISTANCE = 5.0;

    // Cached component types for performance
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = UUIDComponent.getComponentType();

    // Cached reflection Field for model extraction (avoid per-call reflection)
    private static Field cachedModelField = null;
    private static boolean modelFieldInitialized = false;

    static {
        try {
            cachedModelField = ModelComponent.class.getDeclaredField("model");
            cachedModelField.setAccessible(true);
            modelFieldInitialized = true;
        } catch (Exception e) {
            modelFieldInitialized = false;
        }
    }

    public FeedAnimalInteraction() {
        super();
    }

    // Track if the interaction should fail (conditions not met)
    private boolean shouldFail = false;
    // Store target ref for fallback triggering
    private Ref<EntityStore> failedTargetRef = null;

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        InteractionType type,
        InteractionContext context,
        CooldownHandler cooldownHandler
    ) {
        // Log when interaction is triggered (only in verbose mode)
        if (firstRun && LaitsBreedingPlugin.isVerboseLogging()) {
            LaitsBreedingPlugin p = LaitsBreedingPlugin.getInstance();
            if (p != null) {
                p.getLogger().atInfo().log("[FeedAnimal] tick0 triggered! firstRun=%s, type=%s", firstRun, type);
            }
        }

        if (firstRun) {
            shouldFail = false;
            failedTargetRef = null;

            try {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null) {
                    shouldFail = true;
                    return;
                }

                BreedingManager breeding = plugin.getBreedingManager();
                if (breeding == null) {
                    shouldFail = true;
                    return;
                }

                Ref<EntityStore> targetRef = context.getTargetEntity();
                if (targetRef == null) {
                    log("targetRef is null");
                    shouldFail = true;
                    return;
                }

                // Safety check: Skip if target is a player (prevents treating players with animal models as animals)
                // Option B: Just return without triggering fallback - let native Hytale behavior (mounting, etc.) work
                if (isPlayerEntity(targetRef)) {
                    log("Target is a player entity, skipping FeedAnimal interaction");
                    shouldFail = true;
                    return;
                }

                ItemStack heldItem = context.getHeldItem();
                String itemId = heldItem != null ? heldItem.getItemId() : null;
                log("Held item: " + (heldItem != null ? heldItem.getClass().getSimpleName() : "null") + ", itemId: " + itemId);

                // Get model asset ID and check for both enum animals and custom animals
                String modelAssetId = getModelAssetIdFromEntity(targetRef);
                AnimalType animalType = modelAssetId != null ? AnimalType.fromModelAssetId(modelAssetId) : null;
                CustomAnimalConfig customAnimal = null;

                log("Model asset ID: " + modelAssetId);
                log("Animal type: " + (animalType != null ? animalType.name() : "null"));

                // If not a known animal type, check for custom animal
                if (animalType == null && modelAssetId != null && plugin.getConfigManager() != null) {
                    customAnimal = plugin.getConfigManager().getCustomAnimal(modelAssetId);
                    if (customAnimal != null) {
                        log("Found custom animal: " + customAnimal.getDisplayName());
                    }
                }

                if (animalType == null && customAnimal == null) {
                    log("Not a breedable animal, triggering fallback");
                    shouldFail = true;
                    failedTargetRef = targetRef;
                    triggerFallbackInteraction(context, targetRef);
                    return;
                }

                // Skip babies - they can't breed
                if (modelAssetId != null && AnimalType.isBabyVariant(modelAssetId)) {
                    log("Target is a baby animal, skipping feed interaction");
                    shouldFail = true;
                    return;
                }

                // Check interaction permission for tamed animals
                // Note: Taming is now handled via NameAnimalInteraction with UI
                TamingManager tamingManager = plugin.getTamingManager();
                UUID playerUuid = getPlayerUuid(context);

                if (tamingManager != null && playerUuid != null) {
                    UUID animalUuid = getUuidFromRef(targetRef);
                    if (!tamingManager.canPlayerInteract(animalUuid, playerUuid)) {
                        TamedAnimalData tamedData = tamingManager.getTamedData(animalUuid);
                        String ownerName = tamedData != null ? tamedData.getOwnerUuid().toString().substring(0, 8) + "..." : "someone";
                        sendPlayerMessage(context, "This animal belongs to " + ownerName + "!", "#FF5555");
                        shouldFail = true;
                        return;
                    }
                }

                // Check if holding correct food (for both regular and custom animals)
                boolean isCorrectFood = false;
                if (animalType != null) {
                    // Regular animal type
                    if (plugin.getConfigManager() != null) {
                        isCorrectFood = itemId != null && plugin.getConfigManager().isBreedingFood(animalType, itemId);
                        log("Valid foods: " + plugin.getConfigManager().getBreedingFoods(animalType) + ", isCorrectFood: " + isCorrectFood);
                    } else {
                        isCorrectFood = itemId != null && animalType.isBreedingFood(itemId);
                        log("Expected food (fallback): " + animalType.getBreedingFood() + ", isCorrectFood: " + isCorrectFood);
                    }
                } else if (customAnimal != null) {
                    // Custom animal from config
                    isCorrectFood = itemId != null && customAnimal.isBreedingFood(itemId);
                    log("Custom animal valid foods: " + customAnimal.getBreedingFoods() + ", isCorrectFood: " + isCorrectFood);
                }

                if (!isCorrectFood) {
                    log("Wrong food or no item, triggering fallback");
                    shouldFail = true;
                    failedTargetRef = targetRef;
                    triggerFallbackInteraction(context, targetRef, animalType);
                    return;
                }

                log("Correct food! Proceeding with breeding");

                UUID animalId = getUuidFromRef(targetRef);

                // Handle breeding differently for regular vs custom animals
                if (animalType != null) {
                    // Regular animal - use full breeding system
                    BreedingManager.FeedResult result = breeding.tryFeed(animalId, animalType, itemId, targetRef);

                    switch (result) {
                        case SUCCESS:
                            spawnHeartParticles(targetRef);
                            checkForMateAndBreedInstantly(breeding, animalId, animalType, targetRef);
                            playSoundAndConsumeItem(plugin, context, targetRef);
                            break;

                        case ALREADY_IN_LOVE:
                            spawnHeartParticles(targetRef);
                            playSoundAndConsumeItem(plugin, context, targetRef);
                            break;

                        case ON_COOLDOWN:
                        case NOT_ADULT:
                            shouldFail = true;
                            failedTargetRef = targetRef;
                            return;

                        case WRONG_FOOD:
                            shouldFail = true;
                            failedTargetRef = targetRef;
                            triggerFallbackInteraction(context, targetRef, animalType);
                            return;
                    }
                } else if (customAnimal != null) {
                    // Custom animal - use full breeding system
                    log("Feeding custom animal: " + customAnimal.getDisplayName());

                    BreedingManager.FeedResult result = breeding.tryFeedCustomAnimal(animalId, modelAssetId, targetRef);

                    switch (result) {
                        case SUCCESS:
                            spawnHeartParticles(targetRef);
                            checkForCustomAnimalMateAndBreed(breeding, animalId, modelAssetId, targetRef);
                            playSoundAndConsumeItem(plugin, context, targetRef);
                            break;

                        case ALREADY_IN_LOVE:
                            spawnHeartParticles(targetRef);
                            playSoundAndConsumeItem(plugin, context, targetRef);
                            break;

                        case ON_COOLDOWN:
                            shouldFail = true;
                            failedTargetRef = targetRef;
                            return;

                        default:
                            break;
                    }
                }

            } catch (Exception e) {
                shouldFail = true;
                return;
            }
        }

        if (!shouldFail) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
        }
    }

    /**
     * Trigger the original/fallback interaction for an entity (e.g., horse mounting).
     */
    private void triggerFallbackInteraction(InteractionContext context, Ref<EntityStore> targetRef) {
        triggerFallbackInteraction(context, targetRef, null);
    }

    /**
     * Trigger the original/fallback interaction for an entity (e.g., horse mounting).
     * Accepts animal type for robust fallback when cache lookup fails.
     */
    private void triggerFallbackInteraction(InteractionContext context, Ref<EntityStore> targetRef, AnimalType animalType) {
        try {
            String originalInteractionId = LaitsBreedingPlugin.getOriginalInteractionId(targetRef, animalType);
            log("Looking up fallback for targetRef: " + targetRef + " (animalType: " + animalType + ")");
            log("Original interaction ID: " + (originalInteractionId != null ? originalInteractionId : "NOT FOUND"));

            if (originalInteractionId == null || originalInteractionId.isEmpty()) {
                log("No fallback interaction found for entity - interaction will just end");
                return;
            }

            log("Triggering fallback interaction: " + originalInteractionId);

            // Check if server is shutting down - Universe or World may be null/unavailable
            if (Universe.get() == null || Universe.get().getDefaultWorld() == null) {
                log("Server is shutting down, skipping fallback interaction");
                return;
            }

            Object interactionManager = context.getInteractionManager();
            if (interactionManager == null) {
                log("InteractionManager is null");
                return;
            }

            // Wrap in try-catch to handle shutdown race conditions
            RootInteraction rootInteraction;
            try {
                rootInteraction = RootInteraction.getRootInteractionOrUnknown(originalInteractionId);
            } catch (Exception e) {
                log("Failed to get RootInteraction (likely server shutting down): " + e.getMessage());
                return;
            }
            if (rootInteraction == null) {
                log("Could not find RootInteraction: " + originalInteractionId);
                return;
            }

            // Try startChain via reflection (method signature varies)
            for (java.lang.reflect.Method m : interactionManager.getClass().getMethods()) {
                if (m.getName().equals("startChain") && m.getParameterCount() == 5) {
                    try {
                        Ref<EntityStore> entityRef = context.getEntity();
                        Object commandBuffer = context.getCommandBuffer();

                        World world = Universe.get().getDefaultWorld();
                        if (world != null) {
                            final java.lang.reflect.Method startChainMethod = m;
                            final Object finalInteractionManager = interactionManager;
                            final Object finalCommandBuffer = commandBuffer;

                            world.execute(() -> {
                                try {
                                    startChainMethod.invoke(finalInteractionManager, entityRef, finalCommandBuffer,
                                        InteractionType.Use, context, rootInteraction);
                                    log("Successfully triggered fallback via startChain!");
                                } catch (Exception ex) {
                                    log("Scheduled startChain failed: " + ex.getMessage());
                                }
                            });
                            log("Scheduled fallback interaction for next tick");
                            return;
                        }
                    } catch (Exception e) {
                        log("startChain failed: " + e.getMessage());
                    }
                }
            }

            log("Could not find working method to trigger fallback interaction");

        } catch (Exception e) {
            log("triggerFallbackInteraction error: " + e.getMessage());
        }
    }

    private void playSoundAndConsumeItem(LaitsBreedingPlugin plugin, InteractionContext context, Ref<EntityStore> targetRef) {
        try {
            log("playSoundAndConsumeItem called");
            playFeedingSoundAtPosition(targetRef);
            consumePlayerHeldItem(context);
        } catch (Exception e) {
            log("playSoundAndConsumeItem error: " + e.getMessage());
        }
    }

    private void log(String msg) {
        // Only log if verbose logging is enabled
        if (!LaitsBreedingPlugin.isVerboseLogging()) return;

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[FeedAnimal] " + msg);
        }
    }

    private void playFeedingSoundAtPosition(Ref<EntityStore> targetRef) {
        try {
            log("playFeedingSoundAtPosition called");

            Vector3d pos = getEntityPosition(targetRef);
            if (pos == null) {
                log("playFeedingSoundAtPosition: pos is null");
                return;
            }
            log("playFeedingSoundAtPosition: pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ());

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                log("playFeedingSoundAtPosition: world is null");
                return;
            }

            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) {
                log("playFeedingSoundAtPosition: store is null");
                return;
            }

            int soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
            log("playFeedingSoundAtPosition: soundId=" + soundId);
            if (soundId < 0) {
                log("playFeedingSoundAtPosition: soundId < 0, aborting");
                return;
            }

            // Play 3D sound - use reflection for the exact method signature
            try {
                for (java.lang.reflect.Method m : SoundUtil.class.getMethods()) {
                    if (m.getName().equals("playSoundEvent3d") && m.getParameterCount() == 6) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes[0] == int.class && paramTypes[1] == double.class) {
                            Predicate<Object> allPlayers = p -> true;
                            m.invoke(null, soundId, pos.getX(), pos.getY(), pos.getZ(), allPlayers, store);
                            log("playFeedingSoundAtPosition: sound played successfully");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                log("playFeedingSoundAtPosition: playSoundEvent3d failed: " + e.getMessage());
            }

        } catch (Exception e) {
            log("playFeedingSoundAtPosition error: " + e.getMessage());
        }
    }

    private void consumePlayerHeldItem(InteractionContext context) {
        try {
            log("consumePlayerHeldItem called");

            Object container = context.getHeldItemContainer();
            if (container != null) {
                log("consumePlayerHeldItem: got container, class=" + container.getClass().getName());
                short slot = context.getHeldItemSlot();
                log("consumePlayerHeldItem: slot=" + slot);

                // Try removeItemStackFromSlot
                try {
                    java.lang.reflect.Method removeItem = container.getClass().getMethod(
                        "removeItemStackFromSlot", short.class, int.class);
                    removeItem.invoke(container, slot, 1);
                    log("consumePlayerHeldItem: removed 1 item via removeItemStackFromSlot");
                    return;
                } catch (NoSuchMethodException ex) {
                    log("consumePlayerHeldItem: removeItemStackFromSlot not found");
                }

                // Try other methods...
                try {
                    java.lang.reflect.Method removeItem = container.getClass().getMethod(
                        "removeItem", short.class, int.class);
                    removeItem.invoke(container, slot, 1);
                    log("consumePlayerHeldItem: removed 1 item via removeItem");
                    return;
                } catch (NoSuchMethodException ignored) {}
            }

            // Fallback to ItemStack modification
            ItemStack heldItem = context.getHeldItem();
            if (heldItem != null) {
                log("consumePlayerHeldItem: trying direct ItemStack modification");
                int currentQty = heldItem.getQuantity();

                try {
                    java.lang.reflect.Method m = heldItem.getClass().getMethod("adjustQuantity", int.class);
                    m.invoke(heldItem, -1);
                    log("consumePlayerHeldItem: called adjustQuantity(-1)");
                    return;
                } catch (NoSuchMethodException ignored) {}

                try {
                    java.lang.reflect.Method m = heldItem.getClass().getMethod("setQuantity", int.class);
                    m.invoke(heldItem, currentQty - 1);
                    log("consumePlayerHeldItem: called setQuantity(" + (currentQty - 1) + ")");
                    return;
                } catch (NoSuchMethodException ignored) {}
            }

            log("consumePlayerHeldItem: could not consume item");

        } catch (Exception e) {
            log("consumePlayerHeldItem error: " + e.getMessage());
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
            Vector3d heartsPos = new Vector3d(x, y, z);

            // Use reflection to find the right overload
            for (java.lang.reflect.Method method : ParticleUtil.class.getMethods()) {
                if (method.getName().equals("spawnParticleEffect") && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == String.class && params[1] == Vector3d.class) {
                        method.invoke(null, HEARTS_PARTICLE, heartsPos, store);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    private Vector3d getEntityPosition(Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            if (store == null) return null;

            TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Silent - entity may have been removed
        }
        return null;
    }

    private void checkForMateAndBreedInstantly(
        BreedingManager breeding,
        UUID animalId,
        AnimalType animalType,
        Ref<EntityStore> targetRef
    ) {
        Vector3d thisPos = getEntityPosition(targetRef);
        if (thisPos == null) return;

        BreedingData currentData = breeding.getData(animalId);
        if (currentData != null && currentData.getEntityRef() == null) {
            currentData.setEntityRef(targetRef);
        }

        java.util.List<UUID> toRemove = new java.util.ArrayList<>();

        for (UUID otherId : breeding.getTrackedAnimalIds()) {
            if (otherId.equals(animalId)) continue;

            BreedingData otherData = breeding.getData(otherId);
            if (otherData != null &&
                otherData.getAnimalType() == animalType &&
                otherData.isInLove() &&
                !otherData.isPregnant()) {

                @SuppressWarnings("unchecked")
                Ref<EntityStore> otherRef = (Ref<EntityStore>) otherData.getEntityRef();
                if (otherRef == null) continue;

                Vector3d otherPos = getEntityPosition(otherRef);
                if (otherPos == null) {
                    toRemove.add(otherId);
                    continue;
                }

                double distance = calculateDistance(thisPos, otherPos);
                if (distance > BREEDING_DISTANCE) continue;

                BreedingData animalData = breeding.getData(animalId);
                if (animalData != null) {
                    animalData.completeBreeding();
                }
                otherData.completeBreeding();

                // Spawn baby at midpoint between the two parents
                Vector3d midpoint = new Vector3d(
                    (thisPos.getX() + otherPos.getX()) / 2.0,
                    (thisPos.getY() + otherPos.getY()) / 2.0,
                    (thisPos.getZ() + otherPos.getZ()) / 2.0
                );
                // Use plugin's spawnBabyAnimal with parent UUIDs for auto-taming
                LaitsBreedingPlugin pluginInstance = LaitsBreedingPlugin.getInstance();
                if (pluginInstance != null) {
                    pluginInstance.spawnBabyAnimal(animalType, midpoint, animalId, otherId);
                }
                return;
            }
        }

        for (UUID id : toRemove) {
            breeding.removeData(id);
        }
    }

    /**
     * Check for nearby custom animals of the same type in love mode and breed them.
     */
    private void checkForCustomAnimalMateAndBreed(
        BreedingManager breeding,
        UUID animalId,
        String modelAssetId,
        Ref<EntityStore> targetRef
    ) {
        Vector3d thisPos = getEntityPosition(targetRef);
        if (thisPos == null) return;

        // Update entity ref in love data
        BreedingManager.CustomAnimalLoveData currentData = breeding.getCustomAnimalLoveData(animalId);
        if (currentData != null && currentData.getEntityRef() == null) {
            currentData.setEntityRef(targetRef);
        }

        // Find another custom animal of the same type in love mode
        for (BreedingManager.CustomAnimalLoveData otherData : breeding.getCustomAnimalsInLove()) {
            if (otherData.getAnimalId().equals(animalId)) continue;
            if (!otherData.getModelAssetId().equals(modelAssetId)) continue;
            if (!otherData.isInLove()) continue;

            @SuppressWarnings("unchecked")
            Ref<EntityStore> otherRef = (Ref<EntityStore>) otherData.getEntityRef();
            if (otherRef == null) continue;

            Vector3d otherPos = getEntityPosition(otherRef);
            if (otherPos == null) continue;

            double distance = calculateDistance(thisPos, otherPos);
            if (distance > BREEDING_DISTANCE) continue;

            // Found a mate! Breed them
            log("Custom animals breeding: " + modelAssetId + " at distance " + distance);

            // Complete breeding for both
            if (currentData != null) currentData.completeBreeding();
            otherData.completeBreeding();

            // Spawn baby at midpoint between the two parents
            Vector3d midpoint = new Vector3d(
                (thisPos.getX() + otherPos.getX()) / 2.0,
                (thisPos.getY() + otherPos.getY()) / 2.0,
                (thisPos.getZ() + otherPos.getZ()) / 2.0
            );

            // Get the custom animal config for spawning
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            CustomAnimalConfig customConfig = plugin != null ?
                plugin.getConfigManager().getCustomAnimal(modelAssetId) : null;
            spawnCustomAnimalBaby(modelAssetId, customConfig, midpoint);
            return;
        }
    }

    /**
     * Spawn a baby custom animal at the given position.
     * If babyNpcRoleId is set, spawn using that role at full scale.
     * Otherwise, use scaling fallback: spawn adult NPC at 40% scale.
     */
    private void spawnCustomAnimalBaby(String modelAssetId, CustomAnimalConfig customConfig, Vector3d position) {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            final String finalModelAssetId = modelAssetId;
            final CustomAnimalConfig finalConfig = customConfig;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    NPCPlugin npcPlugin = NPCPlugin.get();

                    int roleIndex = -1;
                    String usedRoleName = null;
                    boolean usingBabyRole = false;

                    // 1. First, check if we have a dedicated baby NPC role
                    if (finalConfig != null && finalConfig.getBabyNpcRoleId() != null) {
                        roleIndex = npcPlugin.getIndex(finalConfig.getBabyNpcRoleId());
                        if (roleIndex >= 0) {
                            usedRoleName = finalConfig.getBabyNpcRoleId();
                            usingBabyRole = true;
                            log("Using dedicated baby NPC role: " + usedRoleName);
                        } else {
                            log("Configured babyNpcRoleId not found: " + finalConfig.getBabyNpcRoleId() + ", falling back to scaling");
                        }
                    }

                    // 2. If no baby role, use adult role with scaling fallback
                    if (roleIndex < 0 && finalConfig != null && finalConfig.getAdultNpcRoleId() != null) {
                        roleIndex = npcPlugin.getIndex(finalConfig.getAdultNpcRoleId());
                        if (roleIndex >= 0) {
                            usedRoleName = finalConfig.getAdultNpcRoleId();
                            log("Using adult NPC role with scaling: " + usedRoleName);
                        }
                    }

                    // 3. Fallback: try the model asset ID directly (legacy configs)
                    if (roleIndex < 0) {
                        roleIndex = npcPlugin.getIndex(finalModelAssetId);
                        if (roleIndex >= 0) {
                            usedRoleName = finalModelAssetId;
                            log("Using modelAssetId as role (legacy): " + usedRoleName);
                        }
                    }

                    if (roleIndex < 0) {
                        log("No NPC role found for custom animal: " + finalModelAssetId);
                        log("  adultNpcRoleId=" + (finalConfig != null ? finalConfig.getAdultNpcRoleId() : "null"));
                        log("  babyNpcRoleId=" + (finalConfig != null ? finalConfig.getBabyNpcRoleId() : "null"));
                        log("  TIP: Re-add the custom animal with: /breed custom add <npcRole> <food>");
                        return;
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);

                    // If using baby role, spawn at full scale. Otherwise use scaling fallback (40%)
                    Model scaledModel = null;
                    if (!usingBabyRole) {
                        float babyScale = 0.4f;  // Scaling fallback: 40% size
                        try {
                            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(finalModelAssetId);
                            if (modelAsset != null) {
                                scaledModel = Model.createScaledModel(modelAsset, babyScale);
                                log("Created scaled model at " + (babyScale * 100) + "% size");
                            }
                        } catch (Exception e) {
                            log("Could not create scaled model for custom baby: " + e.getMessage());
                        }
                    }

                    // Use reflection for spawnEntity due to complex signature
                    for (java.lang.reflect.Method m : NPCPlugin.class.getMethods()) {
                        if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                            Class<?> triConsumerClass = m.getParameterTypes()[5];
                            Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                triConsumerClass.getClassLoader(),
                                new Class<?>[] { triConsumerClass },
                                (proxy, method, args) -> null
                            );

                            Object result = m.invoke(npcPlugin, store, roleIndex, position, rotation, scaledModel, noOpCallback);

                            if (result != null) {
                                if (usingBabyRole) {
                                    log("Spawned custom baby (dedicated role): " + usedRoleName + " at " + position);
                                } else {
                                    log("Spawned custom baby (scaled adult): " + usedRoleName + " at " + position);
                                }

                                // Register baby for growth tracking
                                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                                if (plugin != null) {
                                    UUID babyId = UUID.randomUUID();
                                    // Custom babies need custom tracking - for now just log
                                    log("Custom baby registered: " + babyId);
                                }
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log("Error spawning custom animal baby: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log("Error in spawnCustomAnimalBaby: " + e.getMessage());
        }
    }

    private double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        // Delegate to EcsReflectionUtil for consistent UUID handling across all systems
        // This uses UUIDComponent first (stable), falling back to ref-based UUID
        return ref != null ? EcsReflectionUtil.getUuidFromRef(ref) : null;
    }

    /**
     * Check if an entity ref corresponds to a player (prevents treating players with animal models as animals).
     */
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
            // Silent - assume not a player if we can't check
        }
        return false;
    }

    /**
     * Extract the model asset ID from an entity (e.g., "Cow", "Sheep", "CustomCreature").
     * Returns null if unable to determine.
     */
    private String getModelAssetIdFromEntity(Ref<EntityStore> targetRef) {
        try {
            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return null;

            ModelComponent modelComp = store.getComponent(targetRef, MODEL_TYPE);
            if (modelComp == null) return null;

            // Use cached Field for performance (avoid getDeclaredField per call)
            if (!modelFieldInitialized || cachedModelField == null) {
                return null;
            }

            Object model = cachedModelField.get(modelComp);
            if (model == null) return null;

            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0) return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start) return null;
            return modelStr.substring(start, end);

        } catch (Exception e) {
            return null;
        }
    }

    private AnimalType getAnimalTypeFromEntity(Ref<EntityStore> targetRef) {
        String modelAssetId = getModelAssetIdFromEntity(targetRef);
        return modelAssetId != null ? AnimalType.fromModelAssetId(modelAssetId) : null;
    }

    // ===========================================
    // TAMING HELPER METHODS
    // ===========================================

    /**
     * Get the player's UUID from the interaction context.
     */
    private UUID getPlayerUuid(InteractionContext context) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return null;

            Store<EntityStore> store = entityRef.getStore();
            if (store == null) return null;

            UUIDComponent uuidComp = store.getComponent(entityRef, UUID_TYPE);
            if (uuidComp != null && uuidComp.getUuid() != null) {
                return uuidComp.getUuid();
            }
        } catch (Exception e) {
            log("getPlayerUuid error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the player's display name from the interaction context.
     * Since context.getEntity() returns a Ref, we look up the player by UUID.
     */
    private String getPlayerName(InteractionContext context) {
        try {
            // First get the player's UUID
            UUID playerUuid = getPlayerUuid(context);
            if (playerUuid == null) {
                log("getPlayerName: playerUuid is null");
                return null;
            }

            // Look up the player by UUID from the world's players
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                World world = com.hypixel.hytale.server.core.universe.Universe.get().getDefaultWorld();
                if (world != null) {
                    for (com.hypixel.hytale.server.core.entity.entities.Player player : world.getPlayers()) {
                        // Compare UUIDs
                        UUID pUuid = getPlayerUuidFromPlayer(player);
                        if (playerUuid.equals(pUuid)) {
                            String name = player.getDisplayName();
                            log("getPlayerName: found player " + name);
                            return name;
                        }
                    }
                }
            }
            log("getPlayerName: player not found in world");
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

    /**
     * Check if the item ID is a Name Tag item.
     */
    private boolean isHoldingNameTag(String itemId) {
        if (itemId == null) return false;
        // Check for various possible Name Tag item IDs
        return itemId.equalsIgnoreCase("NameTag") ||
               itemId.equalsIgnoreCase("Name_Tag") ||
               itemId.equalsIgnoreCase("Misc_NameTag") ||
               itemId.toLowerCase().contains("nametag");
    }

    /**
     * Send a message to the player.
     */
    private void sendPlayerMessage(InteractionContext context, String message, String color) {
        try {
            Ref<EntityStore> entityRef = context.getEntity();
            if (entityRef == null) return;

            // Try to send message via reflection
            for (java.lang.reflect.Method m : entityRef.getClass().getMethods()) {
                if (m.getName().equals("sendMessage") && m.getParameterCount() == 1) {
                    com.hypixel.hytale.server.core.Message msg =
                        com.hypixel.hytale.server.core.Message.raw(message).color(color);
                    m.invoke(entityRef, msg);
                    return;
                }
            }
        } catch (Exception e) {
            log("sendPlayerMessage error: " + e.getMessage());
        }
    }

    /**
     * Play taming sound at entity position.
     */
    private void playTamingSound(Ref<EntityStore> targetRef) {
        try {
            Vector3d pos = getEntityPosition(targetRef);
            if (pos == null) return;

            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return;

            // Try "SFX_LevelUp" or similar success sound
            int soundId = SoundEvent.getAssetMap().getIndex("SFX_LevelUp");
            if (soundId < 0) {
                soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
            }
            if (soundId < 0) return;

            // Play 3D sound
            for (java.lang.reflect.Method m : SoundUtil.class.getMethods()) {
                if (m.getName().equals("playSoundEvent3d") && m.getParameterCount() == 6) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes[0] == int.class && paramTypes[1] == double.class) {
                        Predicate<Object> allPlayers = p -> true;
                        m.invoke(null, soundId, pos.getX(), pos.getY(), pos.getZ(), allPlayers, store);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log("playTamingSound error: " + e.getMessage());
        }
    }
}
