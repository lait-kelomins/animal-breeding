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
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;

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

    // Heart particle spawner ID
    private static final String HEARTS_PARTICLE = "NPC/Emotions/Spawners/Hearts";

    // Breeding distance - animals must be within this range to breed
    private static final double BREEDING_DISTANCE = 5.0;

    // Cached component types for performance
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();

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

                ItemStack heldItem = context.getHeldItem();
                String itemId = heldItem != null ? heldItem.getItemId() : null;
                log("Held item: " + (heldItem != null ? heldItem.getClass().getSimpleName() : "null") + ", itemId: " + itemId);

                AnimalType animalType = getAnimalTypeFromEntity(targetRef);
                log("Animal type: " + (animalType != null ? animalType.name() : "null"));

                if (animalType == null) {
                    log("Not a breedable animal, triggering fallback");
                    shouldFail = true;
                    failedTargetRef = targetRef;
                    triggerFallbackInteraction(context, targetRef);
                    return;
                }

                // Check if holding correct food
                boolean isCorrectFood = false;
                if (plugin.getConfigManager() != null) {
                    isCorrectFood = itemId != null && plugin.getConfigManager().isBreedingFood(animalType, itemId);
                    log("Valid foods: " + plugin.getConfigManager().getBreedingFoods(animalType) + ", isCorrectFood: " + isCorrectFood);
                } else {
                    isCorrectFood = itemId != null && animalType.isBreedingFood(itemId);
                    log("Expected food (fallback): " + animalType.getBreedingFood() + ", isCorrectFood: " + isCorrectFood);
                }

                if (!isCorrectFood) {
                    log("Wrong food or no item, triggering fallback");
                    shouldFail = true;
                    failedTargetRef = targetRef;
                    triggerFallbackInteraction(context, targetRef);
                    return;
                }

                log("Correct food! Proceeding with breeding");

                UUID animalId = getUuidFromRef(targetRef);
                BreedingManager.FeedResult result = breeding.tryFeed(animalId, animalType, itemId);

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
                        triggerFallbackInteraction(context, targetRef);
                        return;
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
        try {
            String originalInteractionId = LaitsBreedingPlugin.getOriginalInteractionId(targetRef);
            log("Looking up fallback for targetRef: " + targetRef);
            log("Original interaction ID: " + (originalInteractionId != null ? originalInteractionId : "NOT FOUND"));

            if (originalInteractionId == null || originalInteractionId.isEmpty()) {
                log("No fallback interaction found for entity - interaction will just end");
                return;
            }

            log("Triggering fallback interaction: " + originalInteractionId);

            Object interactionManager = context.getInteractionManager();
            if (interactionManager == null) {
                log("InteractionManager is null");
                return;
            }

            RootInteraction rootInteraction = RootInteraction.getRootInteractionOrUnknown(originalInteractionId);
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

                spawnBabyAnimal(animalType, thisPos);
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

    private void spawnBabyAnimal(AnimalType animalType, Vector3d position) {
        try {
            boolean hasBabyVariant = animalType.hasBabyVariant();
            String roleId = hasBabyVariant ? animalType.getBabyNpcRoleId() : animalType.getAdultNpcRoleId();
            float initialScale = hasBabyVariant ? 1.0f : animalType.getScaleForStage(GrowthStage.BABY);

            if (roleId == null) return;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            final String finalRoleId = roleId;
            final AnimalType finalAnimalType = animalType;
            final boolean finalHasBabyVariant = hasBabyVariant;
            final float finalInitialScale = initialScale;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    NPCPlugin npcPlugin = NPCPlugin.get();
                    int roleIndex = npcPlugin.getIndex(finalRoleId);

                    if (roleIndex < 0) return;

                    Vector3f rotation = new Vector3f(0, 0, 0);

                    // Create scaled model if needed
                    Model scaledModel = null;
                    if (!finalHasBabyVariant) {
                        try {
                            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(finalAnimalType.getModelAssetId());
                            if (modelAsset != null) {
                                scaledModel = Model.createScaledModel(modelAsset, finalInitialScale);
                            }
                        } catch (Exception e) {
                            // Silent - will spawn at normal scale
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
                                try {
                                    java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                                    @SuppressWarnings("unchecked")
                                    Ref<EntityStore> entityRef = (Ref<EntityStore>) getFirst.invoke(result);

                                    if (entityRef != null) {
                                        LaitsBreedingPlugin pluginInstance = LaitsBreedingPlugin.getInstance();
                                        if (pluginInstance != null) {
                                            UUID babyId = UUID.randomUUID();
                                            pluginInstance.getBreedingManager().registerBaby(babyId, finalAnimalType, entityRef);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Silent
                                }
                            }
                            break;
                        }
                    }

                } catch (Exception e) {
                    // Silent
                }
            });

        } catch (Exception e) {
            // Silent
        }
    }

    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        try {
            Integer index = ref.getIndex();
            if (index != null) {
                return UUID.nameUUIDFromBytes(("entity_ref_" + index).getBytes());
            }
        } catch (Exception e) {
            // Silent
        }
        return UUID.nameUUIDFromBytes(ref.toString().getBytes());
    }

    private AnimalType getAnimalTypeFromEntity(Ref<EntityStore> targetRef) {
        try {
            Store<EntityStore> store = targetRef.getStore();
            if (store == null) return null;

            ModelComponent modelComp = store.getComponent(targetRef, MODEL_TYPE);
            if (modelComp == null) return null;

            // Extract modelAssetId via reflection (field is private)
            Field modelField = ModelComponent.class.getDeclaredField("model");
            modelField.setAccessible(true);
            Object model = modelField.get(modelComp);
            if (model == null) return null;

            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0) return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start) return null;
            String modelAssetId = modelStr.substring(start, end);

            return AnimalType.fromModelAssetId(modelAssetId);

        } catch (Exception e) {
            return null;
        }
    }
}
