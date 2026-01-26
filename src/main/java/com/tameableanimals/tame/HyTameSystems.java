package com.tameableanimals.tame;

import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.config.AttitudeGroup;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.util.ConfigManager;
import com.tameableanimals.utils.Debug;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ECS systems for taming functionality.
 *
 * Systems:
 * - HyTameActivateSystem: HolderSystem that sets up HyTameComponent on entity add
 * - HyTameTickSystem: EntityTickingSystem that manages actionReady state based on cooldowns
 */
public class HyTameSystems {

    // ============================================
    // ECS SYSTEM 1: Entity add/remove handling (HolderSystem)
    // ============================================

    /**
     * HolderSystem that runs when entities are added/removed.
     * Ensures HyTameComponent exists on tameable animals and restores tamed state.
     */
    public static class HyTameActivateSystem extends HolderSystem<EntityStore> {
        @Nonnull
        private final ComponentType<EntityStore, NPCEntity> npcComponentType;
        private final ComponentType<EntityStore, HyTameComponent> hyTameComponentType;
        private final Query<EntityStore> query;
        private final Set<Dependency<EntityStore>> dependencies;
        private final Set<String> validGroups;

        public HyTameActivateSystem() {
            this.npcComponentType = Objects.requireNonNull(NPCEntity.getComponentType());
            this.hyTameComponentType = HyTameComponent.getComponentType();
            this.query = Query.and(npcComponentType, Query.not(NPCMountComponent.getComponentType()));
            this.dependencies = Set.of(new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class));
            // Use Laits ConfigManager for tameable animal groups
            this.validGroups = LaitsBreedingPlugin.getInstance().getConfigManager().getTameableAnimalGroups();
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return this.query;
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return this.dependencies;
        }

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

            // Setup taming - ensure HyTameComponent exists
            HyTameComponent hyTameComponent = holder.ensureAndGetComponent(this.hyTameComponentType);
            Debug.log("Added HyTameComponent to entity " + npcEntity.getRoleName(), Level.INFO);
            if (hyTameComponent.isTamed()) {
                try {
                    LaitsBreedingPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);
                } catch (IllegalAccessException e) {
                    LaitsBreedingPlugin.getInstance().getLogger().atSevere().log("Failed to override attitude for NPC", e);
                }

                // Remove from over population tracking
                boolean oldState = npcEntity.updateSpawnTrackingState(false);
                if (oldState) {
                    Debug.log("Stopped tracking entity " + npcEntity.getRoleName(), Level.INFO);
                }
            }
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason, @Nonnull Store<EntityStore> store) {
            // Cleanup if needed - handled by DetectTamedDeath/DetectTamedDespawn
        }
    }

    // ============================================
    // ECS SYSTEM 2: Cooldown tick (EntityTickingSystem)
    // ============================================

    /**
     * EntityTickingSystem that ticks on entities with HyTameComponent.
     * Updates actionReady based on breeding state:
     * - Disabled when in love mode
     * - Disabled when on breeding cooldown
     * - Enabled when ready to breed or ready to be tamed
     *
     * Note: This ticks every frame. To reduce overhead, we use a simple check
     * and avoid heavy operations. The state changes are infrequent.
     */
    public static class HyTameTickSystem extends EntityTickingSystem<EntityStore> {
        private static final ComponentType<EntityStore, HyTameComponent> HYTAME_TYPE =
                HyTameComponent.getComponentType();
        private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE =
                UUIDComponent.getComponentType();

        // Throttle tick to reduce overhead (check every ~1 second at 20 TPS)
        private static final int TICK_INTERVAL = 20;
        private int tickCounter = 0;

        @Override
        public Query<EntityStore> getQuery() {
            // Only tick on entities with HyTameComponent
            return HYTAME_TYPE;
        }

        @Override
        public void tick(
                float dt,
                int index,
                @Nonnull ArchetypeChunk<EntityStore> chunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer
        ) {
            // Throttle ticks for performance
            tickCounter++;
            if (tickCounter < TICK_INTERVAL) {
                return;
            }
            tickCounter = 0;

            try {
                HyTameComponent hyTame = chunk.getComponent(index, HYTAME_TYPE);
                if (hyTame == null) return;

                Ref<EntityStore> ref = chunk.getReferenceTo(index);
                if (ref == null || !ref.isValid()) return;

                boolean isTamed = hyTame.isTamed();

                if (!isTamed) {
                    // Not tamed yet - always ready for taming
                    if (!hyTame.isActionReady()) {
                        hyTame.setActionReady(true);
                    }
                    return;
                }

                // === BREEDING STATE CHECK ===
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null) return;

                BreedingManager manager = plugin.getBreedingManager();
                ConfigManager config = plugin.getConfigManager();
                if (manager == null || config == null) return;

                // Get UUID to lookup breeding data
                UUIDComponent uuidComp = chunk.getComponent(index, UUID_TYPE);
                if (uuidComp == null) {
                    // No UUID - default to ready
                    if (!hyTame.isActionReady()) {
                        hyTame.setActionReady(true);
                    }
                    return;
                }

                UUID animalId = uuidComp.getUuid();
                BreedingData data = manager.getData(animalId);

                if (data == null) {
                    // No breeding data = ready for breeding (first time)
                    if (!hyTame.isActionReady()) {
                        hyTame.setActionReady(true);
                    }
                    return;
                }

                // Check if in love mode - action disabled
                if (data.isInLove()) {
                    if (hyTame.isActionReady()) {
                        hyTame.setActionReady(false);
                    }
                    return;
                }

                // Check breeding cooldown
                long cooldownMs = config.getBreedingCooldown(data.getAnimalType());
                if (!data.canBreed(cooldownMs)) {
                    // Still on cooldown
                    if (hyTame.isActionReady()) {
                        hyTame.setActionReady(false);
                    }
                    return;
                }

                // Ready for breeding
                if (!hyTame.isActionReady()) {
                    hyTame.setActionReady(true);
                }

            } catch (Exception e) {
                // Silent - don't spam logs on every tick
            }
        }
    }
}
