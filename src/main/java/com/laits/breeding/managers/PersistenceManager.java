package com.laits.breeding.managers;

import com.laits.breeding.models.TamedAnimalData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages persistence of tamed animal data to JSON files.
 * Handles loading on startup, saving on shutdown, and periodic auto-saves.
 *
 * Optimized for large datasets:
 * - Async saving: serialization and I/O run in background thread
 * - Streaming JSON: writes directly to file without building full JSON in memory
 * - Snapshot isolation: copies collection before async processing
 */
public class PersistenceManager {

    private static final int CURRENT_VERSION = 1;
    private static final String SAVE_FILE_NAME = "tamed_animals.json";

    // Backup limits to prevent disk space exhaustion
    private static final int MAX_BACKUPS = 10;
    private static final int DEFAULT_KEEP_COUNT = 5;

    // GSON for reading (still uses tree model for compatibility)
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // GSON for streaming writes (no pretty printing needed, we handle formatting)
    private static final Gson GSON_STREAM = new GsonBuilder().create();

    private Path saveFilePath;
    private final Object saveLock = new Object();
    private boolean dirty = false;
    private Consumer<String> logger;

    // Track if async save is in progress to prevent overlapping saves
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);

    // Auto-save
    private ScheduledFuture<?> autoSaveTask;
    private volatile long lastSaveTime;

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
     * Save tamed animal data to disk asynchronously.
     * Takes a snapshot of the collection and processes in background thread.
     * Uses streaming JSON writer for memory efficiency.
     *
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void saveData(Collection<TamedAnimalData> tamedAnimals) {
        if (saveFilePath == null) {
            logWarning("Save file path not initialized, cannot save");
            return;
        }

        // Skip if save already in progress
        if (!saveInProgress.compareAndSet(false, true)) {
            log("Save already in progress, skipping");
            return;
        }

        // Take immediate snapshot (fast - just copies references)
        final List<TamedAnimalData> snapshot = new ArrayList<>(tamedAnimals);
        final int snapshotSize = snapshot.size();

        // Mark as not dirty immediately (optimistic)
        dirty = false;

        // Run serialization and I/O in background thread
        CompletableFuture.runAsync(() -> {
            synchronized (saveLock) {
                try {
                    Path tempFile = saveFilePath.resolveSibling(SAVE_FILE_NAME + ".tmp");
                    Files.createDirectories(saveFilePath.getParent());

                    // Use streaming JSON writer for memory efficiency
                    try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile);
                         JsonWriter writer = new JsonWriter(fileWriter)) {

                        writer.setIndent("  "); // Pretty print with 2 spaces

                        writer.beginObject();

                        // Version
                        writer.name("version").value(CURRENT_VERSION);

                        // Timestamp
                        long saveTime = System.currentTimeMillis();
                        writer.name("lastSaved").value(saveTime);

                        // Metadata
                        writer.name("metadata");
                        writer.beginObject();
                        writer.name("pluginVersion").value("1.4.0");
                        writer.name("totalTamed").value(snapshotSize);
                        writer.endObject();

                        // Tamed animals array - streamed one at a time
                        writer.name("tamedAnimals");
                        writer.beginArray();
                        for (TamedAnimalData data : snapshot) {
                            if (data != null && data.getAnimalUuid() != null) {
                                // Stream each animal directly to file
                                GSON_STREAM.toJson(data, TamedAnimalData.class, writer);
                            }
                        }
                        writer.endArray();

                        writer.endObject();
                    }

                    // Atomic move to replace existing file
                    Files.move(tempFile, saveFilePath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);

                    lastSaveTime = System.currentTimeMillis();
                    log("Saved " + snapshotSize + " tamed animals to disk (async)");

                } catch (IOException e) {
                    logError("Failed to save tamed animals: " + e.getMessage());
                    // Mark dirty again for retry on next auto-save
                    dirty = true;
                } finally {
                    saveInProgress.set(false);
                }
            }
        }).exceptionally(e -> {
            logError("Async save failed: " + e.getMessage());
            dirty = true;
            saveInProgress.set(false);
            return null;
        });
    }

    /**
     * Save tamed animal data to disk synchronously.
     * Used for shutdown saves where we must wait for completion.
     * Uses streaming JSON writer for memory efficiency.
     *
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void saveDataSync(Collection<TamedAnimalData> tamedAnimals) {
        if (saveFilePath == null) {
            logWarning("Save file path not initialized, cannot save");
            return;
        }

        // Wait for any async save to complete
        while (saveInProgress.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        synchronized (saveLock) {
            try {
                // Take snapshot
                final List<TamedAnimalData> snapshot = new ArrayList<>(tamedAnimals);
                final int snapshotSize = snapshot.size();

                Path tempFile = saveFilePath.resolveSibling(SAVE_FILE_NAME + ".tmp");
                Files.createDirectories(saveFilePath.getParent());

                // Use streaming JSON writer for memory efficiency
                try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile);
                     JsonWriter writer = new JsonWriter(fileWriter)) {

                    writer.setIndent("  ");

                    writer.beginObject();
                    writer.name("version").value(CURRENT_VERSION);
                    writer.name("lastSaved").value(System.currentTimeMillis());

                    writer.name("metadata");
                    writer.beginObject();
                    writer.name("pluginVersion").value("1.4.0");
                    writer.name("totalTamed").value(snapshotSize);
                    writer.endObject();

                    writer.name("tamedAnimals");
                    writer.beginArray();
                    for (TamedAnimalData data : snapshot) {
                        if (data != null && data.getAnimalUuid() != null) {
                            GSON_STREAM.toJson(data, TamedAnimalData.class, writer);
                        }
                    }
                    writer.endArray();

                    writer.endObject();
                }

                Files.move(tempFile, saveFilePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                dirty = false;
                lastSaveTime = System.currentTimeMillis();
                log("Saved " + snapshotSize + " tamed animals to disk (sync)");

            } catch (IOException e) {
                logError("Failed to save tamed animals (sync): " + e.getMessage());
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
     * Check if a save is currently in progress.
     */
    public boolean isSaveInProgress() {
        return saveInProgress.get();
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
                if (dirty && !saveInProgress.get()) {
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
     * Uses async save by default.
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void forceSave(Collection<TamedAnimalData> tamedAnimals) {
        dirty = true;
        saveData(tamedAnimals);
    }

    /**
     * Force an immediate synchronous save.
     * Blocks until save is complete. Use for shutdown.
     * @param tamedAnimals Collection of tamed animal data to save
     */
    public void forceSaveSync(Collection<TamedAnimalData> tamedAnimals) {
        dirty = true;
        saveDataSync(tamedAnimals);
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
