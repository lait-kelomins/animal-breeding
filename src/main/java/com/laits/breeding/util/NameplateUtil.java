package com.laits.breeding.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility for setting entity nameplates (names displayed above entities).
 */
public class NameplateUtil {
    public static final String UNDEFINED_NAME = "_UNDEFINED";

    /**
     * Initialize the nameplate component type via reflection.
     */
    private static synchronized void initialize() {
    }

    /**
     * Set the nameplate (display name) for an entity.
     *
     * @param store The entity store
     * @param entityRef Reference to the entity
     * @param name The name to display
     * @return true if successful
     */
    public static boolean setEntityNameplate(Store<EntityStore> store, Ref<EntityStore> entityRef, String name) {
        initialize();

        try {
            // Get or create the Nameplate component
            Nameplate nameplateComp = store.getComponent(entityRef, Nameplate.getComponentType());

            if (nameplateComp == null) {
                // Try to add the component if it doesn't exist
                try {
                    nameplateComp = store.ensureAndGetComponent(entityRef, Nameplate.getComponentType());
                } catch (Exception e) {
                    log("Could not add Nameplate component: " + e.getMessage());
                    return false;
                }
            }

            if (nameplateComp == null) {
                return false;
            }

            // Set the name
            nameplateComp.setText(name);
            log("Set nameplate to '" + name + "' for entity " + entityRef);
            return true;

        } catch (Exception e) {
            log("Failed to set nameplate: " + e.getMessage());
            return false;
        }
    }

    /**
     * Set the nameplate using just the entity ref (gets store from ref).
     */
    public static boolean setEntityNameplate(Ref<EntityStore> entityRef, String name) {
        if (entityRef == null) return false;
        Store<EntityStore> store = entityRef.getStore();
        if (store == null) return false;
        return setEntityNameplate(store, entityRef, name);
    }

    /**
     * Clear the nameplate for an entity.
     */
    public static boolean clearEntityNameplate(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        return setEntityNameplate(store, entityRef, "");
    }

    private static void log(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[Nameplate] " + message);
        }
    }
}
