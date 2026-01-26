package com.laits.breeding.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;
import com.laits.breeding.LaitsBreedingPlugin;
import com.tameableanimals.tame.HyTameComponent;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Convenience helper for taming operations.
 * Wraps HyTameComponent ECS operations in simple static methods.
 */
public final class TameHelper {

    private TameHelper() {
        // Utility class - no instantiation
    }

    /**
     * Get the HyTameComponent type from the plugin.
     */
    public static ComponentType<EntityStore, HyTameComponent> getComponentType() {
        return LaitsBreedingPlugin.getInstance().getHyTameComponentType();
    }

    /**
     * Check if an entity is tamed via ECS HyTameComponent.
     *
     * @param ref Entity reference
     * @return true if entity has HyTameComponent with isTamed=true
     */
    public static boolean isTamed(Ref<EntityStore> ref) {
        if (ref == null)
            return false;
        ComponentType<EntityStore, HyTameComponent> type = getComponentType();
        if (type == null)
            return false;

        Store<EntityStore> store = ref.getStore();
        if (store == null)
            return false;

        HyTameComponent comp = store.getComponent(ref, type);
        return comp != null && comp.isTamed();
    }

    /**
     * Get the HyTameComponent for an entity (may be null).
     *
     * @param ref Entity reference
     * @return HyTameComponent or null if not present
     */
    public static HyTameComponent getHyTameComponent(Ref<EntityStore> ref) {
        if (ref == null)
            return null;
        ComponentType<EntityStore, HyTameComponent> type = getComponentType();
        if (type == null)
            return null;

        Store<EntityStore> store = ref.getStore();
        if (store == null)
            return null;

        return store.getComponent(ref, type);
    }

    /**
     * Get or create the HyTameComponent for an entity.
     * Note: Component creation via ensureAndGetComponent() cannot be called during
     * store processing (e.g., from interactions). TameActivateSystem creates the
     * component when entities spawn, so we just retrieve it here.
     *
     * @param ref Entity reference
     * @return HyTameComponent or null if not present
     */
    public static HyTameComponent ensureHyTameComponent(Ref<EntityStore> ref) {
        // Can't use store.ensureAndGetComponent() during store processing
        // TameActivateSystem already creates HyTameComponent for valid animals on spawn
        // So we just get the existing component here
        HyTameComponent comp = getHyTameComponent(ref);
        if (comp == null) {
            log("HyTameComponent not found - animal may not be in a tameable group");
        }
        return comp;
    }

    /**
     * Tame an animal via ECS HyTameComponent.
     * This sets the HyTameComponent state and generates a hytameId.
     * Note: This only works if HyTameComponent already exists (created by TameActivateSystem on spawn).
     *
     * @param ref        Entity reference
     * @param playerUuid Player UUID who is taming
     * @param playerName Player name who is taming
     * @return The HyTameComponent after taming, or null on failure
     */
    public static HyTameComponent tameAnimal(Ref<EntityStore> ref, UUID playerUuid, String playerName) {
        if (ref == null || playerUuid == null || playerName == null) {
            log("TameHelper.tameAnimal called with null parameters");
            log("ref=" + ref + ", playerUuid=" + playerUuid + ", playerName=" + playerName);
            return null;
        }

        HyTameComponent comp = getHyTameComponent(ref);
        if (comp != null) {
            log("Taming animal for player " + playerName + " (" + playerUuid + ")");
            comp.setTamed(playerUuid, playerName);
        } else {
            log("HyTameComponent not found - cannot tame synchronously");
        }
        return comp;
    }

    /**
     * Tame an animal via ECS HyTameComponent, with deferred component creation if needed.
     * Use this when calling from interactions/systems where store is processing.
     * If HyTameComponent exists, tames immediately and calls callback with the component.
     * If HyTameComponent doesn't exist, defers creation to next tick via world.execute().
     *
     * @param ref        Entity reference
     * @param playerUuid Player UUID who is taming
     * @param playerName Player name who is taming
     * @param world      World for deferred execution (can be null to skip deferred)
     * @param callback   Called with HyTameComponent after taming (may be called on next tick)
     */
    public static void tameAnimalDeferred(Ref<EntityStore> ref, UUID playerUuid, String playerName,
                                          World world, Consumer<HyTameComponent> callback) {
        if (ref == null || playerUuid == null || playerName == null) {
            log("TameHelper.tameAnimalDeferred called with null parameters");
            if (callback != null) callback.accept(null);
            return;
        }

        // First try to get existing component (safe during store processing)
        HyTameComponent comp = getHyTameComponent(ref);

        if (comp != null) {
            // Component exists - tame immediately
            log("Taming animal immediately for player " + playerName);
            comp.setTamed(playerUuid, playerName);
            if (callback != null) callback.accept(comp);
        } else if (world != null) {
            // Component doesn't exist - defer creation to next tick
            log("Deferring tame component creation to next tick");
            world.execute(() -> {
                try {
                    // Now safe to create component
                    Store<EntityStore> store = ref.getStore();
                    if (store == null) {
                        log("Store is null in deferred taming");
                        if (callback != null) callback.accept(null);
                        return;
                    }

                    ComponentType<EntityStore, HyTameComponent> type = getComponentType();
                    if (type == null) {
                        log("HyTameComponent type is null");
                        if (callback != null) callback.accept(null);
                        return;
                    }

                    HyTameComponent deferredComp = store.ensureAndGetComponent(ref, type);
                    if (deferredComp != null) {
                        log("Deferred taming for player " + playerName);
                        deferredComp.setTamed(playerUuid, playerName);
                    }
                    if (callback != null) callback.accept(deferredComp);
                } catch (Exception e) {
                    log("Error in deferred taming: " + e.getMessage());
                    if (callback != null) callback.accept(null);
                }
            });
        } else {
            log("HyTameComponent not found and no world provided for deferred creation");
            if (callback != null) callback.accept(null);
        }
    }

    /**
     * Get the owner UUID from a tamed entity.
     *
     * @param ref Entity reference
     * @return Owner UUID or null if not tamed
     */
    public static UUID getOwnerUuid(Ref<EntityStore> ref) {
        HyTameComponent comp = getHyTameComponent(ref);
        return comp != null && comp.isTamed() ? comp.getTamerUUID() : null;
    }

    /**
     * Get the hytameId from a tamed entity.
     *
     * @param ref Entity reference
     * @return HytameId or null if not tamed or no hytameId
     */
    public static UUID getHytameId(Ref<EntityStore> ref) {
        HyTameComponent comp = getHyTameComponent(ref);
        return comp != null ? comp.getHytameId() : null;
    }

    /**
     * Check if a player owns the tamed animal.
     *
     * @param ref        Entity reference
     * @param playerUuid Player UUID to check
     * @return true if player owns this animal
     */
    public static boolean isOwner(Ref<EntityStore> ref, UUID playerUuid) {
        if (playerUuid == null)
            return false;
        UUID owner = getOwnerUuid(ref);
        return playerUuid.equals(owner);
    }

    private static void log(String msg) {
        // Only log if verbose logging is enabled
        if (!LaitsBreedingPlugin.isVerboseLogging())
            return;

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[TameHelper] " + msg);
        }
    }
}
