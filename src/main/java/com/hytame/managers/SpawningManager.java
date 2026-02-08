package com.hytame.managers;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.RemoveReason;
import com.hytame.HyTamePlugin;
import com.hytame.models.AnimalType;
import com.hytame.models.BreedingData;
import com.hytame.models.CustomAnimalConfig;
import com.hytame.models.GrowthStage;
import com.hytame.models.TamedAnimalData;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.util.NameplateUtil;
import com.hytame.tame.HyTameComponent;

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

    private void logVerbose(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atInfo().log(message);
        }
    }

    private void logWarning(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atWarning().log(message);
        }
    }

    private void logError(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atSevere().log(message);
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
        spawnBabyAnimal(animalType, position, parent1Id, parent2Id, null);
    }

    /**
     * Spawn a baby animal with parent UUIDs for auto-taming in a specific world.
     * If BOTH parents are tamed by the same player, the baby will be auto-tamed.
     *
     * For animals WITH baby variants: spawns baby NPC
     * For animals WITHOUT baby variants: spawns adult NPC at small scale (0.4)
     *
     * @param animalType The type of animal to spawn
     * @param position   The spawn position
     * @param parent1Id  UUID of first parent (pass null if unknown)
     * @param parent2Id  UUID of second parent (pass null if unknown)
     * @param worldName  Name of the world to spawn in (null = default world)
     */
    public void spawnBabyAnimal(AnimalType animalType, Vector3d position, UUID parent1Id, UUID parent2Id,
            String worldName) {
        try {
            boolean hasBabyVariant = animalType.hasBabyVariant();
            String roleId = hasBabyVariant ? animalType.getBabyNpcRoleId() : animalType.getAdultNpcRoleId();
            float initialScale = hasBabyVariant ? 1.0f : animalType.getScaleForStage(GrowthStage.BABY);

            logVerbose("Attempting to spawn " + (hasBabyVariant ? "baby" : "scaled adult") + ": " + roleId +
                    (hasBabyVariant ? "" : " at scale " + initialScale));

            // Get world from name if provided, otherwise fall back to default
            World world = null;
            if (worldName != null) {
                world = Universe.get().getWorld(worldName);
                logVerbose("Using world: " + worldName);
            }
            if (world == null) {
                world = Universe.get().getDefaultWorld();
                logVerbose("Using default world");
            }
            if (world == null) {
                logWarning("Cannot spawn baby - world is null");
                return;
            }

            final World finalWorld = world;
            final Vector3d spawnPos = position;
            final AnimalType finalAnimalType = animalType;
            final String finalRoleId = roleId;
            final boolean finalHasBabyVariant = hasBabyVariant;
            final float finalInitialScale = initialScale;
            final UUID finalParent1Id = parent1Id;
            final UUID finalParent2Id = parent2Id;
            final String finalWorldName = worldName;

            finalWorld.execute(() -> {
                try {
                    Store<EntityStore> store = finalWorld.getEntityStore().getStore();
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
                        logVerbose("[HyTame] " + logMessage + " at " +
                                String.format("%.0f, %.0f, %.0f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));

                        // Register baby with breeding manager
                        @SuppressWarnings("unchecked")
                        Ref<EntityStore> babyRefForUuid = (Ref<EntityStore>) entityRef;
                        UUID babyId = EcsReflectionUtil.getUuidFromRef(babyRefForUuid);
                        breedingManager.registerBaby(babyId, finalAnimalType, entityRef);

                        // Set HyTameComponent.growthStage = BABY for alarm-based growth system
                        if (hyTameTypeSupplier != null) {
                            ComponentType<EntityStore, HyTameComponent> hyTameType = hyTameTypeSupplier.get();
                            if (hyTameType != null) {
                                HyTameComponent hyTameComp = store.ensureAndGetComponent(entityRef, hyTameType);
                                if (hyTameComp != null) {
                                    hyTameComp.setGrowthStage(GrowthStage.BABY);
                                    logVerbose("Set HyTameComponent.growthStage = BABY for new baby");
                                }
                            }
                        }

                        // Auto-tame baby if BOTH parents are tamed
                        autoTameBabyIfParentsTamed(store, entityRef, babyId, finalAnimalType, spawnPos,
                                finalParent1Id, finalParent2Id, finalWorldName);

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
            UUID parent1Id, UUID parent2Id, String worldName) {
        if (tamingManager == null || parent1Id == null || parent2Id == null) {
            return;
        }

        TamedAnimalData parent1Data = tamingManager.getTamedData(parent1Id);
        TamedAnimalData parent2Data = tamingManager.getTamedData(parent2Id);

        logVerbose("Parent1 UUID: " + parent1Id + " -> data: " + (parent1Data != null ? "found" : "NOT FOUND"));
        logVerbose("Parent2 UUID: " + parent2Id + " -> data: " + (parent2Data != null ? "found" : "NOT FOUND"));

        // Fallback: if direct UUID lookup fails, search by animal type near spawn position
        // This handles UUID mismatch from stale refs during deferred taming callbacks
        if (parent1Data == null || parent2Data == null) {
            for (TamedAnimalData candidate : tamingManager.getAllTamedAnimals()) {
                if (candidate.getAnimalType() != animalType) continue;

                double dx = candidate.getLastX() - spawnPos.getX();
                double dy = candidate.getLastY() - spawnPos.getY();
                double dz = candidate.getLastZ() - spawnPos.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 10.0) continue;

                if (parent1Data == null) {
                    parent1Data = candidate;
                    logVerbose("Fallback: found parent1 by proximity (" + String.format("%.1f", dist) + " blocks)");
                } else if (parent2Data == null && candidate != parent1Data) {
                    parent2Data = candidate;
                    logVerbose("Fallback: found parent2 by proximity (" + String.format("%.1f", dist) + " blocks)");
                    break;
                }
            }
        }

        // Both parents must be tamed for baby to be auto-tamed
        if (parent1Data == null || parent2Data == null) {
            return;
        }

        // Get owner from first parent (or second if first has no owner)
        UUID ownerUuid = parent1Data.getOwnerUuid();
        String ownerName = parent1Data.getOwnerName();
        if (ownerUuid == null) {
            ownerUuid = parent2Data.getOwnerUuid();
            ownerName = parent2Data.getOwnerName();
        }

        if (ownerUuid == null) {
            return;
        }

        // Ensure ownerName is never null (may be missing from older TamedAnimalData)
        if (ownerName == null) {
            ownerName = "Unknown";
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
                GrowthStage.BABY,
                worldName);

        if (babyTameData != null) {
            // Set owner name (not set by tameAnimal constructor)
            babyTameData.setOwnerName(ownerName);
            if (ownerUuid != null && hyTameTypeSupplier != null) {
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
        spawnCustomAnimalBaby(modelAssetId, customConfig, position, null);
    }

    /**
     * Spawn a baby custom animal at the given position in a specific world.
     * If babyNpcRoleId is set, spawn using that role at full scale.
     * Otherwise, use scaling fallback: spawn adult NPC at 40% scale.
     *
     * @param modelAssetId The model asset ID of the custom animal
     * @param customConfig The custom animal configuration
     * @param position     The spawn position
     * @param worldName    Name of the world to spawn in (null = default world)
     */
    public void spawnCustomAnimalBaby(String modelAssetId, CustomAnimalConfig customConfig, Vector3d position,
            String worldName) {
        try {
            // Get world from name if provided, otherwise fall back to default
            World world = null;
            if (worldName != null) {
                world = Universe.get().getWorld(worldName);
            }
            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }
            if (world == null)
                return;

            final World finalWorld = world;
            final String finalModelAssetId = modelAssetId;
            final CustomAnimalConfig finalConfig = customConfig;
            final Vector3d spawnPos = position;

            finalWorld.execute(() -> {
                try {
                    Store<EntityStore> store = finalWorld.getEntityStore().getStore();

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
                            ModelComponent modelComp = store.getComponent(babyRef, EcsReflectionUtil.MODEL_TYPE);
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
     * @param scale      The target scale (0.4 for baby, 0.7 for juvenile, 1.0 for
     *                   adult)
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
            // Check for null OR invalid (stale) refs
            if (entityRef == null || !entityRef.isValid()) {
                logVerbose("Cannot update scale - entity ref is " + (entityRef == null ? "null" : "stale"));
                return;
            }

            // Get world from BreedingData, fall back to default
            World world = null;
            String worldName = data.getWorldName();
            if (worldName != null) {
                world = Universe.get().getWorld(worldName);
            }
            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }
            if (world == null) {
                logWarning("Cannot update scale - world is null");
                return;
            }

            final World finalWorld = world;
            final Ref<EntityStore> finalEntityRef = entityRef;
            final float targetScale = scale;
            final UUID finalAnimalId = animalId;

            finalWorld.execute(() -> {
                try {
                    Store<EntityStore> store = finalWorld.getEntityStore().getStore();
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

                    store.replaceComponent(finalEntityRef, EcsReflectionUtil.MODEL_TYPE, newModelComp);

                    logVerbose("Set model field to: " + newModel.toString());
                    logVerbose(capitalize(animalType.getId()) + " grew to scale " + String.format("%.1f", targetScale));

                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null)
                            cause = e;
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
     * Transform a baby animal into an adult by removing the baby and spawning an
     * adult NPC.
     * Used for animals WITH baby variants (livestock).
     */
    public void transformBabyToAdult(UUID animalId, AnimalType animalType) {
        try {
            logWarning("Transforming baby " + animalType.getId() + " to adult (animalId=" + animalId + ")");
            logVerbose("Transforming baby " + animalType.getId() + " to adult (animalId=" + animalId + ")");

            BreedingData data = breedingManager.getData(animalId);
            if (data == null) {
                logVerbose("Cannot transform - no breeding data for animalId=" + animalId);
                return;
            }

            String entityWorldName = getWorldNameFromRef(data.getEntityRef());

            if (entityWorldName != null && data.getWorldName() == null) {
                data.setWorldName(entityWorldName);
            }

            Ref<EntityStore> entityRef = data.getEntityRef();

            // Check for null OR invalid (stale) refs - both need reacquisition
            // TODO: remove probably useless code
            if (entityRef == null || !entityRef.isValid()) {
                {
                    Ref<EntityStore> safeEntityRef = tryReacquireBabyRef(animalId, animalType, data.getWorldName());
                    if (safeEntityRef != null && safeEntityRef.isValid()) {
                        data.setEntityRef(safeEntityRef);
                        logVerbose("Re-acquired entityRef for baby " + animalType.getId());
                    } else {
                        logVerbose("Cannot transform - reacquisition failed for " + animalType.getId() +
                                " in world=" + data.getWorldName());
                        return;
                    }
                }
            }

            if (data.getEntityRef() == null || !data.getEntityRef().isValid())
            {
                // failsafe in case we couldn't get valid entity ref
                return;
            }

            final World finalWorld = Universe.get().getWorld(entityWorldName);
            String adultRoleId = animalType.getModelAssetId();
            final Ref<EntityStore> finalEntityRef = data.getEntityRef();
            final UUID finalAnimalId = animalId;

            finalWorld.execute(() -> {
                try {
                    Store<EntityStore> store = finalWorld.getEntityStore().getStore();

                    // Get baby position
                    TransformComponent transformComp = null;
                    try {
                        transformComp = store.getComponent(finalEntityRef, EcsReflectionUtil.TRANSFORM_TYPE);
                    } catch (ArrayIndexOutOfBoundsException aioobEx) {
                        // Entity ref became stale - index is invalid
                        logVerbose("Baby entity ref is stale (ArrayIndexOutOfBounds) - removing tracking data");
                        breedingManager.removeData(finalAnimalId);
                        return;
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
                        if (cause instanceof ArrayIndexOutOfBoundsException) {
                            logVerbose(
                                    "Baby entity ref is stale (ArrayIndexOutOfBounds wrapped) - removing tracking data");
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

                    // Check if baby is tamed - save data for transfer to adult
                    UUID babyUuid = getUuidFromRef(finalEntityRef);
                    TamedAnimalData tamedData = null;
                    if (babyUuid != null && tamingManager != null) {
                        tamedData = tamingManager.getTamedData(babyUuid);
                        if (tamedData != null) {
                            logVerbose("Baby is tamed - will transfer data to adult: " +
                                tamedData.getCustomName() + " (owner=" + tamedData.getOwnerName() + ")");
                        }
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
                            // Use CommandBuffer for deferred removal to avoid race condition
                            // where PositionCacheSystems tries to access invalidated entity ref
                            final RemoveReason finalReason = despawnReason;
                            store.forEachChunk((chunk, commandBuffer) -> {
                                if (finalEntityRef.isValid()) {
                                    commandBuffer.removeEntity(finalEntityRef, finalReason);
                                }
                            });
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
                        Ref<EntityStore> adultRef = result.first();

                        // Transfer taming data from baby to adult
                        if (tamedData != null && babyUuid != null) {
                            UUID adultUuid = getUuidFromRef(adultRef);
                            if (adultUuid != null) {
                                // Get taming info from baby's TamedAnimalData
                                UUID ownerUuid = tamedData.getOwnerUuid();
                                String ownerName = tamedData.getOwnerName();
                                UUID hytameId = tamedData.getHytameId();
                                String customName = tamedData.getCustomName();

                                // Apply HyTameComponent to adult
                                if (ownerUuid != null && hyTameTypeSupplier != null) {
                                    ComponentType<EntityStore, HyTameComponent> hyTameType = hyTameTypeSupplier.get();
                                    if (hyTameType != null) {
                                        HyTameComponent hyTameComp = store.ensureAndGetComponent(adultRef, hyTameType);
                                        if (hyTameComp != null) {
                                            String effectiveOwnerName = (ownerName != null) ? ownerName : "Unknown";
                                            hyTameComp.setTamed(ownerUuid, effectiveOwnerName);
                                            if (hytameId != null) {
                                                hyTameComp.setHytameId(hytameId);
                                            }
                                            logVerbose("Set HyTameComponent on adult: owner=" + effectiveOwnerName +
                                                    ", hytameId=" + hytameId);
                                        }
                                    }
                                }

                                // Re-index persistence from baby UUID to adult UUID
                                // This prevents DetectTamedDespawn from marking baby for respawn
                                tamingManager.markRespawned(babyUuid, adultUuid, adultRef);

                                // Update growth stage to ADULT in tamed data
                                TamedAnimalData updatedTamedData = tamingManager.getTamedData(adultUuid);
                                if (updatedTamedData != null) {
                                    updatedTamedData.setGrowthStage(GrowthStage.ADULT);
                                }

                                // Restore nameplate
                                if (customName != null && !customName.isEmpty()
                                        && !customName.equalsIgnoreCase(NameplateUtil.UNDEFINED_NAME)) {
                                    NameplateUtil.setEntityNameplate(adultRef, customName);
                                }

                                // Create BreedingData for adult with taming info
                                BreedingData adultBreedingData = breedingManager.getOrCreateData(adultUuid, animalType);
                                adultBreedingData.setTamed(true, ownerUuid);
                                adultBreedingData.setCustomName(customName);
                                adultBreedingData.setEntityRef(adultRef);
                                adultBreedingData.setGrowthStage(GrowthStage.ADULT);

                                logVerbose("Transferred taming data from baby to adult: " +
                                    customName + " (owner=" + ownerName + ", hytameId=" + hytameId + ")");
                            }
                        }

                        logVerbose(capitalize(animalType.getId()) + " grew into an adult at " +
                                String.format("%.0f, %.0f, %.0f", babyPosition.getX(),
                                        babyPosition.getY(), babyPosition.getZ()));
                    } else {
                        logWarning("Failed to spawn adult " + animalType.getId());
                    }

                    breedingManager.removeData(finalAnimalId);

                } catch (ArrayIndexOutOfBoundsException aioobEx) {
                    // Entity ref became stale during transformation - this is expected
                    logVerbose("Entity ref became stale during transformation - cleaning up");
                    breedingManager.removeData(finalAnimalId);
                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null)
                            cause = e;
                    }
                    // Handle wrapped ArrayIndexOutOfBoundsException
                    if (cause instanceof ArrayIndexOutOfBoundsException) {
                        logVerbose("Entity ref became stale during transformation (wrapped) - cleaning up");
                        breedingManager.removeData(finalAnimalId);
                        return;
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
     * Get the world name by searching all worlds for the given entity.
     * This is more reliable than trying to get world from store's external data.
     */
    private String getWorldNameFromRef(Ref<EntityStore> ref) {
        if (ref == null)
            return null;

        try {
            UUID entityUuid = getUuidFromRef(ref);
            if (entityUuid == null) {
                logVerbose("[WorldDebug] Could not get UUID from ref");
                return null;
            }

            logVerbose("[WorldDebug] Searching all worlds for entity UUID: " + entityUuid);

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
                        logVerbose("[WorldDebug] Found entity in world: " + worldName);
                        return worldName;
                    }
                } catch (Exception e) {
                    // Skip this world if we can't access its store
                }
            }

            logVerbose("[WorldDebug] Entity not found in any world");
        } catch (Exception e) {
            logVerbose("getWorldNameFromRef error: " + e.getMessage());
        }
        return null;
    }

    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        // Delegate to EcsReflectionUtil for consistent UUID handling across all systems
        // This uses UUIDComponent first (stable), falling back to ref-based UUID
        return ref != null ? EcsReflectionUtil.getUuidFromRef(ref) : null;
    }

    /**
     * Attempt to re-acquire an entityRef for a baby animal by scanning the world.
     */
    @SuppressWarnings("unchecked")
    private Ref<EntityStore> tryReacquireBabyRef(UUID animalId, AnimalType animalType, String worldName) {
        try {
            // Get world from name if provided, otherwise fall back to default
            World world = null;
            if (worldName != null) {
                world = Universe.get().getWorld(worldName);
            }
            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }
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
                        UUIDComponent uuidComponent = store.getComponent(ref, EcsReflectionUtil.UUID_TYPE);

                        if (uuidComponent != null) {
                            UUID candidateId = uuidComponent.getUuid();
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
                    }
                } catch (Exception e) {
                    // Skip invalid refs
                    logVerbose("Error while reacquiring ref");
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
            // Get world from BreedingData, fall back to default
            World world = null;
            String worldName = data.getWorldName();
            if (worldName != null) {
                world = Universe.get().getWorld(worldName);
            }
            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }
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
