package com.laits.breeding.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.singleplayer.SingleplayerModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Permission constants and utilities for HyTame commands.
 *
 * Permission structure:
 * - hytame.*        = Base permission prefix for player commands
 * - hytame.admin.*  = Admin permission prefix
 *
 * Admin access rules differ by game type:
 *
 * SINGLEPLAYER:
 * - World owner always has admin access
 *
 * MULTIPLAYER:
 * - Only OP group or explicit permissions grant admin access
 * - Game mode does NOT affect permissions (servers can have creative builders without admin)
 */
public class HytamePermissions {

    // Permission prefixes
    public static final String BASE = "hytame.";
    public static final String ADMIN = "hytame.admin.";

    // Player permissions (available to all by default via canGeneratePermission=false)
    public static final String HELP = BASE + "help";
    public static final String STATUS = BASE + "status";
    public static final String INFO = BASE + "info";
    public static final String TAME = BASE + "tame";
    public static final String SCAN = BASE + "scan";
    public static final String SETTINGS = BASE + "settings";

    // Admin permissions (require OP or explicit permission grant in multiplayer)
    public static final String CONFIG = ADMIN + "config";
    public static final String GROWTH = ADMIN + "growth";
    public static final String CUSTOM = ADMIN + "custom";
    public static final String DEBUG = ADMIN + "debug";

    /**
     * Check if a player has admin access.
     *
     * Singleplayer: World owner = admin
     * Multiplayer: Only OP/permissions.json = admin
     *
     * Note: Game mode (Creative/Adventure) does NOT affect admin permissions.
     * ECS access requires the world thread. Commands may execute on
     * ForkJoinPool threads, so we check the thread and use safe fallbacks.
     *
     * @param player The player to check
     * @return true if player has admin access
     */
    public static boolean hasAdminAccess(Player player) {
        if (player == null) return false;

        // Singleplayer: world owner has admin
        if (Constants.SINGLEPLAYER) {
            // ECS access requires world thread - check if we're on it
            String threadName = Thread.currentThread().getName();
            boolean onWorldThread = threadName.contains("WorldThread");

            if (onWorldThread) {
                // Safe to access ECS components
                try {
                    // World owner always has admin
                    Ref<EntityStore> ref = player.getReference();
                    if (ref != null && ref.isValid()) {
                        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                        if (playerRef != null && SingleplayerModule.isOwner(playerRef)) {
                            return true;
                        }
                    }
                } catch (Throwable t) {
                    // If ECS access fails for any reason, fall through to permission check
                }
            } else {
                // Not on world thread - can't safely access ECS components
                // In singleplayer, the player is typically the world owner, so be permissive
                // Check permissions first, then default to allowing admin in singleplayer
                if (player.hasPermission(ADMIN + "*") || player.hasPermission("hytame.admin")) {
                    return true;
                }
                // Singleplayer fallback: assume admin access (owner is typically the only player)
                return true;
            }
        }

        // Multiplayer (or singleplayer non-owner on world thread): check permission system only
        return player.hasPermission(ADMIN + "*") || player.hasPermission("hytame.admin");
    }

    /**
     * Check if a player has a specific permission.
     *
     * Singleplayer: World owner = all admin permissions
     * Multiplayer: Uses Hytale's permission system only
     *
     * @param player The player to check
     * @param permission The permission node to check (e.g., "hytame.admin.config")
     * @return true if player has the permission
     */
    public static boolean hasPermission(Player player, String permission) {
        if (player == null) return false;

        // For admin permissions, use the unified check
        if (permission.startsWith(ADMIN)) {
            return hasAdminAccess(player);
        }

        // Non-admin permissions: direct check
        return player.hasPermission(permission);
    }

    /**
     * Check if a player has access to a command based on admin requirement.
     *
     * @param player The player to check
     * @param requiresAdmin Whether the command requires admin access
     * @return true if player has access
     */
    public static boolean hasAccess(Player player, boolean requiresAdmin) {
        if (!requiresAdmin) return true;  // Non-admin commands always allowed
        return hasAdminAccess(player);
    }

    private HytamePermissions() {
        // Utility class - no instantiation
    }
}
