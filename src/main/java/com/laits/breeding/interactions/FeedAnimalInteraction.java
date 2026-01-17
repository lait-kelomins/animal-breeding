package com.laits.breeding.interactions;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;

import java.util.UUID;

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

    public FeedAnimalInteraction() {
        super();
    }

    // Track if the interaction should fail (conditions not met)
    private boolean shouldFail = false;
    // Store target ref for fallback triggering
    private Object failedTargetRef = null;

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        InteractionType type,
        InteractionContext context,
        CooldownHandler cooldownHandler
    ) {
        if (firstRun) {
            shouldFail = false; // Reset for new interaction
            failedTargetRef = null;

            try {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null) {
                    shouldFail = true;
                    return; // Don't run effects
                }

                BreedingManager breeding = plugin.getBreedingManager();
                if (breeding == null) {
                    shouldFail = true;
                    return;
                }

                var targetRef = context.getTargetEntity();
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
                    return; // Not a breedable animal - fall back to default
                }

                // Check if holding correct food BEFORE trying to feed (use ConfigManager for dynamic config)
                LaitsBreedingPlugin pluginInstance = LaitsBreedingPlugin.getInstance();
                boolean isCorrectFood = false;
                if (pluginInstance != null && pluginInstance.getConfigManager() != null) {
                    isCorrectFood = itemId != null && pluginInstance.getConfigManager().isBreedingFood(animalType, itemId);
                    log("Valid foods: " + pluginInstance.getConfigManager().getBreedingFoods(animalType) + ", isCorrectFood: " + isCorrectFood);
                } else {
                    // Fallback to enum default if config not available
                    isCorrectFood = itemId != null && animalType.isBreedingFood(itemId);
                    log("Expected food (fallback): " + animalType.getBreedingFood() + ", isCorrectFood: " + isCorrectFood);
                }

                // If no item held or wrong food, fall back to default behavior (e.g., mount horse)
                if (!isCorrectFood) {
                    log("Wrong food or no item, triggering fallback");
                    shouldFail = true;
                    failedTargetRef = targetRef;
                    triggerFallbackInteraction(context, targetRef);
                    return; // Wrong food - fall back to default (e.g., mount horse)
                }

                log("Correct food! Proceeding with breeding");

                UUID animalId = getUuidFromRef(targetRef);
                BreedingManager.FeedResult result = breeding.tryFeed(animalId, animalType, itemId);

                switch (result) {
                    case SUCCESS:
                        spawnHeartParticles(targetRef);
                        checkForMateAndBreedInstantly(breeding, animalId, animalType, targetRef);
                        // Play sound and consume item via Java (not asset effects)
                        playSoundAndConsumeItem(plugin, context, targetRef);
                        break;

                    case ALREADY_IN_LOVE:
                        spawnHeartParticles(targetRef);
                        // Play sound and consume item via Java (not asset effects)
                        playSoundAndConsumeItem(plugin, context, targetRef);
                        break;

                    case ON_COOLDOWN:
                    case NOT_ADULT:
                        shouldFail = true;
                        failedTargetRef = targetRef;
                        // Don't trigger fallback for cooldown/baby - just don't feed
                        return;

                    case WRONG_FOOD:
                        shouldFail = true;
                        failedTargetRef = targetRef;
                        triggerFallbackInteraction(context, targetRef);
                        return; // Wrong food - fall back to default
                }

            } catch (Exception e) {
                shouldFail = true;
                return;
            }
        }

        // Only run effects (animation, sound, item consumption) if conditions were met
        if (!shouldFail) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
        }
    }

    /**
     * Trigger the original/fallback interaction for an entity (e.g., horse mounting).
     */
    private void triggerFallbackInteraction(InteractionContext context, Object targetRef) {
        try {
            // Get the original interaction ID that was saved before we overwrote it
            String originalInteractionId = LaitsBreedingPlugin.getOriginalInteractionId(targetRef);
            log("Looking up fallback for targetRef: " + targetRef + " (hash=" + System.identityHashCode(targetRef) + ")");
            log("Original interaction ID: " + (originalInteractionId != null ? originalInteractionId : "NOT FOUND"));

            if (originalInteractionId == null || originalInteractionId.isEmpty()) {
                log("No fallback interaction found for entity - interaction will just end");
                return;
            }

            log("Triggering fallback interaction: " + originalInteractionId);

            // Get the InteractionManager from context
            Object interactionManager = context.getInteractionManager();
            if (interactionManager == null) {
                log("InteractionManager is null");
                return;
            }

            // Get the RootInteraction by ID
            Class<?> rootIntClass = Class.forName("com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction");
            java.lang.reflect.Method getRootInt = rootIntClass.getMethod("getRootInteractionOrUnknown", String.class);
            Object rootInteraction = getRootInt.invoke(null, originalInteractionId);

            if (rootInteraction == null) {
                log("Could not find RootInteraction: " + originalInteractionId);
                return;
            }

            // Try to start the interaction via InteractionManager
            // Look for a method like startInteraction, execute, run, etc.
            for (java.lang.reflect.Method m : interactionManager.getClass().getMethods()) {
                String name = m.getName();
                if ((name.contains("start") || name.contains("execute") || name.contains("run") || name.contains("trigger"))
                    && m.getParameterCount() >= 1) {
                    log("Found InteractionManager method: " + name + " with " + m.getParameterCount() + " params");
                }
            }

            // Log all executeChain and startChain method signatures
            for (java.lang.reflect.Method m : interactionManager.getClass().getMethods()) {
                String name = m.getName();
                if (name.equals("executeChain") || name.equals("startChain")) {
                    StringBuilder sig = new StringBuilder(name + "(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sig.append(p.getSimpleName()).append(", ");
                    }
                    sig.append(")");
                    log("Method signature: " + sig);
                }
            }

            // Try executeChain with 3 params - likely (RootInteraction, InteractionType, something)
            for (java.lang.reflect.Method m : interactionManager.getClass().getMethods()) {
                if (m.getName().equals("executeChain") && m.getParameterCount() == 3) {
                    Class<?>[] params = m.getParameterTypes();
                    log("Trying executeChain with params: " + params[0].getSimpleName() + ", " + params[1].getSimpleName() + ", " + params[2].getSimpleName());

                    try {
                        // Try (RootInteraction, InteractionType, boolean) or similar
                        if (params[0].isAssignableFrom(rootIntClass)) {
                            // Get InteractionType.Use
                            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
                            Object useType = null;
                            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                                if (enumConst.toString().equals("Use")) {
                                    useType = enumConst;
                                    break;
                                }
                            }

                            if (params[2] == boolean.class) {
                                m.invoke(interactionManager, rootInteraction, useType, true);
                                log("Triggered fallback via executeChain(RootInteraction, InteractionType, boolean)");
                                return;
                            } else {
                                m.invoke(interactionManager, rootInteraction, useType, null);
                                log("Triggered fallback via executeChain(RootInteraction, InteractionType, ?)");
                                return;
                            }
                        }
                    } catch (Exception e) {
                        log("executeChain failed: " + e.getMessage());
                    }
                }
            }

            // Try startChain(Ref, CommandBuffer, InteractionType, InteractionContext, RootInteraction)
            for (java.lang.reflect.Method m : interactionManager.getClass().getMethods()) {
                if (m.getName().equals("startChain") && m.getParameterCount() == 5) {
                    Class<?>[] params = m.getParameterTypes();
                    log("Trying startChain with params: " + params[0].getSimpleName() + ", " + params[1].getSimpleName() + ", " + params[2].getSimpleName() + ", " + params[3].getSimpleName() + ", " + params[4].getSimpleName());

                    try {
                        // Get the entity ref (player doing the interaction)
                        Object entityRef = context.getEntity();

                        // Get CommandBuffer from context
                        Object commandBuffer = context.getCommandBuffer();

                        // Get InteractionType.Use
                        Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
                        Object useType = null;
                        for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                            if (enumConst.toString().equals("Use")) {
                                useType = enumConst;
                                break;
                            }
                        }

                        log("Calling startChain: entityRef=" + entityRef + ", commandBuffer=" + (commandBuffer != null) + ", useType=" + useType + ", context=" + (context != null) + ", rootInteraction=" + (rootInteraction != null));

                        // Schedule the fallback to run after current tick to avoid conflicts
                        final java.lang.reflect.Method startChainMethod = m;
                        final Object finalEntityRef = entityRef;
                        final Object finalUseType = useType;
                        final Object finalRootInteraction = rootInteraction;
                        final Object finalInteractionManager = interactionManager;

                        // Get the world and schedule on its executor
                        World world = Universe.get().getDefaultWorld();
                        if (world != null) {
                            // Store context values for use in the scheduled task
                            final InteractionContext finalContext = context;
                            final Object finalCommandBuffer = commandBuffer;

                            world.execute(() -> {
                                try {
                                    // Use the command buffer from the original context
                                    startChainMethod.invoke(finalInteractionManager, finalEntityRef, finalCommandBuffer, finalUseType, finalContext, finalRootInteraction);
                                    log("Successfully triggered fallback via startChain (scheduled)!");
                                } catch (Exception ex) {
                                    Throwable cause = ex;
                                    if (ex instanceof java.lang.reflect.InvocationTargetException) {
                                        cause = ((java.lang.reflect.InvocationTargetException) ex).getTargetException();
                                    }
                                    log("Scheduled startChain failed: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                                    if (cause.getMessage() == null) {
                                        cause.printStackTrace();
                                    }
                                }
                            });
                            log("Scheduled fallback interaction for next tick");
                            return;
                        } else {
                            log("Could not get world to schedule fallback");
                        }
                        return;
                    } catch (Exception e) {
                        Throwable cause = e;
                        if (e instanceof java.lang.reflect.InvocationTargetException) {
                            cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        }
                        log("startChain failed: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    }
                }
            }

            log("Could not find working method to trigger fallback interaction");

        } catch (Exception e) {
            log("triggerFallbackInteraction error: " + e.getMessage());
        }
    }

    /**
     * Play sound and consume item via Java after successful validation.
     * This is called instead of relying on asset-based effects so we can
     * conditionally skip sound/consumption when validation fails.
     */
    private void playSoundAndConsumeItem(LaitsBreedingPlugin plugin, InteractionContext context, Object targetRef) {
        try {
            log("playSoundAndConsumeItem called");

            // Play feeding sound at target entity position
            playFeedingSoundAtPosition(targetRef);

            // Consume 1 item from player's hand
            consumePlayerHeldItem(context);
        } catch (Exception e) {
            log("playSoundAndConsumeItem error: " + e.getMessage());
        }
    }

    private void log(String msg) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[FeedAnimal] " + msg);
        }
    }

    /**
     * Play the feeding sound at the target entity's position.
     */
    private void playFeedingSoundAtPosition(Object targetRef) {
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

            // Get sound ID
            Class<?> soundEventClass = Class.forName("com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent");
            Object assetMap = soundEventClass.getMethod("getAssetMap").invoke(null);
            int soundId = (int) assetMap.getClass().getMethod("getIndex", Object.class).invoke(assetMap, "SFX_Consume_Bread");
            log("playFeedingSoundAtPosition: soundId=" + soundId);
            if (soundId < 0) {
                log("playFeedingSoundAtPosition: soundId < 0, aborting");
                return;
            }

            // Get SoundCategory.SFX
            Class<?> soundCategoryClass = Class.forName("com.hypixel.hytale.protocol.SoundCategory");
            Object sfxCategory = null;
            for (Object enumConst : soundCategoryClass.getEnumConstants()) {
                if (enumConst.toString().equals("SFX")) {
                    sfxCategory = enumConst;
                    break;
                }
            }
            if (sfxCategory == null) {
                log("playFeedingSoundAtPosition: sfxCategory is null");
                return;
            }

            // Play 3D sound
            // Signature: playSoundEvent3d(int, double, double, double, Predicate, ComponentAccessor)
            Class<?> soundUtilClass = Class.forName("com.hypixel.hytale.server.core.universe.world.SoundUtil");
            boolean found = false;
            for (java.lang.reflect.Method m : soundUtilClass.getMethods()) {
                if (m.getName().equals("playSoundEvent3d") && m.getParameterCount() == 6) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    // Check if this is the right overload (int, double, double, double, Predicate, ComponentAccessor)
                    if (paramTypes[0] == int.class && paramTypes[1] == double.class) {
                        log("playFeedingSoundAtPosition: calling playSoundEvent3d");
                        // Create a Predicate that returns true for all (play sound for everyone)
                        java.util.function.Predicate<Object> allPlayers = p -> true;
                        m.invoke(null, soundId, pos.getX(), pos.getY(), pos.getZ(), allPlayers, store);
                        found = true;
                        log("playFeedingSoundAtPosition: sound played successfully");
                        break;
                    }
                }
            }
            if (!found) {
                log("playFeedingSoundAtPosition: playSoundEvent3d method not found");
            }
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof java.lang.reflect.InvocationTargetException) {
                cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
            }
            log("playFeedingSoundAtPosition error: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            cause.printStackTrace();
        }
    }

    /**
     * Consume 1 item from the player's held item.
     * Uses context.getHeldItemContainer() and context.getHeldItemSlot() directly.
     */
    private void consumePlayerHeldItem(InteractionContext context) {
        try {
            log("consumePlayerHeldItem called");

            // PRIMARY APPROACH: Use getHeldItemContainer() and getHeldItemSlot() directly
            try {
                // Get the container holding the item
                Object container = context.getHeldItemContainer();
                if (container != null) {
                    log("consumePlayerHeldItem: got container, class=" + container.getClass().getName());

                    // Get the slot index
                    short slot = context.getHeldItemSlot();
                    log("consumePlayerHeldItem: slot=" + slot);

                    // Log container methods
                    StringBuilder containerMethods = new StringBuilder("Container methods: ");
                    for (java.lang.reflect.Method m : container.getClass().getMethods()) {
                        if (!m.getDeclaringClass().equals(Object.class) &&
                            (m.getName().contains("remove") || m.getName().contains("set") ||
                             m.getName().contains("adjust") || m.getName().contains("Item"))) {
                            containerMethods.append(m.getName()).append("(").append(m.getParameterCount()).append("), ");
                        }
                    }
                    log(containerMethods.toString());

                    // Try removeItemStackFromSlot(short slot, int amount)
                    try {
                        java.lang.reflect.Method removeItem = container.getClass().getMethod(
                            "removeItemStackFromSlot", short.class, int.class);
                        removeItem.invoke(container, slot, 1);
                        log("consumePlayerHeldItem: removed 1 item via removeItemStackFromSlot");
                        return;
                    } catch (NoSuchMethodException ex) {
                        log("consumePlayerHeldItem: removeItemStackFromSlot not found");
                    }

                    // Try removeItem(short slot, int amount)
                    try {
                        java.lang.reflect.Method removeItem = container.getClass().getMethod(
                            "removeItem", short.class, int.class);
                        removeItem.invoke(container, slot, 1);
                        log("consumePlayerHeldItem: removed 1 item via removeItem");
                        return;
                    } catch (NoSuchMethodException ignored) {}

                    // Try adjustItemQuantity or similar
                    try {
                        java.lang.reflect.Method adjust = container.getClass().getMethod(
                            "adjustItemQuantity", short.class, int.class);
                        adjust.invoke(container, slot, -1);
                        log("consumePlayerHeldItem: adjusted quantity via adjustItemQuantity");
                        return;
                    } catch (NoSuchMethodException ignored) {}

                    // Get the ItemStack from the slot and modify it
                    try {
                        java.lang.reflect.Method getItem = container.getClass().getMethod("getItem", short.class);
                        Object itemStack = getItem.invoke(container, slot);
                        if (itemStack != null) {
                            log("consumePlayerHeldItem: got ItemStack from container.getItem()");

                            // Try adjustQuantity on ItemStack
                            try {
                                java.lang.reflect.Method adjustQty = itemStack.getClass().getMethod("adjustQuantity", int.class);
                                adjustQty.invoke(itemStack, -1);
                                log("consumePlayerHeldItem: called ItemStack.adjustQuantity(-1)");
                                return;
                            } catch (NoSuchMethodException ignored) {}

                            // Try setQuantity
                            try {
                                java.lang.reflect.Method getQty = itemStack.getClass().getMethod("getQuantity");
                                int currentQty = (int) getQty.invoke(itemStack);
                                java.lang.reflect.Method setQty = itemStack.getClass().getMethod("setQuantity", int.class);
                                setQty.invoke(itemStack, currentQty - 1);
                                log("consumePlayerHeldItem: called ItemStack.setQuantity(" + (currentQty - 1) + ")");
                                return;
                            } catch (NoSuchMethodException ignored) {}
                        }
                    } catch (NoSuchMethodException ignored) {}

                } else {
                    log("consumePlayerHeldItem: container is null");
                }
            } catch (Exception ex) {
                log("consumePlayerHeldItem: container approach failed: " + ex.getMessage());
            }

            // FALLBACK: Try direct ItemStack modification
            try {
                ItemStack heldItem = context.getHeldItem();
                if (heldItem != null) {
                    log("consumePlayerHeldItem: trying direct ItemStack modification");

                    // Log ItemStack methods
                    StringBuilder itemMethods = new StringBuilder("ItemStack methods: ");
                    for (java.lang.reflect.Method m : heldItem.getClass().getMethods()) {
                        if (!m.getDeclaringClass().equals(Object.class) &&
                            (m.getName().contains("uantity") || m.getName().contains("set") ||
                             m.getName().contains("adjust") || m.getName().contains("remove"))) {
                            itemMethods.append(m.getName()).append("(").append(m.getParameterCount()).append("), ");
                        }
                    }
                    log(itemMethods.toString());

                    int currentQty = heldItem.getQuantity();
                    log("consumePlayerHeldItem: current quantity=" + currentQty);

                    // Try adjustQuantity(int)
                    try {
                        java.lang.reflect.Method m = heldItem.getClass().getMethod("adjustQuantity", int.class);
                        m.invoke(heldItem, -1);
                        log("consumePlayerHeldItem: called adjustQuantity(-1)");
                        return;
                    } catch (NoSuchMethodException ignored) {}

                    // Try setQuantity(int)
                    try {
                        java.lang.reflect.Method m = heldItem.getClass().getMethod("setQuantity", int.class);
                        m.invoke(heldItem, currentQty - 1);
                        log("consumePlayerHeldItem: called setQuantity(" + (currentQty - 1) + ")");
                        return;
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Exception ex) {
                log("consumePlayerHeldItem: ItemStack approach failed: " + ex.getMessage());
            }

            log("consumePlayerHeldItem: could not consume item through any approach");

        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof java.lang.reflect.InvocationTargetException) {
                cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
            }
            log("consumePlayerHeldItem error: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
            cause.printStackTrace();
        }
    }

    /**
     * Spawn heart particles at the animal's position.
     */
    private void spawnHeartParticles(Object targetRef) {
        try {
            Vector3d position = getEntityPosition(targetRef);
            if (position == null) return;

            double x = position.getX();
            double y = position.getY() + 1.5;
            double z = position.getZ();

            Class<?> particleUtilClass = Class.forName("com.hypixel.hytale.server.core.universe.world.ParticleUtil");

            java.lang.reflect.Method getStore = targetRef.getClass().getMethod("getStore");
            Object store = getStore.invoke(targetRef);

            for (java.lang.reflect.Method method : particleUtilClass.getMethods()) {
                if (method.getName().equals("spawnParticleEffect") && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == String.class &&
                        params[1].getSimpleName().equals("Vector3d") &&
                        params[2].getSimpleName().equals("ComponentAccessor")) {

                        Vector3d heartsPos = new Vector3d(x, y, z);
                        method.invoke(null, HEARTS_PARTICLE, heartsPos, store);
                        return;
                    }
                }
            }

        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Get entity position from Ref using TransformComponent.
     * Returns null silently if entity reference is invalid (entity was removed).
     */
    private Vector3d getEntityPosition(Object ref) {
        try {
            java.lang.reflect.Method getStore = ref.getClass().getMethod("getStore");
            Object store = getStore.invoke(ref);

            if (store == null) return null;

            Class<?> transformClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
            java.lang.reflect.Method getComponentType = transformClass.getMethod("getComponentType");
            Object componentType = getComponentType.invoke(null);

            Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
            Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");
            java.lang.reflect.Method getComponent = store.getClass().getMethod("getComponent", refClass, componentTypeClass);
            Object transform = getComponent.invoke(store, ref, componentType);

            if (transform != null) {
                java.lang.reflect.Method getPosition = transform.getClass().getMethod("getPosition");
                return (Vector3d) getPosition.invoke(transform);
            }
        } catch (Exception e) {
            // Silent - entity may have been removed
        }
        return null;
    }

    /**
     * Check for another animal in love and breed instantly (spawn baby).
     * Animals must be within BREEDING_DISTANCE blocks to breed.
     */
    private void checkForMateAndBreedInstantly(
        BreedingManager breeding,
        UUID animalId,
        AnimalType animalType,
        Object targetRef
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

                Object otherRef = otherData.getEntityRef();
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

    /**
     * Calculate distance between two positions.
     */
    private double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Spawn a baby animal of the given type at the position using NPCPlugin.
     * For animals WITH baby variants: spawns baby NPC
     * For animals WITHOUT baby variants: spawns adult NPC at small scale (0.4)
     */
    private void spawnBabyAnimal(AnimalType animalType, Vector3d position) {
        try {
            boolean hasBabyVariant = animalType.hasBabyVariant();
            // For baby variants, use baby role; for others, use adult role
            String roleId = hasBabyVariant ? animalType.getBabyNpcRoleId() : animalType.getAdultNpcRoleId();
            float initialScale = hasBabyVariant ? 1.0f : animalType.getScaleForStage(GrowthStage.BABY);

            if (roleId == null) return;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            final Vector3d spawnPos = position;
            final AnimalType finalAnimalType = animalType;
            final String finalRoleId = roleId;
            final boolean finalHasBabyVariant = hasBabyVariant;
            final float finalInitialScale = initialScale;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Class<?> npcPluginClass = Class.forName("com.hypixel.hytale.server.npc.NPCPlugin");
                    java.lang.reflect.Method getInstance = npcPluginClass.getMethod("get");
                    Object npcPlugin = getInstance.invoke(null);

                    java.lang.reflect.Method getIndex = npcPluginClass.getMethod("getIndex", String.class);
                    int roleIndex = (int) getIndex.invoke(npcPlugin, finalRoleId);

                    if (roleIndex < 0) return;

                    try {
                        java.lang.reflect.Method validateRole = npcPluginClass.getMethod("validateSpawnableRole", String.class);
                        validateRole.invoke(npcPlugin, finalRoleId);
                    } catch (Exception e) {
                        // Silent
                    }

                    try {
                        java.lang.reflect.Method prepareRole = npcPluginClass.getMethod("prepareRoleBuilderInfo", int.class);
                        prepareRole.invoke(npcPlugin, roleIndex);
                    } catch (Exception e) {
                        // Silent
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);

                    // Create scaled model if needed (for creatures without baby variants)
                    Object scaledModel = null;
                    if (!finalHasBabyVariant) {
                        try {
                            Class<?> modelAssetClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                            Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                            Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap, finalAnimalType.getModelAssetId());

                            if (modelAsset != null) {
                                Class<?> modelClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                                java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel", modelAssetClass, float.class);
                                scaledModel = createScaledModel.invoke(null, modelAsset, finalInitialScale);
                            }
                        } catch (Exception e) {
                            // Silent - will spawn at normal scale
                        }
                    }

                    Object result = null;
                    for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                        if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                            Class<?>[] paramTypes = m.getParameterTypes();

                            Class<?> triConsumerClass = paramTypes[5];
                            Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                triConsumerClass.getClassLoader(),
                                new Class<?>[] { triConsumerClass },
                                (proxy, method, args) -> null
                            );

                            try {
                                result = m.invoke(npcPlugin, store, roleIndex, spawnPos, rotation, scaledModel, noOpCallback);
                            } catch (Exception e) {
                                // Silent
                            }
                            break;
                        }
                    }

                    // Register baby with breeding manager for tracking
                    if (result != null) {
                        try {
                            Object entityRef = null;
                            try {
                                java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                                entityRef = getFirst.invoke(result);
                            } catch (Exception e) {
                                entityRef = result;
                            }

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

                } catch (Exception e) {
                    // Silent
                }
            });

        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Create a deterministic UUID from a Ref's index.
     */
    private UUID getUuidFromRef(Object ref) {
        try {
            java.lang.reflect.Method getIndex = ref.getClass().getMethod("getIndex");
            Integer index = (Integer) getIndex.invoke(ref);
            if (index != null) {
                return UUID.nameUUIDFromBytes(("entity_ref_" + index).getBytes());
            }
        } catch (Exception e) {
            // Silent
        }
        return UUID.nameUUIDFromBytes(ref.toString().getBytes());
    }

    /**
     * Get the animal type from the entity's ModelComponent.
     */
    private AnimalType getAnimalTypeFromEntity(Object targetRef) {
        try {
            java.lang.reflect.Method getStore = targetRef.getClass().getMethod("getStore");
            Object store = getStore.invoke(targetRef);
            if (store == null) return null;

            Class<?> modelCompClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
            java.lang.reflect.Method getComponentType = modelCompClass.getMethod("getComponentType");
            Object modelCompType = getComponentType.invoke(null);

            Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
            Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");
            java.lang.reflect.Method getComponent = store.getClass().getMethod("getComponent", refClass, componentTypeClass);
            Object modelComp = getComponent.invoke(store, targetRef, modelCompType);

            if (modelComp == null) return null;

            java.lang.reflect.Field modelField = modelComp.getClass().getDeclaredField("model");
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
            // Silent
            return null;
        }
    }
}
