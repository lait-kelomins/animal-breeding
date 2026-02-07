package com.laits.breeding.patches;

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
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
 * <world>/Mods/HyTameConfig/
 * ├── manifest.json
 * └── Server/Patch/
 *     ├── NPC_Cow.json
 *     ├── NPC_Wolf_Black.json
 *     └── ... (one per animal with configured foods)
 */
public class PatchSyncService {

    // Hytale in-game time runs ~36x faster than real time
    // Empirically verified: 0.1 real min → 6 real sec at ratio 36
    private static final double REAL_TO_GAME_TIME_RATIO = 36.0;

    private static final String ASSET_PACK_NAME = "HyTameConfig";
    private static final String MANIFEST_TEMPLATE = """
            {
                "id": "hytame-config",
                "name": "HyTame Dynamic Config",
                "description": "Auto-generated asset patches from HyTame config",
                "version": "1.0.0",
                "authors": ["HyTame Plugin"]
            }
            """;

    private Path assetPackRoot;
    private Path patchFolder;
    private ConfigManager configManager;

    /**
     * Initialize the patch sync service.
     * Does NOT sync patches - call syncAllPatchesDeferred() from start() after server is ready.
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
     * Create the asset pack structure if it doesn't exist.
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
     * Sync all patches from current config state (internal, runs on current thread).
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

            syncCreaturePatch(type.getModelAssetId(), npcPath, configFoods);
            synced++;

            // Also sync growth time patch
            syncGrowthForAnimal(type);
        }

        logVerbose("Synced " + synced + " patches, skipped " + skipped + " (already in sync)");
    }

    /**
     * Check if a patch needs to be written/updated.
     * Compares config foods against existing patch file (or enum default if no patch).
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
            if (valueStart == -1) return Collections.emptyList();

            int arrayStart = content.indexOf("[", valueStart);
            int arrayEnd = content.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return Collections.emptyList();

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
        if (a.size() != b.size()) return false;
        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);
        return setA.equals(setB);
    }

    /**
     * Sync a single creature's patch file.
     *
     * @param name       The animal name (for filename)
     * @param npcPath    The full NPC role path (BaseAssetPath)
     * @param lovedItems List of item IDs for LovedItems parameter
     */
    public void syncCreaturePatch(String name, String npcPath, List<String> lovedItems) {
        if (patchFolder == null) {
            logWarning("PatchSyncService not initialized, cannot sync patch for " + name);
            return;
        }

        Path patchFile = patchFolder.resolve("NPC_" + name + ".json");
        String json = generatePatchJson(npcPath, lovedItems);

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
     * Generate patch JSON for a creature's LovedItems.
     */
    private String generatePatchJson(String npcPath, List<String> lovedItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"$Comment\": \"Auto-generated by HyTame - DO NOT EDIT\",\n");
        sb.append("    \"_BaseAssetPath\": \"Server/").append(npcPath).append(".json\",\n");
        sb.append("    \"Modify\": {\n");
        sb.append("        \"LovedItems\": [");

        for (int i = 0; i < lovedItems.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(lovedItems.get(i)).append("\"");
        }

        sb.append("]\n");
        sb.append("    }\n");
        sb.append("}\n");

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
            syncCreaturePatch(type.getModelAssetId(), npcPath, lovedItems);
        }
    }

    /**
     * Sync growth time patch for a specific animal.
     * Called after growth time config changes.
     */
    public void syncGrowthForAnimal(AnimalType type) {
        // Baby variants have their own NPC role file - resolve it directly
        // Scaled babies run on the adult role itself
        String targetPath;
        if (type.hasBabyVariant()) {
            targetPath = EcsReflectionUtil.resolveNpcRolePath(type.getBabyNpcRoleId());
        } else {
            targetPath = type.getNpcRolePath();
        }
        if (targetPath == null) {
            logVerbose("No NPC path for " + type.name() + ", skipping growth patch sync");
            return;
        }

        ConfigManager.AnimalConfig config = configManager.getAnimalConfig(type);
        double growthMinutes = config.growthTimeMinutes;

        // Write growth patch
        Path patchFile = patchFolder.resolve("Growth_" + type.getModelAssetId() + ".json");
        String json = generateGrowthPatchJson(targetPath, growthMinutes);

        try {
            Files.writeString(patchFile, json);
            logVerbose("Synced growth patch: " + patchFile.getFileName() + " (" + growthMinutes + " min)");
        } catch (IOException e) {
            logWarning("Failed to write growth patch " + patchFile + ": " + e.getMessage());
        }
    }

    private String generateGrowthPatchJson(String npcPath, double realMinutes) {
        double gameMinutes = realMinutes * REAL_TO_GAME_TIME_RATIO;
        String iso = minutesToIso(gameMinutes);
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"$Comment\": \"Auto-generated by HyTame - DO NOT EDIT\",\n");
        sb.append("    \"_BaseAssetPath\": \"Server/").append(npcPath).append(".json\",\n");
        sb.append("    \"Modify\": {\n");
        sb.append("        \"GrowthTimeout\": [\"").append(iso).append("\", \"").append(iso).append("\"]\n");
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
        if (hours > 0) sb.append(hours).append("H");
        if (mins > 0) sb.append(mins).append("M");
        if (secs > 0) sb.append(secs).append("S");
        if (hours == 0 && mins == 0 && secs == 0) sb.append("0S");
        return sb.toString();
    }

    /**
     * Resolve the target NPC role path for patching.
     * Tries reflection first, falls back to config npcRolePath.
     */
    private String resolveTargetPath(String roleId, String configRolePath) {
        // Try reflection first (automatic)
        String resolved = EcsReflectionUtil.resolveNpcRolePath(roleId);
        if (resolved != null) return resolved;
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
            logVerbose("Cannot resolve NPC path for custom animal " + custom.getDisplayName() + ", skipping food patch");
            return;
        }

        List<String> foods = custom.getBreedingFoods();
        if (foods.isEmpty()) {
            deletePatch(custom.getModelAssetId());
        } else {
            syncCreaturePatch(custom.getModelAssetId(), targetPath, foods);
        }
    }

    /**
     * Sync growth time patch for a custom animal.
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
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[PatchSync] " + message);
        }
    }

    private void logWarning(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atWarning().log("[PatchSync] " + message);
        }
    }
}
