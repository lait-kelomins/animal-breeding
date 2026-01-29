package com.laits.breeding.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.util.ConfigManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Main /breedconfig command with proper sub-commands.
 * Uses the Hytale command API with typed arguments and tab completion.
 *
 * Usage:
 * /breedconfig - Show current config summary
 * /breedconfig list [category] - List animals (optionally by category)
 * /breedconfig info <animal> - Show detailed info for an animal
 * /breedconfig preset list - List available presets
 * /breedconfig preset apply <name> - Apply a preset
 * /breedconfig reload - Reload from file
 * /breedconfig save - Save current config
 * /breedconfig enable <animal|category|ALL> - Enable breeding
 * /breedconfig disable <animal|category|ALL> - Disable breeding
 * /breedconfig set <animal> food <item> - Set primary breeding food
 * /breedconfig set <animal> growth <min> - Set growth time
 * /breedconfig set <animal> cooldown <min> - Set breed cooldown
 * /breedconfig addfood <animal> <item> - Add breeding food
 * /breedconfig removefood <animal> <item> - Remove breeding food
 */
public class BreedingConfigCommand extends AbstractCommand {

    public BreedingConfigCommand() {
        super("breedconfig", "[Deprecated] Manage breeding configuration - Use /breed config instead");

        // Register all sub-commands
        addSubCommand(new ReloadSubCommand());
        addSubCommand(new SaveSubCommand());
        addSubCommand(new ListSubCommand());
        addSubCommand(new InfoSubCommand());
        addSubCommand(new EnableSubCommand());
        addSubCommand(new DisableSubCommand());
        addSubCommand(new SetSubCommand());
        addSubCommand(new AddFoodSubCommand());
        addSubCommand(new RemoveFoodSubCommand());
        addSubCommand(new PresetSubCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw("[Deprecated] Use /breed config instead").color("#FFAA00"));
        ctx.sendMessage(Message.raw(""));

        // Called when /breedconfig is run with no sub-command - show summary
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin == null) {
            ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
            return CompletableFuture.completedFuture(null);
        }

