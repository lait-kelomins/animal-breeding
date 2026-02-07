package com.tameableanimals.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.laits.breeding.models.GrowthStage;
import com.tameableanimals.tame.HyTameComponent;

import javax.annotation.Nonnull;

/**
 * Sensor that checks if an entity's growth stage is NOT ADULT.
 * Used with alarm sensor to trigger growth when Growth_Ready alarm passes.
 *
 * Returns true if the entity can still grow (BABY or JUVENILE stage).
 */
public class SensorGrowthReady extends SensorBase {

    public SensorGrowthReady(@Nonnull BuilderSensorGrowthReady builder, @Nonnull BuilderSupport support) {
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

            // If no HyTameComponent, entity can't grow through our system
            if (hyTameComponent == null) {
                return false;
            }

            // Check if entity is not yet adult (can still grow)
            GrowthStage currentStage = hyTameComponent.getGrowthStage();
            boolean canGrow = currentStage != GrowthStage.ADULT;

            return super.matches(ref, role, dt, store) && canGrow;
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
