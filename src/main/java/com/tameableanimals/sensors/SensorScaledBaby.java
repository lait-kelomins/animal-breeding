package com.tameableanimals.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.EcsReflectionUtil;
import com.tameableanimals.tame.HyTameComponent;

import javax.annotation.Nonnull;

/**
 * Sensor that checks if an entity is a "scaled baby" - an animal that uses
 * scaling for growth instead of having a dedicated baby NPC variant.
 *
 * Scaled babies are:
 * - Animals WITHOUT baby NPC variants (wolves, bears, foxes, etc.)
 * - Currently at BABY or JUVENILE growth stage
 * - Using ModelComponent scale < 1.0
 *
 * This sensor returns true when:
 * 1. The animal type does NOT have a baby NPC variant (uses scaling)
 * 2. The HyTameComponent.growthStage is not ADULT
 *
 * Used to trigger growth updates for scaled animals when Growth_Ready alarm passes.
 */
public class SensorScaledBaby extends SensorBase {

    public SensorScaledBaby(@Nonnull BuilderSensorScaledBaby builder, @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store) {
        try {
            // Get HyTameComponent to check growth stage
            var componentType = HyTameComponent.getComponentType();
            if (componentType == null) {
                return false;
            }

            HyTameComponent hyTameComponent = store.getComponent(ref, componentType);

            // If no HyTameComponent, can't be a tracked baby
            if (hyTameComponent == null) {
                return false;
            }

            // Check if not yet adult
            GrowthStage currentStage = hyTameComponent.getGrowthStage();
            if (currentStage == GrowthStage.ADULT) {
                return false;
            }

            // Get the model asset ID to determine animal type
            ModelComponent modelComp = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
            if (modelComp == null) {
                return false;
            }

            Model model = modelComp.getModel();
            if (model == null) {
                return false;
            }

            String modelAssetId = model.getModelAssetId();
            if (modelAssetId == null) {
                return false;
            }

            // Get the animal type
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            if (animalType == null) {
                return false;
            }

            // Check if this animal uses scaling (does NOT have a baby NPC variant)
            // Baby NPC variants (livestock) like Cow_Calf use separate NPC roles
            // Scaled babies (wolves, bears, etc.) use the adult NPC role with scaled model
            boolean usesScaling = !animalType.hasBabyVariant();

            return super.matches(ref, role, dt, store) && usesScaling;
        } catch (Exception e) {
            // If anything goes wrong, don't match (fail safely)
            return false;
        }
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
