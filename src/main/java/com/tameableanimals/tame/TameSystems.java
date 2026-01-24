package com.tameableanimals.tame;

import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.config.AttitudeGroup;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;
import com.tameableanimals.config.ConfigManager;
import com.tameableanimals.TameableAnimalsPlugin;
import com.tameableanimals.utils.Debug;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public class TameSystems {
    public static class TameActivateSystem extends HolderSystem<EntityStore>
    {
        @Nonnull
        private final ComponentType<EntityStore, NPCEntity> npcComponentType;
        private final ComponentType<EntityStore, TameComponent> tameComponentType;
        private final Query<EntityStore> query;
        private final Set<Dependency<EntityStore>> dependencies;
        private final Set<String> validGroups;

        public TameActivateSystem() {
            this.npcComponentType = Objects.requireNonNull(NPCEntity.getComponentType());
            this.tameComponentType = TameComponent.getComponentType();
            this.query = Query.and(npcComponentType, Query.not(NPCMountComponent.getComponentType()));
            this.dependencies = Set.of(new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class));
            this.validGroups = ConfigManager.getConfig().getTameableAnimalGroups();
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() { return this.query; }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() { return this.dependencies; }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason, @Nonnull Store<EntityStore> store) {

            // Ensure supported animal
            NPCEntity npcEntity = holder.getComponent(this.npcComponentType);
            if (npcEntity == null) return;

            Role role = npcEntity.getRole();
            if (role == null) return;

            WorldSupport worldSupport = role.getWorldSupport();

            AttitudeGroup attitudeGroup = AttitudeGroup.getAssetMap().getAsset(worldSupport.getAttitudeGroup());
            if (attitudeGroup == null || !validGroups.contains(attitudeGroup.getId())) return;

            // Setup taming
            TameComponent tameComponent = holder.ensureAndGetComponent(this.tameComponentType);
            if (tameComponent.isTamed()) {
                try {
                    TameableAnimalsPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);
                } catch (IllegalAccessException e) {
                    TameableAnimalsPlugin.get().getLogger().atSevere().log("Failed to override attitude for NPC", e);
                }

                // Remove from over population tracking
                boolean oldState = npcEntity.updateSpawnTrackingState(false);
                if (oldState == true) Debug.log("Stopped tacking entity " + npcEntity.getRoleName(), Level.INFO);
            }

        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {

        }
    }
}
