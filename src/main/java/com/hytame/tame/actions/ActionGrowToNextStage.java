package com.hytame.tame.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytame.HyTamePlugin;
import com.hytame.models.AnimalType;
import com.hytame.models.GrowthStage;
import com.hytame.models.TamedAnimalData;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.util.NameplateUtil;
import com.hytame.tame.HyTameComponent;

import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Action that triggers growth transition for baby animals.
 *
 * Growth Logic:
 * - For Baby NPC roles (livestock): despawn baby, spawn adult at same position
 * - For Scaled babies (wild): update model scale to next stage
 *
 * Growth Stages: BABY -> JUVENILE -> ADULT (or BABY -> ADULT for baby NPC roles)
 *
 * This action is triggered when:
 * 1. Growth_Ready alarm has passed
 * 2. GrowthReady sensor returns true (not yet adult)
 */
public class ActionGrowToNextStage extends ActionBase {

    public ActionGrowToNextStage(@Nonnull BuilderActionGrowToNextStage builder, @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        // Check if entity has HyTameComponent with non-adult growth stage
        HyTameComponent hyTame = store.getComponent(ref, HyTameComponent.getComponentType());
        if (hyTame == null) {
            return false;
        }
        return hyTame.canGrow();
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        log("ActionGrowToNextStage: execute called");

        // Get HyTameComponent
        HyTameComponent hyTame = store.getComponent(ref, HyTameComponent.getComponentType());
        if (hyTame == null) {
            log("ActionGrowToNextStage: HyTameComponent not found");
            return false;
        }

        GrowthStage currentStage = hyTame.getGrowthStage();
        if (currentStage == GrowthStage.ADULT) {
            log("ActionGrowToNextStage: Already adult, nothing to do");
            return false;
        }

        // Get model to determine animal type
        ModelComponent modelComp = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
        if (modelComp == null || modelComp.getModel() == null) {
            log("ActionGrowToNextStage: No model component");
            return false;
        }

        String modelAssetId = modelComp.getModel().getModelAssetId();
        AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);

        if (animalType == null) {
            log("ActionGrowToNextStage: Unknown animal type: " + modelAssetId);
            return false;
        }

