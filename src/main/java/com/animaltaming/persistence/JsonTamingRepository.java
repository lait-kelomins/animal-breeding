package com.animaltaming.persistence;

import com.animaltaming.api.model.TamedAnimal;
import com.animaltaming.persistence.codec.TamedAnimalCodec;
import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON file-based implementation of TamingRepository.
 * Uses atomic writes (temp file + rename) to prevent corruption.
 *
 * Storage structure:
 * - data/animals/{uuid}.json - Individual animal files
 * - data/owners/{uuid}.json - Owner index files
 */
public class JsonTamingRepository implements TamingRepository {

    private final Path dataFolder;
    private final Path animalsFolder;
    private final Path ownersFolder;
    private final TamedAnimalCodec codec;
    private final Gson gson;

    // Cache for loaded animals
    private final Map<UUID, TamedAnimal> cache = new ConcurrentHashMap<>();
    // Cache for owner indexes
    private final Map<UUID, Set<UUID>> ownerIndex = new ConcurrentHashMap<>();

    public JsonTamingRepository(Path pluginFolder, TamedAnimalCodec codec) {
        this.dataFolder = pluginFolder.resolve("data");
        this.animalsFolder = dataFolder.resolve("animals");
        this.ownersFolder = dataFolder.resolve("owners");
        this.codec = Objects.requireNonNull(codec, "codec required");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        initializeFolders();
    }

    private void initializeFolders() {
        try {
            Files.createDirectories(animalsFolder);
            Files.createDirectories(ownersFolder);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data folders", e);
        }
    }

    @Override
    public void save(TamedAnimal animal) {
        Objects.requireNonNull(animal, "animal required");

        Path targetFile = animalsFolder.resolve(animal.id() + ".json");
        Path tempFile = animalsFolder.resolve(animal.id() + ".json.tmp");

        try {
            // Write to temp file first
            String json = codec.encode(animal);
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            // Atomic move to target
            Files.move(tempFile, targetFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            // Update cache
            cache.put(animal.id(), animal);

            // Update owner index
            updateOwnerIndex(animal.ownerId(), animal.id(), true);

        } catch (IOException e) {
            // Cleanup temp file on failure
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to save animal " + animal.id(), e);
        }
    }

    @Override
    public Optional<TamedAnimal> load(UUID animalId) {
        Objects.requireNonNull(animalId, "animalId required");

        // Check cache first
        if (cache.containsKey(animalId)) {
            return Optional.of(cache.get(animalId));
        }

        // Load from file
        Path animalFile = animalsFolder.resolve(animalId + ".json");
        if (!Files.exists(animalFile)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(animalFile, StandardCharsets.UTF_8);
            TamedAnimal animal = codec.decode(json);
            cache.put(animalId, animal);
            return Optional.of(animal);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[TamingRepository] Failed to load animal " + animalId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(UUID animalId) {
        Objects.requireNonNull(animalId, "animalId required");

        TamedAnimal animal = cache.remove(animalId);
        Path animalFile = animalsFolder.resolve(animalId + ".json");

        try {
            boolean deleted = Files.deleteIfExists(animalFile);

            // Update owner index if we had the animal cached
            if (animal != null) {
                updateOwnerIndex(animal.ownerId(), animalId, false);
            }

            return deleted;
        } catch (IOException e) {
            System.err.println("[TamingRepository] Failed to delete animal " + animalId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public Set<UUID> findByOwner(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId required");

        // Check cache
        if (ownerIndex.containsKey(ownerId)) {
            return new HashSet<>(ownerIndex.get(ownerId));
        }

        // Load from file
        Path ownerFile = ownersFolder.resolve(ownerId + ".json");
        if (!Files.exists(ownerFile)) {
            return Set.of();
        }

        try {
            String json = Files.readString(ownerFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonArray animalsArray = obj.getAsJsonArray("animals");

            Set<UUID> animals = ConcurrentHashMap.newKeySet();
            for (JsonElement element : animalsArray) {
                try {
                    animals.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }

            ownerIndex.put(ownerId, animals);
            return new HashSet<>(animals);
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("[TamingRepository] Failed to load owner index " + ownerId + ": " + e.getMessage());
            return Set.of();
        }
    }

    @Override
    public Collection<TamedAnimal> loadAll() {
        List<TamedAnimal> animals = new ArrayList<>();

        try (var stream = Files.list(animalsFolder)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .filter(p -> !p.toString().endsWith(".tmp"))
                  .forEach(path -> {
                      String filename = path.getFileName().toString();
                      String uuidStr = filename.substring(0, filename.length() - 5);
                      try {
                          UUID animalId = UUID.fromString(uuidStr);
                          load(animalId).ifPresent(animals::add);
                      } catch (IllegalArgumentException e) {
                          System.err.println("[TamingRepository] Invalid animal file: " + filename);
                      }
                  });
        } catch (IOException e) {
            System.err.println("[TamingRepository] Failed to list animals: " + e.getMessage());
        }

        return animals;
    }

    @Override
    public boolean exists(UUID animalId) {
        if (cache.containsKey(animalId)) {
            return true;
        }
        return Files.exists(animalsFolder.resolve(animalId + ".json"));
    }

    @Override
    public int count() {
        try (var stream = Files.list(animalsFolder)) {
            return (int) stream
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.toString().endsWith(".tmp"))
                    .count();
        } catch (IOException e) {
            return cache.size();
        }
    }

    private void updateOwnerIndex(UUID ownerId, UUID animalId, boolean add) {
        Set<UUID> animals = ownerIndex.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet());

        if (add) {
            animals.add(animalId);
        } else {
            animals.remove(animalId);
        }

        saveOwnerIndex(ownerId);
    }

    private void saveOwnerIndex(UUID ownerId) {
        Set<UUID> animals = ownerIndex.get(ownerId);
        if (animals == null) {
            return;
        }

        JsonObject obj = new JsonObject();
        JsonArray animalsArray = new JsonArray();
        for (UUID animalId : animals) {
            animalsArray.add(animalId.toString());
        }
        obj.add("animals", animalsArray);

        Path targetFile = ownersFolder.resolve(ownerId + ".json");
        Path tempFile = ownersFolder.resolve(ownerId + ".json.tmp");

        try {
            Files.writeString(tempFile, gson.toJson(obj), StandardCharsets.UTF_8);
            Files.move(tempFile, targetFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            System.err.println("[TamingRepository] Failed to save owner index " + ownerId + ": " + e.getMessage());
        }
    }

    /**
     * Clear all caches.
     */
    public void clearCache() {
        cache.clear();
        ownerIndex.clear();
    }
}
