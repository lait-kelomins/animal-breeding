package com.hytame.tame.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytame.tame.HyTameComponent;

import javax.annotation.Nonnull;

/**
 * Sensor that checks if an entity's needsBreedCooldown flag is set.
 * Used with alarm sensor to trigger Breed_Cooldown alarm after breeding completes.
 *
 * Returns true if the entity has needsBreedCooldown == true.
 */
public class SensorNeedsBreedCooldown extends SensorBase {

    public SensorNeedsBreedCooldown(@Nonnull BuilderSensorNeedsBreedCooldown builder, @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store) {
        try {
            var componentType = HyTameComponent.getComponentType();
            if (componentType == null) {
                return false;
            }

            HyTameComponent hyTameComponent = store.getComponent(ref, componentType);
            if (hyTameComponent == null) {
                return false;
            }

            return super.matches(ref, role, dt, store) && hyTameComponent.isNeedsBreedCooldown();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