        ConfigManager config = plugin.getConfigManager();
        showConfigSummary(ctx, config);
        return CompletableFuture.completedFuture(null);
    }

    // ==================== Helper Methods ====================

    private static ConfigManager getConfig() {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        return plugin != null ? plugin.getConfigManager() : null;
    }

    /**
     * Check if command sender has admin access.
     * @return true if access is denied (should return early)
     */
    private static boolean checkAdminDenied(CommandContext ctx) {
        if (ctx.sender() instanceof Player player) {
            if (!HytamePermissions.hasAdminAccess(player)) {
                ctx.sendMessage(Message.raw("This command requires admin permissions (or Creative mode).").color("#FF5555"));
                return true;
            }
        }
        return false;
    }

    private static void showConfigSummary(CommandContext ctx, ConfigManager config) {
        // Test different color formats to find what works
        ctx.sendMessage(Message.raw("=== Breeding Config ===").color("#FF9900")); // Hex color
        ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                .insert(Message.raw(config.getActivePreset()).color("#FFFFFF")));

        // Count by category
        Map<AnimalType.Category, int[]> counts = new java.util.EnumMap<>(AnimalType.Category.class);
        for (AnimalType.Category cat : AnimalType.Category.values()) {
            counts.put(cat, new int[] { 0, 0 }); // [enabled, total]
        }
        for (AnimalType type : AnimalType.values()) {
            int[] c = counts.get(type.getCategory());
            c[1]++;
            if (config.isAnimalEnabled(type))
                c[0]++;
        }

        ctx.sendMessage(Message.raw("Categories:").color("#AAAAAA"));
        for (AnimalType.Category cat : AnimalType.Category.values()) {
            int[] c = counts.get(cat);
            String hexColor = c[0] > 0 ? "#55FF55" : "#AAAAAA"; // Green if enabled, gray if not
            ctx.sendMessage(Message.raw("  ")
                    .insert(Message.raw(cat.name()).color(hexColor))
                    .insert(Message.raw(": " + c[0] + "/" + c[1] + " enabled").color("#AAAAAA")));
        }

        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Type ").color("#AAAAAA")
                .insert(Message.raw("/breedconfig").color("#FFFFFF"))
                .insert(Message.raw(" and press TAB for commands").color("#AAAAAA")));
    }

    // ==================== Sub-Commands ====================

    /** /breedconfig reload - Admin only */
    public static class ReloadSubCommand extends AbstractCommand {
        public ReloadSubCommand() {
            super("reload", "Reload configuration from file");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }
            config.loadFromFile(LaitsBreedingPlugin.getInstance().getDataDirectory().resolve("config.json"));
            ctx.sendMessage(Message.raw("Config reloaded from file.").color("#55FF55"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig save - Admin only */
    public static class SaveSubCommand extends AbstractCommand {
        public SaveSubCommand() {
            super("save", "Save current configuration to file");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }
            config.saveToFile();
            ctx.sendMessage(Message.raw("Config saved to file.").color("#55FF55"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig list [category] */
    public static class ListSubCommand extends AbstractCommand {
        private final OptionalArg<AnimalType.Category> categoryArg;

        public ListSubCommand() {
            super("list", "List all animals or filter by category");
            categoryArg = withOptionalArg("category", "Filter by category (LIVESTOCK, MAMMAL, etc.)",
                    ArgTypes.forEnum("category", AnimalType.Category.class));
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            AnimalType.Category filterCat = ctx.get(categoryArg);

            String title = "=== Animal Config" + (filterCat != null ? " (" + filterCat + ")" : "") + " ===";
            ctx.sendMessage(Message.raw(title).color("#FF9900"));

            AnimalType.Category currentCat = null;
            for (AnimalType type : AnimalType.values()) {
                if (filterCat != null && type.getCategory() != filterCat)
                    continue;

                // Print category header
                if (currentCat != type.getCategory()) {
                    currentCat = type.getCategory();
                    ctx.sendMessage(Message.raw("--- " + currentCat.name() + " ---").color("#FFFF55"));
                }

                ConfigManager.AnimalConfig ac = config.getAnimalConfig(type);
                boolean enabled = config.isAnimalEnabled(type);
                int foodCount = ac != null ? ac.breedingFoods.size() : 1;
                double growth = ac != null ? ac.growthTimeMinutes : 30.0;
                boolean hasBaby = type.hasBabyVariant();

                // Build message with proper colors
                Message line = Message.raw(enabled ? "+ " : "- ").color(enabled ? "#55FF55" : "#FF5555")
                        .insert(Message.raw(String.format("%-15s ", type.name())).color("#FFFFFF"))
                        .insert(Message.raw("foods=").color("#AAAAAA"))
                        .insert(Message.raw(String.valueOf(foodCount)).color("#FFFF55"))
                        .insert(Message.raw(" growth=").color("#AAAAAA"))
                        .insert(Message.raw(String.format("%.0fm", growth)).color("#FFFF55"));
                if (!hasBaby) {
                    line = line.insert(Message.raw(" (no baby)").color("#555555"));
                }
                ctx.sendMessage(line);
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig info <animal> */
    public static class InfoSubCommand extends AbstractCommand {
        private final RequiredArg<String> animalArg;

        public InfoSubCommand() {
            super("info", "Show detailed information for an animal");
            animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String animalId = ctx.get(animalArg);
            ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);

            if (lookup == null) {
                ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                        .insert(Message.raw(animalId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            if (lookup.isBuiltIn()) {
                // Built-in animal info
                AnimalType type = lookup.getBuiltInType();
                ConfigManager.AnimalConfig ac = config.getAnimalConfig(type);

                ctx.sendMessage(Message.raw("=== " + type.name() + " ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Category: ").color("#AAAAAA")
                        .insert(Message.raw(type.getCategory().name()).color("#FFFFFF")));
                boolean breedingEnabled = config.isBreedingEnabled(type);
                boolean tamingEnabled = config.isTamingEnabled(type);
                ctx.sendMessage(Message.raw("Breeding: ").color("#AAAAAA")
                        .insert(Message.raw(breedingEnabled ? "Yes" : "No").color(breedingEnabled ? "#55FF55" : "#FF5555"))
                        .insert(Message.raw("  Taming: ").color("#AAAAAA"))
                        .insert(Message.raw(tamingEnabled ? "Yes" : "No").color(tamingEnabled ? "#55FF55" : "#FF5555")));
                boolean hasBaby = type.hasBabyVariant();
                Message babyMsg = Message.raw("Has Baby: ").color("#AAAAAA")
                        .insert(Message.raw(hasBaby ? "Yes" : "No").color(hasBaby ? "#55FF55" : "#FF5555"));
                if (hasBaby) {
                    babyMsg = babyMsg.insert(Message.raw(" (" + type.getBabyNpcRoleId() + ")").color("#AAAAAA"));
                }
                ctx.sendMessage(babyMsg);
                ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                        .insert(Message.raw((ac != null ? ac.growthTimeMinutes : 30.0) + " min").color("#FFFF55")));
                ctx.sendMessage(Message.raw("Cooldown: ").color("#AAAAAA")
                        .insert(Message.raw((ac != null ? ac.breedCooldownMinutes : 5.0) + " min").color("#FFFF55")));
                ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));

                List<String> foods = config.getBreedingFoods(type);
                for (int i = 0; i < foods.size(); i++) {
                    String food = foods.get(i);
                    boolean isPrimary = i == 0;
                    ctx.sendMessage(Message.raw(isPrimary ? "* " : "  ").color(isPrimary ? "#55FF55" : "#AAAAAA")
                            .insert(Message.raw(food).color("#FFFFFF")));
                }
            } else {
                // Custom animal info
                CustomAnimalConfig custom = lookup.getCustomConfig();

                ctx.sendMessage(Message.raw("=== " + custom.getDisplayName() + " (Custom) ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Model ID: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getModelAssetId()).color("#FFFFFF")));
                boolean breedingEnabled = custom.isBreedingEnabled();
                boolean tamingEnabled = custom.isTamingEnabled();
                ctx.sendMessage(Message.raw("Breeding: ").color("#AAAAAA")
                        .insert(Message.raw(breedingEnabled ? "Yes" : "No").color(breedingEnabled ? "#55FF55" : "#FF5555"))
                        .insert(Message.raw("  Taming: ").color("#AAAAAA"))
                        .insert(Message.raw(tamingEnabled ? "Yes" : "No").color(tamingEnabled ? "#55FF55" : "#FF5555")));
                ctx.sendMessage(Message.raw("NPC Role: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getAdultNpcRoleId()).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getGrowthTimeMinutes() + " min").color("#FFFF55")));
                ctx.sendMessage(Message.raw("Cooldown: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getBreedCooldownMinutes() + " min").color("#FFFF55")));
                ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));

                List<String> foods = custom.getBreedingFoods();
                for (int i = 0; i < foods.size(); i++) {
                    String food = foods.get(i);
                    boolean isPrimary = i == 0;
                    ctx.sendMessage(Message.raw(isPrimary ? "* " : "  ").color(isPrimary ? "#55FF55" : "#AAAAAA")
                            .insert(Message.raw(food).color("#FFFFFF")));
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig enable <target> - Admin only */
    public static class EnableSubCommand extends AbstractCommand {
        private final RequiredArg<String> targetArg;

        public EnableSubCommand() {
            super("enable", "Enable breeding for animal, category, or ALL");
            targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String target = ctx.get(targetArg).toUpperCase();
            handleToggle(ctx, config, target, true);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig disable <target> - Admin only */
    public static class DisableSubCommand extends AbstractCommand {
        private final RequiredArg<String> targetArg;

        public DisableSubCommand() {
            super("disable", "Disable breeding for animal, category, or ALL");
            targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String target = ctx.get(targetArg).toUpperCase();
            handleToggle(ctx, config, target, false);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Shared toggle logic for enable/disable */
    private static void handleToggle(CommandContext ctx, ConfigManager config, String target, boolean enable) {
        String statusColor = enable ? "#55FF55" : "#FF5555";
        String statusWord = enable ? "Enabled" : "Disabled";

        // Check if it's ALL
        if (target.equalsIgnoreCase("ALL")) {
            for (AnimalType type : AnimalType.values()) {
                config.setAnimalEnabled(type, enable);
            }
            // Also enable/disable all custom animals
            for (String customId : config.getCustomAnimals().keySet()) {
                config.setCustomAnimalEnabled(customId, enable);
            }
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for ALL animals (including custom).").color("#AAAAAA")));
            return;
        }

        // Check if it's a category
        try {
            AnimalType.Category cat = AnimalType.Category.valueOf(target.toUpperCase());
            int count = 0;
            for (AnimalType type : AnimalType.values()) {
                if (type.getCategory() == cat) {
                    config.setAnimalEnabled(type, enable);
                    count++;
                }
            }
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for " + count + " " + cat.name() + " animals.")
                            .color("#AAAAAA")));
            return;
        } catch (IllegalArgumentException ignored) {
        }

        // Use unified lookup for individual animal (built-in or custom)
        ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(target);
        if (lookup != null) {
            config.setAnyAnimalEnabled(target, enable);
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for ").color("#AAAAAA"))
                    .insert(Message.raw(lookup.getDisplayName()).color("#FFFFFF")));
        } else {
            ctx.sendMessage(Message.raw("Unknown animal or category: ").color("#FF5555")
                    .insert(Message.raw(target).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Animals: COW, PIG, CHICKEN, or custom animal names").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Categories: ").color("#AAAAAA")
                    .insert(Message.raw(Arrays.toString(AnimalType.Category.values())).color("#FFFFFF")));
        }
    }

    /** /breedconfig enabletaming <target> - Admin only */
    public static class EnableTamingSubCommand extends AbstractCommand {
        private final RequiredArg<String> targetArg;

        public EnableTamingSubCommand() {
            super("enabletaming", "Enable taming for animal, category, or ALL");
            targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String target = ctx.get(targetArg).toUpperCase();
            handleTamingToggle(ctx, config, target, true);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig disabletaming <target> - Admin only */
    public static class DisableTamingSubCommand extends AbstractCommand {
        private final RequiredArg<String> targetArg;

        public DisableTamingSubCommand() {
            super("disabletaming", "Disable taming for animal, category, or ALL");
            targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String target = ctx.get(targetArg).toUpperCase();
            handleTamingToggle(ctx, config, target, false);
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Shared toggle logic for taming enable/disable */
    private static void handleTamingToggle(CommandContext ctx, ConfigManager config, String target, boolean enable) {
        String statusColor = enable ? "#55FF55" : "#FF5555";
        String statusWord = enable ? "Enabled" : "Disabled";

        // Check if it's ALL
        if (target.equalsIgnoreCase("ALL")) {
            for (AnimalType type : AnimalType.values()) {
                config.setTamingEnabled(type, enable);
            }
            // Also enable/disable all custom animals
            for (String customId : config.getCustomAnimals().keySet()) {
                config.setCustomAnimalTamingEnabled(customId, enable);
            }
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" taming for ALL animals (including custom).").color("#AAAAAA")));
            return;
        }

        // Check if it's a category
        try {
            AnimalType.Category cat = AnimalType.Category.valueOf(target.toUpperCase());
            int count = 0;
            for (AnimalType type : AnimalType.values()) {
                if (type.getCategory() == cat) {
                    config.setTamingEnabled(type, enable);
                    count++;
                }
            }
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" taming for " + count + " " + cat.name() + " animals.")
                            .color("#AAAAAA")));
            return;
        } catch (IllegalArgumentException ignored) {
        }

        // Use unified lookup for individual animal (built-in or custom)
        ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(target);
        if (lookup != null) {
            config.setAnyAnimalTamingEnabled(target, enable);
            ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" taming for ").color("#AAAAAA"))
                    .insert(Message.raw(lookup.getDisplayName()).color("#FFFFFF")));
        } else {
            ctx.sendMessage(Message.raw("Unknown animal or category: ").color("#FF5555")
                    .insert(Message.raw(target).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Animals: COW, PIG, CHICKEN, or custom animal names").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Categories: ").color("#AAAAAA")
                    .insert(Message.raw(Arrays.toString(AnimalType.Category.values())).color("#FFFFFF")));
        }
    }

    /** /breedconfig set <animal> <property> <value> - Admin only */
    public static class SetSubCommand extends AbstractCommand {
        private final RequiredArg<String> animalArg;
        private final RequiredArg<String> propertyArg;
        private final RequiredArg<String> valueArg;

        public SetSubCommand() {
            super("set", "Set animal property (food, growth, cooldown)");
            animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                    ArgTypes.STRING);
            propertyArg = withRequiredArg("property", "Property: food, growth, cooldown",
                    ArgTypes.STRING);
            valueArg = withRequiredArg("value", "New value",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String animalId = ctx.get(animalArg);
            String property = ctx.get(propertyArg).toLowerCase();
            String value = ctx.get(valueArg);

            // Use unified lookup - works for both built-in and custom animals
            ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
            if (lookup == null) {
                ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                        .insert(Message.raw(animalId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            String displayName = lookup.getDisplayName();

            switch (property) {
                case "food":
                    // Resolve food shortcut
                    String resolvedFood = resolveFoodShortcut(value);
                    config.setAnyAnimalFood(animalId, resolvedFood);
                    ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                            .insert(Message.raw(displayName).color("#FFFFFF"))
                            .insert(Message.raw(" primary food to: ").color("#55FF55"))
                            .insert(Message.raw(resolvedFood).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("(This replaces all foods. Use ").color("#AAAAAA")
                            .insert(Message.raw("/breed config addfood").color("#FFFFFF"))
                            .insert(Message.raw(" to add more.)").color("#AAAAAA")));
                    break;

                case "growth":
                    try {
                        double minutes = Double.parseDouble(value);
                        config.setAnyAnimalGrowthTime(animalId, minutes);
                        ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                .insert(Message.raw(displayName).color("#FFFFFF"))
                                .insert(Message.raw(" growth time to: ").color("#55FF55"))
                                .insert(Message.raw(minutes + " min").color("#FFFF55")));
                    } catch (NumberFormatException e) {
                        ctx.sendMessage(Message.raw("Invalid number: ").color("#FF5555")
                                .insert(Message.raw(value).color("#FFFFFF")));
                    }
                    break;

                case "cooldown":
                    try {
                        double minutes = Double.parseDouble(value);
                        config.setAnyAnimalCooldown(animalId, minutes);
                        ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                .insert(Message.raw(displayName).color("#FFFFFF"))
                                .insert(Message.raw(" cooldown to: ").color("#55FF55"))
                                .insert(Message.raw(minutes + " min").color("#FFFF55")));
                    } catch (NumberFormatException e) {
                        ctx.sendMessage(Message.raw("Invalid number: ").color("#FF5555")
                                .insert(Message.raw(value).color("#FFFFFF")));
                    }
                    break;

                default:
                    ctx.sendMessage(Message.raw("Unknown property: ").color("#FF5555")
                            .insert(Message.raw(property).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Valid: food, growth, cooldown").color("#AAAAAA"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig addfood <animal> <item> - Admin only */
    public static class AddFoodSubCommand extends AbstractCommand {
        private final RequiredArg<String> animalArg;
        private final RequiredArg<String> foodArg;

        public AddFoodSubCommand() {
            super("addfood", "Add a breeding food to an animal");
            animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                    ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Item ID or shortcut (e.g., Carrot, Wheat, Apple)",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String animalId = ctx.get(animalArg);
            String foodInput = ctx.get(foodArg);

            // Use unified lookup
            ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
            if (lookup == null) {
                ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                        .insert(Message.raw(animalId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
            if (foodInput == null || foodInput.isEmpty()) {
                ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Resolve food shortcut
            String food = resolveFoodShortcut(foodInput);

            config.addAnyAnimalFood(animalId, food);
            ctx.sendMessage(Message.raw("Added ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" to " + lookup.getDisplayName() + " breeding foods.").color("#55FF55")));
            ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                    .insert(Message.raw(String.join(", ", config.getAnyAnimalFoods(animalId))).color("#FFFFFF")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig removefood <animal> <item> - Admin only */
    public static class RemoveFoodSubCommand extends AbstractCommand {
        private final RequiredArg<String> animalArg;
        private final RequiredArg<String> foodArg;

        public RemoveFoodSubCommand() {
            super("removefood", "Remove a breeding food from an animal");
            animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                    ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Item ID or shortcut to remove",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String animalId = ctx.get(animalArg);
            String foodInput = ctx.get(foodArg);

            // Use unified lookup
            ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
            if (lookup == null) {
                ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                        .insert(Message.raw(animalId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
            if (foodInput == null || foodInput.isEmpty()) {
                ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Resolve food shortcut
            String food = resolveFoodShortcut(foodInput);

            List<String> foods = config.getAnyAnimalFoods(animalId);
            if (foods.size() <= 1) {
                ctx.sendMessage(Message.raw("Cannot remove last food. Use ").color("#FF5555")
                        .insert(Message.raw("/breed config set food").color("#FFFFFF"))
                        .insert(Message.raw(" to replace instead.").color("#FF5555")));
                return CompletableFuture.completedFuture(null);
            }

            config.removeAnyAnimalFood(animalId, food);
            ctx.sendMessage(Message.raw("Removed ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" from " + lookup.getDisplayName() + " breeding foods.").color("#55FF55")));
            ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                    .insert(Message.raw(String.join(", ", config.getAnyAnimalFoods(animalId))).color("#FFFFFF")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig preset - with list, apply, save, and restore sub-commands */
    public static class PresetSubCommand extends AbstractCommand {
        public PresetSubCommand() {
            super("preset", "Manage configuration presets");
            addSubCommand(new PresetListSubCommand());
            addSubCommand(new PresetApplySubCommand());
            addSubCommand(new PresetSaveSubCommand());
            addSubCommand(new PresetRestoreSubCommand());
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            // Show preset help when /breedconfig preset is called alone
            ctx.sendMessage(Message.raw("=== Preset Commands ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("/breedconfig preset list ").color("#FFFFFF")
                    .insert(Message.raw("- Show available presets").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breedconfig preset apply <name> ").color("#FFFFFF")
                    .insert(Message.raw("- Apply a preset").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breedconfig preset save <name> ").color("#FFFFFF")
                    .insert(Message.raw("- Save current config as preset").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breedconfig preset restore <name> ").color("#FFFFFF")
                    .insert(Message.raw("- Reset built-in preset to defaults").color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig preset list */
    public static class PresetListSubCommand extends AbstractCommand {
        public PresetListSubCommand() {
            super("list", "Show available presets");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("=== Available Presets ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Current: ").color("#AAAAAA")
                    .insert(Message.raw(config.getActivePreset()).color("#FFFFFF")));
            for (String preset : config.getAvailablePresets()) {
                boolean isCurrent = preset.equals(config.getActivePreset());
                boolean isBuiltin = config.isBuiltinPreset(preset);
                String desc;
                switch (preset) {
                    case "default":
                        desc = "Original values, livestock only";
                        break;
                    case "default_extended":
                        desc = "Default timings + multiple foods";
                        break;
                    case "lait_curated":
                        desc = "Balanced timings, multiple foods";
                        break;
                    case "zoo":
                        desc = "Real animals (no mythic/vermin/boss)";
                        break;
                    case "all":
                        desc = "All 119 animals enabled";
                        break;
                    default:
                        desc = "(custom)";
                        break;
                }
                String marker = isCurrent ? "* " : (isBuiltin ? "  " : "  ");
                Message line = Message.raw(marker).color(isCurrent ? "#55FF55" : "#AAAAAA")
                        .insert(Message.raw(preset).color("#FFFFFF"))
                        .insert(Message.raw(" - " + desc).color("#555555"));
                ctx.sendMessage(line);
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig preset apply <name> - Admin only */
    public static class PresetApplySubCommand extends AbstractCommand {
        private final RequiredArg<String> presetArg;

        public PresetApplySubCommand() {
            super("apply", "Apply a configuration preset");
            presetArg = withRequiredArg("preset", "Preset name (default, lait_curated)",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String presetName = ctx.get(presetArg).toLowerCase();
            if (config.applyPreset(presetName)) {
                ctx.sendMessage(Message.raw("Applied preset: ").color("#55FF55")
                        .insert(Message.raw(presetName).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                        .insert(Message.raw("/breedconfig save").color("#FFFFFF"))
                        .insert(Message.raw(" to persist changes.").color("#AAAAAA")));
            } else {
                ctx.sendMessage(Message.raw("Unknown preset: ").color("#FF5555")
                        .insert(Message.raw(presetName).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Available: ").color("#AAAAAA")
                        .insert(Message.raw(String.join(", ", config.getAvailablePresets())).color("#FFFFFF")));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /breedconfig preset save <name> - Admin only */
    public static class PresetSaveSubCommand extends AbstractCommand {
        private final RequiredArg<String> presetArg;

        public PresetSaveSubCommand() {
            super("save", "Save current configuration as a preset");
            presetArg = withRequiredArg("name", "Name for the new preset",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String presetName = ctx.get(presetArg).toLowerCase().replaceAll("[^a-z0-9_-]", "_");
            if (config.saveAsPreset(presetName)) {
                ctx.sendMessage(Message.raw("Saved preset: ").color("#55FF55")
                        .insert(Message.raw(presetName).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("File: ").color("#AAAAAA")
                        .insert(Message.raw("mods/presets/" + presetName + ".json").color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("Failed to save preset!").color("#FF5555"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /breedconfig preset restore <name> - Reset a built-in preset to default
     * values - Admin only
     */
    public static class PresetRestoreSubCommand extends AbstractCommand {
        private final RequiredArg<String> presetArg;

        public PresetRestoreSubCommand() {
            super("restore", "Reset a built-in preset to its default values");
            presetArg = withRequiredArg("preset", "Preset name (default, lait_curated, zoo, all)",
                    ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            ConfigManager config = getConfig();
            if (config == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String presetName = ctx.get(presetArg).toLowerCase();

            if (!config.isBuiltinPreset(presetName)) {
                ctx.sendMessage(Message.raw("Can only restore built-in presets: ").color("#FF5555")
                        .insert(Message.raw("default, default_extended, lait_curated, zoo, all").color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }

            if (config.restorePreset(presetName)) {
                ctx.sendMessage(Message.raw("Restored preset to defaults: ").color("#55FF55")
                        .insert(Message.raw(presetName).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("All customizations have been reset.").color("#AAAAAA"));
            } else {
                ctx.sendMessage(Message.raw("Failed to restore preset!").color("#FF5555"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    // ==================== Food Shortcut Resolver ====================

    /**
     * Food shortcut mappings for easier command usage.
     * Maps short names like "Carrot" to full item IDs like
     * "Plant_Crop_Carrot_Item".
     */
    private static final Map<String, String> FOOD_SHORTCUTS = new HashMap<>();
    static {
        // Crops
        FOOD_SHORTCUTS.put("carrot", "Plant_Crop_Carrot_Item");
        FOOD_SHORTCUTS.put("wheat", "Plant_Crop_Wheat_Item");
        FOOD_SHORTCUTS.put("corn", "Plant_Crop_Corn_Item");
        FOOD_SHORTCUTS.put("potato", "Plant_Crop_Potato_Item");
        FOOD_SHORTCUTS.put("lettuce", "Plant_Crop_Lettuce_Item");
        FOOD_SHORTCUTS.put("cauliflower", "Plant_Crop_Cauliflower_Item");
        FOOD_SHORTCUTS.put("rice", "Plant_Crop_Rice_Item");

        // Mushrooms
        FOOD_SHORTCUTS.put("mushroom_brown", "Plant_Crop_Mushroom_Cap_Brown");
        FOOD_SHORTCUTS.put("mushroom_red", "Plant_Crop_Mushroom_Cap_Red");
        FOOD_SHORTCUTS.put("brown_mushroom", "Plant_Crop_Mushroom_Cap_Brown");
        FOOD_SHORTCUTS.put("red_mushroom", "Plant_Crop_Mushroom_Cap_Red");

        // Fruits
        FOOD_SHORTCUTS.put("apple", "Plant_Fruit_Apple");
        FOOD_SHORTCUTS.put("berries", "Plant_Fruit_Berries_Red");
        FOOD_SHORTCUTS.put("red_berries", "Plant_Fruit_Berries_Red");
        FOOD_SHORTCUTS.put("cactus_flower", "Plant_Cactus_Flower");

        // Meat (raw and cooked)
        FOOD_SHORTCUTS.put("wildmeat", "Food_Wildmeat_Raw");
        FOOD_SHORTCUTS.put("wildmeat_raw", "Food_Wildmeat_Raw");
        FOOD_SHORTCUTS.put("wildmeat_cooked", "Food_Wildmeat_Cooked");
        FOOD_SHORTCUTS.put("cooked_wildmeat", "Food_Wildmeat_Cooked");
        FOOD_SHORTCUTS.put("meat", "Food_Wildmeat_Raw");
        FOOD_SHORTCUTS.put("meat_raw", "Food_Wildmeat_Raw");
        FOOD_SHORTCUTS.put("raw_meat", "Food_Wildmeat_Raw");
        FOOD_SHORTCUTS.put("meat_cooked", "Food_Wildmeat_Cooked");
        FOOD_SHORTCUTS.put("cooked_meat", "Food_Wildmeat_Cooked");
        FOOD_SHORTCUTS.put("beef", "Food_Beef_Raw");
        FOOD_SHORTCUTS.put("beef_raw", "Food_Beef_Raw");
        FOOD_SHORTCUTS.put("pork", "Food_Pork_Raw");
        FOOD_SHORTCUTS.put("pork_raw", "Food_Pork_Raw");
        FOOD_SHORTCUTS.put("chicken_meat", "Food_Chicken_Raw");
        FOOD_SHORTCUTS.put("chicken_raw", "Food_Chicken_Raw");

        // Fish
        FOOD_SHORTCUTS.put("fish", "Food_Fish_Raw");
        FOOD_SHORTCUTS.put("fish_raw", "Food_Fish_Raw");
        FOOD_SHORTCUTS.put("raw_fish", "Food_Fish_Raw");
        FOOD_SHORTCUTS.put("fish_grilled", "Food_Fish_Grilled");
        FOOD_SHORTCUTS.put("grilled_fish", "Food_Fish_Grilled");
        FOOD_SHORTCUTS.put("fish_cooked", "Food_Fish_Grilled");
        FOOD_SHORTCUTS.put("cooked_fish", "Food_Fish_Grilled");
    }

    /**
     * Resolve a food shortcut to its full item ID.
     * If the input doesn't match any shortcut, returns the input unchanged.
     */
    public static String resolveFoodShortcut(String input) {
        if (input == null)
            return null;
        String resolved = FOOD_SHORTCUTS.get(input.toLowerCase());
        return resolved != null ? resolved : input;
    }

    // ==================== Food Display Names (Reverse Mapping) ====================

    /**
     * Maps item IDs to human-readable display names.
     */
    private static final Map<String, String> FOOD_DISPLAY_NAMES = new HashMap<>();
    static {
        // Crops
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Carrot_Item", "Carrot");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Wheat_Item", "Wheat");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Corn_Item", "Corn");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Potato_Item", "Potato");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Lettuce_Item", "Lettuce");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Cauliflower_Item", "Cauliflower");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Rice_Item", "Rice");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Beetroot_Item", "Beetroot");

        // Mushrooms
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Mushroom_Cap_Brown", "Brown Mushroom");
        FOOD_DISPLAY_NAMES.put("Plant_Crop_Mushroom_Cap_Red", "Red Mushroom");

        // Fruits
        FOOD_DISPLAY_NAMES.put("Plant_Fruit_Apple", "Apple");
        FOOD_DISPLAY_NAMES.put("Plant_Fruit_Berries_Red", "Red Berries");
        FOOD_DISPLAY_NAMES.put("Plant_Cactus_Flower", "Cactus Flower");

        // Meat
        FOOD_DISPLAY_NAMES.put("Food_Wildmeat_Raw", "Raw Meat");
        FOOD_DISPLAY_NAMES.put("Food_Wildmeat_Cooked", "Cooked Meat");
        FOOD_DISPLAY_NAMES.put("Food_Beef_Raw", "Raw Beef");
        FOOD_DISPLAY_NAMES.put("Food_Beef_Cooked", "Cooked Beef");
        FOOD_DISPLAY_NAMES.put("Food_Pork_Raw", "Raw Pork");
        FOOD_DISPLAY_NAMES.put("Food_Pork_Cooked", "Cooked Pork");
        FOOD_DISPLAY_NAMES.put("Food_Chicken_Raw", "Raw Chicken");
        FOOD_DISPLAY_NAMES.put("Food_Chicken_Cooked", "Cooked Chicken");

        // Fish
        FOOD_DISPLAY_NAMES.put("Food_Fish_Raw", "Raw Fish");
        FOOD_DISPLAY_NAMES.put("Food_Fish_Grilled", "Grilled Fish");

        // Seeds
        FOOD_DISPLAY_NAMES.put("Plant_Seed_Wheat", "Wheat Seeds");
        FOOD_DISPLAY_NAMES.put("Plant_Seed_Corn", "Corn Seeds");
        FOOD_DISPLAY_NAMES.put("Plant_Seed_Carrot", "Carrot Seeds");
        FOOD_DISPLAY_NAMES.put("Seeds", "Seeds");
    }

    /**
     * Get a human-readable display name for a food item ID.
     * If no display name is found, attempts to generate one from the item ID.
     */
    public static String getFoodDisplayName(String itemId) {
        if (itemId == null) return "Unknown";

        // Check for known display name
        String displayName = FOOD_DISPLAY_NAMES.get(itemId);
        if (displayName != null) return displayName;

        // Auto-generate from item ID: "Plant_Crop_Carrot_Item" -> "Carrot"
        String name = itemId;

        // Remove common prefixes
        if (name.startsWith("Plant_Crop_")) name = name.substring(11);
        else if (name.startsWith("Plant_Fruit_")) name = name.substring(12);
        else if (name.startsWith("Plant_Seed_")) name = name.substring(11);
        else if (name.startsWith("Plant_")) name = name.substring(6);
        else if (name.startsWith("Food_")) name = name.substring(5);

        // Remove common suffixes
        if (name.endsWith("_Item")) name = name.substring(0, name.length() - 5);

        // Replace underscores with spaces and capitalize
        name = name.replace("_", " ");

        // Title case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    /**
     * Convert a list of food item IDs to human-readable display names.
     */
    public static String getFoodDisplayList(List<String> foods) {
        if (foods == null || foods.isEmpty()) return "(none)";
        return foods.stream()
                .map(BreedingConfigCommand::getFoodDisplayName)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
