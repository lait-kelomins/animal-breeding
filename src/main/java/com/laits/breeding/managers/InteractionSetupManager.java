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

            if (!configManager.isAnimalEnabled(animalType))
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

            if (animalType != null && !configManager.isAnimalEnabled(animalType)) {
                logVerbose("Skipping disabled animal: " + animalType);
                return;
            }
            if (customAnimal != null && !customAnimal.isEnabled()) {
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

            // Set up interactions for adults - must be deferred
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
                            String hintKey = "animalbreeding.interactionHints.feed";
                            setupAbility2HintOnly(worldStore, finalEntityRef, hintKey);
                            logVerbose("Ability2 hint set up for: " + finalModelAssetId);
                        } else {
                            logVerbose("New adult animal detected: " + finalModelAssetId + " (no hints)");
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

            String feedInteractionId = "Root_FeedAnimal";

            InteractionType useType = null;
            for (InteractionType enumConst : InteractionType.class.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            String currentUse = interactions.getInteractionId(useType);
            String currentHint = interactions.getInteractionHint();

            ComponentType<EntityStore, HyTameInteractionComponent> hyTameType = getHyTameInteractionType();
            HyTameInteractionComponent origComp = hyTameType != null
                    ? store.getComponent(entityRef, hyTameType)
                    : null;

            if (origComp == null || !origComp.isCaptured()) {
                if (currentUse == null || !currentUse.equals(feedInteractionId)) {
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

            if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                interactions.setInteractionId(useType, feedInteractionId);
                logVerbose("[BuiltIn] " + animalType + ": set interaction to " + feedInteractionId);
            }

            String hintKey = animalType.isMountable()
                    ? "animalbreeding.interactionHints.legacyFeedOrMount"
                    : "animalbreeding.interactionHints.legacyFeed";
            interactions.setInteractionHint(hintKey);
            logVerbose("[SetupInteraction] SUCCESS for " + animalType + ": interactionId=" + feedInteractionId + ", hint=" + hintKey);

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

            String feedInteractionId = "Root_FeedAnimal";

            InteractionType useType = null;
            for (InteractionType enumConst : InteractionType.class.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            String currentUse = interactions.getInteractionId(useType);
            String currentHint = interactions.getInteractionHint();
            logVerbose("[CustomAnimal] " + animalName + ": currentUse='" + currentUse + "', currentHint='" + currentHint + "'");

            ComponentType<EntityStore, HyTameInteractionComponent> hyTameType = getHyTameInteractionType();
            HyTameInteractionComponent origComp = hyTameType != null
                    ? store.getComponent(entityRef, hyTameType)
                    : null;

            if (origComp == null || !origComp.isCaptured()) {
                if (currentUse == null || !currentUse.equals(feedInteractionId)) {
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

            if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                if (currentUse != null && currentUse.startsWith("*")) {
                    interactions.setInteractionId(useType, null);
                    logVerbose("[CustomAnimal] " + animalName + ": cleared special interaction '" + currentUse + "' to null");
                }
                interactions.setInteractionId(useType, feedInteractionId);
                logVerbose("[CustomAnimal] " + animalName + ": set interaction to " + feedInteractionId);
            }

            interactions.setInteractionHint("animalbreeding.interactionHints.legacyFeed");
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

            InteractionType useType = null;
            for (InteractionType enumConst : InteractionType.class.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            if (shouldShowFeed) {
                interactions.setInteractionId(useType, "Root_FeedAnimal");
                String hintKey = animalType.isMountable()
                        ? "animalbreeding.interactionHints.legacyFeedOrMount"
                        : "animalbreeding.interactionHints.legacyFeed";
                interactions.setInteractionHint(hintKey);
            } else {
                OriginalInteractionState original = InteractionStateCache.getInstance().getOriginalState(entityRef);
                if (original != null) {
                    interactions.setInteractionId(useType, original.getInteractionId());
                    if (original.hasHint()) {
                        interactions.setInteractionHint(original.getHint());
                    } else {
                        interactions.setInteractionHint((String) null);
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

        List<BreedingData> dataToProcess = new ArrayList<>();
        for (BreedingData data : breedingManager.getAllBreedingData()) {
            dataToProcess.add(data);
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

            InteractionType ability2Type = null;
            for (InteractionType enumConst : InteractionType.class.getEnumConstants()) {
                if (enumConst.toString().equals("Ability2")) {
                    ability2Type = enumConst;
                    break;
                }
            }

            if (ability2Type == null) {
                logVerbose("Could not find Ability2 InteractionType");
                return;
            }

            interactions.setInteractionId(ability2Type, "Root_FeedAnimal");
            interactions.setInteractionHint(hintKey);
            logVerbose("Set up Ability2 hint: " + hintKey);

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
