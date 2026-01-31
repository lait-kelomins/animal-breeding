package com.laits.breeding.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.commands.HytamePermissions;
import com.laits.breeding.interactions.InteractionStateCache;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.GrowthManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.listeners.NewAnimalSpawnDetector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Container for deprecated standalone commands.
 * These are kept for backwards compatibility but show deprecation notices.
 */
public class LegacyCommands {

    // Track if we've shown the Hytalor warning this session
    private static final Set<UUID> hytalorWarningShown = ConcurrentHashMap.newKeySet();

    /**
     * Check Hytalor and show appropriate message.
     * @return true if command should be blocked (non-admin without Hytalor)
     */
    private static boolean checkHytalorWarning(CommandContext ctx) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null || plugin.isHytalorInstalled()) {
            return false;
        }

        // Check if sender is admin
        boolean isAdmin = !(ctx.sender() instanceof Player) ||
                          HytamePermissions.hasAdminAccess((Player) ctx.sender());

        if (isAdmin) {
            UUID playerUuid = null;
            if (ctx.sender() instanceof Player player) {
                try {
                    playerUuid = player.getUuid();
                } catch (Exception e) { }
            }

            if (playerUuid == null || !hytalorWarningShown.contains(playerUuid)) {
                ctx.sendMessage(Message.raw("⚠ HYTALOR NOT DETECTED - HyTame requires Hytalor!").color("#FF5555"));
                ctx.sendMessage(Message.raw("Install from: curseforge.com/hytale/mods/hytalor").color("#AAAAAA"));
                ctx.sendMessage(Message.raw(""));
                if (playerUuid != null) {
                    hytalorWarningShown.add(playerUuid);
                }
            }
            return false; // Allow admin to continue
        } else {
            ctx.sendMessage(Message.raw("[HyTame] This feature is currently unavailable.").color("#FF5555"));
            return true; // Block non-admin
        }
    }

    /**
     * Show taming info and list tamed animals.
     * Usage: /taminginfo
     */
    public static class TamingInfoCommand extends AbstractCommand {

        public TamingInfoCommand() {
            super("taminginfo", "[Deprecated] Show taming information - Use /breed info instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkHytalorWarning(ctx)) return CompletableFuture.completedFuture(null);
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed info instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getTamingManager() == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            TamingManager taming = plugin.getTamingManager();

            ctx.sendMessage(Message.raw("=== Taming Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Total tamed: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Awaiting respawn: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getDespawnedCount())).color("#FFFF55")));

            // Note: Player-specific info requires world thread access
            // For now just show global stats
            ctx.sendMessage(Message.raw("Use /breedstatus for detailed info").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle whether other players can interact with your tamed animals.
     * Usage: /tamingsettings
     * NOTE: Currently not functional due to thread safety constraints.
     */
    public static class TamingSettingsCommand extends AbstractCommand {

        public TamingSettingsCommand() {
            super("tamingsettings", "[Deprecated] Toggle taming settings - Use /breed settings instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed settings instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("This command is not yet available.").color("#FFFF55"));
            ctx.sendMessage(Message.raw("By default, others CAN interact with your tamed animals.").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Show plugin help.
     * Usage: /laitsbreeding
     */
    public static class BreedingHelpCommand extends AbstractCommand {

        public BreedingHelpCommand() {
            super("laitsbreeding", "[Deprecated] Show breeding plugin help - Use /breed help instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkHytalorWarning(ctx)) return CompletableFuture.completedFuture(null);
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("=== Lait's Animal Breeding v" + LaitsBreedingPlugin.VERSION + " ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Main Command: ").color("#AAAAAA")
                    .insert(Message.raw("/breed").color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Commands:").color("#FFAA00"));
            ctx.sendMessage(Message.raw("/breed").color("#FFFFFF")
                    .insert(Message.raw(" - Main command (recommended)").color("#55FF55")));
            ctx.sendMessage(Message.raw("/breedstatus").color("#FFFFFF")
                    .insert(Message.raw(" - View tracked animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breedconfig").color("#FFFFFF")
                    .insert(Message.raw(" - Configuration commands").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breedgrowth").color("#FFFFFF")
                    .insert(Message.raw(" - Toggle baby growth").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/taminginfo").color("#FFFFFF")
                    .insert(Message.raw(" - View tamed animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/customanimal").color("#FFFFFF")
                    .insert(Message.raw(" - Manage custom animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Feed animals their favorite food to breed!").color("#55FF55"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Show breeding status.
     * Usage: /breedstatus
     */
    public static class BreedingStatusCommand extends AbstractCommand {

        public BreedingStatusCommand() {
            super("breedstatus", "[Deprecated] Show breeding status - Use /breed status instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkHytalorWarning(ctx)) return CompletableFuture.completedFuture(null);
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed status instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            BreedingManager breeding = plugin.getBreedingManager();
            GrowthManager growth = plugin.getGrowthManager();
            TamingManager taming = plugin.getTamingManager();

            ctx.sendMessage(Message.raw("=== Breeding Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                    .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Animals tracked: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getTrackedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Pregnant: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getPregnantCount())).color("#FFFF55")));
            ctx.sendMessage(Message.raw("In love: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getInLoveCount())).color("#FF55FF")));
            ctx.sendMessage(Message.raw(""));

            Map<GrowthStage, Integer> stageCounts = growth.getGrowthStageCounts();
            ctx.sendMessage(Message.raw("Growth stages:").color("#FFFF55"));
            ctx.sendMessage(Message.raw("  Babies: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.BABY))).color("#55FFFF")));
            ctx.sendMessage(Message.raw("  Juveniles: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.JUVENILE))).color("#55FFFF")));
            ctx.sendMessage(Message.raw("  Adults: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.ADULT))).color("#55FF55")));

            // Spawn detector statistics
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Spawn Detection:").color("#FFFF55"));
            int detectedCount = NewAnimalSpawnDetector.getDetectedCount();
            long lastDetection = NewAnimalSpawnDetector.getLastDetectionTime();
            String lastAnimal = NewAnimalSpawnDetector.getLastDetectedAnimal();

            ctx.sendMessage(Message.raw("  Detected spawns: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(detectedCount)).color("#FFFFFF")));
            if (lastDetection > 0) {
                long secondsAgo = (System.currentTimeMillis() - lastDetection) / 1000;
                ctx.sendMessage(Message.raw("  Last detection: ").color("#AAAAAA")
                        .insert(Message.raw(lastAnimal + " (" + secondsAgo + "s ago)").color("#55FFFF")));
            } else {
                ctx.sendMessage(Message.raw("  Last detection: ").color("#AAAAAA")
                        .insert(Message.raw("none").color("#777777")));
            }

            if (breeding.getTrackedCount() == 0) {
                ctx.sendMessage(Message.raw(""));
                ctx.sendMessage(Message.raw("No animals tracked yet. Feed some animals!").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle verbose logging.
     * Usage: /breedlogs
     */
    public static class BreedingLogsCommand extends AbstractCommand {

        public BreedingLogsCommand() {
            super("breedlogs", "Toggle breeding plugin verbose logs");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean newState = !LaitsBreedingPlugin.isVerboseLogging();
            LaitsBreedingPlugin.setVerboseLogging(newState);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo()
                        .log("[Lait:AnimalBreeding] Verbose logging " + (newState ? "enabled" : "disabled"));
            }

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Verbose logging ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));
            if (newState) {
                ctx.sendMessage(Message.raw("Debug information will now appear in server logs.").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    public static class NoClipCommand extends AbstractCommand {
        // Track which players have noclip enabled
        private static final Set<String> noclipPlayers = ConcurrentHashMap.newKeySet();

        public NoClipCommand() {
            super("noclip", "Toggle noclip (invulnerable + fly camera)");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            Player player = (Player) ctx.sender();
            String playerName = player.getDisplayName();

            // Toggle state
            boolean enabling = !noclipPlayers.contains(playerName);

            if (enabling) {
                noclipPlayers.add(playerName);
            } else {
                noclipPlayers.remove(playerName);
            }

            // Send fly camera packet
            try {
            } catch (Exception e) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin != null) {
                    plugin.getLogger().atWarning().log("Fly camera packet error: " + e.getMessage());
                }
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle baby growth on/off.
     * Usage: /breedgrowth
     */
    public static class BreedingGrowthCommand extends AbstractCommand {

        public BreedingGrowthCommand() {
            super("breedgrowth", "[Deprecated] Toggle baby animal growth - Use /breed growth instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed growth instead").color("#FFAA00"));

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            boolean newState = !plugin.getConfigManager().isGrowthEnabled();
            plugin.getConfigManager().setGrowthEnabled(newState);

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Baby growth ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));

            if (!newState) {
                ctx.sendMessage(Message.raw("Babies will not grow into adults until re-enabled.").color("#AAAAAA"));
            }

            // Save config to persist the setting
            plugin.getConfigManager().saveToFile();

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Manually trigger animal scan for debugging.
     * Usage: /breedscan
     */
    public static class BreedingScanCommand extends AbstractCommand {

        public BreedingScanCommand() {
            super("breedscan", "Manually trigger animal scan for breeding interactions");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Enable verbose logging for this scan
            boolean wasVerbose = LaitsBreedingPlugin.isVerboseLogging();
            LaitsBreedingPlugin.setVerboseLogging(true);

            ctx.sendMessage(Message.raw("Starting manual animal scan...").color("#FFFF55"));
            ctx.sendMessage(Message.raw("Check server logs for details (verbose logging enabled)").color("#AAAAAA"));

            try {
                plugin.autoSetupNearbyAnimals();
                ctx.sendMessage(Message.raw("Scan triggered successfully").color("#55FF55"));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Scan error: " + e.getMessage()).color("#FF5555"));
            }

            // Restore verbose logging state after a delay
            if (!wasVerbose) {
                plugin.getTickScheduler().schedule(() -> {
                    LaitsBreedingPlugin.setVerboseLogging(false);
                }, 5, TimeUnit.SECONDS);
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle development debug mode - broadcasts debug messages to all players
     * in-game.
     * Usage: /breeddev
     */
    public static class BreedingDevCommand extends AbstractCommand {

        public BreedingDevCommand() {
            super("breeddev", "Toggle in-game chat logging (shows all debug messages in chat)");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean newState = !LaitsBreedingPlugin.isDevMode();
            LaitsBreedingPlugin.setDevMode(newState);

            // Also enable/disable verbose logging to match
            LaitsBreedingPlugin.setVerboseLogging(newState);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo()
                        .log("[Lait:AnimalBreeding] Chat logging " + (newState ? "enabled" : "disabled"));
            }

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Chat logging ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));
            if (newState) {
                ctx.sendMessage(Message.raw("All debug messages will now appear in chat.").color("#FFAA00"));
                ctx.sendMessage(Message.raw("Use /breeddev again to disable.").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    // Different hint formats to test - will cycle through these
    // Found: game uses localization keys like "server.interactionHints.xxx"
    // Language file has: interactionHints.generic = Press [{key}] to interact
    // Note: Localization keys only work when loaded from JSON assets, not runtime
    // For runtime, we need to use resolved text or a special format
    private static final String[] HINT_FORMATS = {
            "Feed", // 0: Simple text (current default)
            "Press [F] to Feed", // 1: Literal with [F]
            "Press [Use] to Feed", // 2: With interaction type name
            "[F] Feed", // 3: Key prefix
            "§ePress §f[F]§e to Feed", // 4: With color codes
            "server.interactionHints.generic", // 5: Localization key (may not work)
            "Press [{key}] to Feed", // 6: Raw format placeholder
            "@server.interactionHints.generic", // 7: Try @ prefix
    };

    private static int currentHintFormatIndex = 0;

    public static String getCurrentHintFormat() {
        return HINT_FORMATS[currentHintFormatIndex];
    }

    public static int cycleHintFormat() {
        currentHintFormatIndex = (currentHintFormatIndex + 1) % HINT_FORMATS.length;
        return currentHintFormatIndex;
    }

    /**
     * Test hint format command.
     * Usage: /breedhint
     */
    public static class BreedingHintCommand extends AbstractCommand {

        public BreedingHintCommand() {
            super("breedhint", "Cycle through hint format options");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            int newIndex = cycleHintFormat();
            String newFormat = getCurrentHintFormat();

            ctx.sendMessage(Message.raw("Hint Test - Format #" + newIndex + ": ").color("#FFFF55")
                    .insert(Message.raw("\"" + newFormat + "\"").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Look at an animal to see the new hint format.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Run ").color("#AAAAAA")
                    .insert(Message.raw("/breedhint").color("#FFFFFF"))
                    .insert(Message.raw(" again to try the next format.").color("#AAAAAA")));

            // Force re-setup of interactions with new hint
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.attachInteractionsToAnimals();
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Debug command to show cache sizes for memory leak monitoring.
     * Usage: /breedcaches
     */
    public static class BreedingCachesCommand extends AbstractCommand {

        public BreedingCachesCommand() {
            super("breedcaches", "Show cache sizes for debugging memory leaks");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not available").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("=== Cache Status ===").color("#FF9900"));

            // Spawn detector cache
            if (plugin.getSpawnDetector() != null) {
                int spawnCacheSize = plugin.getSpawnDetector().getProcessedCacheSize();
                ctx.sendMessage(Message.raw("  processedEntities: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(spawnCacheSize)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  processedEntities: ").color("#AAAAAA")
                        .insert(Message.raw("N/A (detector not running)").color("#FF5555")));
            }

            // Original interactions cache
            int interactionsCacheSize = InteractionStateCache.getInstance().getCacheSize();
            ctx.sendMessage(Message.raw("  originalStates: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(interactionsCacheSize)).color("#FFFFFF")));

            // Breeding data cache
            if (plugin.getBreedingManager() != null) {
                int breedingCacheSize = plugin.getBreedingManager().getTrackedCount();
                ctx.sendMessage(Message.raw("  breedingDataMap: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(breedingCacheSize)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  breedingDataMap: ").color("#AAAAAA")
                        .insert(Message.raw("N/A").color("#FF5555")));
            }

            // Taming manager caches
            if (plugin.getTamingManager() != null) {
                int tamedCount = plugin.getTamingManager().getTamedCount();
                ctx.sendMessage(Message.raw("  tamedAnimals: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(tamedCount)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  tamedAnimals: ").color("#AAAAAA")
                        .insert(Message.raw("N/A").color("#FF5555")));
            }

            ctx.sendMessage(Message.raw("Caches are cleaned periodically (every 5-10 min).").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * [DEPRECATED] Name tag command - replaced by Name Tag item with UI.
     * Usage: Use a Name Tag item on an animal to open the naming UI.
     */
    public static class NameTagCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        public NameTagCommand() {
            super("nametag", "[Deprecated] Use Name Tag item on animal instead");
            nameArg = withRequiredArg("name", "Name for the animal", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] This command has been replaced.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("To tame an animal:").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  1. Hold a Name Tag item").color("#FFFFFF"));
            ctx.sendMessage(Message.raw("  2. Press F on an animal").color("#FFFFFF"));
            ctx.sendMessage(Message.raw("  3. Enter a name in the UI").color("#FFFFFF"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * [DEPRECATED] Untame command - use /breed untame <name> instead.
     */
    public static class UntameCommand extends AbstractCommand {

        public UntameCommand() {
            super("untame", "[Deprecated] Use /breed untame <name> instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] This command has been replaced.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("To release a tamed animal:").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  Use: /breed untame <animal-name>").color("#FFFFFF"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
