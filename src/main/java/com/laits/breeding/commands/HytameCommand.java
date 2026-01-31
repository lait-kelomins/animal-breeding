package com.laits.breeding.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.listeners.DetectTamedDeath;
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.PersistenceManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.ConfigManager;
import com.tameableanimals.tame.HyTameComponent;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified command for all HyTame functionality.
 * Usage: /hytame [subcommand]
 * Subcommands: help, status, config, growth, tame, untame, info, settings, custom, scan, debug
 *
 * Note: /breed is kept as a deprecated alias.
 */
public class HytameCommand extends AbstractCommand {

    // Permission constants
    public static final String PERM_ADMIN = "hytame.admin.";

    private static final Message DEPRECATION_WARNING = Message.raw(
            "[Deprecated] Use /hytame instead. /breed will be removed in a future version."
    ).color("#FFAA00");

    public HytameCommand() {
        super("hytame", "Main command for HyTame");
        // Player commands (Adventure mode access)
        addSubCommand(new HytameHelpSubCommand());
        addSubCommand(new HytameStatusSubCommand());
        addSubCommand(new HytameInfoSubCommand());
        addSubCommand(new HytameTameSubCommand());
        addSubCommand(new HytameUntameSubCommand());
        addSubCommand(new HytameSettingsSubCommand());
        addSubCommand(new HytameScanSubCommand());
        addSubCommand(new HytameFoodsSubCommand());
        // Admin commands (require admin permissions)
        addSubCommand(new HytameConfigSubCommand());
        addSubCommand(new HytameGrowthSubCommand());
        addSubCommand(new HytameCustomSubCommand());
        addSubCommand(new HytameDebugSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Check Hytalor requirement first
        checkHytalorWarning(ctx);
        // Check if invoked via deprecated /breed alias
        checkDeprecatedAlias(ctx);
        // Default action: show help
        showHelp(ctx);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Check if the command was invoked via the deprecated /breed alias
     * and show a deprecation warning if so.
     */
    private static void checkDeprecatedAlias(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input != null && input.toLowerCase().startsWith("breed")) {
            ctx.sendMessage(DEPRECATION_WARNING);
            ctx.sendMessage(Message.raw("")); // Blank line after warning
        }
    }

    /**
     * Check admin permission and send error message if denied.
     * @return true if access is denied (command should return early)
     */
    private static boolean checkAdminDenied(CommandContext ctx) {
        if (ctx.sender() instanceof Player player) {
            if (!HytamePermissions.hasAdminAccess(player)) {
                ctx.sendMessage(Message.raw("This command requires admin permissions.").color("#FF5555"));
                return true;
            }
        }
        return false;
    }

    // Track if we've shown the Hytalor warning this session (per-player would be better but this reduces spam)
    private static final java.util.Set<UUID> hytalorWarningShown = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Show Hytalor warning if not installed. Shows once per player per session.
     * Called at the start of every command execution.
     */
    private static void checkHytalorWarning(CommandContext ctx) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null || plugin.isHytalorInstalled()) {
            return; // Hytalor is installed, no warning needed
        }

        // Get player UUID to track if we've shown warning
        UUID playerUuid = null;
        if (ctx.sender() instanceof Player player) {
            try {
                playerUuid = player.getUuid();
            } catch (Exception e) {
                // Fallback - show warning
            }
        }

