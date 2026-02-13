package com.hytame.patch;

import com.hytame.HyTamePlugin;
import com.hytame.util.ConfigManager;
import com.hytame.util.EcsReflectionUtil;
import com.hytame.models.AnimalType;
import com.hytame.models.CustomAnimalConfig;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.assetstore.AssetPack;
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

    // All Variant roles of Template_Animal_Neutral need Modify for LovedItems
    // to reach Enabled: Compute during role compilation (memory rule #19).
    // Only Template_Animal_Neutral defines LovedItems in its Parameters.
    // Source: docs/npc-inheritance-existing.md
    private static final Set<String> ANIMAL_NEUTRAL_VARIANTS = Set.of(
        // Livestock (adults)
        "Boar", "Bison", "Camel", "Chicken", "Chicken_Desert", "Cow", "Goat",
        "Horse", "Mouflon", "Pig", "Pig_Wild", "Rabbit", "Ram", "Sheep",
        "Skrill", "Turkey", "Warthog",
        // Livestock (babies)
        "Boar_Piglet", "Bison_Calf", "Bunny", "Camel_Calf", "Chicken_Chick",
        "Chicken_Desert_Chick", "Cow_Calf", "Goat_Kid", "Horse_Foal",
        "Mouflon_Lamb", "Pig_Piglet", "Pig_Wild_Piglet", "Ram_Lamb",
        "Sheep_Lamb", "Skrill_Chick", "Turkey_Chick", "Warthog_Piglet",
        // Mammals
        "Antelope", "Armadillo", "Deer_Doe", "Deer_Stag",
        "Moose_Bull", "Moose_Cow", "Mosshorn", "Mosshorn_Plain",
        // Others
        "Crab", "Flamingo", "Penguin", "Tetrabird", "Tortoise"
    );

    // Subset: animals whose vanilla Modify section already overrides LovedItems.
    // These need "$.LovedItems" (replace) vs just "LovedItems" (add) in Modify.
    // Source: reverse-engineer/source/Assets/Server/NPC/Roles/ (grep for LovedItems)
    private static final Set<String> HAS_NATIVE_LOVED_ITEMS = Set.of(
        "Boar", "Boar_Piglet", "Bunny", "Camel", "Camel_Calf",
        "Chicken", "Chicken_Chick", "Chicken_Desert", "Chicken_Desert_Chick",
        "Cow", "Cow_Calf", "Goat", "Goat_Kid", "Horse", "Horse_Foal",
        "Mosshorn", "Mosshorn_Plain", "Mouflon", "Mouflon_Lamb",
        "Penguin", "Pig", "Pig_Piglet", "Pig_Wild", "Pig_Wild_Piglet",
        "Rabbit", "Ram", "Ram_Lamb", "Sheep", "Sheep_Lamb",
        "Skrill", "Skrill_Chick", "Turkey", "Turkey_Chick", "Warthog_Piglet"
    );

    private Path assetPackRoot;
    private Path patchFolder;
    private ConfigManager configManager;
    private boolean forceSyncAll = false;

    /**
     * Initialize the patch sync service.
     * Creates directory structure, manifest, and writes ALL patch files immediately.
     * Must be called during setup() so patches exist on disk before LoadAssetEvent fires.
     *
     * @param configManager The config manager instance
     */
    public void initialize(ConfigManager configManager) {
        // Use PluginManager.MODS_PATH (same as InspectorPatches)
        this.assetPackRoot = PluginManager.MODS_PATH.resolve(ASSET_PACK_NAME);
        this.patchFolder = assetPackRoot.resolve("Server").resolve("Patch");
        this.configManager = configManager;

        ensureAssetPackExists();

        // Write all patches immediately during setup(), before LoadAssetEvent fires.
        // AssetModule loads all registered packs at LoadAssetEvent priority -16,
        // so patches must be on disk before that.
        syncAllPatchesInternal();
    }

    /**
     * Schedule patch sync after a delay.
     * Call from plugin start() after server is fully initialized.
     */
    /**
     * Sync all patches immediately (blocking).
     * Call after preset changes or bulk enable/disable operations.
     */
    public void syncAllPatches() {
        syncAllPatchesInternal();
    }

    /**
     * Force sync all patches, ignoring needsSync check.
     * Writes patches for ALL animals with configured foods.
     */
    public void forceSyncAllPatches() {
        forceSyncAll = true;
        try {
            syncAllPatchesInternal();
        } finally {
            forceSyncAll = false;
        }
    }

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
     * Create the asset pack directory structure and manifest.
     * Does NOT register with AssetModule — call registerAssetPack() from start().
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
        } catch (IOException e) {
            logWarning("Failed to create asset pack structure: " + e.getMessage());
        }
    }

    /**
     * Ensure the Config_HyTame asset pack is registered with AssetModule.
     * On returning installs, AssetModule.setup() already scanned MODS_PATH and found
     * our directory. On first install, we need to register it manually.
     *
     * Called from LoadAssetEvent handler (priority -20, before AssetModule loads at -16).
     * At this point hasLoaded is still false, so registerPack() just adds to the list
     * and AssetModule will load it along with all other packs at priority -16.
     */
    public void ensurePackRegistered() {
        try {
            AssetModule assetModule = AssetModule.get();
            if (assetModule == null) {
                logWarning("AssetModule not available, cannot register pack");
                return;
            }

            // Check if already registered (AssetModule scans MODS_PATH during its setup)
            for (AssetPack pack : assetModule.getAssetPacks()) {
                if (pack.getName().contains(ASSET_PACK_NAME)) {
                    logVerbose("Pack already registered by AssetModule: " + pack.getName());
                    return;
                }
            }

            // First install: register the pack before AssetModule loads at -16
            registerAssetPack();
        } catch (Exception e) {
            logWarning("Failed to ensure pack registered: " + e.getMessage());
        }
    }

    /**
     * Register Config_HyTame as an asset pack with AssetModule.
     * Adds the pack to AssetModule's list so it gets loaded during LoadAssetEvent.
     */
    public void registerAssetPack() {
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
        if (forceSyncAll) return true;

        Path patchFile = patchFolder.resolve("NPC_" + type.getModelAssetId() + ".json");

        if (Files.exists(patchFile)) {
            // Compare foods
            List<String> patchFoods = readLovedItemsFromPatch(patchFile);
            if (!foodsMatch(configFoods, patchFoods)) return true;

            // Compare path (may have changed due to path fixes)
            String expectedPath = "Server/" + type.getNpcRolePath() + ".json";
            String existingPath = readBaseAssetPathFromPatch(patchFile);
            if (!expectedPath.equals(existingPath)) return true;

            // Check format: Animal_Neutral variants need Modify, others need Parameters
            try {
                String content = Files.readString(patchFile);
                boolean needsModify = ANIMAL_NEUTRAL_VARIANTS.contains(type.getModelAssetId());
                boolean hasModify = content.contains("\"Modify\"");
                if (needsModify && !hasModify) return true;   // Needs Modify but has Parameters
                if (!needsModify && hasModify) return true;   // Has Modify but needs Parameters
            } catch (IOException ignored) {}

            return false;
        }

        // No patch exists - always write one. HyTame's config foods
        // likely differ from vanilla LovedItems, so a patch is needed.
        return true;
    }

    /**
     * Read LovedItems array from an existing patch file.
     */
    private List<String> readLovedItemsFromPatch(Path patchFile) {
        try {
            String content = Files.readString(patchFile);
            // Try Parameters format first: "Value": [...]
            int valueStart = content.indexOf("\"Value\"");
            if (valueStart == -1) {
                // Try Modify format: "$.LovedItems": [...] (replace) or "LovedItems": [...] (set)
                valueStart = content.indexOf("\"$.LovedItems\"");
            }
            if (valueStart == -1) {
                valueStart = content.indexOf("\"LovedItems\"");
            }
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
     * Read _BaseAssetPath from an existing patch file.
     */
    private String readBaseAssetPathFromPatch(Path patchFile) {
        try {
            String content = Files.readString(patchFile);
            String key = "\"_BaseAssetPath\"";
            int keyIdx = content.indexOf(key);
            if (keyIdx == -1) return "";
            int colonIdx = content.indexOf(":", keyIdx + key.length());
            if (colonIdx == -1) return "";
            int quoteStart = content.indexOf("\"", colonIdx + 1);
            int quoteEnd = content.indexOf("\"", quoteStart + 1);
            if (quoteStart == -1 || quoteEnd == -1) return "";
            return content.substring(quoteStart + 1, quoteEnd);
        } catch (IOException e) {
            return "";
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
        String json = generateCombinedPatchJson(name, npcPath, lovedItems, breedCooldownMinutes, growthMinutes);

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
     * Generate combined patch JSON with LovedItems + optional timing parameters.
     *
     * LovedItems placement depends on the template chain:
     * - Template_Animal_Neutral variants: use Modify so LovedItems reaches Enabled: Compute.
     *   "$.LovedItems" if vanilla Modify already overrides it, "LovedItems" otherwise.
     * - All other roles: use Parameters (no Enabled compute dependency on LovedItems).
     *
     * Timing parameters always use Parameters section (no Enabled compute dependency).
     */
    private String generateCombinedPatchJson(String modelAssetId, String npcPath,
            List<String> lovedItems, double breedCooldownMinutes, double growthMinutes) {
        // Template_Animal_Neutral variants need Modify for LovedItems to reach
        // Enabled: Compute (memory rule #19). All other roles use Parameters.
        boolean useModify = ANIMAL_NEUTRAL_VARIANTS.contains(modelAssetId);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"$Comment\": \"Auto-generated by HyTame - DO NOT EDIT\",\n");
        sb.append("    \"_BaseAssetPath\": \"Server/").append(npcPath).append(".json\",\n");

        if (useModify) {
            // Variant role — use Modify so LovedItems reaches Enabled: Compute
            sb.append("    \"Modify\": {\n");
            // $.LovedItems = replace existing override; LovedItems = set from template default
            boolean hasNative = HAS_NATIVE_LOVED_ITEMS.contains(modelAssetId);
            String modifyKey = hasNative ? "$.LovedItems" : "LovedItems";
            sb.append("        \"").append(modifyKey).append("\": [");
            for (int i = 0; i < lovedItems.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(lovedItems.get(i)).append("\"");
            }
            sb.append("]\n");
            sb.append("    }");
        } else {
            // No native LovedItems — create via Parameters
            sb.append("    \"Parameters\": {\n");
            sb.append("        \"LovedItems\": {\n");
            sb.append("            \"Value\": [");
            for (int i = 0; i < lovedItems.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append("\"").append(lovedItems.get(i)).append("\"");
            }
            sb.append("]\n");
            sb.append("        }");
        }

        // Timing parameters use Parameters section (no Enabled compute dependency)
        boolean hasBreedCooldown = breedCooldownMinutes > 0;
        boolean hasGrowth = growthMinutes > 0;
        if (hasBreedCooldown || hasGrowth) {
            if (!useModify) {
                // Already in Parameters block, just continue
                sb.append(",\n");
            } else {
                sb.append(",\n    \"Parameters\": {\n");
            }
            boolean first = true;
            if (hasBreedCooldown) {
                String iso = minutesToIso(breedCooldownMinutes * REAL_TO_GAME_TIME_RATIO);
                sb.append("        \"BreedCooldownTimeout\": {\n");
                sb.append("            \"Value\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
                sb.append("        }");
                first = false;
            }
            if (hasGrowth) {
                String iso = minutesToIso(growthMinutes * REAL_TO_GAME_TIME_RATIO);
                if (!first) sb.append(",\n");
                sb.append("        \"GrowthTimeout\": {\n");
                sb.append("            \"Value\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
                sb.append("        }");
            }
            sb.append("\n    }");
        } else if (!useModify) {
            // Close the Parameters block opened for LovedItems
            sb.append("\n    }");
        }

        sb.append("\n}\n");
        return sb.toString();
    }

    /**
     * Get all items that should be in LovedItems for an animal.
     * Only includes foods for enabled features (taming/breeding).
     * Returns empty if both are disabled — causes patch deletion.
     */
    private List<String> getAllLovedItems(ConfigManager.AnimalConfig config) {
        if (!config.breedingEnabled && !config.tamingEnabled) {
            return Collections.emptyList();
        }
        Set<String> all = new LinkedHashSet<>();
        if (config.tamingEnabled) {
            all.addAll(config.getEffectiveTamingFoods());
        }
        if (config.breedingEnabled) {
            all.addAll(config.getEffectiveBreedingFoods());
        }
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
