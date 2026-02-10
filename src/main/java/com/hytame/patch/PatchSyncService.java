package com.hytame.patch;

import com.hytame.HyTamePlugin;
import com.hytame.util.ConfigManager;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.models.AnimalType;
import com.hytame.models.CustomAnimalConfig;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Service for generating Hytale asset patches from config.
 * Syncs LovedItems parameter in NPC assets based on configured foods.
 *
 * Generated Asset Pack structure:
 * <world>/Mods/Config_HyTame/
 * ├── manifest.json
 * └── Server/Patch/
 * ├── NPC_Cow.json
 * ├── NPC_Wolf_Black.json
 * └── ... (one per animal with configured foods)
 */
public class PatchSyncService {

    // Hytale in-game time runs ~36x faster than real time
    // Empirically verified: 0.1 real min → 6 real sec at ratio 36
    private static final double REAL_TO_GAME_TIME_RATIO = 36.0;

    private static final String ASSET_PACK_NAME = "Config_HyTame";
    private static final String MANIFEST_TEMPLATE = """
            {
                "Group": "HyTame",
                "Name": "Config_HyTame",
                "Version": "1.0.0",
                "Description": "[Auto-Generated] Contains config and patches for HyTame",
                "Authors": [
                    {
                        "Name": "Lait",
                        "Email": "lait.kelomins@gmail.com",
                        "Url": ""
                    },
                    {
                        "Name": "TheBrandolorian",
                        "Email": "",
                        "Url": ""
                    }
                ],
                "Website": "",
                "Main": "com.hytame.Config_HyTame",
                "ServerVersion": "*",
                "Dependencies": {
                    "com.hypersonicsharkz:Hytalor": "*"
                },
                "IncludesAssetPack": true
            }
            """;

    private Path assetPackRoot;
    private Path patchFolder;
    private ConfigManager configManager;

    /**
     * Initialize the patch sync service.
     * Does NOT sync patches - call syncAllPatchesDeferred() from start() after
     * server is ready.
     *
     * @param configManager The config manager instance
     */
    public void initialize(ConfigManager configManager) {
        // Use PluginManager.MODS_PATH (same as InspectorPatches)
        this.assetPackRoot = PluginManager.MODS_PATH.resolve(ASSET_PACK_NAME);
        this.patchFolder = assetPackRoot.resolve("Server").resolve("Patch");
        this.configManager = configManager;

        ensureAssetPackExists();
        // Don't sync here - called from start() after server is ready
    }