        // Determine growth strategy
        if (animalType.hasBabyVariant()) {
            // Baby NPC role (livestock) - transform to adult
            return executeTransformToAdult(ref, role, store, hyTame, animalType);
        } else {
            // Scaled baby - update scale
            return executeScaleGrowth(ref, role, store, hyTame, animalType, modelComp);
        }
    }

    /**
     * Transform a baby NPC (livestock) into an adult by despawning baby and spawning adult.
     * Baby -> Adult transition (single step for baby NPC roles).
     */
    private boolean executeTransformToAdult(Ref<EntityStore> ref, Role role, Store<EntityStore> store,
            HyTameComponent hyTame, AnimalType animalType) {
        log("ActionGrowToNextStage: Transforming baby NPC to adult: " + animalType.getId());

        // Get current position
        TransformComponent transform = store.getComponent(ref, EcsReflectionUtil.TRANSFORM_TYPE);
        if (transform == null) {
            log("ActionGrowToNextStage: No transform component");
            return false;
        }

        Vector3d position = transform.getPosition();
        if (position == null) {
            log("ActionGrowToNextStage: No position");
            return false;
        }

        // Copy tame data to transfer to adult
        boolean wasTamed = hyTame.isTamed();
        java.util.UUID tamerUUID = hyTame.getTamerUUID();
        String tamerName = hyTame.getTamerName();
        java.util.UUID hytameId = hyTame.getHytameId();

        // Get adult NPC role ID
        String adultRoleId = animalType.getAdultNpcRoleId();
        int roleIndex = NPCPlugin.get().getIndex(adultRoleId);

        if (roleIndex < 0) {
            log("ActionGrowToNextStage: Adult role not found: " + adultRoleId);
            return false;
        }

        // Spawn adult
        Vector3f rotation = new Vector3f(0, 0, 0);
        Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get()
                .spawnEntity(store, roleIndex, position, rotation, null, null);

        if (result == null || result.first() == null) {
            log("ActionGrowToNextStage: Failed to spawn adult");
            return false;
        }

        Ref<EntityStore> adultRef = result.first();

        // Defer tame transfer + baby removal to next tick
        // ensureAndGetComponent doesn't work reliably during NPC tick iteration
        try {
            RemoveReason removeReason = findRemoveReason();
            World world = ((EntityStore) store.getExternalData()).getWorld();
            world.execute(() -> {
                try {
                    // Transfer tame data to adult
                    if (wasTamed && adultRef.isValid()) {
                        HyTameComponent adultTame = store.ensureAndGetComponent(adultRef, HyTameComponent.getComponentType());
                        if (adultTame != null && tamerUUID != null && tamerName != null) {
                            adultTame.setTamed(tamerUUID, tamerName);
                            if (hytameId != null) {
                                adultTame.setHytameId(hytameId);
                            }
                            adultTame.setGrowthStage(GrowthStage.ADULT);
                            log("ActionGrowToNextStage: Transferred tame data to adult");

                            // Update TamingManager: re-key UUID maps for the new adult entity
                            HyTamePlugin plugin = HyTamePlugin.getInstance();
                            if (plugin != null && plugin.getTamingManager() != null && hytameId != null) {
                                UUID newAdultUuid = EcsReflectionUtil.getUuidFromRef(adultRef);
                                if (newAdultUuid != null) {
                                    plugin.getTamingManager().updateEntityAfterGrowth(hytameId, newAdultUuid, adultRef);

                                    // Restore nameplate from TamedAnimalData custom name
                                    TamedAnimalData tamedData = plugin.getTamingManager().getTamedData(newAdultUuid);
                                    if (tamedData != null) {
                                        String customName = tamedData.getCustomName();
                                        if (customName != null && !customName.isEmpty()
                                                && !customName.equalsIgnoreCase(NameplateUtil.UNDEFINED_NAME)) {
                                            NameplateUtil.setEntityNameplate(adultRef, customName);
                                            log("ActionGrowToNextStage: Restored nameplate: " + customName);
                                        }
                                    }
                                } else {
                                    plugin.getTamingManager().updateEntityRef(hytameId, adultRef, true);
                                }
                            }
                        }
                    }

                    // Remove baby entity
                    if (removeReason != null && ref.isValid()) {
                        store.removeEntity(ref, removeReason);
                    }
                } catch (Exception ex) {
                    log("ActionGrowToNextStage: Deferred operations failed: " + ex.getMessage());
                }
            });
        } catch (Exception e) {
            log("ActionGrowToNextStage: Error scheduling deferred operations: " + e.getMessage());
        }

        log("ActionGrowToNextStage: " + animalType.getId() + " grew into an adult at " +
                String.format("%.0f, %.0f, %.0f", position.getX(), position.getY(), position.getZ()));

        return true;
    }

    /**
     * Update model scale for a scaled baby (wild animals).
     * BABY -> JUVENILE -> ADULT progression.
     */
    private boolean executeScaleGrowth(Ref<EntityStore> ref, Role role, Store<EntityStore> store,
            HyTameComponent hyTame, AnimalType animalType, ModelComponent modelComp) {
        GrowthStage currentStage = hyTame.getGrowthStage();
        GrowthStage nextStage = currentStage.getNextStage();

        log("ActionGrowToNextStage: Scaling " + animalType.getId() + " from " + currentStage + " to " + nextStage);

        // Get target scale
        float targetScale = animalType.getScaleForStage(nextStage);

        // Get model asset for creating scaled model
        Model currentModel = modelComp.getModel();
        String modelAssetId = currentModel.getModelAssetId();
        ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelAssetId);

        if (modelAsset == null) {
            log("ActionGrowToNextStage: ModelAsset not found: " + modelAssetId);
            return false;
        }

        try {
            // Create new scaled model
            Model newModel = Model.createScaledModel(modelAsset, targetScale);
            ModelComponent newModelComp = new ModelComponent(newModel);

            // Replace model component
            store.replaceComponent(ref, EcsReflectionUtil.MODEL_TYPE, newModelComp);

            // Update growth stage
            hyTame.setGrowthStage(nextStage);

            log("ActionGrowToNextStage: " + animalType.getId() + " grew to " + nextStage +
                    " (scale " + String.format("%.1f", targetScale) + ")");

            // If now adult, update TamingManager
            if (nextStage == GrowthStage.ADULT) {
                HyTamePlugin plugin = HyTamePlugin.getInstance();
                if (plugin != null && plugin.getTamingManager() != null && hyTame.getHytameId() != null) {
                    plugin.getTamingManager().updateGrowthStage(hyTame.getHytameId(), GrowthStage.ADULT);
                }
            }

            return true;
        } catch (Exception e) {
            log("ActionGrowToNextStage: Error scaling model: " + e.getMessage());
            return false;
        }
    }

    /**
     * Find a valid RemoveReason enum constant.
     */
    private RemoveReason findRemoveReason() {
        for (RemoveReason reason : RemoveReason.values()) {
            String name = reason.name();
            if (name.contains("DESPAWN") || name.contains("REMOVE") || name.contains("DELETE")) {
                return reason;
            }
        }
        // Fallback to first value
        if (RemoveReason.values().length > 0) {
            return RemoveReason.values()[0];
        }
        return null;
    }

    private void log(String msg) {
        // Only log if verbose logging is enabled
        if (!HyTamePlugin.isVerboseLogging()) {
            return;
        }

        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[Growth] " + msg);
        }
    }
}
