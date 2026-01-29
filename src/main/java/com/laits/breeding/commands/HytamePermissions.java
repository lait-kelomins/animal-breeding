package com.laits.breeding.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;

/**
 * Permission constants and utilities for HyTame commands.
 *
 * Permission structure:
 * - hytame.*        = Base permission prefix for player commands
 * - hytame.admin.*  = Admin permission prefix
 *
 * Admin access is granted if:
 * - Player is in Creative mode (automatic admin), OR
 * - Player has the required permission node (via /perm)
 */
public class HytamePermissions {

    // Permission prefixes
    public static final String BASE = "hytame.";
    public static final String ADMIN = "hytame.admin.";

    // Player permissions (available to all by default)
    public static final String HELP = BASE + "help";
    public static final String STATUS = BASE + "status";
    public static final String INFO = BASE + "info";
    public static final String TAME = BASE + "tame";
    public static final String SCAN = BASE + "scan";
    public static final String SETTINGS = BASE + "settings";

    // Admin permissions
    public static final String CONFIG = ADMIN + "config";
    public static final String GROWTH = ADMIN + "growth";
    public static final String CUSTOM = ADMIN + "custom";
    public static final String DEBUG = ADMIN + "debug";

    /**
     * Check if a player has admin access.
     * Admin access is granted if:
     * - Player is in Creative mode, OR
     * - Player has the "hytame.admin" permission
     *
     * @param player The player to check
     * @return true if player has admin access
     */
    public static boolean hasAdminAccess(Player player) {
        if (player == null) return false;
        // Creative mode = automatic admin
        if (player.getGameMode() == GameMode.Creative) {
            return true;
        }
        // Check for admin permission node
        return player.hasPermission(ADMIN + "*") || player.hasPermission("hytame.admin");
    }

    /**
     * Check if a player has a specific admin permission.
     * Access is granted if:
     * - Player is in Creative mode, OR
     * - Player has the specific permission node
     *
     * @param player The player to check
     * @param permission The permission node to check (e.g., "hytame.admin.config")
     * @return true if player has access
     */
    public static boolean hasPermission(Player player, String permission) {
        if (player == null) return false;
        // Creative mode = automatic admin for all hytame.admin.* permissions
        if (permission.startsWith(ADMIN) && player.getGameMode() == GameMode.Creative) {
            return true;
        }
        // Check for specific permission node
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
