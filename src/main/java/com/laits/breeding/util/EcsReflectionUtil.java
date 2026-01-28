package com.laits.breeding.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.builtin.adventure.farming.component.CoopResidentComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Utility class for ECS (Entity Component System) reflection operations.
 * Provides cached component type access and entity utility methods.
 *
 * This class centralizes all reflection-based ECS operations to:
 * - Cache component types for performance
 * - Provide consistent entity model/UUID extraction
 * - Handle edge cases and errors gracefully
 */
public final class EcsReflectionUtil {

    // ========================================================================
    // CACHED ECS COMPONENT TYPES - Performance optimization
    // These are called frequently in hot loops (tick systems, queries)
    // ========================================================================

    // Core entity components (CRITICAL - used in loops)
    public static final ComponentType<EntityStore, NPCEntity> NPC_TYPE = NPCEntity.getComponentType();
    public static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE = DespawnComponent.getComponentType();

    // Transform and model (HIGH frequency)
    public static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent
            .getComponentType();
    public static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    public static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = UUIDComponent.getComponentType();

    // Player components (HIGH - per-action)
    public static final ComponentType<EntityStore, Player> PLAYER_TYPE = Player.getComponentType();
    public static final ComponentType<EntityStore, PlayerRef> PLAYER_REF_TYPE = PlayerRef.getComponentType();

    // Interaction components (MEDIUM frequency)
    public static final ComponentType<EntityStore, Interactions> INTERACTIONS_TYPE = Interactions.getComponentType();
    public static final ComponentType<EntityStore, Interactable> INTERACTABLE_TYPE = Interactable.getComponentType();

    // Entity state components (MEDIUM frequency)
    public static final ComponentType<EntityStore, Nameplate> NAMEPLATE_TYPE = Nameplate.getComponentType();

    // Death/stat components (LOW frequency but good to cache)
    public static final ComponentType<EntityStore, DeathComponent> DEATH_TYPE = DeathComponent.getComponentType();
    public static final ComponentType<EntityStore, EntityStatMap> ENTITY_STAT_MAP_TYPE = EntityStatMap.getComponentType();

    // Coop component (for chicken coop integration - prevents false despawn detection)
    public static final ComponentType<EntityStore, CoopResidentComponent> COOP_RESIDENT_TYPE = CoopResidentComponent.getComponentType();

    // Network ID component (for matching packet entityId to server entities)
    public static final ComponentType<EntityStore, NetworkId> NETWORK_ID_TYPE = NetworkId.getComponentType();

    // Private constructor - utility class
    private EcsReflectionUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Get a stable key for an entity ref using UUIDComponent.
     * Falls back to index if UUID not available.
     *
     * @param entityRef The entity reference
     * @return Stable string key (UUID string or "idx:N"), or null if unavailable
     */
    public static String getStableEntityKey(Ref<EntityStore> entityRef) {
        if (!(entityRef instanceof Ref))
            return null;
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(entityRef, UUID_TYPE);
                if (uuidComp != null && uuidComp.getUuid() != null) {
                    return uuidComp.getUuid().toString();
                }
            }
        } catch (Exception e) {
            // Fall through to index-based key
        }
        // Fallback to ref index if UUID not available
        try {
            Integer index = entityRef.getIndex();
            if (index != null) {
                return "idx:" + index;
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get the model asset ID from an entity's ModelComponent.
     * Uses cached reflection for performance.
     *
     * @param store     The entity store
     * @param entityRef The entity reference
     * @return Model asset ID (e.g., "Cow", "Sheep"), or null if unavailable
     */
    public static String getEntityModelAssetId(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            ModelComponent modelComp = store.getComponent(entityRef, MODEL_TYPE);
            if (modelComp == null)
                return null;

            Model model = modelComp.getModel();
            if (model == null)
                return null;

            return model.getModelAssetId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the model ID from an Entity object.
     * Convenience method that handles ref extraction internally.
     *
     * @param entity The entity
     * @return Model asset ID, or entity.toString() as fallback
     */
    @SuppressWarnings("unchecked")
    public static String getEntityModelId(Entity entity) {
        try {
            Object entityRef = getEntityRef(entity);
            if (entityRef != null && entityRef instanceof Ref) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    ModelComponent modelComp = store.getComponent((Ref<EntityStore>) entityRef, MODEL_TYPE);
                    if (modelComp != null) {
                        Model model = modelComp.getModel();

                        if (model != null) {
                            return model.getModelAssetId();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to alternative
        }

        // Fallback: use entity toString which may contain type info
        return entity.toString();
    }

    /**
     * Get entity reference from an Entity object via reflection.
     * Tries getRef() first, then getEntityRef() as fallback.
     *
     * @param entity The entity
     * @return Entity reference object, or null if unavailable
     */
    public static Ref<EntityStore> getEntityRef(Entity entity) {
        Ref<EntityStore> ref = entity.getReference();
        
        if (ref != null) {
            return ref;
        }
         
        // Not sure if it works but keeping as a fallback
        try {
            // Try Entity.getRef() method
            Method getRef = entity.getClass().getMethod("getRef");
            return (Ref<EntityStore>) getRef.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Try alternative methods
            try {
                // Some entities might have getEntityRef()
                Method getEntityRefMethod = entity.getClass().getMethod("getEntityRef");
                return (Ref<EntityStore>) getEntityRefMethod.invoke(entity);
            } catch (Exception e2) {
                // Silent - not all entities have ref access
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get UUID for an entity using ECS UUIDComponent.
     * Falls back to generating a consistent UUID from entity ref.
     *
     * @param entity The entity
     * @return UUID for the entity
     */
    @SuppressWarnings("unchecked")
    public static UUID getEntityUUID(Entity entity) {
        try {
            // Try to get from UUIDComponent via ECS
            Object entityRef = getEntityRef(entity);
            if (entityRef != null && entityRef instanceof Ref) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, UUID_TYPE);
                    if (uuidComp != null) {
                        UUID uuid = uuidComp.getUuid();
                        if (uuid != null) {
                            return uuid;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to alternative
        }

        // Fallback: generate consistent UUID from entity ref string
        Object entityRef = getEntityRef(entity);
        if (entityRef != null) {
            return UUID.nameUUIDFromBytes(("entity_" + entityRef.toString()).getBytes());
        }

        // Last resort: generate from entity toString
        return UUID.nameUUIDFromBytes(entity.toString().getBytes());
    }

    /**
     * Get UUID from an entity ref.
     *
     * @param ref The entity reference
     * @return UUID, or a generated fallback UUID
     */
    public static UUID getUuidFromRef(Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
                if (uuidComp != null) {
                    UUID uuid = uuidComp.getUuid();
                    if (uuid != null) {
                        return uuid;
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to fallback
        }

        // Fallback: use ref index if available (stable within session)
        try {
            Integer index = ref.getIndex();
            if (index != null) {
                return UUID.nameUUIDFromBytes(("entity_ref_" + index).getBytes());
            }
        } catch (Exception e) {
            // Silent
        }
        return UUID.nameUUIDFromBytes(ref.toString().getBytes());
    }
}
