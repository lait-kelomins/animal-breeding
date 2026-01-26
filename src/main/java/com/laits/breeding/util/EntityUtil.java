package com.laits.breeding.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Static utility class for common entity operations.
 * Provides methods for extracting UUIDs, positions, and references from entities.
 */
public final class EntityUtil {

    private EntityUtil() {
        // Prevent instantiation
    }

    /**
     * Check if an entity ref corresponds to a player.
     * Used to prevent treating players with animal models as animals.
     *
     * @param ref The entity reference to check
     * @return true if the entity is a player, false otherwise
     */
    public static boolean isPlayerEntity(Ref<EntityStore> ref) {
        try {
            UUID entityUuid = getUuidFromRef(ref);
            if (entityUuid == null)
                return false;

            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return false;

            for (Player player : world.getPlayers()) {
                UUID playerUuid = getPlayerUuidFromPlayer(player);
                if (entityUuid.equals(playerUuid)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Silent - assume not a player if we can't check
        }
        return false;
    }

    /**
     * Get UUID from an entity reference.
     *
     * @param ref The entity reference
     * @return The UUID, or null if not available
     */
    public static UUID getUuidFromRef(Ref<EntityStore> ref) {
        return EcsReflectionUtil.getUuidFromRef(ref);
    }

    /**
     * Get UUID from a Player entity.
     *
     * @param player The player entity
     * @return The player's UUID, or null if not available
     */
    @SuppressWarnings("unchecked")
    public static UUID getPlayerUuidFromPlayer(Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                Store<EntityStore> store = ((Ref<EntityStore>) entityRef).getStore();
                if (store != null) {
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef,
                            EcsReflectionUtil.UUID_TYPE);
                    if (uuidComp != null) {
                        return uuidComp.getUuid();
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get entity position from Entity object.
     *
     * @param entity The entity
     * @return The entity's position, or null if not available
     */
    public static Vector3d getEntityPosition(Entity entity) {
        Ref<EntityStore> ref = entity.getReference();
        if (ref != null) {
            return getPositionFromRef(ref);
        }

        try {
            // Fallback: Try to get position directly from Entity
            java.lang.reflect.Method getPosition = entity.getClass().getMethod("getPosition");
            Object pos = getPosition.invoke(entity);
            if (pos instanceof Vector3d) {
                return (Vector3d) pos;
            }
            // Try to convert Vector3f to Vector3d
            if (pos != null && pos.getClass().getSimpleName().equals("Vector3f")) {
                java.lang.reflect.Method getX = pos.getClass().getMethod("getX");
                java.lang.reflect.Method getY = pos.getClass().getMethod("getY");
                java.lang.reflect.Method getZ = pos.getClass().getMethod("getZ");
                float x = (Float) getX.invoke(pos);
                float y = (Float) getY.invoke(pos);
                float z = (Float) getZ.invoke(pos);
                return new Vector3d(x, y, z);
            }
        } catch (NoSuchMethodException e) {
            // Try alternate method names
            try {
                java.lang.reflect.Method getPos = entity.getClass().getMethod("getPos");
                Object pos = getPos.invoke(entity);
                if (pos instanceof Vector3d) {
                    return (Vector3d) pos;
                }
            } catch (Exception e2) {
                // Continue to fallback
            }
        } catch (Exception e) {
            // Silent
        }

        return null;
    }

    /**
     * Get position from an entity reference via ECS TransformComponent.
     *
     * @param entityRef The entity reference
     * @return The entity's position, or null if not available
     */
    public static Vector3d getPositionFromRef(Ref<EntityStore> entityRef) {
        if (entityRef == null)
            return null;
        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null)
                return null;

            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Entity may have despawned
        }
        return null;
    }

    /**
     * Get the Ref<EntityStore> from an Entity object.
     *
     * @param entity The entity
     * @return The entity reference, or null if not available
     */
    public static Ref<EntityStore> getEntityRef(Entity entity) {
        return EcsReflectionUtil.getEntityRef(entity);
    }

    /**
     * Get UUID for an entity using ECS UUIDComponent.
     *
     * @param entity The entity
     * @return The entity's UUID, or null if not available
     */
    public static UUID getEntityUUID(Entity entity) {
        return EcsReflectionUtil.getEntityUUID(entity);
    }

    /**
     * Get UUID for a player entity.
     *
     * @param player The player
     * @return The player's UUID, or null if not available
     */
    @SuppressWarnings("unchecked")
    public static UUID getPlayerUuidFromEntity(Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                World world = player.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef,
                            EcsReflectionUtil.UUID_TYPE);
                    if (uuidComp != null && uuidComp.getUuid() != null) {
                        return uuidComp.getUuid();
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get model asset ID for an entity using ECS ModelComponent.
     *
     * @param entity The entity
     * @return The model asset ID (e.g., "Cow", "Sheep"), or null if not available
     */
    public static String getEntityModelId(Entity entity) {
        return EcsReflectionUtil.getEntityModelId(entity);
    }
}
