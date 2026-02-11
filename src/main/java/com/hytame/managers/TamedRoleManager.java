package com.hytame.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hytame.HyTamePlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages asset-based tamed roles for animals.
 * When an animal is tamed, it transitions from its wild role (e.g., "Cow")
 * to a tamed role (e.g., "Cow_HyTamed") using Hytale's native RoleChangeSystem.
 *
 * Tamed role naming convention: <WildRoleName>_HyTamed
 * The manager dynamically checks if a _HyTamed role exists for any wild role.
 * If the role doesn't exist, the role change is skipped (taming still works via HyTameComponent).
 *
 * This approach provides:
 * - Persistent behavior changes that survive server restarts
 * - Native Hytale integration (no runtime reflection hacks)
 * - Consistent attitude, hints, and interactions via role assets
 * - Easy expansion: just create <AnimalName>_HyTamed.json asset
 */
public class TamedRoleManager {

    // Suffix for tamed roles - uses HyTamed to avoid conflicts with base game
    private static final String TAMED_SUFFIX = "_HyTamed";

    // Cached role indices for O(1) lookup (null = checked and not found)
    private final Map<String, Integer> roleIndexCache = new ConcurrentHashMap<>();

    // Track roles we've already checked don't exist (to avoid repeated lookups)
    private final Map<String, Boolean> checkedRoles = new ConcurrentHashMap<>();

    // Track initialization state
    private volatile boolean initialized = false;

    public TamedRoleManager() {
    }

    private void log(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atInfo().log(message);
        }
    }

    private void logWarning(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atWarning().log(message);
        }
    }

    /**
     * Initialize the manager at plugin startup.
     */
    public void initialize() {
        log("Initializing TamedRoleManager with dynamic role detection...");
        log("Tamed role naming convention: <WildRole>" + TAMED_SUFFIX);
        initialized = true;
        log("Initialization complete - roles will be discovered on demand");
    }

    /**
     * Check if the manager is initialized and ready.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Derive the tamed role name from a wild role name.
     * Convention: <WildRoleName>_HyTamed
     *
     * @param wildRoleName The wild role name (e.g., "Cow", "Rex_Cave")
     * @return The tamed role name (e.g., "Cow_HyTamed", "Rex_Cave_HyTamed")
     */
    public String deriveTamedRoleName(String wildRoleName) {
        if (wildRoleName == null || wildRoleName.isEmpty()) {
            return null;
        }
        // Don't double-suffix
        if (wildRoleName.endsWith(TAMED_SUFFIX)) {
            return wildRoleName;
        }
        return wildRoleName + TAMED_SUFFIX;
    }

    /**
     * Check if a wild role has a corresponding tamed variant asset.
     *
     * @param wildRoleName The wild role name (e.g., "Cow", "Horse")
     * @return true if a _HyTamed variant exists in the game
     */
    public boolean hasTamedRole(String wildRoleName) {
        if (wildRoleName == null) {
            return false;
        }
        String tamedRoleName = deriveTamedRoleName(wildRoleName);
        return getRoleIndex(tamedRoleName) >= 0;
    }

    /**
     * Get the tamed role name for a wild role (if it exists).
     *
     * @param wildRoleName The wild role name (e.g., "Cow")
     * @return The tamed role name if it exists, null otherwise
     */
    public String getTamedRoleName(String wildRoleName) {
        if (wildRoleName == null) {
            return null;
        }
        String tamedRoleName = deriveTamedRoleName(wildRoleName);
        // Only return if the role actually exists
        if (getRoleIndex(tamedRoleName) >= 0) {
            return tamedRoleName;
        }
        return null;
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

        // Check if we already know it doesn't exist
        if (checkedRoles.containsKey(roleName)) {
            return -1;
        }

        // Not in cache - fetch and cache
        int index = getRoleIndexUncached(roleName);
        if (index >= 0) {
            roleIndexCache.put(roleName, index);
            log("Discovered tamed role: " + roleName + " (index=" + index + ")");
        } else {
            checkedRoles.put(roleName, true);
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
            return -1;
        }
    }

    /**
     * Apply the tamed role to an NPC when it is tamed.
     * This changes the NPC's role from wild (e.g., Cow) to tamed (e.g., Cow_HyTamed).
     * If no _HyTamed role exists, returns false (taming still works via HyTameComponent).
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
            if (currentRoleName.endsWith(TAMED_SUFFIX)) {
                log("applyTamedRole: " + currentRoleName + " is already a tamed role");
                return true; // Already tamed, success
            }

            // Derive tamed role name
            String tamedRoleName = deriveTamedRoleName(currentRoleName);

            // Get tamed role index (checks if role exists)
            int tamedRoleIndex = getRoleIndex(tamedRoleName);
            if (tamedRoleIndex < 0) {
                log("applyTamedRole: no " + tamedRoleName + " role exists, skipping role change");
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
            if (currentRoleName == null) {
                return false;
            }

            if (currentRoleName.endsWith(TAMED_SUFFIX)) {
                return true; // Already tamed
            }

            String tamedRoleName = deriveTamedRoleName(currentRoleName);
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
            return roleName != null && roleName.endsWith(TAMED_SUFFIX);
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
     * Get the tamed role suffix used by this manager.
     */
    public static String getTamedSuffix() {
        return TAMED_SUFFIX;
    }

    /**
     * Get statistics about the role manager.
     */
    public String getStats() {
        return "TamedRoleManager: initialized=" + initialized +
               ", discoveredRoles=" + roleIndexCache.size() +
               ", checkedMissing=" + checkedRoles.size();
    }
}
