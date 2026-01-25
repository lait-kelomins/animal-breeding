package com.laits.breeding.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.lang.reflect.Field;
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

    // Cached ECS component types for performance
    public static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
            TransformComponent.getComponentType();
    public static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE =
            ModelComponent.getComponentType();
    public static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE =
            UUIDComponent.getComponentType();

    // These are initialized at runtime since their getComponentType() may not be public
    private static Object INTERACTIONS_COMP_TYPE = null;
    private static Object INTERACTABLE_COMP_TYPE = null;

    // Cached reflection Field for ModelComponent.model (avoid per-call getDeclaredField)
    private static Field cachedModelField = null;
    private static boolean modelFieldInitialized = false;

    // Static initializer for reflection cache
    static {
        try {
            cachedModelField = ModelComponent.class.getDeclaredField("model");
            cachedModelField.setAccessible(true);
            modelFieldInitialized = true;
        } catch (Exception e) {
            modelFieldInitialized = false;
        }
    }

    // Private constructor - utility class
    private EcsReflectionUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Get the Interactions component type via reflection.
     * Cached after first call.
     */
    public static Object getInteractionsComponentType() {
        if (INTERACTIONS_COMP_TYPE == null) {
            try {
                INTERACTIONS_COMP_TYPE = Interactions.class.getMethod("getComponentType").invoke(null);
            } catch (Exception e) {
                System.out.println("[EcsReflectionUtil] ERROR: Failed to get Interactions component type: " + e.getMessage());
            }
        }
        return INTERACTIONS_COMP_TYPE;
    }

    /**
     * Get the Interactable component type via reflection.
     * Cached after first call.
     */
    public static Object getInteractableComponentType() {
        if (INTERACTABLE_COMP_TYPE == null) {
            try {
                INTERACTABLE_COMP_TYPE = Interactable.class.getMethod("getComponentType").invoke(null);
            } catch (Exception e) {
                System.out.println("[EcsReflectionUtil] ERROR: Failed to get Interactable component type: " + e.getMessage());
            }
        }
        return INTERACTABLE_COMP_TYPE;
    }

    /**
     * Check if model field reflection is initialized and available.
     */
    public static boolean isModelFieldInitialized() {
        return modelFieldInitialized;
    }

    /**
     * Get the cached model field for ModelComponent.
     * Returns null if reflection failed during initialization.
     */
    public static Field getCachedModelField() {
        return cachedModelField;
    }

    /**
     * Get a stable key for an entity ref using UUIDComponent.
     * Falls back to index if UUID not available.
     *
     * @param entityRef The entity reference
     * @return Stable string key (UUID string or "idx:N"), or null if unavailable
     */
    public static String getStableEntityKey(Object entityRef) {
        if (!(entityRef instanceof Ref))
            return null;
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
                if (uuidComp != null && uuidComp.getUuid() != null) {
                    return uuidComp.getUuid().toString();
                }
            }
        } catch (Exception e) {
            // Fall through to index-based key
        }
        // Fallback to ref index if UUID not available
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            Integer index = ref.getIndex();
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
     * @param store The entity store
     * @param entityRef The entity reference
     * @return Model asset ID (e.g., "Cow", "Sheep"), or null if unavailable
     */
    public static String getEntityModelAssetId(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        try {
            ModelComponent modelComp = store.getComponent(entityRef, MODEL_TYPE);
            if (modelComp == null)
                return null;

            // Use cached Field for performance (avoid getDeclaredField per call)
            if (!modelFieldInitialized || cachedModelField == null)
                return null;

            Object model = cachedModelField.get(modelComp);
            if (model == null)
                return null;

            // Extract modelAssetId from model.toString()
            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0)
                return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start)
                return null;
            return modelStr.substring(start, end);

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
                        // Use cached Field for performance (avoid getDeclaredField per call)
                        if (!modelFieldInitialized || cachedModelField == null)
                            return entity.toString();

                        Object model = cachedModelField.get(modelComp);

                        if (model != null) {
                            // Parse modelAssetId from toString to avoid additional reflection
                            String modelStr = model.toString();
                            int start = modelStr.indexOf("modelAssetId='");
                            if (start >= 0) {
                                start += 14;
                                int end = modelStr.indexOf("'", start);
                                if (end > start) {
                                    return modelStr.substring(start, end);
                                }
                            }
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
    public static Object getEntityRef(Entity entity) {
        try {
            // Try Entity.getRef() method
            Method getRef = entity.getClass().getMethod("getRef");
            return getRef.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Try alternative methods
            try {
                // Some entities might have getEntityRef()
                Method getEntityRefMethod = entity.getClass().getMethod("getEntityRef");
                return getEntityRefMethod.invoke(entity);
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
