package com.laits.breeding.managers;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.laits.breeding.components.HyTameInteractionComponent;
import com.laits.breeding.interactions.InteractionStateCache;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.models.OriginalInteractionState;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.EntityUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages ECS interaction setup for breedable animals.
 * Handles attaching FeedAnimal interactions and dynamic hint switching.
 */
public class InteractionSetupManager {

    private final ConfigManager configManager;
    private final BreedingManager breedingManager;
    private Supplier<ComponentType<EntityStore, HyTameInteractionComponent>> hyTameInteractionTypeSupplier;

    // Build flags
    private boolean useEntityBasedInteractions = true;
    private boolean useLegacyFeedInteraction = true;
    private boolean showAbility2HintsOnEntities = true;
    private boolean modifyInteractionHints = true; // If true, sets custom hints (requires cleanup on mod disable)

    // Cached InteractionTypes (avoid enum iteration every call)
    private static InteractionType cachedUseType = null;
    private static InteractionType cachedAbility2Type = null;
    private static boolean interactionTypesCached = false;

    private static final String FEED_INTERACTION_ID = "Root_FeedAnimal";

    // Logging
    private boolean verboseLogging = false;
    private Consumer<String> logger;
    private Consumer<String> warningLogger;

    public InteractionSetupManager(ConfigManager configManager, BreedingManager breedingManager) {
        this.configManager = configManager;
        this.breedingManager = breedingManager;
    }

    // Configuration setters
    public void setHyTameInteractionTypeSupplier(Supplier<ComponentType<EntityStore, HyTameInteractionComponent>> supplier) {
        this.hyTameInteractionTypeSupplier = supplier;
    }

    public void setUseEntityBasedInteractions(boolean use) {
        this.useEntityBasedInteractions = use;
    }

    public void setUseLegacyFeedInteraction(boolean use) {
        this.useLegacyFeedInteraction = use;
    }

    public void setShowAbility2HintsOnEntities(boolean show) {
        this.showAbility2HintsOnEntities = show;
    }

