package com.laits.breeding.managers;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.RemoveReason;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.NameplateUtil;
import com.tameableanimals.tame.HyTameComponent;

import it.unimi.dsi.fastutil.Pair;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Manages spawning of baby animals and growth transformations.
 * Handles:
 * - Baby animal spawning (with and without baby variants)
 * - Custom animal baby spawning
 * - Entity scale updates during growth
 * - Baby to adult transformations
 */
public class SpawningManager {

    // Dependencies (injected)
    private BreedingManager breedingManager;
    private TamingManager tamingManager;
    private Supplier<ComponentType<EntityStore, HyTameComponent>> hyTameTypeSupplier;

    // Logging
    private boolean verboseLogging = false;
    private Consumer<String> logger;
    private Consumer<String> warningLogger;
    private Consumer<String> errorLogger;

    // Helper for getting model asset ID from entity
    private Function<Object[], String> modelAssetIdGetter;

    public SpawningManager() {
    }

    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================

    public void setBreedingManager(BreedingManager breedingManager) {
        this.breedingManager = breedingManager;
    }

    public void setTamingManager(TamingManager tamingManager) {
        this.tamingManager = tamingManager;
    }

    public void setHyTameTypeSupplier(Supplier<ComponentType<EntityStore, HyTameComponent>> supplier) {
        this.hyTameTypeSupplier = supplier;
    }

    public void setModelAssetIdGetter(Function<Object[], String> getter) {
        this.modelAssetIdGetter = getter;
    }

