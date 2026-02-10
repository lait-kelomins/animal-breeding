package com.hytame.commands;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
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
import com.hytame.HyTamePlugin;
import com.hytame.models.AnimalType;
import com.hytame.models.CustomAnimalConfig;
import com.hytame.patch.PatchSyncService;
import com.hytame.util.AnimalFinder;
import com.hytame.util.EcsReflectionUtil;

import it.unimi.dsi.fastutil.Pair;

import com.hypixel.hytale.server.core.entity.entities.Player;

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

    // Permission constant - use HytameCommand's for consistency
    private static final String PERM_ADMIN = HytameCommand.PERM_ADMIN;

    /**
     * Check admin permission and send error message if denied.
     * Uses HytamePermissions for singleplayer/multiplayer logic.
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

    /**
     * Sync the patch file for a custom animal after config changes.
     */
    private static void syncCustomAnimalPatch(HyTamePlugin plugin, String modelAssetId) {
        PatchSyncService patchSyncService = plugin.getPatchSyncService();
        if (patchSyncService == null) return;
        CustomAnimalConfig custom = plugin.getConfigManager().getCustomAnimal(modelAssetId);
        if (custom == null) return;
        patchSyncService.syncForCustomAnimal(custom);
    }

    public CustomAnimalCommand() {
        super("customanimal", "[Deprecated] Manage custom animals - Use /hytame custom instead");
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
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        ctx.sendMessage(Message.raw("[Deprecated] Use /hytame custom instead").color("#FFAA00"));
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
        ctx.sendMessage(Message.raw("Use /hytame config save after changes to persist!").color("#FFAA00"));
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
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
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
                ctx.sendMessage(Message.raw("Use /hytame custom scan to find creatures nearby.").color("#AAAAAA"));
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
                ctx.sendMessage(Message.raw("Use /hytame custom remove" + modelAssetId + " first.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            // 4. Store both model and role
            plugin.getConfigManager().addCustomAnimal(modelAssetId, foods);
            plugin.getConfigManager().setCustomAnimalNpcRole(modelAssetId, roleName);

            // 5. Sync patch file (LovedItems + parameters)
            syncCustomAnimalPatch(plugin, modelAssetId);

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
                    .insert(Message.raw("/hytame config save").color("#FFFFFF"))
                    .insert(Message.raw(" to persist changes.").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("To set a baby role: ").color("#AAAAAA")
                    .insert(Message.raw("/hytame custom setbaby" + modelAssetId + " <babyRole>").color("#FFFF55")));

            return CompletableFuture.completedFuture(null);
        }

        /**
         * Discover the model asset ID for a role.
         * First searches existing entities in the world, then falls back to spawning
         * a temp entity if none found.
         */
        private String discoverModelFromRole(HyTamePlugin plugin, String roleName, int roleIndex) {
            try {
                World world = Universe.get().getDefaultWorld();

                if (world == null) {
                    try {
                        java.lang.reflect.Method getWorlds = Universe.class.getMethod("getWorlds");
                        @SuppressWarnings("unchecked")
                        java.util.Collection<World> worlds = (java.util.Collection<World>) getWorlds
                                .invoke(Universe.get());
                        if (worlds != null && !worlds.isEmpty()) {
                            world = worlds.iterator().next();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                if (world == null) {
                    plugin.getLogger().atWarning().log("No world available for model discovery");
                    return null;
                }

                final World finalWorld = world;
                CompletableFuture<String> future = new CompletableFuture<>();

                finalWorld.execute(() -> {
                    try {
                        Store<EntityStore> store = finalWorld.getEntityStore().getStore();

                        // Strategy 1: Find an existing entity with matching role name
                        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
                        ComponentType<EntityStore, ModelComponent> modelType = EcsReflectionUtil.MODEL_TYPE;
                        String[] foundModel = {null};

                        store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                            if (foundModel[0] != null) return; // already found
                            for (int i = 0; i < chunk.size(); i++) {
                                NPCEntity npc = chunk.getComponent(i, npcType);
                                if (npc != null && roleName.equals(npc.getRoleName())) {
                                    Ref<EntityStore> ref = chunk.getReferenceTo(i);
                                    String modelId = extractModelFromRef(plugin, store, ref);
                                    if (modelId != null) {
                                        foundModel[0] = modelId;
                                        return;
                                    }
                                }
                            }
                        });

                        if (foundModel[0] != null) {
                            if (HyTamePlugin.isVerboseLogging()) {
                                plugin.getLogger().atInfo().log(
                                        "[ModelDiscovery] Found existing entity with role %s, model: %s",
                                        roleName, foundModel[0]);
                            }
                            future.complete(foundModel[0]);
                            return;
                        }

                        // Strategy 2: Spawn a temp entity and read its model
                        if (HyTamePlugin.isVerboseLogging()) {
                            plugin.getLogger().atInfo().log(
                                    "[ModelDiscovery] No existing entity found, spawning temp for role: %s",
                                    roleName);
                        }

                        Vector3d tempPos = new Vector3d(0, 500, 0);
                        Vector3f rotation = new Vector3f(0, 0, 0);

                        Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get().spawnEntity(store, roleIndex,
                                tempPos, rotation, null, null);

                        if (result != null) {
                            Ref<EntityStore> entityRef = result.left();
                            NPCEntity npcEntity = result.right();

                            if (entityRef != null) {
                                String modelId = extractModelFromRef(plugin, store, entityRef);
                                npcEntity.setDespawning(true);
                                future.complete(modelId);
                                return;
                            }
                        }

                        plugin.getLogger().atWarning().log("[ModelDiscovery] All strategies failed for %s", roleName);
                        future.complete(null);
                    } catch (Exception e) {
                        plugin.getLogger().atWarning().log("[ModelDiscovery] Error: %s", e.getMessage());
                        future.complete(null);
                    }
                });

                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("Model discovery failed for %s: %s", roleName, e.getMessage());
                return null;
            }
        }

        /**
         * Extract model asset ID from entity reference using ModelComponent.
         */
        private String extractModelFromRef(HyTamePlugin plugin, Store<EntityStore> store, Ref<EntityStore> ref) {
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
                if (HyTamePlugin.isVerboseLogging()) {
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
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (plugin.getConfigManager().removeCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Removed custom animal: ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Use /hytame config save to persist changes!").color("#FFAA00"));
            } else {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal list - Public, no permission required */
    public static class CustomAnimalListCommand extends AbstractCommand {
        public CustomAnimalListCommand() {
            super("list", "List all custom animals");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            HyTamePlugin plugin = HyTamePlugin.getInstance();
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

    /** /customanimal info <modelAssetId> - Deprecated, use /hytame config info */
    public static class CustomAnimalInfoCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalInfoCommand() {
            super("info", "[Deprecated] Use /hytame config info <animal> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config info " + ctx.get(modelArg) + " instead").color("#FFAA00"));

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

    /** /customanimal enable <modelAssetId> - Deprecated, use /hytame config enable */
    public static class CustomAnimalEnableCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalEnableCommand() {
            super("enable", "[Deprecated] Use /hytame config enable <animal> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config enable " + modelId + " instead").color("#FFAA00"));
            plugin.getConfigManager().setCustomAnimalEnabled(modelId, true);
            ctx.sendMessage(Message.raw("Enabled custom animal: ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF")));
            plugin.autoSetupNearbyAnimals();
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal disable <modelAssetId> - Deprecated, use /hytame config disable */
    public static class CustomAnimalDisableCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;

        public CustomAnimalDisableCommand() {
            super("disable", "[Deprecated] Use /hytame config disable <animal> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config disable " + modelId + " instead").color("#FFAA00"));
            plugin.getConfigManager().setCustomAnimalEnabled(modelId, false);
            ctx.sendMessage(Message.raw("Disabled custom animal: ").color("#FF5555")
                    .insert(Message.raw(modelId).color("#FFFFFF")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal addfood - Deprecated, use /hytame config addfood */
    public static class CustomAnimalAddFoodCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> foodArg;

        public CustomAnimalAddFoodCommand() {
            super("addfood", "[Deprecated] Use /hytame config addfood <animal> <food> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Food item ID to add", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config addfood " + modelId + " <food> instead").color("#FFAA00"));
            String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
            plugin.getConfigManager().addCustomAnimalFood(modelId, food);
            syncCustomAnimalPatch(plugin, modelId);
            ctx.sendMessage(Message.raw("Added food ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" to " + modelId).color("#AAAAAA")));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal removefood - Deprecated, use /hytame config removefood */
    public static class CustomAnimalRemoveFoodCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<String> foodArg;

        public CustomAnimalRemoveFoodCommand() {
            super("removefood", "[Deprecated] Use /hytame config removefood <animal> <food> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            foodArg = withRequiredArg("food", "Food item ID to remove", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config removefood " + modelId + " <food> instead").color("#FFAA00"));
            String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
            plugin.getConfigManager().removeCustomAnimalFood(modelId, food);
            syncCustomAnimalPatch(plugin, modelId);
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
    /** Scan - Public, no permission required */
    public static class CustomAnimalScanCommand extends AbstractCommand {
        public CustomAnimalScanCommand() {
            super("scan", "Scan world for all creature modelAssetIds");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            HyTamePlugin plugin = HyTamePlugin.getInstance();
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
                        .insert(Message.raw("/hytame custom add <name> <food>").color("#FFFFFF"))
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
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            String roleId = ctx.get(roleArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Use /hytame custom add first").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.getConfigManager().setCustomAnimalNpcRole(modelId, roleId);
            ctx.sendMessage(Message.raw("Set NPC role for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(roleId).color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /hytame config save to persist").color("#AAAAAA"));
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
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin == null || plugin.getConfigManager() == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String modelId = ctx.get(modelArg);
            String babyRoleId = ctx.get(babyRoleArg);

            if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                ctx.sendMessage(Message.raw("Use /hytame custom add first").color("#AAAAAA"));
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
            syncCustomAnimalPatch(plugin, modelId);
            ctx.sendMessage(Message.raw("Set baby NPC role for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(babyRoleId).color("#FFAA00")));
            ctx.sendMessage(Message.raw("Babies will now spawn using this role instead of scaling.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Use /hytame config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal setgrowth - Deprecated, use /hytame config set <animal> growth */
    public static class CustomAnimalSetGrowthCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<Double> timeArg;

        public CustomAnimalSetGrowthCommand() {
            super("setgrowth", "[Deprecated] Use /hytame config set <animal> growth <min> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            timeArg = withRequiredArg("minutes", "Growth time in minutes", ArgTypes.DOUBLE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
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

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config set " + modelId + " growth " + minutes + " instead").color("#FFAA00"));
            plugin.getConfigManager().setCustomAnimalGrowthTime(modelId, minutes);
            syncCustomAnimalPatch(plugin, modelId);
            ctx.sendMessage(Message.raw("Set growth time for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(minutes + " min").color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /hytame config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }

    /** /customanimal setcooldown - Deprecated, use /hytame config set <animal> cooldown */
    public static class CustomAnimalSetCooldownCommand extends AbstractCommand {
        private final RequiredArg<String> modelArg;
        private final RequiredArg<Double> timeArg;

        public CustomAnimalSetCooldownCommand() {
            super("setcooldown", "[Deprecated] Use /hytame config set <animal> cooldown <min> instead");
            modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            timeArg = withRequiredArg("minutes", "Cooldown in minutes", ArgTypes.DOUBLE);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (checkAdminDenied(ctx)) return CompletableFuture.completedFuture(null);
            HyTamePlugin plugin = HyTamePlugin.getInstance();
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

            ctx.sendMessage(Message.raw("[Deprecated] Use /hytame config set " + modelId + " cooldown " + minutes + " instead").color("#FFAA00"));
            plugin.getConfigManager().setCustomAnimalCooldown(modelId, minutes);
            syncCustomAnimalPatch(plugin, modelId);
            ctx.sendMessage(Message.raw("Set cooldown for ").color("#55FF55")
                    .insert(Message.raw(modelId).color("#FFFFFF"))
                    .insert(Message.raw(" to ").color("#55FF55"))
                    .insert(Message.raw(minutes + " min").color("#FFAA00")));
            ctx.sendMessage(Message.raw("Use /hytame config save to persist").color("#AAAAAA"));
            return CompletableFuture.completedFuture(null);
        }
    }
}
