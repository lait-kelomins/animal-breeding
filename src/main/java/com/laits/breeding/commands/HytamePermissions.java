package com.laits.breeding.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;

/**
 * Permission constants and utilities for HyTame commands.
 *
 * Permission structure:
 * - hytame.*        = Base permission prefix for player commands
 * - hytame.admin.*  = Admin permission prefix (requires Creative mode)
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

    // Admin permissions (require Creative mode)
    public static final String CONFIG = ADMIN + "config";
    public static final String GROWTH = ADMIN + "growth";
    public static final String CUSTOM = ADMIN + "custom";
    public static final String DEBUG = ADMIN + "debug";

    /**
     * Check if a player has admin access.
     * Admin access is granted if the player is in Creative mode.
     *
     * @param player The player to check
     * @return true if player has admin access
     */
    public static boolean hasAdminAccess(Player player) {
        if (player == null) return false;
        return player.getGameMode() == GameMode.Creative;
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