    public void setModifyInteractionHints(boolean modify) {
        this.modifyInteractionHints = modify;
    }

    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public void setWarningLogger(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    private void logVerbose(String message) {
        if (verboseLogging && logger != null) {
            logger.accept(message);
        }
    }

    private void logWarning(String message) {
        if (warningLogger != null) {
            warningLogger.accept(message);
        }
    }

    /**
     * Cache InteractionType enum values to avoid iteration on every call.
     */
    private static void ensureInteractionTypesCached() {
        if (interactionTypesCached) return;
        for (InteractionType enumConst : InteractionType.class.getEnumConstants()) {
            String name = enumConst.toString();
            if ("Use".equals(name)) {
                cachedUseType = enumConst;
            } else if ("Ability2".equals(name)) {
                cachedAbility2Type = enumConst;
            }
        }
        interactionTypesCached = true;
    }

    private ComponentType<EntityStore, HyTameInteractionComponent> getHyTameInteractionType() {
        return hyTameInteractionTypeSupplier != null ? hyTameInteractionTypeSupplier.get() : null;
    }

    /**
     * Set up interactions for a single entity if it's a breedable animal.
     * Must be called from the world thread.
     */
    public void setupSingleEntity(World world, Ref<EntityStore> entityRef) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            String modelAssetId = EcsReflectionUtil.getEntityModelAssetId(store, entityRef);
            if (modelAssetId == null)
                return;

            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            if (animalType == null)
                return;

            logVerbose("Setting up animal: " + modelAssetId + " (" + animalType + ")");

            // Skip if neither breeding nor taming is enabled
            if (!configManager.isBreedingEnabled(animalType) && !configManager.isTamingEnabled(animalType))
                return;

            boolean isBaby = AnimalType.isBabyVariant(modelAssetId);

            // Register babies for growth tracking
            if (isBaby) {
                UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
                if (breedingManager.getData(babyId) == null) {
                    breedingManager.registerBaby(babyId, animalType, entityRef);
                    logVerbose("Registered baby for growth tracking: " + modelAssetId);
                }
            }

            // Set up interactions for adults (babies can't breed)
            if (!isBaby) {
                setupEntityInteractions(store, entityRef, animalType);
            }

        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid entity")) {
                return;
            }
            logVerbose("setupSingleEntity error: " + e.getMessage());
        } catch (Exception e) {
            logVerbose("setupSingleEntity error: " + e.getMessage());
        }
    }

    /**
     * Callback from NewAnimalSpawnDetector when a new animal is detected.
     */
    public void onNewAnimalDetected(Store<EntityStore> store, Ref<EntityStore> entityRef,
            String modelAssetId, AnimalType animalType, World world) {
        try {
            if (entityRef == null || !entityRef.isValid())
                return;

            logVerbose("NewAnimalSpawnDetector: Immediate detection of " + modelAssetId);

            CustomAnimalConfig customAnimal = null;
            if (animalType == null) {
                customAnimal = configManager.getCustomAnimal(modelAssetId);
            }

            // Skip if neither breeding nor taming is enabled
            if (animalType != null && !configManager.isBreedingEnabled(animalType) && !configManager.isTamingEnabled(animalType)) {
                logVerbose("Skipping disabled animal: " + animalType);
                return;
            }
            if (customAnimal != null && !customAnimal.isBreedingEnabled() && !customAnimal.isTamingEnabled()) {
                logVerbose("Skipping disabled custom animal: " + modelAssetId);
                return;
            }

            boolean isBaby = AnimalType.isBabyVariant(modelAssetId);

            // Register babies for growth tracking
            if (isBaby && animalType != null) {
                UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
                if (breedingManager.getData(babyId) == null) {
                    breedingManager.registerBaby(babyId, animalType, entityRef);
                    logVerbose("Registered new baby for growth tracking: " + modelAssetId);
                }
            }

            // Set up interactions for adults
            // Must use world.execute() because store is processing during onEntityAdded callback
            if (!isBaby && world != null) {
                final Ref<EntityStore> finalEntityRef = entityRef;
                final AnimalType finalAnimalType = animalType;
                final CustomAnimalConfig finalCustomAnimal = customAnimal;
                final String finalModelAssetId = modelAssetId;

                world.execute(() -> {
                    try {
                        if (!finalEntityRef.isValid())
                            return;
                        Store<EntityStore> worldStore = world.getEntityStore().getStore();

                        if (useEntityBasedInteractions) {
                            if (finalAnimalType != null) {
                                setupEntityInteractions(worldStore, finalEntityRef, finalAnimalType);
                                logVerbose("Interactions set up for new animal: " + finalModelAssetId);
                            } else if (finalCustomAnimal != null) {
                                setupCustomAnimalInteractions(worldStore, finalEntityRef, finalCustomAnimal);
                                logVerbose("[CustomAnimal] Interactions set up for: " + finalModelAssetId);
                            }
                        } else if (showAbility2HintsOnEntities) {
                            String hintKey = "server.interactionHints.feed";
                            setupAbility2HintOnly(worldStore, finalEntityRef, hintKey);
                            logVerbose("Ability2 hint set up for: " + finalModelAssetId);
                        }
                    } catch (Exception e) {
                        logVerbose("Deferred interaction setup error: " + e.getMessage());
                    }
                });
            }

        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid entity")) {
                return;
            }
            logVerbose("onNewAnimalDetected error: " + e.getMessage());
        } catch (Exception e) {
            logVerbose("onNewAnimalDetected error: " + e.getMessage());
        }
    }

    /**
     * Set up breeding interactions on a single entity.
     */
    public void setupEntityInteractions(Store<EntityStore> store, Ref<EntityStore> entityRef, AnimalType animalType) {
        if (!useLegacyFeedInteraction) {
            return;
        }

        try {
            if (EntityUtil.isPlayerEntity(entityRef)) {
                logVerbose("[SetupInteraction] Skipping player entity with animal model");
                return;
            }

            String modelAssetId = EcsReflectionUtil.getEntityModelAssetId(store, entityRef);
            if (modelAssetId != null && AnimalType.isBabyVariant(modelAssetId)) {
                logVerbose("[SetupInteraction] Skipping baby animal: " + modelAssetId);
                return;
            }

            // Ensure Interactable component
            try {
                store.ensureAndGetComponent(entityRef, EcsReflectionUtil.INTERACTABLE_TYPE);
            } catch (Exception e) {
                // Silent
            }

            Interactions interactions = store.getComponent(entityRef, EcsReflectionUtil.INTERACTIONS_TYPE);
            if (interactions == null) {
                logVerbose("[SetupInteraction] Skipping non-NPC entity (no Interactions component)");
                return;
            }

            // Use cached InteractionType to avoid enum iteration every call
            ensureInteractionTypesCached();
            InteractionType useType = cachedUseType;

            String currentUse = interactions.getInteractionId(useType);

            // Early exit: if already set up, skip all the work
            ComponentType<EntityStore, HyTameInteractionComponent> hyTameType = getHyTameInteractionType();
            HyTameInteractionComponent origComp = hyTameType != null
                    ? store.getComponent(entityRef, hyTameType)
                    : null;

            if (origComp != null && origComp.isCaptured() && FEED_INTERACTION_ID.equals(currentUse)) {
                // Already fully set up - nothing to do
                return;
            }

            String currentHint = interactions.getInteractionHint();

            if (origComp == null || !origComp.isCaptured()) {
                if (currentUse == null || !currentUse.equals(FEED_INTERACTION_ID)) {
                    if (hyTameType != null) {
                        origComp = store.ensureAndGetComponent(entityRef, hyTameType);
                        origComp.setOriginalInteractionId(currentUse);
                        origComp.setOriginalHint(currentHint);
                        origComp.setCaptured(true);
                        logVerbose("[BuiltIn] " + animalType + ": persisted original interaction=" + currentUse + ", hint=" + currentHint);
                    }
                    InteractionStateCache.getInstance().storeOriginalState(entityRef, currentUse, currentHint, animalType);
                }
            } else {
                InteractionStateCache.getInstance().storeOriginalState(entityRef, origComp.getOriginalInteractionId(),
                        origComp.getOriginalHint(), animalType);
                logVerbose("[BuiltIn] " + animalType + ": restored original from ECS: interaction=" + origComp.getOriginalInteractionId());
            }

            if (currentUse == null || !currentUse.equals(FEED_INTERACTION_ID)) {
                interactions.setInteractionId(useType, FEED_INTERACTION_ID);
                logVerbose("[BuiltIn] " + animalType + ": set interaction to " + FEED_INTERACTION_ID);
            }

            // Only modify hints if flag is enabled (requires cleanup on mod disable)
            if (modifyInteractionHints) {
                String hintKey = animalType.isMountable()
                        ? "server.interactionHints.legacyFeedOrMount"
                        : "server.interactionHints.legacyFeed";
                interactions.setInteractionHint(hintKey);
                logVerbose("[SetupInteraction] SUCCESS for " + animalType + ": interactionId=" + FEED_INTERACTION_ID + ", hint=" + hintKey);
            } else {
                logVerbose("[SetupInteraction] SUCCESS for " + animalType + ": interactionId=" + FEED_INTERACTION_ID);
            }

        } catch (Exception e) {
            logWarning("[SetupInteraction] ERROR for " + animalType + ": " + e.getMessage());
        }
    }

    /**
     * Set up breeding interactions on a custom animal entity.
     */
    public void setupCustomAnimalInteractions(Store<EntityStore> store, Ref<EntityStore> entityRef,
            CustomAnimalConfig customAnimal) {
        if (!useLegacyFeedInteraction) {
            return;
        }

        String animalName = customAnimal.getModelAssetId();
        logVerbose("[CustomAnimal] setupCustomAnimalInteractions CALLED for: " + animalName);

        try {
            if (EntityUtil.isPlayerEntity(entityRef)) {
                logVerbose("[CustomAnimal] Skipping player entity with custom animal model: " + animalName);
                return;
            }

            String actualModelId = EcsReflectionUtil.getEntityModelAssetId(store, entityRef);
            if (actualModelId != null && AnimalType.isBabyVariant(actualModelId)) {
                logVerbose("[CustomAnimal] Skipping baby animal: " + actualModelId);
                return;
            }

            try {
                store.ensureAndGetComponent(entityRef, EcsReflectionUtil.INTERACTABLE_TYPE);
            } catch (Exception e) {
                // Silent
            }

            Interactions interactions = store.getComponent(entityRef, EcsReflectionUtil.INTERACTIONS_TYPE);
            if (interactions == null) {
                logVerbose("[CustomAnimal] Skipping non-NPC entity (no Interactions component): " + animalName);
                return;
            }

            // Use cached InteractionType to avoid enum iteration every call
            ensureInteractionTypesCached();
            InteractionType useType = cachedUseType;

            String currentUse = interactions.getInteractionId(useType);

            // Early exit: if already set up, skip all the work
            ComponentType<EntityStore, HyTameInteractionComponent> hyTameType = getHyTameInteractionType();
            HyTameInteractionComponent origComp = hyTameType != null
                    ? store.getComponent(entityRef, hyTameType)
                    : null;

            if (origComp != null && origComp.isCaptured() && FEED_INTERACTION_ID.equals(currentUse)) {
                // Already fully set up - nothing to do
                return;
            }

            String currentHint = interactions.getInteractionHint();
            logVerbose("[CustomAnimal] " + animalName + ": currentUse='" + currentUse + "', currentHint='" + currentHint + "'");

            if (origComp == null || !origComp.isCaptured()) {
                if (currentUse == null || !currentUse.equals(FEED_INTERACTION_ID)) {
                    if (hyTameType != null) {
                        origComp = store.ensureAndGetComponent(entityRef, hyTameType);
                        origComp.setOriginalInteractionId(currentUse);
                        origComp.setOriginalHint(currentHint);
                        origComp.setCaptured(true);
                        logVerbose("[CustomAnimal] " + animalName + ": persisted original interaction=" + currentUse);
                    }
                    InteractionStateCache.getInstance().storeOriginalState(entityRef, currentUse, currentHint, null);
                }
            } else {
                InteractionStateCache.getInstance().storeOriginalState(entityRef, origComp.getOriginalInteractionId(),
                        origComp.getOriginalHint(), null);
                logVerbose("[CustomAnimal] " + animalName + ": restored original from ECS");
            }

            if (currentUse == null || !currentUse.equals(FEED_INTERACTION_ID)) {
                if (currentUse != null && currentUse.startsWith("*")) {
                    interactions.setInteractionId(useType, null);
                    logVerbose("[CustomAnimal] " + animalName + ": cleared special interaction '" + currentUse + "' to null");
                }
                interactions.setInteractionId(useType, FEED_INTERACTION_ID);
                logVerbose("[CustomAnimal] " + animalName + ": set interaction to " + FEED_INTERACTION_ID);
            }

            // Only modify hints if flag is enabled (requires cleanup on mod disable)
            if (modifyInteractionHints) {
                interactions.setInteractionHint("server.interactionHints.legacyFeed");
            }
            logVerbose("[CustomAnimal] " + animalName + ": setup complete");

        } catch (Exception e) {
            logWarning("[CustomAnimal] " + animalName + ": setup error: " + e.getMessage());
        }
    }

    /**
     * Update an animal's interaction state based on breeding status.
     */
    @SuppressWarnings("unchecked")
    public void updateAnimalInteractionState(Ref<EntityStore> entityRef, AnimalType animalType, BreedingData data) {
        if (!useEntityBasedInteractions) {
            return;
        }

        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null)
                return;

            boolean shouldShowFeed = true;
            if (data != null) {
                if (data.isInLove()) {
                    shouldShowFeed = false;
                }
                long cooldown = configManager.getBreedingCooldown(animalType);
                if (data.getCooldownRemaining(cooldown) > 0) {
                    shouldShowFeed = false;
                }
            }

            Interactions interactions = store.getComponent(entityRef, EcsReflectionUtil.INTERACTIONS_TYPE);
            if (interactions == null)
                return;

            // Use cached InteractionType
            ensureInteractionTypesCached();
            InteractionType useType = cachedUseType;

            if (shouldShowFeed) {
                interactions.setInteractionId(useType, FEED_INTERACTION_ID);
                // Only modify hints if flag is enabled (requires cleanup on mod disable)
                if (modifyInteractionHints) {
                    String hintKey = animalType.isMountable()
                            ? "server.interactionHints.legacyFeedOrMount"
                            : "server.interactionHints.legacyFeed";
                    interactions.setInteractionHint(hintKey);
                }
            } else {
                // Restore original interaction (e.g., for mounting)
                OriginalInteractionState original = InteractionStateCache.getInstance().getOriginalState(entityRef);
                if (original != null) {
                    interactions.setInteractionId(useType, original.getInteractionId());
                    // Only restore hints if flag is enabled
                    if (modifyInteractionHints) {
                        if (original.hasHint()) {
                            interactions.setInteractionHint(original.getHint());
                        } else {
                            interactions.setInteractionHint((String) null);
                        }
                    }
                }
            }

        } catch (Exception e) {
            // Entity may have despawned
        }
    }

    /**
     * Update interaction states for all tracked animals.
     */
    public void updateTrackedAnimalStates() {
        if (!useEntityBasedInteractions) {
            return;
        }

        World world = Universe.get() != null ? Universe.get().getDefaultWorld() : null;
        if (world == null) {
            return;
        }

        // Collect data to process - skip if empty to avoid unnecessary world.execute() scheduling
        List<BreedingData> dataToProcess = new ArrayList<>();
        for (BreedingData data : breedingManager.getAllBreedingData()) {
            // Only include animals that have entity refs (others can't be updated)
            if (data.getEntityRef() != null) {
                dataToProcess.add(data);
            }
        }

        // Skip world.execute() entirely if nothing to process
        if (dataToProcess.isEmpty()) {
            return;
        }

        world.execute(() -> {
            for (BreedingData data : dataToProcess) {
                Object refObj = data.getEntityRef();
                AnimalType animalType = data.getAnimalType();

                if (refObj == null || animalType == null)
                    continue;

                try {
                    @SuppressWarnings("unchecked")
                    Ref<EntityStore> entityRef = (Ref<EntityStore>) refObj;

                    if (!entityRef.isValid()) {
                        data.setEntityRef(null);
                        continue;
                    }

                    updateAnimalInteractionState(entityRef, animalType, data);
                } catch (Exception e) {
                    data.setEntityRef(null);
                }
            }
        });
    }

    /**
     * Set up Ability2 hint on an entity (for item-based feeding).
     */
    public void setupAbility2HintOnly(Store<EntityStore> store, Ref<EntityStore> entityRef, String hintKey) {
        try {
            try {
                store.ensureAndGetComponent(entityRef, EcsReflectionUtil.INTERACTABLE_TYPE);
            } catch (Exception e) {
                // Silent
            }

            Interactions interactions = store.getComponent(entityRef, EcsReflectionUtil.INTERACTIONS_TYPE);
            if (interactions == null) {
                logVerbose("[SetupInteraction] Skipping non-NPC entity (no Interactions component)");
                return;
            }

            // Use cached InteractionType
            ensureInteractionTypesCached();
            InteractionType ability2Type = cachedAbility2Type;

            if (ability2Type == null) {
                logVerbose("Could not find Ability2 InteractionType");
                return;
            }

            interactions.setInteractionId(ability2Type, FEED_INTERACTION_ID);
            // Only modify hints if flag is enabled (requires cleanup on mod disable)
            if (modifyInteractionHints) {
                interactions.setInteractionHint(hintKey);
                logVerbose("Set up Ability2 hint: " + hintKey);
            } else {
                logVerbose("Set up Ability2 interaction (no custom hint)");
            }

        } catch (Exception e) {
            logVerbose("setupAbility2HintOnly error: " + e.getMessage());
        }
    }

    /**
     * Store the original interaction ID for a custom animal entity.
     */
    public void storeOriginalInteractionIdForCustom(Ref<EntityStore> entityRef, String originalId,
            CustomAnimalConfig customAnimal) {
        if (originalId != null && !originalId.isEmpty()) {
            InteractionStateCache.getInstance().storeOriginalState(entityRef, originalId, null, null);
        }
    }
}
