package com.laits.breeding.managers;

import com.laits.breeding.models.TamedAnimalData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages persistence of tamed animal data to JSON files.
 * Handles loading on startup, saving on shutdown, and periodic auto-saves.
 */
public class PersistenceManager {

    private static final int CURRENT_VERSION = 1;
    private static final String SAVE_FILE_NAME = "tamed_animals.json";

    // Backup limits to prevent disk space exhaustion
    private static final int MAX_BACKUPS = 10;
    private static final int DEFAULT_KEEP_COUNT = 5;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private Path saveFilePath;
    private final Object saveLock = new Object();
    private boolean dirty = false;
    private Consumer<String> logger;

    // Auto-save
    private ScheduledFuture<?> autoSaveTask;
    private long lastSaveTime;

    public PersistenceManager() {
    }

    /**
     * Set the logger for output messages.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private void logWarning(String message) {
        if (logger != null) {
            logger.accept("[WARNING] " + message);
        }
    }

    private void logError(String message) {
        if (logger != null) {
            logger.accept("[ERROR] " + message);
        }
    }

    /**
     * Initialize with the plugin data directory.
     * @param dataDirectory The plugin's data directory
     */
    public void initialize(Path dataDirectory) {
        this.saveFilePath = dataDirectory.resolve(SAVE_FILE_NAME);
        log("PersistenceManager initialized. Save file: " + saveFilePath);
    }

    /**
     * Load saved tamed animal data from disk.
     * @return List of tamed animal data, empty list if no save file or error
     */
    public List<TamedAnimalData> loadData() {
        List<TamedAnimalData> result = new ArrayList<>();

        if (saveFilePath == null) {
            logWarning("Save file path not initialized");
            return result;
        }

        if (!Files.exists(saveFilePath)) {
            log("No save file found, starting fresh");
            return result;
        }

        try {
            String json = Files.readString(saveFilePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Check version for migration
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version < CURRENT_VERSION) {
                log("Migrating save data from version " + version + " to " + CURRENT_VERSION);
                // Future: Add migration logic here
            }

            // Load tamed animals
            if (root.has("tamedAnimals") && root.get("tamedAnimals").isJsonArray()) {
                JsonArray animalsArray = root.getAsJsonArray("tamedAnimals");
                for (JsonElement elem : animalsArray) {
                    try {
                        TamedAnimalData data = GSON.fromJson(elem, TamedAnimalData.class);
                        // Validate required fields: animalUuid and ownerUuid must be present
                        if (data != null && data.getAnimalUuid() != null && data.getOwnerUuid() != null) {
                            result.add(data);
                        } else if (data != null) {
                            logWarning("Skipping tamed animal with missing UUID (animal=" +
                                data.getAnimalUuid() + ", owner=" + data.getOwnerUuid() + ")");
                        }
                    } catch (Exception e) {
                        logWarning("Failed to parse tamed animal entry: " + e.getMessage());
                    }
                }
            }

            // Safe extraction of lastSaved timestamp
            try {
                lastSaveTime = (root.has("lastSaved") && root.get("lastSaved").isJsonPrimitive())
                    ? root.get("lastSaved").getAsLong() : 0;
            } catch (Exception e) {
                lastSaveTime = 0;
            }
            log("Loaded " + result.size() + " tamed animals from save file");

        } catch (Exception e) {
            logError("Failed to load save file: " + e.getMessage());
        }

        return result;
    }

    /**
     * Save tamed animal data to disk.
     * Uses atomic write (temp file + move) to prevent corruption.
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void saveData(Collection<TamedAnimalData> tamedAnimals) {
        if (saveFilePath == null) {
            logWarning("Save file path not initialized, cannot save");
            return;
        }

        synchronized (saveLock) {
            try {
                // Build save object
                JsonObject root = new JsonObject();
                root.addProperty("version", CURRENT_VERSION);
                root.addProperty("lastSaved", System.currentTimeMillis());

                // Metadata
                JsonObject metadata = new JsonObject();
                metadata.addProperty("pluginVersion", "1.3.0");
                metadata.addProperty("totalTamed", tamedAnimals.size());
                root.add("metadata", metadata);

                // Tamed animals array
                JsonArray animalsArray = new JsonArray();
                for (TamedAnimalData data : tamedAnimals) {
                    if (data != null && data.getAnimalUuid() != null) {
                        JsonElement elem = GSON.toJsonTree(data);
                        animalsArray.add(elem);
                    }
                }
                root.add("tamedAnimals", animalsArray);

                // Write to temp file first
                Path tempFile = saveFilePath.resolveSibling(SAVE_FILE_NAME + ".tmp");
                Files.createDirectories(saveFilePath.getParent());
                Files.writeString(tempFile, GSON.toJson(root));

                // Atomic move to replace existing file
                Files.move(tempFile, saveFilePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                dirty = false;
                lastSaveTime = System.currentTimeMillis();
                log("Saved " + tamedAnimals.size() + " tamed animals to disk");

            } catch (IOException e) {
                logError("Failed to save tamed animals: " + e.getMessage());
                // Keep dirty flag set for retry on next auto-save
            }
        }
    }

    /**
     * Mark data as dirty (needs saving).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Check if data needs saving.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Get the time of the last successful save.
     */
    public long getLastSaveTime() {
        return lastSaveTime;
    }

