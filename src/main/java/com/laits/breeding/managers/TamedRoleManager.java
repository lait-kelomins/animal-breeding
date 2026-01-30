package com.laits.breeding.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages asset-based tamed roles for animals.
 * When an animal is tamed, it transitions from its wild role (e.g., "Cow")
 * to a tamed role (e.g., "Cow_Tamed") using Hytale's native RoleChangeSystem.
 *
 * This approach provides:
 * - Persistent behavior changes that survive server restarts
 * - Native Hytale integration (no runtime reflection hacks)
 * - Consistent attitude, hints, and interactions via role assets
 */
public class TamedRoleManager {

    // Wild -> Tamed role name mapping (covers adults and young variants)
    private static final Map<String, String> WILD_TO_TAMED = Map.ofEntries(
        // Cow
        Map.entry("Cow", "Cow_Tamed"),
        Map.entry("Cow_Calf", "Cow_Calf_Tamed"),
        // Horse
        Map.entry("Horse", "Horse_Tamed"),
        Map.entry("Horse_Foal", "Horse_Foal_Tamed"),
        // Pig
        Map.entry("Pig", "Pig_Tamed"),
        Map.entry("Pig_Piglet", "Pig_Piglet_Tamed"),
        // Sheep
        Map.entry("Sheep", "Sheep_Tamed"),
        Map.entry("Sheep_Lamb", "Sheep_Lamb_Tamed"),
        // Chicken
        Map.entry("Chicken", "Chicken_Tamed"),
        Map.entry("Chicken_Chick", "Chicken_Chick_Tamed"),
        // Goat
        Map.entry("Goat", "Goat_Tamed"),
        Map.entry("Goat_Kid", "Goat_Kid_Tamed"),
        // Turkey
        Map.entry("Turkey", "Turkey_Tamed"),
        Map.entry("Turkey_Chick", "Turkey_Chick_Tamed"),
        // Rabbit/Bunny
        Map.entry("Rabbit", "Rabbit_Tamed"),
        Map.entry("Bunny", "Bunny_Tamed"),
        // Boar
        Map.entry("Boar", "Boar_Tamed"),
        Map.entry("Boar_Piglet", "Boar_Piglet_Tamed"),
        // Bison
        Map.entry("Bison", "Bison_Tamed"),
        Map.entry("Bison_Calf", "Bison_Calf_Tamed"),
        // Camel
        Map.entry("Camel", "Camel_Tamed"),
        Map.entry("Camel_Calf", "Camel_Calf_Tamed"),
        // Ram
        Map.entry("Ram", "Ram_Tamed"),
        Map.entry("Ram_Lamb", "Ram_Lamb_Tamed")
    );

    // Cached role indices for O(1) lookup
    private final Map<String, Integer> roleIndexCache = new ConcurrentHashMap<>();

    // Track initialization state
    private volatile boolean initialized = false;

    // Logging
    private Consumer<String> logger;
    private Consumer<String> warningLogger;

    public TamedRoleManager() {
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public void setWarningLogger(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept("[TamedRoleManager] " + message);
        }
    }

    private void logWarning(String message) {
        if (warningLogger != null) {
            warningLogger.accept("[TamedRoleManager] " + message);
        }
    }

    /**
     * Initialize the manager at plugin startup.
     * Preloads role indices to ensure they exist and are cached.
     */
    public void initialize() {
        log("Initializing TamedRoleManager...");

        int loadedCount = 0;
        int missingCount = 0;

        for (String tamedRoleName : WILD_TO_TAMED.values()) {
            // Skip duplicates (some young variants share flock arrays)
            if (roleIndexCache.containsKey(tamedRoleName)) {
                continue;
            }

            int index = getRoleIndexUncached(tamedRoleName);
            if (index >= 0) {
                roleIndexCache.put(tamedRoleName, index);
                loadedCount++;
                log("Cached role index: " + tamedRoleName + " = " + index);
            } else {
                missingCount++;
                logWarning("Tamed role not found: " + tamedRoleName + " (asset may be missing)");
            }
        }

        initialized = true;
        log("Initialization complete: " + loadedCount + " roles cached, " + missingCount + " missing");
    }

    /**
     * Check if the manager is initialized and ready.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if a wild role has a corresponding tamed variant.
     *
     * @param wildRoleName The wild role name (e.g., "Cow", "Horse")
     * @return true if a tamed variant exists
     */
    public boolean hasTamedRole(String wildRoleName) {
        return wildRoleName != null && WILD_TO_TAMED.containsKey(wildRoleName);
    }

    /**
     * Get the tamed role name for a wild role.
     *
     * @param wildRoleName The wild role name (e.g., "Cow")
     * @return The tamed role name (e.g., "Cow_Tamed") or null if not found
     */
    public String getTamedRoleName(String wildRoleName) {
        return wildRoleName != null ? WILD_TO_TAMED.get(wildRoleName) : null;
    }

    /**
     * Get the role index for a role name, using cache for efficiency.
     *
     * @param roleName Role name to look up
     * @return Role index or -1 if not found
     */
    public int getRoleIndex(String roleName) {
        if (roleName == null) return -1;

        // Check cache first
        Integer cached = roleIndexCache.get(roleName);
        if (cached != null) {
            return cached;
        }

        // Not in cache - fetch and cache
        int index = getRoleIndexUncached(roleName);
        if (index >= 0) {
            roleIndexCache.put(roleName, index);
        }
        return index;
    }

