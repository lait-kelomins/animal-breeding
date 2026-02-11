package com.hytame.managers;

import com.hytame.HyTamePlugin;
import com.hytame.models.TamedAnimalData;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Manages legacy tamed_animals.json persistence.
 *
 * New taming data is persisted via ECS Tamed component. This manager only:
 * - Loads existing entries on startup (for migration/tracking)
 * - Removes dead animals from the file when they die
 * - Cleans up old backup/tmp files and the old Lait_AnimalBreeding directory
 */
public class PersistenceManager {

    private static final int CURRENT_VERSION = 1;
    private static final String SAVE_FILE_NAME = "tamed_animals.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_STREAM = new GsonBuilder().create();

    private Path saveFilePath;
    private final Object saveLock = new Object();
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);

    public PersistenceManager() {
    }

    private void log(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atInfo().log(message);
        }
    }

    private void logWarning(String message) {
        if (HyTamePlugin.isVerboseLogging()) {
            HyTamePlugin.getInstance().getLogger().atWarning().log(message);
        }
    }

    /**
     * Initialize with the plugin data directory.
     * Cleans up backup/tmp files and old Lait_AnimalBreeding directory.
     */
    public void initialize(Path dataDirectory) {
        this.saveFilePath = dataDirectory.resolve(SAVE_FILE_NAME);

        // Clean up tmp and backup files in current directory
        cleanupTempAndBackups(dataDirectory);

        // Clean up everything in old Lait_AnimalBreeding directory
        Path oldDir = dataDirectory.getParent().resolve("Lait_AnimalBreeding");
        if (Files.exists(oldDir)) {
            cleanupTempAndBackups(oldDir);
            // Also delete the main file from old dir (it's been migrated)
            try {
                Path oldFile = oldDir.resolve(SAVE_FILE_NAME);
                if (Files.deleteIfExists(oldFile)) {
                    log("Deleted legacy " + SAVE_FILE_NAME + " from Lait_AnimalBreeding");
                }
            } catch (IOException e) {
                logWarning("Failed to delete legacy file from old dir: " + e.getMessage());
            }
        }
    }

    /**
     * Delete tmp and backup files for tamed_animals in a directory.
     */
    private void cleanupTempAndBackups(Path directory) {
        // Delete temp file
        try {
            Files.deleteIfExists(directory.resolve(SAVE_FILE_NAME + ".tmp"));
        } catch (IOException e) {
            // ignore
        }

        // Delete backup files
        try {
            if (Files.exists(directory)) {
                List<Path> backups = Files.list(directory)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.startsWith("tamed_animals") && name.endsWith(".json")
                                    && name.contains("_backup_");
                        })
                        .toList();
                for (Path backup : backups) {
                    try {
                        Files.delete(backup);
                    } catch (IOException e) {
                        logWarning("Failed to delete backup: " + backup.getFileName());
                    }
                }
            }
        } catch (IOException e) {
            logWarning("Failed to list backups: " + e.getMessage());
        }
    }

    /**
     * Load tamed animal data from disk. Filters out dead animals and rewrites the file.
     * @return List of alive tamed animal data, empty list if no file or error
     */
    public List<TamedAnimalData> loadData() {
        List<TamedAnimalData> result = new ArrayList<>();

        if (saveFilePath == null || !Files.exists(saveFilePath)) {
            return result;
        }

        int deadCount = 0;
        try {
            String json = Files.readString(saveFilePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("tamedAnimals") && root.get("tamedAnimals").isJsonArray()) {
                JsonArray animalsArray = root.getAsJsonArray("tamedAnimals");
                for (JsonElement elem : animalsArray) {
                    try {
                        TamedAnimalData data = GSON.fromJson(elem, TamedAnimalData.class);
                        if (data != null && data.getAnimalUuid() != null && data.getOwnerUuid() != null) {
                            if (data.isDead()) {
                                deadCount++;
                            } else {
                                result.add(data);
                            }
                        }
                    } catch (Exception e) {
                        logWarning("Failed to parse tamed animal entry: " + e.getMessage());
                    }
                }
            }

            log("Loaded " + result.size() + " tamed animals from file (" + deadCount + " dead removed)");

        } catch (Exception e) {
            logWarning("Failed to load " + SAVE_FILE_NAME + ": " + e.getMessage());
        }

        // Rewrite file without dead entries
        if (deadCount > 0) {
            HyTamePlugin.getInstance().getLogger().atInfo().log(
                    "[Taming] Removed " + deadCount + " dead animals from " + SAVE_FILE_NAME);
            writeFile(result);
        }

        return result;
    }

    /**
     * Save tamed animal data, filtering out dead entries.
     */
    public void saveData(Collection<TamedAnimalData> tamedAnimals) {
        if (saveFilePath == null) return;
        if (!saveInProgress.compareAndSet(false, true)) return;

        List<TamedAnimalData> alive = tamedAnimals.stream()
                .filter(d -> d != null && !d.isDead() && d.getAnimalUuid() != null)
                .toList();

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                writeFile(alive);
            } finally {
                saveInProgress.set(false);
            }
        }).exceptionally(e -> {
            logWarning("Async save failed: " + e.getMessage());
            saveInProgress.set(false);
            return null;
        });
    }

    /**
     * Save synchronously, filtering out dead entries.
     */
    public void saveDataSync(Collection<TamedAnimalData> tamedAnimals) {
        if (saveFilePath == null) return;

        List<TamedAnimalData> alive = tamedAnimals.stream()
                .filter(d -> d != null && !d.isDead() && d.getAnimalUuid() != null)
                .toList();

        writeFile(alive);
    }

    /**
     * Write alive animals to file. Deletes file if list is empty.
     */
    private void writeFile(List<TamedAnimalData> animals) {
        synchronized (saveLock) {
            try {
                if (animals.isEmpty()) {
                    Files.deleteIfExists(saveFilePath);
                    log("Deleted " + SAVE_FILE_NAME + " (no animals remaining)");
                    return;
                }

                Path tempFile = saveFilePath.resolveSibling(SAVE_FILE_NAME + ".tmp");
                Files.createDirectories(saveFilePath.getParent());

                try (BufferedWriter fileWriter = Files.newBufferedWriter(tempFile);
                     JsonWriter writer = new JsonWriter(fileWriter)) {

                    writer.setIndent("  ");
                    writer.beginObject();
                    writer.name("version").value(CURRENT_VERSION);
                    writer.name("lastSaved").value(System.currentTimeMillis());

                    writer.name("tamedAnimals");
                    writer.beginArray();
                    for (TamedAnimalData data : animals) {
                        GSON_STREAM.toJson(data, TamedAnimalData.class, writer);
                    }
                    writer.endArray();

                    writer.endObject();
                }

                Files.move(tempFile, saveFilePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);

                log("Saved " + animals.size() + " tamed animals to " + SAVE_FILE_NAME);

            } catch (IOException e) {
                logWarning("Failed to save " + SAVE_FILE_NAME + ": " + e.getMessage());
            }
        }
    }

    public void markDirty() { }
    public boolean isDirty() { return false; }
    public boolean isSaveInProgress() { return saveInProgress.get(); }
    public long getLastSaveTime() { return 0; }
    public void startAutoSave(ScheduledExecutorService scheduler,
                              Supplier<Collection<TamedAnimalData>> dataSupplier,
                              long intervalMinutes) { }
    public void stopAutoSave() { }

    public void forceSave(Collection<TamedAnimalData> tamedAnimals) {
        saveData(tamedAnimals);
    }

    public void forceSaveSync(Collection<TamedAnimalData> tamedAnimals) {
        saveDataSync(tamedAnimals);
    }

    public Path getSaveFilePath() { return saveFilePath; }
}