    /**
     * Start periodic auto-save.
     * @param scheduler The executor service for scheduling
     * @param dataSupplier Supplier that returns the current tamed animal data
     * @param intervalMinutes Auto-save interval in minutes
     */
    public void startAutoSave(ScheduledExecutorService scheduler,
                              Supplier<Collection<TamedAnimalData>> dataSupplier,
                              long intervalMinutes) {
        if (autoSaveTask != null) {
            autoSaveTask.cancel(false);
        }

        autoSaveTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (dirty) {
                    log("Auto-saving tamed animals...");
                    saveData(dataSupplier.get());
                }
            } catch (Exception e) {
                logError("Auto-save failed: " + e.getMessage());
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        log("Auto-save started with interval of " + intervalMinutes + " minutes");
    }

    /**
     * Stop the auto-save task.
     */
    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel(false);
            autoSaveTask = null;
            log("Auto-save stopped");
        }
    }

    /**
     * Force an immediate save regardless of dirty state.
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void forceSave(Collection<TamedAnimalData> tamedAnimals) {
        dirty = true;
        saveData(tamedAnimals);
    }

    /**
     * Create a backup of the current save file.
     * Automatically cleans up old backups to stay within MAX_BACKUPS limit.
     * @return true if backup was created successfully
     */
    public boolean createBackup() {
        if (saveFilePath == null || !Files.exists(saveFilePath)) {
            return false;
        }

        try {
            // Clean up old backups first to enforce limit
            cleanupOldBackups(MAX_BACKUPS - 1); // -1 because we're about to create a new one

            String timestamp = String.valueOf(System.currentTimeMillis());
            Path backupPath = saveFilePath.resolveSibling(
                    SAVE_FILE_NAME.replace(".json", "_backup_" + timestamp + ".json"));
            Files.copy(saveFilePath, backupPath);
            log("Created backup: " + backupPath);
            return true;
        } catch (IOException e) {
            logError("Failed to create backup: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the current number of backup files.
     * @return count of backup files
     */
    public int getBackupCount() {
        if (saveFilePath == null) return 0;

        try {
            Path directory = saveFilePath.getParent();
            return (int) Files.list(directory)
                    .filter(p -> p.getFileName().toString().contains("_backup_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Delete old backup files, keeping only the most recent ones.
     * @param keepCount Number of backups to keep
     */
    public void cleanupOldBackups(int keepCount) {
        if (saveFilePath == null) return;

        try {
            Path directory = saveFilePath.getParent();
            List<Path> backups = Files.list(directory)
                    .filter(p -> p.getFileName().toString().contains("_backup_"))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        // Sort by timestamp in filename (descending - newest first)
                        try {
                            long timeA = extractTimestamp(a.getFileName().toString());
                            long timeB = extractTimestamp(b.getFileName().toString());
                            return Long.compare(timeB, timeA);
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .toList();

            // Delete backups beyond keepCount
            for (int i = keepCount; i < backups.size(); i++) {
                Files.delete(backups.get(i));
                log("Deleted old backup: " + backups.get(i).getFileName());
            }

        } catch (IOException e) {
            logWarning("Failed to cleanup old backups: " + e.getMessage());
        }
    }

    private long extractTimestamp(String filename) {
        // Extract timestamp from filename like "tamed_animals_backup_1234567890.json"
        int start = filename.indexOf("_backup_") + 8;
        int end = filename.lastIndexOf(".json");
        if (start > 0 && end > start) {
            return Long.parseLong(filename.substring(start, end));
        }
        return 0;
    }

    /**
     * Get the path to the save file.
     */
    public Path getSaveFilePath() {
        return saveFilePath;
    }
}