    /**
     * Get the role index without caching (direct NPCPlugin lookup).
     */
    private int getRoleIndexUncached(String roleName) {
        try {
            return NPCPlugin.get().getIndex(roleName);
        } catch (Exception e) {
            logWarning("Error looking up role index for " + roleName + ": " + e.getMessage());
            return -1;
        }
    }

    /**
     * Apply the tamed role to an NPC when it is tamed.
     * This changes the NPC's role from wild (e.g., Cow) to tamed (e.g., Cow_Tamed).
     *
     * @param npcRef Reference to the NPC entity
     * @param store  Entity store containing the NPC
     * @return true if role change was requested successfully, false otherwise
     */
    public boolean applyTamedRole(Ref<EntityStore> npcRef, Store<EntityStore> store) {
        if (npcRef == null || store == null) {
            log("applyTamedRole: npcRef or store is null");
            return false;
        }

        if (!npcRef.isValid()) {
            log("applyTamedRole: npcRef is invalid");
            return false;
        }

        try {
            // Get the NPCEntity component to access current role
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null) {
                log("applyTamedRole: entity is not an NPC");
                return false;
            }

            // Get current role name
            String currentRoleName = npcEntity.getRoleName();
            if (currentRoleName == null) {
                log("applyTamedRole: could not get current role name");
                return false;
            }

            // Check if already tamed
            if (currentRoleName.endsWith("_Tamed")) {
                log("applyTamedRole: " + currentRoleName + " is already a tamed role");
                return true; // Already tamed, success
            }

            // Look up tamed role name
            String tamedRoleName = getTamedRoleName(currentRoleName);
            if (tamedRoleName == null) {
                log("applyTamedRole: no tamed role mapping for " + currentRoleName);
                return false;
            }

            // Get tamed role index
            int tamedRoleIndex = getRoleIndex(tamedRoleName);
            if (tamedRoleIndex < 0) {
                logWarning("applyTamedRole: tamed role " + tamedRoleName + " not found (asset missing?)");
                return false;
            }

            // Get current role for the role change request
            Role currentRole = npcEntity.getRole();
            if (currentRole == null) {
                log("applyTamedRole: could not get current role object");
                return false;
            }

            // Request the role change
            // changeAppearance=false because we keep the same model, just change behavior
            RoleChangeSystem.requestRoleChange(
                npcRef,
                currentRole,
                tamedRoleIndex,
                false,  // Don't change appearance
                store
            );

            log("Requested role change: " + currentRoleName + " -> " + tamedRoleName +
                " (index=" + tamedRoleIndex + ")");
            return true;

        } catch (Exception e) {
            logWarning("applyTamedRole error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Apply the tamed role with a specific state transition.
     *
     * @param npcRef   Reference to the NPC entity
     * @param store    Entity store containing the NPC
     * @param newState The state to transition to (e.g., "Idle")
     * @return true if role change was requested successfully
     */
    public boolean applyTamedRoleWithState(Ref<EntityStore> npcRef, Store<EntityStore> store, String newState) {
        if (npcRef == null || store == null) {
            return false;
        }

        if (!npcRef.isValid()) {
            return false;
        }

        try {
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null) {
                return false;
            }

            String currentRoleName = npcEntity.getRoleName();
            if (currentRoleName == null || currentRoleName.endsWith("_Tamed")) {
                return currentRoleName != null && currentRoleName.endsWith("_Tamed");
            }

            String tamedRoleName = getTamedRoleName(currentRoleName);
            if (tamedRoleName == null) {
                return false;
            }

            int tamedRoleIndex = getRoleIndex(tamedRoleName);
            if (tamedRoleIndex < 0) {
                return false;
            }

            Role currentRole = npcEntity.getRole();
            if (currentRole == null) {
                return false;
            }

            // Request role change with state
            RoleChangeSystem.requestRoleChange(
                npcRef,
                currentRole,
                tamedRoleIndex,
                false,
                newState,
                null,  // No substate
                store
            );

            log("Requested role change with state: " + currentRoleName + " -> " + tamedRoleName +
                " (state=" + newState + ")");
            return true;

        } catch (Exception e) {
            logWarning("applyTamedRoleWithState error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if an NPC currently has a tamed role.
     *
     * @param npcRef Reference to the NPC entity
     * @param store  Entity store containing the NPC
     * @return true if the NPC has a tamed role
     */
    public boolean hasTamedRoleApplied(Ref<EntityStore> npcRef, Store<EntityStore> store) {
        if (npcRef == null || store == null || !npcRef.isValid()) {
            return false;
        }

        try {
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null) {
                return false;
            }

            String roleName = npcEntity.getRoleName();
            return roleName != null && roleName.endsWith("_Tamed");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the current role name of an NPC.
     *
     * @param npcRef Reference to the NPC entity
     * @param store  Entity store containing the NPC
     * @return Role name or null if not an NPC or error
     */
    public String getCurrentRoleName(Ref<EntityStore> npcRef, Store<EntityStore> store) {
        if (npcRef == null || store == null || !npcRef.isValid()) {
            return null;
        }

        try {
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            return npcEntity != null ? npcEntity.getRoleName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get statistics about the role manager.
     */
    public String getStats() {
        return "TamedRoleManager: initialized=" + initialized +
               ", cachedRoles=" + roleIndexCache.size() +
               ", totalMappings=" + WILD_TO_TAMED.size();
    }
}