    // ========================================================================
    // LOGGING CONFIGURATION
    // ========================================================================

    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public void setWarningLogger(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    public void setErrorLogger(Consumer<String> errorLogger) {
        this.errorLogger = errorLogger;
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

    private void logError(String message) {
        if (errorLogger != null) {
            errorLogger.accept(message);
        }
    }

    // ========================================================================
    // BABY SPAWNING
    // ========================================================================

    /**
     * Spawn a baby animal with parent UUIDs for auto-taming.
     * If BOTH parents are tamed by the same player, the baby will be auto-tamed.
     *
     * For animals WITH baby variants: spawns baby NPC
     * For animals WITHOUT baby variants: spawns adult NPC at small scale (0.4)
     *
     * @param animalType The type of animal to spawn
     * @param position   The spawn position
     * @param parent1Id  UUID of first parent (pass null if unknown)
     * @param parent2Id  UUID of second parent (pass null if unknown)
     */
    public void spawnBabyAnimal(AnimalType animalType, Vector3d position, UUID parent1Id, UUID parent2Id) {
        try {
            boolean hasBabyVariant = animalType.hasBabyVariant();
            String roleId = hasBabyVariant ? animalType.getBabyNpcRoleId() : animalType.getAdultNpcRoleId();
            float initialScale = hasBabyVariant ? 1.0f : animalType.getScaleForStage(GrowthStage.BABY);

            logVerbose("Attempting to spawn " + (hasBabyVariant ? "baby" : "scaled adult") + ": " + roleId +
                    (hasBabyVariant ? "" : " at scale " + initialScale));

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot spawn baby - world is null");
                return;
            }

            final Vector3d spawnPos = position;
            final AnimalType finalAnimalType = animalType;
            final String finalRoleId = roleId;
            final boolean finalHasBabyVariant = hasBabyVariant;
            final float finalInitialScale = initialScale;
            final UUID finalParent1Id = parent1Id;
            final UUID finalParent2Id = parent2Id;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    boolean roleExists = NPCPlugin.get().hasRoleName(finalRoleId);

                    if (!roleExists) {
                        logWarning("NPC role not found: " + finalRoleId);
                        return;
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);

                    // Create scaled model if needed (for creatures without baby variants)
                    Model scaledModel = null;
                    if (!finalHasBabyVariant) {
                        try {
                            DefaultAssetMap<String, ModelAsset> assetMap = ModelAsset.getAssetMap();
                            ModelAsset modelAsset = assetMap.getAsset(finalAnimalType.getModelAssetId());

                            if (modelAsset != null) {
                                scaledModel = Model.createScaledModel(modelAsset, finalInitialScale);
                            }
                        } catch (Exception e) {
                            logWarning("Failed to create scaled model: " + e.getMessage());
                        }
                    }

                    Ref<EntityStore> entityRef = null;
                    int roleIndex = NPCPlugin.get().getIndex(finalRoleId);

                    if (roleIndex >= 0) {
                        try {
                            NPCPlugin.get().validateSpawnableRole(finalRoleId);
                        } catch (Exception e) {
                            // Silent
                        }

                        try {
                            NPCPlugin.get().prepareRoleBuilderInfo(roleIndex);
                        } catch (Exception e) {
                            // Silent
                        }

                        try {
                            entityRef = NPCPlugin.get()
                                    .spawnEntity(store, roleIndex, spawnPos, rotation, scaledModel, null, null)
                                    .first();
                        } catch (Exception e) {
                            // Silent
                        }
                    }

                    if (entityRef != null) {
                        String logMessage = finalHasBabyVariant
                                ? "Baby " + finalAnimalType.getId() + " born"
                                : "Young " + finalAnimalType.getId() + " born (scale "
                                        + String.format("%.1f", finalInitialScale) + ")";
                        logVerbose("[Lait:AnimalBreeding] " + logMessage + " at " +
                                String.format("%.0f, %.0f, %.0f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));

                        // Register baby with breeding manager
                        @SuppressWarnings("unchecked")
                        Ref<EntityStore> babyRefForUuid = (Ref<EntityStore>) entityRef;
                        UUID babyId = EcsReflectionUtil.getUuidFromRef(babyRefForUuid);
                        breedingManager.registerBaby(babyId, finalAnimalType, entityRef);

                        // Auto-tame baby if BOTH parents are tamed
                        autoTameBabyIfParentsTamed(store, entityRef, babyId, finalAnimalType, spawnPos,
                                finalParent1Id, finalParent2Id);

                    } else {
                        logWarning("Failed to spawn " + (finalHasBabyVariant ? "baby" : "young") + " "
                                + finalAnimalType.getId() + " - spawn returned null");
                    }

                } catch (Exception e) {
                    logError("Error spawning baby: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logError("Error in spawnBabyAnimal: " + e.getMessage());
        }
    }

    /**
     * Auto-tame a baby if both parents are tamed.
     */
    private void autoTameBabyIfParentsTamed(Store<EntityStore> store, Ref<EntityStore> entityRef,
            UUID babyId, AnimalType animalType, Vector3d spawnPos,
            UUID parent1Id, UUID parent2Id) {
        if (tamingManager == null || parent1Id == null || parent2Id == null) {
            return;
        }

        TamedAnimalData parent1Data = tamingManager.getTamedData(parent1Id);
        TamedAnimalData parent2Data = tamingManager.getTamedData(parent2Id);

        logVerbose("Parent1 UUID: " + parent1Id + " -> data: " + (parent1Data != null ? "found" : "NOT FOUND"));
        logVerbose("Parent2 UUID: " + parent2Id + " -> data: " + (parent2Data != null ? "found" : "NOT FOUND"));

        // Both parents must be tamed for baby to be auto-tamed
        if (parent1Data == null || parent2Data == null) {
            return;
        }

        // Get owner from first parent (or second if first has no owner)
        UUID ownerUuid = parent1Data.getOwnerUuid();
        if (ownerUuid == null) {
            ownerUuid = parent2Data.getOwnerUuid();
        }

        if (ownerUuid == null) {
            return;
        }

        String babyName = NameplateUtil.UNDEFINED_NAME;

        // Tame the baby with BABY growth stage
        TamedAnimalData babyTameData = tamingManager.tameAnimal(
                babyId,
                ownerUuid,
                babyName,
                animalType,
                entityRef,
                spawnPos.getX(),
                spawnPos.getY(),
                spawnPos.getZ(),
                GrowthStage.BABY);

        if (babyTameData != null) {
            String ownerName = babyTameData.getOwnerName();
            if (ownerUuid != null && ownerName != null && hyTameTypeSupplier != null) {
                ComponentType<EntityStore, HyTameComponent> hyTameType = hyTameTypeSupplier.get();
                if (hyTameType != null) {
                    HyTameComponent hyTameComp = store.ensureAndGetComponent(entityRef, hyTameType);
                    if (hyTameComp != null) {
                        hyTameComp.setTamed(ownerUuid, ownerName);
                        if (babyTameData.getHytameId() != null) {
                            hyTameComp.setHytameId(babyTameData.getHytameId());
                        }
                        logVerbose("Set HyTameComponent on baby: owner=" + ownerName +
                                ", hytameId=" + babyTameData.getHytameId());
                    }
                }
            }

            logVerbose("Auto-tamed baby " + babyName + " (UUID: " + babyId +
                    ") with growthStage: " + babyTameData.getGrowthStage() +
                    " to owner of both parents");
        } else {
            logVerbose("Failed to auto-tame baby - tameAnimal returned null");
        }
    }

    /**
     * Spawn a baby custom animal at the given position.
     * If babyNpcRoleId is set, spawn using that role at full scale.
     * Otherwise, use scaling fallback: spawn adult NPC at 40% scale.
     */
    public void spawnCustomAnimalBaby(String modelAssetId, CustomAnimalConfig customConfig, Vector3d position) {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            final String finalModelAssetId = modelAssetId;
            final CustomAnimalConfig finalConfig = customConfig;
            final Vector3d spawnPos = position;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    String usedRoleName = null;
                    boolean usingBabyRole = false;
                    boolean roleExists = false;

                    // 1. First, check if we have a dedicated baby NPC role
                    if (finalConfig != null && finalConfig.getBabyNpcRoleId() != null) {
                        roleExists = NPCPlugin.get().hasRoleName(finalConfig.getBabyNpcRoleId());
                        if (roleExists) {
                            usedRoleName = finalConfig.getBabyNpcRoleId();
                            usingBabyRole = true;
                            logVerbose("Using dedicated baby NPC role: " + usedRoleName);
                        }
                    }

                    // 2. If no baby role, use adult role with scaling fallback
                    if (!roleExists) {
                        String adultRole = finalConfig != null ? finalConfig.getAdultNpcRoleId() : null;
                        if (adultRole == null)
                            adultRole = finalModelAssetId;

                        roleExists = NPCPlugin.get().hasRoleName(adultRole);
                        if (roleExists) {
                            usedRoleName = adultRole;
                            logVerbose("Using adult NPC role with scaling: " + usedRoleName);
                        }
                    }

                    if (!roleExists || usedRoleName == null) {
                        logWarning("[CustomBreed] No valid NPC role found for: " + finalModelAssetId);
                        return;
                    }

                    // Spawn the entity
                    Vector3f rotation = new Vector3f(0, 0, 0);
                    int roleIndex = NPCPlugin.get().getIndex(usedRoleName);
                    Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos,
                            rotation, null, null);

                    if (result == null) {
                        logWarning("[CustomBreed] Failed to spawn baby: " + usedRoleName);
                        return;
                    }

                    Ref<EntityStore> babyRef = result.first();

                    // Apply scaling if not using baby role (40% size)
                    if (!usingBabyRole && babyRef != null) {
                        float babyScale = 0.4f;
                        try {
                            ModelComponent modelComp = store.getComponent(babyRef, ModelComponent.getComponentType());
                            if (modelComp != null) {
                                java.lang.reflect.Method setScale = modelComp.getClass().getMethod("setScale",
                                        float.class);
                                setScale.invoke(modelComp, babyScale);
                                logVerbose("Applied baby scale " + babyScale + " to custom animal");
                            }
                        } catch (Exception e) {
                            logVerbose("Could not apply scale: " + e.getMessage());
                        }
                    }

                    logVerbose("[CustomBreed] Spawned baby " + finalModelAssetId + " at " +
                            String.format("(%.1f, %.1f, %.1f)", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));

                } catch (Exception e) {
                    logWarning("[CustomBreed] Error spawning baby: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logWarning("[CustomBreed] Error in spawnCustomAnimalBaby: " + e.getMessage());
        }
    }

    // ========================================================================
    // GROWTH / SCALE UPDATES
    // ========================================================================

    /**
     * Update an entity's model scale (for creatures without baby variants).
     *
     * @param animalId   The animal's UUID
     * @param animalType The type of animal
     * @param scale      The target scale (0.4 for baby, 0.7 for juvenile, 1.0 for adult)
     */
    public void updateEntityScale(UUID animalId, AnimalType animalType, float scale) {
        try {
            logVerbose("Updating scale for " + animalType.getId() + " to " + scale);

            BreedingData data = breedingManager.getData(animalId);
            if (data == null) {
                logWarning("Cannot update scale - no breeding data for animal");
                return;
            }

            Ref<EntityStore> entityRef = data.getEntityRef();
            if (entityRef == null) {
                logWarning("Cannot update scale - no entity ref for animal");
                return;
            }

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot update scale - world is null");
                return;
            }

            final Ref<EntityStore> finalEntityRef = entityRef;
            final float targetScale = scale;
            final UUID finalAnimalId = animalId;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    ComponentType<EntityStore, ModelComponent> modelType = EcsReflectionUtil.MODEL_TYPE;

                    ModelComponent modelComp = null;
                    try {
                        modelComp = store.getComponent(finalEntityRef, modelType);
                    } catch (Exception refEx) {
                        Throwable cause = refEx;
                        if (refEx instanceof java.lang.reflect.InvocationTargetException) {
                            cause = ((java.lang.reflect.InvocationTargetException) refEx).getTargetException();
                        }
                        if (cause instanceof IllegalStateException &&
                                cause.getMessage() != null &&
                                cause.getMessage().contains("Invalid entity")) {
                            logVerbose("Entity ref is stale - removing tracking data");
                            breedingManager.removeData(finalAnimalId);
                            return;
                        }
                        throw refEx;
                    }

                    if (modelComp == null) {
                        logVerbose("Entity has no ModelComponent - removing stale data");
                        breedingManager.removeData(finalAnimalId);
                        return;
                    }

                    Model currentModel = modelComp.getModel();
                    if (currentModel == null) {
                        logWarning("Entity has no model - cannot scale");
                        return;
                    }

                    String modelAssetId = currentModel.getModelAssetId();
                    ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelAssetId);

                    if (modelAsset == null) {
                        logWarning("ModelAsset not found: " + modelAssetId);
                        return;
                    }

                    Model newModel = Model.createScaledModel(modelAsset, targetScale);
                    ModelComponent newModelComp = new ModelComponent(newModel);

                    store.replaceComponent(finalEntityRef, ModelComponent.getComponentType(), newModelComp);

                    logVerbose("Set model field to: " + newModel.toString());
                    logVerbose(capitalize(animalType.getId()) + " grew to scale " + String.format("%.1f", targetScale));

                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null) cause = e;
                    }
                    String errorMsg = cause.getMessage();
                    if (errorMsg == null) {
                        errorMsg = cause.getClass().getSimpleName() + " (no message)";
                    }
                    logError("Error updating entity scale: " + errorMsg);
                    cause.printStackTrace();
                }
            });

        } catch (Exception e) {
            logError("Error in updateEntityScale: " + e.getMessage());
        }
    }

    /**
     * Transform a baby animal into an adult by removing the baby and spawning an adult NPC.
     * Used for animals WITH baby variants (livestock).
     */
    public void transformBabyToAdult(UUID animalId, AnimalType animalType) {
        try {
            logVerbose("Transforming baby " + animalType.getId() + " to adult");

            BreedingData data = breedingManager.getData(animalId);
            if (data == null) {
                logWarning("Cannot transform - no breeding data for animal");
                return;
            }

            Ref<EntityStore> entityRef = data.getEntityRef();
            if (entityRef == null) {
                entityRef = tryReacquireBabyRef(animalId, animalType);
                if (entityRef != null) {
                    data.setEntityRef(entityRef);
                    logVerbose("Re-acquired entityRef for baby " + animalType.getId());
                } else {
                    // Silent fail
                    // logWarning("Cannot transform - no entity ref for animal (re-acquisition failed)");
                    return;
                }
            }

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot transform - world is null");
                return;
            }

            String adultRoleId = animalType.getModelAssetId();
            final Ref<EntityStore> finalEntityRef = entityRef;
            final UUID finalAnimalId = animalId;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    // Get baby position
                    TransformComponent transformComp = null;
                    try {
                        transformComp = store.getComponent(finalEntityRef, TransformComponent.getComponentType());
                    } catch (Exception refEx) {
                        Throwable cause = refEx;
                        if (refEx instanceof java.lang.reflect.InvocationTargetException) {
                            cause = ((java.lang.reflect.InvocationTargetException) refEx).getTargetException();
                        }
                        if (cause instanceof IllegalStateException &&
                                cause.getMessage() != null &&
                                cause.getMessage().contains("Invalid entity")) {
                            logVerbose("Baby entity ref is stale - removing tracking data");
                            breedingManager.removeData(finalAnimalId);
                            return;
                        }
                        throw refEx;
                    }

                    if (transformComp == null) {
                        logVerbose("Baby entity no longer exists - removing stale data");
                        breedingManager.removeData(finalAnimalId);
                        return;
                    }

                    Vector3d babyPosition = transformComp.getPosition();
                    if (babyPosition == null) {
                        logVerbose("Baby entity no longer has valid position - removing stale data");
                        breedingManager.removeData(finalAnimalId);
                        return;
                    }

                    // Remove the baby entity
                    try {
                        // Use reflection to find valid RemoveReason constant
                        RemoveReason despawnReason = null;
                        for (RemoveReason reason : RemoveReason.values()) {
                            String name = reason.name();
                            if (name.contains("DESPAWN") || name.contains("REMOVE") || name.contains("DELETE")) {
                                despawnReason = reason;
                                break;
                            }
                        }
                        if (despawnReason == null && RemoveReason.values().length > 0) {
                            despawnReason = RemoveReason.values()[0];
                        }
                        if (despawnReason != null) {
                            store.removeEntity(finalEntityRef, despawnReason);
                        }
                    } catch (Exception e) {
                        // Silent
                    }

                    // Spawn adult
                    int roleIndex = NPCPlugin.get().getIndex(adultRoleId);
                    if (roleIndex < 0) {
                        logWarning("Adult NPC role not found: " + adultRoleId);
                        return;
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);
                    Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get()
                            .spawnEntity(store, roleIndex, babyPosition, rotation, null, null);

                    if (result != null && result.first() != null) {
                        logVerbose(capitalize(animalType.getId()) + " grew into an adult at " +
                                String.format("%.0f, %.0f, %.0f", babyPosition.getX(),
                                        babyPosition.getY(), babyPosition.getZ()));
                    } else {
                        logWarning("Failed to spawn adult " + animalType.getId());
                    }

                    breedingManager.removeData(finalAnimalId);

                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null) cause = e;
                    }
                    String errorMsg = cause.getMessage();
                    if (errorMsg == null) {
                        errorMsg = cause.getClass().getSimpleName() + " (no message)";
                    }
                    logError("Error transforming to adult: " + errorMsg);
                    cause.printStackTrace();
                    breedingManager.removeData(finalAnimalId);
                }
            });

        } catch (Exception e) {
            logError("Error in transformBabyToAdult: " + e.getMessage());
        }
    }

    /**
     * Attempt to re-acquire an entityRef for a baby animal by scanning the world.
     */
    @SuppressWarnings("unchecked")
    private Ref<EntityStore> tryReacquireBabyRef(UUID animalId, AnimalType animalType) {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return null;

            String babyModelId = animalType.getBabyModelAssetId();
            if (babyModelId == null)
                return null;

            Store<EntityStore> store = world.getEntityStore().getStore();

            java.lang.reflect.Method getAllRefs = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("getAllRefs") && m.getParameterCount() == 0) {
                    getAllRefs = m;
                    break;
                }
            }

            if (getAllRefs == null)
                return null;

            Iterable<Ref<EntityStore>> refs = (Iterable<Ref<EntityStore>>) getAllRefs.invoke(store);

            for (Ref<EntityStore> ref : refs) {
                try {
                    String modelAssetId = null;
                    if (modelAssetIdGetter != null) {
                        modelAssetId = modelAssetIdGetter.apply(new Object[] { store, ref });
                    }
                    if (modelAssetId != null && modelAssetId.equalsIgnoreCase(babyModelId)) {
                        UUID candidateId = UUID.nameUUIDFromBytes(ref.toString().getBytes());
                        if (candidateId.equals(animalId)) {
                            logVerbose("tryReacquireBabyRef: Found matching baby by UUID");
                            return ref;
                        }

                        BreedingData foundData = breedingManager.findBabyByRef(ref);
                        if (foundData != null && foundData.getAnimalId().equals(animalId)) {
                            logVerbose("tryReacquireBabyRef: Found matching baby by ref comparison");
                            return ref;
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid refs
                }
            }

            logVerbose("tryReacquireBabyRef: No matching baby found for " + animalType.getId());
            return null;
        } catch (Exception e) {
            logVerbose("tryReacquireBabyRef error: " + e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Perform instant breeding between two animals.
     */
    public void performInstantBreeding(BreedingData animal1, BreedingData animal2, AnimalType type,
            Vector3d spawnPos) {
        animal1.completeBreeding();
        animal2.completeBreeding();
        spawnBabyAnimal(type, spawnPos, animal1.getAnimalId(), animal2.getAnimalId());
    }

    /**
     * Get position from BreedingData's entityRef.
     */
    @SuppressWarnings("unchecked")
    public Vector3d getPositionFromBreedingData(BreedingData data) {
        Object entityRef = data.getEntityRef();
        if (entityRef == null || !(entityRef instanceof Ref))
            return null;

        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return null;

            Store<EntityStore> store = world.getEntityStore().getStore();
            TransformComponent transform = store.getComponent((Ref<EntityStore>) entityRef,
                    EcsReflectionUtil.TRANSFORM_TYPE);

            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Silent
        }

        return null;
    }

    /**
     * Calculate distance between two positions.
     */
    public double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
