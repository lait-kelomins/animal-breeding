package com.hytame.tame.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytame.tame.HyTameComponent;

import javax.annotation.Nonnull;

/**
 * Action that clears the needsBreedCooldown flag on HyTameComponent.
 * Used by passive instructions after setting the Breed_Cooldown alarm,
 * and as a harmless no-op when alarm passes.
 */
public class ActionClearBreedCooldownFlag extends ActionBase {

    public ActionClearBreedCooldownFlag(@Nonnull BuilderActionClearBreedCooldownFlag builder, @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        try {
            HyTameComponent hyTame = store.getComponent(ref, HyTameComponent.getComponentType());
            return hyTame != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo,
            double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        try {
            HyTameComponent hyTame = store.getComponent(ref, HyTameComponent.getComponentType());
            if (hyTame != null) {
                hyTame.setNeedsBreedCooldown(false);
                return true;
            }
        } catch (Exception e) {
            // Fail safely
        }
        return false;
    }
}
