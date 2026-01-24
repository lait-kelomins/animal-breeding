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
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.util.ConfigManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified command for all breeding functionality.
 * Usage: /breed [subcommand]
 * Subcommands: help, status, config, growth, tame, untame, info, settings, custom
 */
public class BreedCommand extends AbstractCommand {
    public BreedCommand() {
        super("breed", "Main command for Lait's Animal Breeding");
        addSubCommand(new BreedHelpSubCommand());
        addSubCommand(new BreedStatusSubCommand());
        addSubCommand(new BreedConfigSubCommand());
        addSubCommand(new BreedGrowthSubCommand());
        addSubCommand(new BreedTameSubCommand());
        addSubCommand(new BreedUntameSubCommand());
        addSubCommand(new BreedInfoSubCommand());
        addSubCommand(new BreedSettingsSubCommand());
        addSubCommand(new BreedCustomSubCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        // Default action: show help
        showHelp(ctx);
        return CompletableFuture.completedFuture(null);
    }

    private static void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Lait's Animal Breeding ===").color("#FF9900"));
        ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Commands:").color("#FFAA00"));
        ctx.sendMessage(Message.raw("/breed help").color("#FFFFFF")
                .insert(Message.raw(" - Show this help").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed status").color("#FFFFFF")
                .insert(Message.raw(" - View tracked animals").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed config ...").color("#FFFFFF")
                .insert(Message.raw(" - Configuration commands").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed growth").color("#FFFFFF")
                .insert(Message.raw(" - Toggle baby growth").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed tame <name>").color("#FFFFFF")
                .insert(Message.raw(" - Prepare to tame an animal").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed untame").color("#FFFFFF")
                .insert(Message.raw(" - Release a tamed animal").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed info").color("#FFFFFF")
                .insert(Message.raw(" - Show taming info").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed settings").color("#FFFFFF")
                .insert(Message.raw(" - Taming settings").color("#AAAAAA")));
        ctx.sendMessage(Message.raw("/breed custom ...").color("#FFFFFF")
                .insert(Message.raw(" - Manage custom animals").color("#AAAAAA")));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Feed animals their favorite food to breed!").color("#55FF55"));
    }

    // --- Subcommand: help ---
    public static class BreedHelpSubCommand extends AbstractCommand {
        public BreedHelpSubCommand() {
            super("help", "Show help information");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            showHelp(ctx);
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: status ---
    public static class BreedStatusSubCommand extends AbstractCommand {
        public BreedStatusSubCommand() {
            super("status", "View tracked animals and breeding stats");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
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

            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: config (delegates to BreedingConfigCommand subcommands) ---
    public static class BreedConfigSubCommand extends AbstractCommand {
        public BreedConfigSubCommand() {
            super("config", "Configuration commands");
            // Add all config subcommands
            addSubCommand(new BreedingConfigCommand.ReloadSubCommand());
            addSubCommand(new BreedingConfigCommand.SaveSubCommand());
            addSubCommand(new BreedingConfigCommand.ListSubCommand());
            addSubCommand(new BreedingConfigCommand.InfoSubCommand());
            addSubCommand(new BreedingConfigCommand.EnableSubCommand());
            addSubCommand(new BreedingConfigCommand.DisableSubCommand());
            addSubCommand(new BreedingConfigCommand.SetSubCommand());
            addSubCommand(new BreedingConfigCommand.AddFoodSubCommand());
            addSubCommand(new BreedingConfigCommand.RemoveFoodSubCommand());
            addSubCommand(new BreedingConfigCommand.PresetSubCommand());
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            // Show config summary
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Config not loaded!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }
            ctx.sendMessage(Message.raw("=== Breeding Config ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                    .insert(Message.raw(plugin.getConfigManager().getActivePreset()).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Type ").color("#AAAAAA")
                    .insert(Message.raw("/breed config").color("#FFFFFF"))
                    .insert(Message.raw(" and press TAB for subcommands").color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: growth ---
    public static class BreedGrowthSubCommand extends AbstractCommand {
        public BreedGrowthSubCommand() {
            super("growth", "Toggle baby animal growth");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
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
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: tame ---
    // [DEPRECATED] Taming is now done via Name Tag item with UI
    public static class BreedTameSubCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        public BreedTameSubCommand() {
            super("tame", "[Deprecated] Use Name Tag item on animal instead");
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

    // --- Subcommand: untame ---
    // [DEPRECATED] Untaming should be done via a dedicated UI or command with animal name
    public static class BreedUntameSubCommand extends AbstractCommand {
        public BreedUntameSubCommand() {
            super("untame", "[Deprecated] Release a tamed animal");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] This command has been replaced.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("To release a tamed animal, use the naming UI").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("to rename it, or contact server admin.").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: info ---
    public static class BreedInfoSubCommand extends AbstractCommand {
        public BreedInfoSubCommand() {
            super("info", "Show taming information");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
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
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: settings ---
    public static class BreedSettingsSubCommand extends AbstractCommand {
        public BreedSettingsSubCommand() {
            super("settings", "Taming settings");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("This command is not yet available.").color("#FFFF55"));
            ctx.sendMessage(Message.raw("By default, others CAN interact with your tamed animals.").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    // --- Subcommand: custom (delegates to CustomAnimalCommand subcommands) ---
    public static class BreedCustomSubCommand extends AbstractCommand {
        public BreedCustomSubCommand() {
            super("custom", "Manage custom animals from other mods");
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
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Custom Animal Commands ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("/breed custom scan").color("#FFFFFF")
                    .insert(Message.raw(" - Find creature names in world").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom add <model> <food>").color("#FFFFFF")
                    .insert(Message.raw(" - Add custom animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom remove <model>").color("#FFFFFF")
                    .insert(Message.raw(" - Remove custom animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom list").color("#FFFFFF")
                    .insert(Message.raw(" - List added custom animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom info <model>").color("#FFFFFF")
                    .insert(Message.raw(" - Show details").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom setrole <model> <role>").color("#FFFFFF")
                    .insert(Message.raw(" - Set NPC role for spawning").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom setgrowth <model> <min>").color("#FFFFFF")
                    .insert(Message.raw(" - Set growth time").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom setcooldown <model> <min>").color("#FFFFFF")
                    .insert(Message.raw(" - Set breeding cooldown").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("Run ").color("#AAAAAA")
                    .insert(Message.raw("/breed custom scan").color("#FFFF55"))
                    .insert(Message.raw(" first to find creature names!").color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }
}
