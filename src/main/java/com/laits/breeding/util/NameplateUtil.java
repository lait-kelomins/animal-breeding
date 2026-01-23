package com.laits.breeding.util;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Utility for setting entity nameplates (names displayed above entities).
 */
public class NameplateUtil {

    private static ComponentType<EntityStore, ?> nameplateComponentType = null;
    private static Method setNameMethod = null;
    private static boolean initialized = false;
    private static boolean available = false;

    /**
     * Initialize the nameplate component type via reflection.
     */
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to find the Nameplate component class
            Class<?> nameplateClass = Class.forName(
                "com.hypixel.hytale.server.core.entity.nameplate.Nameplate");

            // Get the component type
            Method getComponentType = nameplateClass.getMethod("getComponentType");
            @SuppressWarnings("unchecked")
            ComponentType<EntityStore, ?> compType =
                (ComponentType<EntityStore, ?>) getComponentType.invoke(null);
            nameplateComponentType = compType;

            // Find setName method
            for (Method m : nameplateClass.getMethods()) {
                if (m.getName().equals("setName") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == String.class) {
                    setNameMethod = m;
                    break;
                }
            }

            if (setNameMethod == null) {
                // Try alternative method names
                for (Method m : nameplateClass.getMethods()) {
                    if ((m.getName().equals("setText") || m.getName().equals("setDisplayName"))
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                        setNameMethod = m;
                        break;
                    }
                }
            }

            available = nameplateComponentType != null && setNameMethod != null;

            if (available) {
                log("Nameplate system initialized successfully");
            } else {
                log("Nameplate component found but setName method not available");
            }
        } catch (ClassNotFoundException e) {
            log("Nameplate component class not found - nameplates not available");
        } catch (Exception e) {
            log("Failed to initialize nameplate system: " + e.getMessage());
        }
    }

    /**
     * Check if nameplate system is available.
     */
    public static boolean isAvailable() {
        initialize();
        return available;
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

        if (!available) {
            return false;
        }

        try {
            // Get or create the Nameplate component
            Object nameplateComp = store.getComponent(entityRef, nameplateComponentType);

            if (nameplateComp == null) {
                // Try to add the component if it doesn't exist
                try {
                    nameplateComp = store.ensureAndGetComponent(entityRef, nameplateComponentType);
                } catch (Exception e) {
                    log("Could not add Nameplate component: " + e.getMessage());
                    return false;
                }
            }

            if (nameplateComp == null) {
                return false;
            }

            // Set the name
            setNameMethod.invoke(nameplateComp, name);
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
