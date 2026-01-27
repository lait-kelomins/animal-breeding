package com.tameableanimals.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.tameableanimals.tame.TameComponent;

import javax.annotation.Nonnull;

public class SensorTamed extends SensorBase {
    protected final boolean value;

    public SensorTamed(@Nonnull BuilderSensorTamed builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.value = builder.getValue(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store) {
        TameComponent tameComponent = store.getComponent(ref, TameComponent.getComponentType());
        if (tameComponent == null) return false;

        return super.matches(ref, role, dt, store) && tameComponent.isTamed() == this.value;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