        // Show warning if not shown to this player yet
        if (playerUuid == null || !hytalorWarningShown.contains(playerUuid)) {
            ctx.sendMessage(Message.raw("").color("#000000")); // Blank line
            ctx.sendMessage(Message.raw("╔════════════════════════════════════════╗").color("#FF5555"));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("⚠ HYTALOR NOT DETECTED").color("#FFFF55"))
                    .insert(Message.raw("              ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("║                                        ║").color("#FF5555"));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("HyTame requires Hytalor to work.").color("#FFFFFF"))
                    .insert(Message.raw("   ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("Without it, taming and breeding").color("#AAAAAA"))
                    .insert(Message.raw("    ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("features will NOT work properly.").color("#AAAAAA"))
                    .insert(Message.raw("   ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("║                                        ║").color("#FF5555"));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("Install Hytalor from:").color("#AAAAAA"))
                    .insert(Message.raw("              ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("║  ").color("#FF5555")
                    .insert(Message.raw("curseforge.com/hytale/mods/hytalor").color("#55FFFF"))
                    .insert(Message.raw(" ║").color("#FF5555")));
            ctx.sendMessage(Message.raw("╚════════════════════════════════════════╝").color("#FF5555"));
            ctx.sendMessage(Message.raw("").color("#000000")); // Blank line

            if (playerUuid != null) {
                hytalorWarningShown.add(playerUuid);
            }
        }
    }

    private static void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== HyTame - Animal Breeding & Taming ===").color("#FF9900"));
        ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Player Commands:").color("#FFAA00"));
        ctx.sendMessage(Message.raw("/hytame help").color("#FFFFFF")
                .insert(Message.raw(" - Show this help").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame status").color("#FFFFFF")
                .insert(Message.raw(" - View tracked animals").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame info").color("#FFFFFF")
                .insert(Message.raw(" - Show taming info").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame tame <name>").color("#FFFFFF")
                .insert(Message.raw(" - Prepare to tame an animal").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame untame").color("#FFFFFF")
                .insert(Message.raw(" - Release a tamed animal").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame settings").color("#FFFFFF")
                .insert(Message.raw(" - Taming settings").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame scan").color("#FFFFFF")
                .insert(Message.raw(" - Scan for untracked babies").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame config info <animal>").color("#FFFFFF")
                .insert(Message.raw(" - View animal details").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame config list").color("#FFFFFF")
                .insert(Message.raw(" - List all animals").color("#AAAAAA")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Admin Commands ").color("#FFAA00")
                .insert(Message.raw("(admin):").color("#888888")));
        ctx.sendMessage(Message.raw("/hytame config enable/disable").color("#FFFFFF")
                .insert(Message.raw(" - Toggle breeding/taming").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame growth").color("#FFFFFF")
                .insert(Message.raw(" - Toggle baby growth").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame custom ...").color("#FFFFFF")
                .insert(Message.raw(" - Manage custom animals").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/hytame debug ...").color("#FFFFFF")
                .insert(Message.raw(" - Debug commands").color("#AAAAAA")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Feed animals their favorite food to breed!").color("#55FF55"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw(">>> ").color("#FFFF55")
                .insert(Message.raw("/hytame foods").color("#55FFFF"))
                .insert(Message.raw(" - Quick guide to all animal foods!").color("#FFFF55")));
    }

    // =========================================================================
    // Player Subcommands (no permission check needed)
    // =========================================================================

    // --- Subcommand: help --- Public, no permission required
    public static class HytameHelpSubCommand extends AbstractCommand {
        public HytameHelpSubCommand() {
            super("help", "Show help information");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            showHelp(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            showHelp(ctx);
        }
    }

    // --- Subcommand: status --- Public, no permission required
    public static class HytameStatusSubCommand extends AbstractCommand {
        public HytameStatusSubCommand() {
            super("status", "View tracked animals and breeding stats");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeStatusLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeStatusLogic(ctx);
        }

        private static void executeStatusLogic(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return;
            }

            BreedingManager breeding = plugin.getBreedingManager();
            TamingManager taming = plugin.getTamingManager();

            ctx.sendMessage(Message.raw("=== Breeding Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Animals tracked: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getTrackedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("In love mode: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getInLoveCount())).color("#FF69B4")));
            ctx.sendMessage(Message.raw("Pregnant: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getPregnantCount())).color("#FFFF55")));

            if (taming != null) {
                ctx.sendMessage(Message.raw("Tamed animals: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#55FF55")));
            }
        }
    }

    // --- Subcommand: info --- Public, no permission required
    public static class HytameInfoSubCommand extends AbstractCommand {
        public HytameInfoSubCommand() {
            super("info", "Show taming information");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeInfoLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeInfoLogic(ctx);
        }

        private static void executeInfoLogic(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getTamingManager() == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return;
            }

            TamingManager taming = plugin.getTamingManager();
            ctx.sendMessage(Message.raw("=== Taming Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Total tamed: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Awaiting respawn: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getDespawnedCount())).color("#FFFF55")));
        }
    }

    // --- Subcommand: tame ---
    // [DEPRECATED] Taming is now done via Name Tag item with UI
    public static class HytameTameSubCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        public HytameTameSubCommand() {
            super("tame", "[Deprecated] Use Name Tag item on animal instead");
            nameArg = withRequiredArg("name", "Name for the animal", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeTameLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeTameLogic(ctx);
        }

        private static void executeTameLogic(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] This command has been replaced.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("To tame an animal:").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  1. Hold a Name Tag item").color("#FFFFFF"));
            ctx.sendMessage(Message.raw("  2. Press Left Click on an animal").color("#FFFFFF"));
            ctx.sendMessage(Message.raw("  3. Enter a name in the UI").color("#FFFFFF"));
        }
    }

    // --- Subcommand: untame ---
    // [DEPRECATED] Untaming should be done via a dedicated UI or command with animal name
    public static class HytameUntameSubCommand extends AbstractCommand {
        public HytameUntameSubCommand() {
            super("untame", "[Deprecated] Release a tamed animal");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeUntameLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeUntameLogic(ctx);
        }

        private static void executeUntameLogic(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] This command has been replaced.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("To release a tamed animal, use the naming UI").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("to rename it, or contact server admin.").color("#AAAAAA"));
        }
    }

    // --- Subcommand: settings --- Public, no permission required
    public static class HytameSettingsSubCommand extends AbstractCommand {
        public HytameSettingsSubCommand() {
            super("settings", "Taming settings");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeSettingsLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeSettingsLogic(ctx);
        }

        private static void executeSettingsLogic(CommandContext ctx) {
            ctx.sendMessage(Message.raw("This command is not yet available.").color("#FFFF55"));
            ctx.sendMessage(Message.raw("By default, others CAN interact with your tamed animals.").color("#AAAAAA"));
        }
    }

    // --- Subcommand: scan --- Public, no permission required
    public static class HytameScanSubCommand extends AbstractCommand {
        public HytameScanSubCommand() {
            super("scan", "Scan for untracked baby animals");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeScanLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeScanLogic(ctx);
        }

        private static void executeScanLogic(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return;
            }

            ctx.sendMessage(Message.raw("Scanning for untracked babies...").color("#AAAAAA"));

            // Run scan on world thread
            World world = Universe.get().getDefaultWorld();
            if (world != null) {
                world.execute(() -> {
                    int found = plugin.scanForUntrackedBabies();
                    if (found > 0) {
                        ctx.sendMessage(Message.raw("Found and registered ").color("#55FF55")
                                .insert(Message.raw(String.valueOf(found)).color("#FFFFFF"))
                                .insert(Message.raw(" untracked babies!").color("#55FF55")));
                    } else {
                        ctx.sendMessage(Message.raw("No untracked babies found.").color("#AAAAAA"));
                    }

                    // Also show current baby count
                    BreedingManager breeding = plugin.getBreedingManager();
                    int babyCount = breeding.getTrackedBabyUuids().size();
                    ctx.sendMessage(Message.raw("Total tracked babies: ").color("#AAAAAA")
                            .insert(Message.raw(String.valueOf(babyCount)).color("#FFFFFF")));
                });
            } else {
                ctx.sendMessage(Message.raw("World not available!").color("#FF5555"));
            }
        }
    }

    // --- Subcommand: foods --- Public, no permission required
    public static class HytameFoodsSubCommand extends AbstractCommand {
        public HytameFoodsSubCommand() {
            super("foods", "Quick reference for animal foods");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeFoodsLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeFoodsLogic(ctx);
        }

        private static void executeFoodsLogic(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            ConfigManager config = plugin != null ? plugin.getConfigManager() : null;

            ctx.sendMessage(Message.raw("=== Animal Food Reference ===").color("#FF9900"));
            ctx.sendMessage(Message.raw(""));

            if (config == null) {
                ctx.sendMessage(Message.raw("Error: Could not load configuration.").color("#FF5555"));
                return;
            }

            // Show enabled livestock with their configured foods
            ctx.sendMessage(Message.raw("Enabled Animals:").color("#FFAA00"));
            int count = 0;
            for (com.laits.breeding.models.AnimalType type : com.laits.breeding.models.AnimalType.values()) {
                if (config.isBreedingEnabled(type) || config.isTamingEnabled(type)) {
                    List<String> foods = config.getBreedingFoods(type);
                    String foodList = BreedingConfigCommand.getFoodDisplayList(foods);

                    // Build status indicators
                    String status = "";
                    if (config.isBreedingEnabled(type) && config.isTamingEnabled(type)) {
                        status = " [B+T]";
                    } else if (config.isBreedingEnabled(type)) {
                        status = " [B]";
                    } else {
                        status = " [T]";
                    }

                    ctx.sendMessage(Message.raw("  " + type.getId()).color("#FFFFFF")
                            .insert(Message.raw(status).color("#888888"))
                            .insert(Message.raw(" - ").color("#555555"))
                            .insert(Message.raw(foodList).color("#55FF55")));
                    count++;
                }
            }

            // Show custom animals if any
            var customAnimals = config.getCustomAnimals();
            if (customAnimals != null && !customAnimals.isEmpty()) {
                ctx.sendMessage(Message.raw(""));
                ctx.sendMessage(Message.raw("Custom Animals:").color("#FFAA00"));
                for (var entry : customAnimals.entrySet()) {
                    var custom = entry.getValue();
                    if (custom.isBreedingEnabled() || custom.isTamingEnabled()) {
                        List<String> foods = custom.getBreedingFoods();
                        String foodList = BreedingConfigCommand.getFoodDisplayList(foods);

                        String status = "";
                        if (custom.isBreedingEnabled() && custom.isTamingEnabled()) {
                            status = " [B+T]";
                        } else if (custom.isBreedingEnabled()) {
                            status = " [B]";
                        } else {
                            status = " [T]";
                        }

                        ctx.sendMessage(Message.raw("  " + entry.getKey()).color("#FFFFFF")
                                .insert(Message.raw(status).color("#888888"))
                                .insert(Message.raw(" - ").color("#555555"))
                                .insert(Message.raw(foodList).color("#55FF55")));
                        count++;
                    }
                }
            }

            if (count == 0) {
                ctx.sendMessage(Message.raw("  (no animals enabled)").color("#888888"));
            }

            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Legend: ").color("#888888")
                    .insert(Message.raw("[B]").color("#AAAAAA"))
                    .insert(Message.raw("=Breeding ").color("#666666"))
                    .insert(Message.raw("[T]").color("#AAAAAA"))
                    .insert(Message.raw("=Taming ").color("#666666"))
                    .insert(Message.raw("[B+T]").color("#AAAAAA"))
                    .insert(Message.raw("=Both").color("#666666")));
            ctx.sendMessage(Message.raw(""));

            // How to tame
            ctx.sendMessage(Message.raw("How to Tame:").color("#FFAA00"));
            ctx.sendMessage(Message.raw("  1. Hold a ").color("#AAAAAA")
                    .insert(Message.raw("Name Tag").color("#55FFFF"))
                    .insert(Message.raw(" item").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  2. Press ").color("#AAAAAA")
                    .insert(Message.raw("F").color("#FFFF55"))
                    .insert(Message.raw(" on an animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  3. Enter a name in the UI").color("#AAAAAA"));
            ctx.sendMessage(Message.raw(""));

            // Useful commands
            ctx.sendMessage(Message.raw("Commands:").color("#FFAA00"));
            ctx.sendMessage(Message.raw("  /hytame config info <animal>").color("#FFFFFF")
                    .insert(Message.raw(" - Full details").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  /hytame config list").color("#FFFFFF")
                    .insert(Message.raw(" - All animals").color("#AAAAAA")));
        }
    }

    // =========================================================================
    // Admin Subcommands (admin)
    // =========================================================================

    // --- Subcommand: config (delegates to BreedingConfigCommand subcommands) ---
    // Note: info/list are available to everyone, modifying commands require admin permission
    public static class HytameConfigSubCommand extends AbstractCommand {
        public HytameConfigSubCommand() {
            super("config", "Configuration commands (some require admin)");
            // No permission on parent - individual subcommands check permissions
            addSubCommand(new BreedingConfigCommand.ReloadSubCommand());
            addSubCommand(new BreedingConfigCommand.SaveSubCommand());
            addSubCommand(new BreedingConfigCommand.ListSubCommand());
            addSubCommand(new BreedingConfigCommand.InfoSubCommand());
            addSubCommand(new BreedingConfigCommand.EnableSubCommand());
            addSubCommand(new BreedingConfigCommand.DisableSubCommand());
            addSubCommand(new BreedingConfigCommand.EnableTamingSubCommand());
            addSubCommand(new BreedingConfigCommand.DisableTamingSubCommand());
            addSubCommand(new BreedingConfigCommand.SetSubCommand());
            addSubCommand(new BreedingConfigCommand.AddFoodSubCommand());
            addSubCommand(new BreedingConfigCommand.RemoveFoodSubCommand());
            addSubCommand(new BreedingConfigCommand.PresetSubCommand());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeConfigLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeConfigLogic(ctx);
        }

        private static void executeConfigLogic(CommandContext ctx) {
            // Show config summary
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Config not loaded!").color("#FF5555"));
                return;
            }
            ctx.sendMessage(Message.raw("=== HyTame Config ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                    .insert(Message.raw(plugin.getConfigManager().getActivePreset()).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Type ").color("#AAAAAA")
                    .insert(Message.raw("/hytame config").color("#FFFFFF"))
                    .insert(Message.raw(" and press TAB for subcommands").color("#AAAAAA")));
        }
    }

    // --- Subcommand: growth ---
    public static class HytameGrowthSubCommand extends AbstractCommand {
        public HytameGrowthSubCommand() {
            super("growth", "Toggle baby animal growth (admin)");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeGrowthLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return;
            executeGrowthLogic(ctx);
        }

        private static void executeGrowthLogic(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return;
            }

            ConfigManager config = plugin.getConfigManager();
            boolean current = config.isGrowthEnabled();
            config.setGrowthEnabled(!current);

            if (config.isGrowthEnabled()) {
                ctx.sendMessage(Message.raw("Baby growth: ").color("#AAAAAA")
                        .insert(Message.raw("ENABLED").color("#55FF55")));
                ctx.sendMessage(Message.raw("Babies will grow into adults over time.").color("#AAAAAA"));
            } else {
                ctx.sendMessage(Message.raw("Baby growth: ").color("#AAAAAA")
                        .insert(Message.raw("DISABLED").color("#FF5555")));
                ctx.sendMessage(Message.raw("Babies will stay babies forever!").color("#AAAAAA"));
            }
        }
    }

    // --- Subcommand: custom (delegates to CustomAnimalCommand subcommands) ---
    // No permission on parent - subcommands handle their own permissions
    public static class HytameCustomSubCommand extends AbstractCommand {
        public HytameCustomSubCommand() {
            super("custom", "Manage custom animals from other mods");
            // No requirePermission() - parent just shows help, subcommands check individually
            addSubCommand(new CustomAnimalCommand.CustomAnimalAddCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalRemoveCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalListCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalInfoCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalEnableCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalDisableCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalAddFoodCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalRemoveFoodCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalScanCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalSetRoleCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalSetBabyCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalSetGrowthCommand());
            addSubCommand(new CustomAnimalCommand.CustomAnimalSetCooldownCommand());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeCustomLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            executeCustomLogic(ctx);
        }

        private static void executeCustomLogic(CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Custom Animal Commands ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("/hytame custom scan").color("#FFFFFF")
                    .insert(Message.raw(" - Find creature names in world").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom add <model> <food>").color("#FFFFFF")
                    .insert(Message.raw(" - Add custom animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom remove <model>").color("#FFFFFF")
                    .insert(Message.raw(" - Remove custom animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom list").color("#FFFFFF")
                    .insert(Message.raw(" - List added custom animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom info <model>").color("#FFFFFF")
                    .insert(Message.raw(" - Show details").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom setrole <model> <role>").color("#FFFFFF")
                    .insert(Message.raw(" - Set NPC role for spawning").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom setgrowth <model> <min>").color("#FFFFFF")
                    .insert(Message.raw(" - Set growth time").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame custom setcooldown <model> <min>").color("#FFFFFF")
                    .insert(Message.raw(" - Set breeding cooldown").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("Run ").color("#AAAAAA")
                    .insert(Message.raw("/hytame custom scan").color("#FFFF55"))
                    .insert(Message.raw(" first to find creature names!").color("#AAAAAA")));
        }
    }

    // --- Subcommand: debug ---
    public static class HytameDebugSubCommand extends AbstractCommand {
        public HytameDebugSubCommand() {
            super("debug", "Debug commands for taming system (admin)");
            addSubCommand(new DebugMemorySubCommand());
            addSubCommand(new DebugFileSubCommand());
            addSubCommand(new DebugEventsSubCommand());
            addSubCommand(new DebugClearSubCommand());
            addSubCommand(new DebugTameStatusCommand());
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);
            executeDebugLogic(ctx);
            return CompletableFuture.completedFuture(null);
        }

        /** Called from deprecated /breed command wrapper */
        public void executeFromDeprecated(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return;
            executeDebugLogic(ctx);
        }

        private static void executeDebugLogic(CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Debug Commands ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("/hytame debug memory").color("#FFFFFF")
                    .insert(Message.raw(" - Log tamed animals in memory").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame debug file").color("#FFFFFF")
                    .insert(Message.raw(" - Log tamed animals from save file").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame debug events").color("#FFFFFF")
                    .insert(Message.raw(" - Log last detected death/despawn UUIDs").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame debug clear").color("#FFFFFF")
                    .insert(Message.raw(" - Clear tracked event UUIDs").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/hytame debug tameStatus").color("#FFFFFF")
                    .insert(Message.raw(" - Get target npcs tame status").color("#AAAAAA")));
        }

        // --- Debug: memory ---
        public static class DebugMemorySubCommand extends AbstractCommand {
            public DebugMemorySubCommand() {
                super("memory", "Log tamed animals currently in memory");
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
                checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);

                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getTamingManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                TamingManager taming = plugin.getTamingManager();
                Collection<TamedAnimalData> animals = taming.getAllTamedAnimals();

                ctx.sendMessage(Message.raw("=== Tamed Animals in Memory ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Total: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(animals.size())).color("#FFFFFF")));
                ctx.sendMessage(Message.raw(""));

                if (animals.isEmpty()) {
                    ctx.sendMessage(Message.raw("No tamed animals in memory.").color("#AAAAAA"));
                } else {
                    int index = 1;
                    for (TamedAnimalData data : animals) {
                        String status = data.isDead() ? "[DEAD]" : (data.isDespawned() ? "[DESPAWNED]" : "[ACTIVE]");
                        String statusColor = data.isDead() ? "#FF5555" : (data.isDespawned() ? "#FFFF55" : "#55FF55");

                        ctx.sendMessage(Message.raw(index + ". ").color("#AAAAAA")
                                .insert(Message.raw(data.getCustomName()).color("#FFFFFF"))
                                .insert(Message.raw(" ").color("#AAAAAA"))
                                .insert(Message.raw(status).color(statusColor)));

                        ctx.sendMessage(Message.raw("   UUID: ").color("#AAAAAA")
                                .insert(Message.raw(data.getAnimalUuid().toString()).color("#888888")));

                        String typeStr = data.getAnimalType() != null ? data.getAnimalType().name() : "CUSTOM";
                        ctx.sendMessage(Message.raw("   Type: ").color("#AAAAAA")
                                .insert(Message.raw(typeStr).color("#FFFFFF")));

                        index++;
                    }
                }

                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Debug: file ---
        public static class DebugFileSubCommand extends AbstractCommand {
            public DebugFileSubCommand() {
                super("file", "Log tamed animals from save file");
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
                checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);

                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getPersistenceManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                PersistenceManager persistence = plugin.getPersistenceManager();
                List<TamedAnimalData> animals = persistence.loadData();

                ctx.sendMessage(Message.raw("=== Tamed Animals in File ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("File: ").color("#AAAAAA")
                        .insert(Message.raw(persistence.getSaveFilePath().toString()).color("#888888")));
                ctx.sendMessage(Message.raw("Total: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(animals.size())).color("#FFFFFF")));
                ctx.sendMessage(Message.raw(""));

                if (animals.isEmpty()) {
                    ctx.sendMessage(Message.raw("No tamed animals in save file.").color("#AAAAAA"));
                } else {
                    int index = 1;
                    for (TamedAnimalData data : animals) {
                        String status = data.isDead() ? "[DEAD]" : (data.isDespawned() ? "[DESPAWNED]" : "[ACTIVE]");
                        String statusColor = data.isDead() ? "#FF5555" : (data.isDespawned() ? "#FFFF55" : "#55FF55");

                        ctx.sendMessage(Message.raw(index + ". ").color("#AAAAAA")
                                .insert(Message.raw(data.getCustomName()).color("#FFFFFF"))
                                .insert(Message.raw(" ").color("#AAAAAA"))
                                .insert(Message.raw(status).color(statusColor)));

                        ctx.sendMessage(Message.raw("   UUID: ").color("#AAAAAA")
                                .insert(Message.raw(data.getAnimalUuid().toString()).color("#888888")));

                        String typeStr = data.getAnimalType() != null ? data.getAnimalType().name() : "CUSTOM";
                        ctx.sendMessage(Message.raw("   Type: ").color("#AAAAAA")
                                .insert(Message.raw(typeStr).color("#FFFFFF")));

                        index++;
                    }
                }

                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Debug: events ---
        public static class DebugEventsSubCommand extends AbstractCommand {
            public DebugEventsSubCommand() {
                super("events", "Log last detected death and despawn UUIDs");
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
                checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);

                ctx.sendMessage(Message.raw("=== Last Detected Events ===").color("#FF9900"));

                // Deaths from DetectTamedDeath
                List<UUID> deaths = DetectTamedDeath.getLastDetectedDeaths();
                ctx.sendMessage(Message.raw(""));
                ctx.sendMessage(Message.raw("Deaths (last " + deaths.size() + "):").color("#FF5555"));
                if (deaths.isEmpty()) {
                    ctx.sendMessage(Message.raw("  None").color("#AAAAAA"));
                } else {
                    for (int i = 0; i < deaths.size(); i++) {
                        ctx.sendMessage(Message.raw("  " + (i + 1) + ". ").color("#AAAAAA")
                                .insert(Message.raw(deaths.get(i).toString()).color("#888888")));
                    }
                }

                // Despawns from LaitsBreedingPlugin
                List<UUID> despawns = LaitsBreedingPlugin.getLastDetectedDespawns();
                ctx.sendMessage(Message.raw(""));
                ctx.sendMessage(Message.raw("Despawns (last " + despawns.size() + "):").color("#FFFF55"));
                if (despawns.isEmpty()) {
                    ctx.sendMessage(Message.raw("  None").color("#AAAAAA"));
                } else {
                    for (int i = 0; i < despawns.size(); i++) {
                        ctx.sendMessage(Message.raw("  " + (i + 1) + ". ").color("#AAAAAA")
                                .insert(Message.raw(despawns.get(i).toString()).color("#888888")));
                    }
                }

                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Debug: clear ---
        public static class DebugClearSubCommand extends AbstractCommand {
            public DebugClearSubCommand() {
                super("clear", "Clear tracked event UUIDs");
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
                checkHytalorWarning(ctx);
            checkDeprecatedAlias(ctx);

                DetectTamedDeath.clearTrackedDeaths();
                LaitsBreedingPlugin.clearTrackedDespawns();

                ctx.sendMessage(Message.raw("Cleared tracked death and despawn UUIDs.").color("#55FF55"));
                return CompletableFuture.completedFuture(null);
            }
        }

        public class DebugTameStatusCommand extends AbstractTargetEntityCommand {

            public DebugTameStatusCommand() {
                super("TameStatus", "Displays NPC's tame status");
            }

            @Override
            protected boolean canGeneratePermission() {
                return false;
            }

            @Override
            protected void execute(@Nonnull CommandContext context, @Nonnull ObjectList<Ref<EntityStore>> entities, @Nonnull World world, @Nonnull Store<EntityStore> store) {
                // Check admin permission (uses HytamePermissions for singleplayer/multiplayer logic)
                if (context.sender() instanceof Player player) {
                    if (!HytamePermissions.hasAdminAccess(player)) {
                        context.sendMessage(Message.raw("This command requires admin permissions.").color("#FF5555"));
                        return;
                    }
                }
                Ref<EntityStore> playerRef = context.senderAsPlayerRef();
                if (playerRef == null) return;

                for (Ref<EntityStore> entityRef : entities) {
                    ComponentType<EntityStore, NPCEntity> componentType = NPCEntity.getComponentType();
                    if (componentType == null) continue;

                    NPCEntity npcComponent = store.getComponent(entityRef, componentType);
                    if (npcComponent == null) continue;

                    Role role = npcComponent.getRole();
                    if (role == null) continue;

                    WorldSupport worldSupport = role.getWorldSupport();
                    Attitude defaultAttitude = worldSupport.getDefaultPlayerAttitude();

                    Attitude currentAttitude;
                    try {
                        currentAttitude = worldSupport.getAttitude(entityRef, playerRef, store);
                    } catch (NullPointerException e) {
                        context.sendMessage(Message.raw(role.getRoleName() +  " attitude not initialized"));
                        continue;
                    }

                    HyTameComponent tameComponent = store.getComponent(entityRef, HyTameComponent.getComponentType());

                    String attitudeStatus = "Attitude: Default(" + defaultAttitude + "), Current(" + currentAttitude + ")";
                    String tameStatus = tameComponent != null ? "Tamed: Status(" + tameComponent.isTamed() + "), Owner(" + tameComponent.getTamerName() + ")" : "Cannot be tamed";
                    context.sendMessage(Message.raw(npcComponent.getRoleName()).insert(attitudeStatus).insert(tameStatus));
                }
            }
        }
    }
}
