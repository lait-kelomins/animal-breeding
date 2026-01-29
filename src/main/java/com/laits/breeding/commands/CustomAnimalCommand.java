package com.laits.breeding.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.util.AnimalFinder;
import com.laits.breeding.util.EcsReflectionUtil;

import it.unimi.dsi.fastutil.Pair;

import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * /customanimal - Manage custom animals from other mods.
 * Usage:
 * /customanimal add <modelAssetId> <food1> [food2] [food3] - Add a custom
 * animal
 * /customanimal remove <modelAssetId> - Remove a custom animal
 * /customanimal list - List all custom animals
 * /customanimal info <modelAssetId> - Show info about a custom animal
 * /customanimal enable <modelAssetId> - Enable a custom animal
 * /customanimal disable <modelAssetId> - Disable a custom animal
 * /customanimal addfood <modelAssetId> <food> - Add a breeding food
 * /customanimal removefood <modelAssetId> <food> - Remove a breeding food
 */
public class CustomAnimalCommand extends AbstractCommand {
    public CustomAnimalCommand() {
        super("customanimal", "[Deprecated] Manage custom animals - Use /breed custom instead");
        addSubCommand(new CustomAnimalAddCommand());
        addSubCommand(new CustomAnimalRemoveCommand());
        addSubCommand(new CustomAnimalListCommand());
        addSubCommand(new CustomAnimalInfoCommand());
        addSubCommand(new CustomAnimalEnableCommand());
        addSubCommand(new CustomAnimalDisableCommand());
        addSubCommand(new CustomAnimalAddFoodCommand());
        addSubCommand(new CustomAnimalRemoveFoodCommand());
        addSubCommand(new CustomAnimalScanCommand());
        addSubCommand(new CustomAnimalSetRoleCommand());
        addSubCommand(new CustomAnimalSetBabyCommand());
        addSubCommand(new CustomAnimalSetGrowthCommand());
        addSubCommand(new CustomAnimalSetCooldownCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw("[Deprecated] Use /breed custom instead").color("#FFAA00"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("=== Custom Animal Commands ===").color("#FF9900"));
        ctx.sendMessage(Message.raw("/customanimal add <model> <food> ").color("#AAAAAA")
                .insert(Message.raw("- Add custom animal").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("/customanimal remove <model> ").color("#AAAAAA")
                .insert(Message.raw("- Remove custom animal").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("/customanimal list ").color("#AAAAAA")
                .insert(Message.raw("- List all custom animals").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("/customanimal info <model> ").color("#AAAAAA")
                .insert(Message.raw("- Show custom animal info").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("/customanimal enable/disable <model> ").color("#AAAAAA")
                .insert(Message.raw("- Toggle enabled").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("/customanimal addfood/removefood <model> <food> ").color("#AAAAAA")
                .insert(Message.raw("- Modify foods").color("#FFFFFF")));
        ctx.sendMessage(Message.raw("Use /breedconfig save after changes to persist!").color("#FFAA00"));
        return CompletableFuture.completedFuture(null);
    }

    /** /customanimal add <npcRole> <food1> [food2] [food3] */
    public static class CustomAnimalAddCommand extends AbstractCommand {
        private final RequiredArg<String> roleArg;
        private final RequiredArg<String> food1Arg;
        private final OptionalArg<String> food2Arg;
        private final OptionalArg<String> food3Arg;

        public CustomAnimalAddCommand() {
            super("add", "Add a custom animal by NPC role");
            roleArg = withRequiredArg("npcRole", "NPC role name (validates and auto-discovers model)", ArgTypes.STRING);
            food1Arg = withRequiredArg("food1", "Primary breeding food item ID", ArgTypes.STRING);
            food2Arg = withOptionalArg("food2", "Optional second food", ArgTypes.STRING);
            food3Arg = withOptionalArg("food3", "Optional third food", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String roleName = ctx.get(roleArg);
            String food1 = BreedingConfigCommand.resolveFoodShortcut(ctx.get(food1Arg));

            List<String> foods = new ArrayList<>();
            foods.add(food1);

            String food2 = ctx.get(food2Arg);
            if (food2 != null && !food2.isEmpty()) {
                foods.add(BreedingConfigCommand.resolveFoodShortcut(food2));
            }
            String food3 = ctx.get(food3Arg);
            if (food3 != null && !food3.isEmpty()) {
                foods.add(BreedingConfigCommand.resolveFoodShortcut(food3));
            }

            // 1. Validate the NPC role exists
            NPCPlugin npcPlugin = NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(roleName);
            if (roleIndex < 0) {
                ctx.sendMessage(Message.raw("NPC role not found: " + roleName).color("#FF5555"));
                ctx.sendMessage(Message.raw("Make sure this is a valid NPC role name.").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("Use /breed custom scan to find creatures nearby.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            // 2. Discover model by spawning temp entity
            ctx.sendMessage(Message.raw("Discovering model for role: " + roleName + "...").color("#AAAAAA"));

            String modelAssetId = discoverModelFromRole(plugin, roleName, roleIndex);
            if (modelAssetId == null) {
                ctx.sendMessage(Message.raw("Could not determine model for role: " + roleName).color("#FF5555"));
                ctx.sendMessage(Message.raw("The role exists but model discovery failed.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            // 3. Check if model already registered
            if (plugin.getConfigManager().isCustomAnimal(modelAssetId)) {
                ctx.sendMessage(Message.raw("Model '" + modelAssetId + "' already registered!").color("#FFAA00"));
                ctx.sendMessage(Message.raw("Use /breed custom remove " + modelAssetId + " first.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            // 4. Store both model and role
            plugin.getConfigManager().addCustomAnimal(modelAssetId, foods);
            plugin.getConfigManager().setCustomAnimalNpcRole(modelAssetId, roleName);

            ctx.sendMessage(Message.raw("Added custom animal!").color("#55FF55"));
            ctx.sendMessage(Message.raw("  NPC Role: ").color("#AAAAAA")
                    .insert(Message.raw(roleName).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Model: ").color("#AAAAAA")
                    .insert(Message.raw(modelAssetId).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Foods: ").color("#AAAAAA")
                    .insert(Message.raw(String.join(", ", foods)).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Scanning world for creatures...").color("#AAAAAA"));

            // Trigger rescan to set up interactions
            plugin.autoSetupNearbyAnimals();

            ctx.sendMessage(Message.raw("Interactions set up! Feed the creature to breed.").color("#55FF55"));
            ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                    .insert(Message.raw("/breedconfig save").color("#FFFFFF"))
                    .insert(Message.raw(" to persist changes.").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("To set a baby role: ").color("#AAAAAA")
                    .insert(Message.raw("/breed custom setbaby " + modelAssetId + " <babyRole>").color("#FFFF55")));

            return CompletableFuture.completedFuture(null);
        }

        /**
         * Discover the model asset ID by spawning a temp entity and reading its
         * ModelComponent.
         */
        private String discoverModelFromRole(LaitsBreedingPlugin plugin, String roleName, int roleIndex) {
            try {
                World world = Universe.get().getDefaultWorld();

                // If getDefaultWorld fails, try to get the world from plugin's stored entities
                if (world == null) {
                    if (LaitsBreedingPlugin.isVerboseLogging()) {
                        plugin.getLogger().atInfo()
                                .log("[ModelDiscovery] getDefaultWorld returned null, trying alternative methods...");
                    }

                    // Try getting world via reflection on Universe
                    try {
                        java.lang.reflect.Method getWorlds = Universe.class.getMethod("getWorlds");
                        @SuppressWarnings("unchecked")
                        java.util.Collection<World> worlds = (java.util.Collection<World>) getWorlds
                                .invoke(Universe.get());
                        if (worlds != null && !worlds.isEmpty()) {
                            world = worlds.iterator().next();
                            if (LaitsBreedingPlugin.isVerboseLogging()) {
                                plugin.getLogger().atInfo().log("[ModelDiscovery] Got world from getWorlds()");
                            }
                        }
                    } catch (Exception e) {
                        if (LaitsBreedingPlugin.isVerboseLogging()) {
                            plugin.getLogger().atInfo().log("[ModelDiscovery] getWorlds() not available: %s",
                                    e.getMessage());
                        }
                    }
                }

                if (world == null) {
                    plugin.getLogger().atWarning().log("No world available for model discovery");
                    return null;
                }

                // Make effectively final for lambda
                final World finalWorld = world;

                // Use a CompletableFuture to get result from world thread
                CompletableFuture<String> future = new CompletableFuture<>();

                if (LaitsBreedingPlugin.isVerboseLogging()) {
                    plugin.getLogger().atInfo().log("[ModelDiscovery] Starting discovery for role: %s (index: %d)",
                            roleName, roleIndex);
                }

                finalWorld.execute(() -> {
                    try {
                        Store<EntityStore> store = finalWorld.getEntityStore().getStore();
                        NPCPlugin npcPlugin = NPCPlugin.get();

                        // Spawn at high Y location (above world) - negative Y may not work
                        Vector3d tempPos = new Vector3d(0, 500, 0);
                        Vector3f rotation = new Vector3f(0, 0, 0);

                        if (LaitsBreedingPlugin.isVerboseLogging()) {
                            plugin.getLogger().atInfo().log("[ModelDiscovery] Spawning temp entity at %s", tempPos);
                        }

                        // Use reflection for spawnEntity

                        Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get().spawnEntity(store, roleIndex,
                                tempPos, rotation, null, null);

                        if (result != null) {
                            if (LaitsBreedingPlugin.isVerboseLogging()) {
                                plugin.getLogger().atInfo()
                                        .log("[ModelDiscovery] Spawn succeeded, extracting model...");
                            }

                            // Result is Pair<Ref<EntityStore>, NPCEntity> - fastutil uses left()/right()
                            // Try multiple method names for compatibility
                            Ref<EntityStore> entityRef = null;
                            NPCEntity npcEntity = null;

                            // Try left()/right() first (fastutil ObjectObjectImmutablePair)
                            entityRef = result.left();
                            npcEntity = result.right();

                            if (entityRef != null) {
                                String modelId = extractModelFromRef(plugin, store, entityRef);

                                if (LaitsBreedingPlugin.isVerboseLogging()) {
                                    plugin.getLogger().atInfo().log("[ModelDiscovery] Extracted model: %s",
                                            modelId);
                                }

                                // Despawn the temp entity
                                npcEntity.setDespawning(true);

                                future.complete(modelId);
                                return;
                            } else {
                                plugin.getLogger().atWarning().log("[ModelDiscovery] entityRef is null");
                            }
                        } else {
                            plugin.getLogger().atWarning().log("[ModelDiscovery] spawnEntity returned null");
                        }
                        future.complete(null);
                    } catch (Exception e) {
                        plugin.getLogger().atWarning().log("[ModelDiscovery] Error: %s", e.getMessage());
                        e.printStackTrace();
                        future.complete(null);
                    }
                });

                // Wait for result with timeout
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("Model discovery failed for %s: %s", roleName, e.getMessage());
                return null;
            }
        }

        /**
         * Extract model asset ID from entity reference using ModelComponent.
         */
        private String extractModelFromRef(LaitsBreedingPlugin plugin, Store<EntityStore> store, Ref<EntityStore> ref) {
            try {
                ModelComponent modelComp = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
                if (modelComp == null) {
                    plugin.getLogger().atWarning().log("[ModelDiscovery] ModelComponent is null");
                    return null;
                }

                java.lang.reflect.Field modelField = ModelComponent.class.getDeclaredField("model");
                modelField.setAccessible(true);
                Object model = modelField.get(modelComp);
                if (model == null) {
                    plugin.getLogger().atWarning().log("[ModelDiscovery] model field is null");
                    return null;
                }

                String modelStr = model.toString();
                if (LaitsBreedingPlugin.isVerboseLogging()) {
                    plugin.getLogger().atInfo().log("[ModelDiscovery] Model toString: %s", modelStr);
                }

                // Try modelAssetId='...' format
                int start = modelStr.indexOf("modelAssetId='");
                if (start >= 0) {
                    start += 14;
                    int end = modelStr.indexOf("'", start);
                    if (end > start) {
                        return modelStr.substring(start, end);
                    }
                }

                // Try modelAssetId=... format (without quotes)
                start = modelStr.indexOf("modelAssetId=");
                if (start >= 0) {
                    start += 13;
                    int end = modelStr.indexOf(",", start);
                    if (end < 0)
                        end = modelStr.indexOf(")", start);
                    if (end < 0)
                        end = modelStr.indexOf("}", start);
                    if (end > start) {
                        return modelStr.substring(start, end).trim();
                    }
                }

                plugin.getLogger().atWarning().log("[ModelDiscovery] Could not parse modelAssetId from: %s", modelStr);
                return null;
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[ModelDiscovery] extractModelFromRef error: %s", e.getMessage());
                return null;
            }
        }
    }

    /** /customanimal remove <modelAssetId> */
    public static class CustomAnimalRemoveCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalRemoveCommand() {
            super("remove", "Remove a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID to remove", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (plugin.getConfigManager().removeCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Removed custom animal: ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /breedconfig save to persist changes!").color("#FFAA00"));
            } else {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal list */
    public static class CustomAnimalListCommand extends AbstractCommand {
        public CustomAnimalListCommand() {
            super("list", "List all custom animals");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            Map<String, CustomAnimalConfig> customs = plugin.getConfigManager().getCustomAnimals();
            if (customs.isEmpty()) {
                ctx.sendMessage(Message.raw("No custom animals defined.").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("Use /customanimal add <model> <food> to add one!").color("#FFAA00"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("=== Custom Animals (" + customs.size() + ") ===").color("#FF9900"));
            for (CustomAnimalConfig custom : customs.values()) {
                String status = custom.isEnabled() ? "[ON]" : "[OFF]";
                String statusColor = custom.isEnabled() ? "#55FF55" : "#FF5555";
                ctx.sendMessage(Message.raw(status).color(statusColor)
                        .insert(Message.raw(" " + custom.getModelAssetId()).color("#FFFFFF"))
                        .insert(Message.raw(" - " + custom.getBreedingFoods().size() + " foods").color("#AAAAAA")));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal info <modelAssetId> */
    public static class CustomAnimalInfoCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalInfoCommand() {
            super("info", "Show info about a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            CustomAnimalConfig custom = plugin.getConfigManager().getCustomAnimal(modelId);
            if (custom == null) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("=== " + custom.getDisplayName() + " ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Model ID: ").color("#AAAAAA")
                    .insert(Message.raw(custom.getModelAssetId()).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Enabled: ").color("#AAAAAA")
                    .insert(Message.raw(custom.isEnabled() ? "Yes" : "No")
                            .color(custom.isEnabled() ? "#55FF55" : "#FF5555")));
            ctx.sendMessage(Message.raw("Mountable: ").color("#AAAAAA")
                    .insert(Message.raw(custom.isMountable() ? "Yes" : "No").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                    .insert(Message.raw(custom.getGrowthTimeMinutes() + " min").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Breed Cooldown: ").color("#AAAAAA")
                    .insert(Message.raw(custom.getBreedCooldownMinutes() + " min").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));
            for (String food : custom.getBreedingFoods()) {
                ctx.sendMessage(Message.raw("  - " + food).color("#FFFFFF"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal enable <modelAssetId> */
    public static class CustomAnimalEnableCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalEnableCommand() {
            super("enable", "Enable a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalEnabled(modelId, true);
            ctx.sendMessage(Message.raw("Enabled custom animal: ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF")));
            plugin.autoSetupNearbyAnimals();
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal disable <modelAssetId> */
    public static class CustomAnimalDisableCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalDisableCommand() {
            super("disable", "Disable a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalEnabled(modelId, false);
            ctx.sendMessage(Message.raw("Disabled custom animal: ").color("#FF5555")
                    .insert(Message.raw(modelId).color("#FFFFFF")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal addfood <modelAssetId> <food> */
    public static class CustomAnimalAddFoodCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> foodArg;

        public CustomAnimalAddFoodCommand() {
            super("addfood", "Add a breeding food to a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Food item ID to add", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
            plugin.getConfigManager().addCustomAnimalFood(modelId, food);
            ctx.sendMessage(Message.raw("Added food ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" to " + modelId).color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal removefood <modelAssetId> <food> */
    public static class CustomAnimalRemoveFoodCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> foodArg;

        public CustomAnimalRemoveFoodCommand() {
            super("removefood", "Remove a breeding food from a custom animal");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Food item ID to remove", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
            plugin.getConfigManager().removeCustomAnimalFood(modelId, food);
            ctx.sendMessage(Message.raw("Removed food ").color("#FF5555")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" from " + modelId).color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Scan the world for all entities and show their modelAssetIds.
     * Helps users find the exact name to use for custom animals.
     */
    public static class CustomAnimalScanCommand extends AbstractCommand {
        public CustomAnimalScanCommand() {
            super("scan", "Scan world for all creature modelAssetIds");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("Scanning world for creatures...").color("#FFFF55"));

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                ctx.sendMessage(Message.raw("No world available!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Find ALL entities with ModelComponent
            AnimalFinder.findAnimals(world, false, animals -> {
                if (animals.isEmpty()) {
                    ctx.sendMessage(Message.raw("No creatures found in the world.").color("#AAAAAA"));
                    return;
                }

                // Group by modelAssetId and count
                Map<String, Integer> counts = new TreeMap<>();
                for (AnimalFinder.FoundAnimal animal : animals) {
                    String id = animal.getModelAssetId();
                    counts.merge(id, 1, Integer::sum);
                }

                ctx.sendMessage(
                        Message.raw("=== Detected Creatures (" + counts.size() + " types) ===").color("#FF9900"));

                // Show built-in animals first
                ctx.sendMessage(Message.raw("Built-in animals:").color("#55FF55"));
                int builtInCount = 0;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    AnimalType type = AnimalType.fromModelAssetId(entry.getKey());
                    if (type != null) {
                        ctx.sendMessage(Message.raw("  " + entry.getKey()).color("#AAAAAA")
                                .insert(Message.raw(" x" + entry.getValue()).color("#FFFFFF"))
                                .insert(Message.raw(" [" + type + "]").color("#55FF55")));
                        builtInCount++;
                    }
                }
                if (builtInCount == 0) {
                    ctx.sendMessage(Message.raw("  (none found)").color("#AAAAAA"));
                }

                // Show other creatures (potential custom animals)
                ctx.sendMessage(Message.raw("Other creatures (can add as custom):").color("#FFAA00"));
                int otherCount = 0;
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    AnimalType type = AnimalType.fromModelAssetId(entry.getKey());
                    if (type == null) {
                        // Check if already added as custom
                        boolean isCustom = plugin.getConfigManager().isCustomAnimal(entry.getKey());
                        String status = isCustom ? " [ADDED]" : "";
                        String statusColor = isCustom ? "#55FF55" : "#FFAA00";
                        ctx.sendMessage(Message.raw("  " + entry.getKey()).color("#FFFFFF")
                                .insert(Message.raw(" x" + entry.getValue()).color("#AAAAAA"))
                                .insert(Message.raw(status).color(statusColor)));
                        otherCount++;
                    }
                }
                if (otherCount == 0) {
                    ctx.sendMessage(Message.raw("  (none found)").color("#AAAAAA"));
                }

                ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                        .insert(Message.raw("/breed custom add <name> <food>").color("#FFFFFF"))
                        .insert(Message.raw(" to add a creature").color("#AAAAAA")));

                // Show registered custom animals for comparison
                Map<String, CustomAnimalConfig> customAnimals = plugin.getConfigManager().getCustomAnimals();
                if (!customAnimals.isEmpty()) {
                    ctx.sendMessage(Message.raw(""));
                    ctx.sendMessage(Message.raw("Registered custom animals:").color("#55FFFF"));
                    for (String registeredName : customAnimals.keySet()) {
                        boolean foundInWorld = counts.containsKey(registeredName);
                        String foundStatus = foundInWorld ? " [IN WORLD]" : " [NOT FOUND]";
                        String foundColor = foundInWorld ? "#55FF55" : "#FF5555";
                        ctx.sendMessage(Message.raw("  " + registeredName).color("#FFFFFF")
                                .insert(Message.raw(foundStatus).color(foundColor)));
                    }

                    // Trigger interaction setup for any custom animals found in world
                    ctx.sendMessage(Message.raw("Setting up interactions for custom animals...").color("#AAAAAA"));
                    plugin.autoSetupNearbyAnimals();
                }
            });

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /customanimal setrole <modelAssetId> <roleId> - Set the NPC role for spawning
     */
    public static class CustomAnimalSetRoleCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> roleArg;

        public CustomAnimalSetRoleCommand() {
            super("setrole", "Set the NPC role ID for spawning babies");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            roleArg = withRequiredArg("roleId", "NPC role ID", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            String roleId = ctx.get(roleArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Use /breed custom add first").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalNpcRole(modelId, roleId);
            ctx.sendMessage(Message.raw("Set NPC role for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(roleId).color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal setbaby <modelAssetId> <babyRoleId> - Set the baby NPC role */
    public static class CustomAnimalSetBabyCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> babyRoleArg;

        public CustomAnimalSetBabyCommand() {
            super("setbaby", "Set the NPC role for spawning babies");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID of the adult", ArgTypes.STRING);
            babyRoleArg = withRequiredArg("babyRoleId", "NPC role ID for baby spawning", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            String babyRoleId = ctx.get(babyRoleArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Use /breed custom add first").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            // Validate the baby role exists
            NPCPlugin npcPlugin = NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(babyRoleId);
            if (roleIndex < 0) {
                ctx.sendMessage(Message.raw("Baby NPC role not found: " + babyRoleId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Make sure this is a valid NPC role name.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalBabyRole(modelId, babyRoleId);
            ctx.sendMessage(Message.raw("Set baby NPC role for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(babyRoleId).color("#FFAA00")));
            ctx.sendMessage(Message.raw("Babies will now spawn using this role instead of scaling.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal setgrowth <modelAssetId> <minutes> - Set the growth time */
    public static class CustomAnimalSetGrowthCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<Double> timeArg;

        public CustomAnimalSetGrowthCommand() {
            super("setgrowth", "Set growth time in minutes");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            timeArg = withRequiredArg("minutes", "Growth time in minutes", ArgTypes.DOUBLE);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            double minutes = ctx.get(timeArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            if (minutes <= 0) {
                ctx.sendMessage(Message.raw("Growth time must be positive").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalGrowthTime(modelId, minutes);
            ctx.sendMessage(Message.raw("Set growth time for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(minutes + " min").color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /customanimal setcooldown <modelAssetId> <minutes> - Set the breeding
     * cooldown
     */
    public static class CustomAnimalSetCooldownCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<Double> timeArg;

        public CustomAnimalSetCooldownCommand() {
            super("setcooldown", "Set breeding cooldown in minutes");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            timeArg = withRequiredArg("minutes", "Cooldown in minutes", ArgTypes.DOUBLE);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            double minutes = ctx.get(timeArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            if (minutes < 0) {
                ctx.sendMessage(Message.raw("Cooldown must be non-negative").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalCooldown(modelId, minutes);
            ctx.sendMessage(Message.raw("Set cooldown for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(minutes + " min").color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