    /**
     * Schedule patch sync after a delay.
     * Call from plugin start() after server is fully initialized.
     */
    public void syncAllPatchesDeferred(int delaySeconds) {
        new Thread(() -> {
            try {
                Thread.sleep(delaySeconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            logVerbose("Starting deferred patch sync...");
            syncAllPatchesInternal();
        }, "HyTame-PatchSync").start();
    }

    /**
     * Create the asset pack structure and register it with AssetModule
     * so Hytalor discovers and hot-reloads patches from it.
     */
    private void ensureAssetPackExists() {
        try {
            // Create directories
            Files.createDirectories(patchFolder);

            // Create manifest if missing
            Path manifestPath = assetPackRoot.resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                Files.writeString(manifestPath, MANIFEST_TEMPLATE);
                logVerbose("Created asset pack manifest: " + manifestPath);
            }

            // Register as an asset pack so Hytalor picks up Server/Patch/
            registerAssetPack();
        } catch (IOException e) {
            logWarning("Failed to create asset pack structure: " + e.getMessage());
        }
    }

    /**
     * Register HyTameConfig as an asset pack with AssetModule.
     * Hytalor iterates registered asset packs and loads patches from each pack's
     * Server/Patch/.
     * Without this registration, Hytalor doesn't know our patch directory exists.
     */
    private void registerAssetPack() {
        try {
            PluginManifest manifest = new PluginManifest(
                    "com.hytame",
                    ASSET_PACK_NAME,
                    Semver.fromString("1.0.0"),
                    "Auto-generated asset patches from HyTame config",
                    new ArrayList<>(), // authors
                    "", // website
                    null, // serverVersion
                    null, // source
                    new HashMap<>(), // dependencies
                    new HashMap<>(), // optionalDependencies
                    new HashMap<>(), // conflicts
                    new ArrayList<>(), // subPlugins
                    false // disabledByDefault
            );

            AssetModule.get().registerPack(
                    "com.hytame:" + ASSET_PACK_NAME,
                    assetPackRoot,
                    manifest);

            logVerbose(
                    "Registered asset pack: com.hytame:" + ASSET_PACK_NAME + " at " + assetPackRoot.toAbsolutePath());
        } catch (Exception e) {
            logWarning("Failed to register asset pack: " + e.getMessage());
        }
    }

    /**
     * Sync all patches from current config state (internal, runs on current
     * thread).
     * Only writes if patch file is missing or out of sync with config.
     */
    private void syncAllPatchesInternal() {
        int synced = 0;
        int skipped = 0;

        // Process livestock first (most commonly used), then others
        List<AnimalType> ordered = new ArrayList<>();
        for (AnimalType type : AnimalType.values()) {
            if (type.getCategory() == AnimalType.Category.LIVESTOCK) {
                ordered.add(0, type); // Add at front
            } else {
                ordered.add(type);
            }
        }

        for (AnimalType type : ordered) {
            String npcPath = type.getNpcRolePath();
            if (npcPath == null) {
                skipped++;
                continue;
            }

            ConfigManager.AnimalConfig config = configManager.getAnimalConfig(type);
            List<String> configFoods = getAllLovedItems(config);

            if (configFoods.isEmpty()) {
                skipped++;
                continue;
            }

            // Check if patch needs to be written
            if (!needsSync(type, configFoods)) {
                skipped++;
                continue;
            }

            ConfigManager.AnimalConfig ac = configManager.getAnimalConfig(type);
            double breedCooldown = ac.breedCooldownMinutes;
            // Include growth in adult patch only for non-baby-variant (scaled babies)
            double growthForAdult = type.hasBabyVariant() ? -1 : ac.growthTimeMinutes;

            syncCreaturePatch(type.getModelAssetId(), npcPath, configFoods, breedCooldown, growthForAdult);
            synced++;

            // Baby-variant animals need a separate growth patch targeting the baby role
            if (type.hasBabyVariant()) {
                syncGrowthForAnimal(type);
            }
        }

        // Also sync custom animals
        int customSynced = 0;
        Map<String, CustomAnimalConfig> customAnimals = configManager.getCustomAnimals();
        for (CustomAnimalConfig custom : customAnimals.values()) {
            if (!custom.getBreedingFoods().isEmpty()) {
                syncForCustomAnimal(custom);
                customSynced++;
            }
        }

        logVerbose("Synced " + synced + " patches + " + customSynced + " custom, skipped " + skipped + " (already in sync)");
    }

    /**
     * Check if a patch needs to be written/updated.
     * Compares config foods against existing patch file (or enum default if no
     * patch).
     */
    private boolean needsSync(AnimalType type, List<String> configFoods) {
        Path patchFile = patchFolder.resolve("NPC_" + type.getModelAssetId() + ".json");

        if (Files.exists(patchFile)) {
            // Compare against existing patch
            List<String> patchFoods = readLovedItemsFromPatch(patchFile);
            return !foodsMatch(configFoods, patchFoods);
        } else {
            // No patch exists - only create if config differs from enum default
            String defaultFood = type.getDefaultBreedingFood();
            if (configFoods.size() == 1 && configFoods.contains(defaultFood)) {
                return false; // Config matches default, no patch needed
            }
            return true; // Config differs from default, need patch
        }
    }

    /**
     * Read LovedItems array from an existing patch file.
     */
    private List<String> readLovedItemsFromPatch(Path patchFile) {
        try {
            String content = Files.readString(patchFile);
            // Simple parsing - find "Value": [...] and extract items
            int valueStart = content.indexOf("\"Value\"");
            if (valueStart == -1)
                return Collections.emptyList();

            int arrayStart = content.indexOf("[", valueStart);
            int arrayEnd = content.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1)
                return Collections.emptyList();

            String arrayContent = content.substring(arrayStart + 1, arrayEnd);
            List<String> foods = new ArrayList<>();
            for (String item : arrayContent.split(",")) {
                String trimmed = item.trim().replace("\"", "");
                if (!trimmed.isEmpty()) {
                    foods.add(trimmed);
                }
            }
            return foods;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Check if two food lists contain the same items (order-independent).
     */
    private boolean foodsMatch(List<String> a, List<String> b) {
        if (a.size() != b.size())
            return false;
        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);
        return setA.equals(setB);
    }

    /**
     * Sync the combined NPC patch file for an animal (adult role).
     * Includes LovedItems, BreedCooldownTimeout, and GrowthTimeout (for non-baby-variant animals).
     *
     * @param name                The animal name (for filename)
     * @param npcPath             The full NPC role path (BaseAssetPath)
     * @param lovedItems          List of item IDs for LovedItems parameter
     * @param breedCooldownMinutes Breed cooldown in real minutes (-1 to omit)
     * @param growthMinutes       Growth time in real minutes (-1 to omit)
     */
    public void syncCreaturePatch(String name, String npcPath, List<String> lovedItems,
            double breedCooldownMinutes, double growthMinutes) {
        if (patchFolder == null) {
            logWarning("PatchSyncService not initialized, cannot sync patch for " + name);
            return;
        }

        Path patchFile = patchFolder.resolve("NPC_" + name + ".json");
        String json = generateCombinedPatchJson(npcPath, lovedItems, breedCooldownMinutes, growthMinutes);

        try {
            Files.writeString(patchFile, json);
            logVerbose("Synced patch: " + patchFile.getFileName());
        } catch (IOException e) {
            logWarning("Failed to write patch file " + patchFile + ": " + e.getMessage());
        }
    }

    /**
     * Delete a creature's patch file.
     * Called when all custom foods are removed.
     *
     * @param name The animal name
     */
    public void deletePatch(String name) {
        if (patchFolder == null) {
            return;
        }

        Path patchFile = patchFolder.resolve("NPC_" + name + ".json");
        try {
            Files.deleteIfExists(patchFile);
            logVerbose("Deleted patch: " + patchFile.getFileName());
        } catch (IOException e) {
            logWarning("Failed to delete patch file " + patchFile + ": " + e.getMessage());
        }
    }

    /**
     * Generate combined patch JSON with LovedItems + optional Parameters.
     */
    private String generateCombinedPatchJson(String npcPath, List<String> lovedItems,
            double breedCooldownMinutes, double growthMinutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"$Comment\": \"Auto-generated by HyTame - DO NOT EDIT\",\n");
        sb.append("    \"_BaseAssetPath\": \"Server/").append(npcPath).append(".json\",\n");

        // Modify section: LovedItems
        sb.append("    \"Modify\": {\n");
        sb.append("        \"LovedItems\": [");
        for (int i = 0; i < lovedItems.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("\"").append(lovedItems.get(i)).append("\"");
        }
        sb.append("]\n");
        sb.append("    }");

        // Parameters section: BreedCooldownTimeout and/or GrowthTimeout
        boolean hasBreedCooldown = breedCooldownMinutes > 0;
        boolean hasGrowth = growthMinutes > 0;
        if (hasBreedCooldown || hasGrowth) {
            sb.append(",\n");
            sb.append("    \"Parameters\": {\n");
            boolean first = true;
            if (hasBreedCooldown) {
                String iso = minutesToIso(breedCooldownMinutes * REAL_TO_GAME_TIME_RATIO);
                sb.append("        \"BreedCooldownTimeout\": {\n");
                sb.append("            \"Value\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
                sb.append("        }");
                first = false;
            }
            if (hasGrowth) {
                if (!first) sb.append(",\n"); else sb.append("");
                String iso = minutesToIso(growthMinutes * REAL_TO_GAME_TIME_RATIO);
                sb.append("        \"GrowthTimeout\": {\n");
                sb.append("            \"Value\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
                sb.append("        }");
            }
            sb.append("\n    }");
        }

        sb.append("\n}\n");
        return sb.toString();
    }

    /**
     * Get all items that should be in LovedItems for an animal.
     * This is the union of taming and breeding foods.
     */
    private List<String> getAllLovedItems(ConfigManager.AnimalConfig config) {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(config.getEffectiveTamingFoods());
        all.addAll(config.getEffectiveBreedingFoods());
        return new ArrayList<>(all);
    }

    /**
     * Sync patch for a specific animal type.
     * Called after addfood/removefood commands.
     *
     * @param type The animal type that was modified
     */
    public void syncForAnimal(AnimalType type) {
        String npcPath = type.getNpcRolePath();
        if (npcPath == null) {
            logVerbose("No NPC path for " + type.name() + ", skipping patch sync");
            return;
        }

        ConfigManager.AnimalConfig config = configManager.getAnimalConfig(type);
        List<String> lovedItems = getAllLovedItems(config);

        if (lovedItems.isEmpty()) {
            deletePatch(type.getModelAssetId());
        } else {
            double breedCooldown = config.breedCooldownMinutes;
            double growthForAdult = type.hasBabyVariant() ? -1 : config.growthTimeMinutes;
            syncCreaturePatch(type.getModelAssetId(), npcPath, lovedItems, breedCooldown, growthForAdult);
        }
    }

    /**
     * Sync growth time patch for a specific animal.
     * For baby-variant animals: writes separate Growth_ patch targeting baby role.
     * For scaled babies: re-syncs the combined NPC_ patch (growth is included there).
     */
    public void syncGrowthForAnimal(AnimalType type) {
        if (type.hasBabyVariant()) {
            // Baby variants need a separate patch targeting the baby NPC role
            String targetPath = EcsReflectionUtil.resolveNpcRolePath(type.getBabyNpcRoleId());
            if (targetPath == null) {
                logVerbose("No NPC path for " + type.name() + " baby role, skipping growth patch sync");
                return;
            }

            ConfigManager.AnimalConfig config = configManager.getAnimalConfig(type);
            Path patchFile = patchFolder.resolve("Growth_" + type.getModelAssetId() + ".json");
            String json = generateGrowthPatchJson(targetPath, config.growthTimeMinutes);

            try {
                Files.writeString(patchFile, json);
                logVerbose("Synced growth patch: " + patchFile.getFileName() + " (" + config.growthTimeMinutes + " min)");
            } catch (IOException e) {
                logWarning("Failed to write growth patch " + patchFile + ": " + e.getMessage());
            }
        } else {
            // Scaled babies: growth is in the combined NPC_ patch, re-sync it
            syncForAnimal(type);
        }
    }

    private String generateGrowthPatchJson(String npcPath, double realMinutes) {
        double gameMinutes = realMinutes * REAL_TO_GAME_TIME_RATIO;
        String iso = minutesToIso(gameMinutes);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"$Comment\": \"Auto-generated by HyTame - DO NOT EDIT\",\n");
        sb.append("    \"_BaseAssetPath\": \"Server/").append(npcPath).append(".json\",\n");
        sb.append("    \"Parameters\": {\n");
        sb.append("        \"GrowthTimeout\": {\n");
        sb.append("            \"Value\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Convert minutes to ISO 8601 duration string.
     * Supports H, M, S designators for clean output.
     * 360.0 -> "PT6H", 720.0 -> "PT12H", 90.5 -> "PT1H30M30S"
     */
    private String minutesToIso(double minutes) {
        int totalSeconds = (int) Math.round(minutes * 60);
        int hours = totalSeconds / 3600;
        int mins = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;

        StringBuilder sb = new StringBuilder("PT");
        if (hours > 0)
            sb.append(hours).append("H");
        if (mins > 0)
            sb.append(mins).append("M");
        if (secs > 0)
            sb.append(secs).append("S");
        if (hours == 0 && mins == 0 && secs == 0)
            sb.append("0S");
        return sb.toString();
    }

    /**
     * Resolve the target NPC role path for patching.
     * Tries reflection first, falls back to config npcRolePath.
     */
    private String resolveTargetPath(String roleId, String configRolePath) {
        // Try reflection first (automatic)
        String resolved = EcsReflectionUtil.resolveNpcRolePath(roleId);
        if (resolved != null)
            return resolved;
        // Fallback to config
        return configRolePath;
    }

    /**
     * Sync food patch for a custom animal.
     *
     * @param custom The custom animal config
     */
    public void syncForCustomAnimal(CustomAnimalConfig custom) {
        String targetPath = resolveTargetPath(custom.getAdultNpcRoleId(), custom.getNpcRolePath());
        if (targetPath == null) {
            logVerbose("Cannot resolve NPC path for custom animal " + custom.getDisplayName() + ", skipping patch");
            return;
        }

        List<String> foods = custom.getBreedingFoods();
        if (foods.isEmpty()) {
            deletePatch(custom.getModelAssetId());
        } else {
            double breedCooldown = custom.getBreedCooldownMinutes();
            double growthForAdult = custom.hasBabyVariant() ? -1 : custom.getGrowthTimeMinutes();
            syncCreaturePatch(custom.getModelAssetId(), targetPath, foods, breedCooldown, growthForAdult);
        }

        // Baby-variant custom animals need a separate growth patch for the baby role
        if (custom.hasBabyVariant()) {
            syncGrowthForCustomAnimal(custom);
        }
    }

    /**
     * Sync growth time patch for a custom animal's baby role.
     * Only needed for baby-variant animals (separate NPC role path).
     *
     * @param custom The custom animal config
     */
    public void syncGrowthForCustomAnimal(CustomAnimalConfig custom) {
        String roleId = custom.hasBabyVariant() ? custom.getBabyNpcRoleId() : custom.getAdultNpcRoleId();
        String targetPath = resolveTargetPath(roleId, custom.getNpcRolePath());
        if (targetPath == null) {
            logVerbose("Cannot resolve NPC path for custom animal " + custom.getDisplayName() + ", skipping growth patch");
            return;
        }

        double growthMinutes = custom.getGrowthTimeMinutes();
        Path patchFile = patchFolder.resolve("Growth_" + custom.getModelAssetId() + ".json");
        String json = generateGrowthPatchJson(targetPath, growthMinutes);

        try {
            Files.writeString(patchFile, json);
            logVerbose("Synced custom growth patch: " + patchFile.getFileName() + " (" + growthMinutes + " min)");
        } catch (IOException e) {
            logWarning("Failed to write custom growth patch " + patchFile + ": " + e.getMessage());
        }
    }

    private void logVerbose(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && HyTamePlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[PatchSync] " + message);
        }
    }

    private void logWarning(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atWarning().log("[PatchSync] " + message);
        }
    }
}
