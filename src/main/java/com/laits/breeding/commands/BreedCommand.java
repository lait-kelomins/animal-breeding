package com.laits.breeding.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.commands.HytamePermissions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * DEPRECATED: Use /hytame instead.
 *
 * This command is kept for backwards compatibility and shows a deprecation warning
 * before delegating to the corresponding /hytame command.
 *
 * Will be removed in a future version.
 */
public class BreedCommand extends AbstractCommand {

    private static final Message DEPRECATION_WARNING = Message.raw(
            "[Deprecated] Use /hytame instead. /breed will be removed in a future version."
    ).color("#FFAA00");

    public BreedCommand() {
        super("breed", "[Deprecated] Use /hytame - Animal Breeding & Taming");
        // Mirror all subcommands from HytameCommand with deprecation wrapper
        addSubCommand(new DeprecatedHelpSubCommand());
        addSubCommand(new DeprecatedStatusSubCommand());
        addSubCommand(new DeprecatedInfoSubCommand());
        addSubCommand(new DeprecatedTameSubCommand());
        addSubCommand(new DeprecatedUntameSubCommand());
        addSubCommand(new DeprecatedSettingsSubCommand());
        addSubCommand(new DeprecatedScanSubCommand());
        addSubCommand(new DeprecatedFoodsSubCommand());
        addSubCommand(new DeprecatedConfigSubCommand());
        addSubCommand(new DeprecatedGrowthSubCommand());
        addSubCommand(new DeprecatedCustomSubCommand());
        addSubCommand(new DeprecatedDebugSubCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Check Hytalor requirement - blocks non-admins if missing
        if (showHytalorWarningIfNeeded(ctx)) {
            return CompletableFuture.completedFuture(null);
        }
        // Show deprecation warning then help
        ctx.sendMessage(DEPRECATION_WARNING);
        ctx.sendMessage(Message.raw(""));
        // Show help from HytameCommand
        showDeprecatedHelp(ctx);
        return CompletableFuture.completedFuture(null);
    }

    // Track if we've shown the Hytalor warning this session
    private static final java.util.Set<UUID> hytalorWarningShown = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Check Hytalor and show appropriate message.
     * @return true if command should be blocked (non-admin without Hytalor)
     */
    private static boolean showHytalorWarningIfNeeded(CommandContext ctx) {
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

    private static void showDeprecatedHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== /breed is deprecated - Use /hytame ===").color("#FFAA00"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("The following commands have been renamed:").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /breed help    ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame help").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed status  ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame status").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed config  ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame config").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed growth  ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame growth").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed tame    ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame tame").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed untame  ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame untame").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed info    ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame info").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed settings").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame settings").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed custom  ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame custom").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed scan    ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame scan").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("  /breed debug   ").color("#888888")
                .insert(Message.raw("->").color("#555555"))
                .insert(Message.raw(" /hytame debug").color("#FFFFFF")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("For full help, run: ").color("#AAAAAA")
                .insert(Message.raw("/hytame help").color("#55FF55")));
    }

    /**
     * Base class for deprecated subcommands that shows warning before executing.
     */
    private static abstract class DeprecatedSubCommand extends AbstractCommand {
        public DeprecatedSubCommand(String name, String description) {
            super(name, "[Deprecated] " + description);
        }

        @Override
        protected final CompletableFuture<Void> execute(CommandContext ctx) {
            if (showHytalorWarningIfNeeded(ctx)) {
                return CompletableFuture.completedFuture(null);
            }
            ctx.sendMessage(DEPRECATION_WARNING);
            ctx.sendMessage(Message.raw(""));
            return executeDeprecated(ctx);
        }

        protected abstract CompletableFuture<Void> executeDeprecated(CommandContext ctx);
    }

    // --- Deprecated subcommand implementations ---
    // Each delegates to the corresponding HytameCommand subcommand logic

    private static class DeprecatedHelpSubCommand extends DeprecatedSubCommand {
        public DeprecatedHelpSubCommand() {
            super("help", "Show help information");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameHelpSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedStatusSubCommand extends DeprecatedSubCommand {
        public DeprecatedStatusSubCommand() {
            super("status", "View tracked animals");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameStatusSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedInfoSubCommand extends DeprecatedSubCommand {
        public DeprecatedInfoSubCommand() {
            super("info", "Show taming info");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameInfoSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedTameSubCommand extends DeprecatedSubCommand {
        public DeprecatedTameSubCommand() {
            super("tame", "Tame an animal");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameTameSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedUntameSubCommand extends DeprecatedSubCommand {
        public DeprecatedUntameSubCommand() {
            super("untame", "Release a tamed animal");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameUntameSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedSettingsSubCommand extends DeprecatedSubCommand {
        public DeprecatedSettingsSubCommand() {
            super("settings", "Taming settings");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameSettingsSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedScanSubCommand extends DeprecatedSubCommand {
        public DeprecatedScanSubCommand() {
            super("scan", "Scan for untracked babies");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameScanSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedFoodsSubCommand extends DeprecatedSubCommand {
        public DeprecatedFoodsSubCommand() {
            super("foods", "Quick food reference");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameFoodsSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedConfigSubCommand extends DeprecatedSubCommand {
        public DeprecatedConfigSubCommand() {
            super("config", "Configuration commands");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameConfigSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedGrowthSubCommand extends DeprecatedSubCommand {
        public DeprecatedGrowthSubCommand() {
            super("growth", "Toggle baby growth");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameGrowthSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedCustomSubCommand extends DeprecatedSubCommand {
        public DeprecatedCustomSubCommand() {
            super("custom", "Manage custom animals");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameCustomSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class DeprecatedDebugSubCommand extends DeprecatedSubCommand {
        public DeprecatedDebugSubCommand() {
            super("debug", "Debug commands");
        }

        @Override
        protected CompletableFuture<Void> executeDeprecated(CommandContext ctx) {
            new HytameCommand.HytameDebugSubCommand().executeFromDeprecated(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }
}
