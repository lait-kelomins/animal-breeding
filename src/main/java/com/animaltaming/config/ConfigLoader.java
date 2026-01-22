package com.animaltaming.config;

import com.animaltaming.api.model.DietType;
import com.animaltaming.api.model.TamingConfig;
import com.google.gson.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and validates species taming configurations from JSON files.
 * Throws on invalid configs - no silent defaults.
 */
public class ConfigLoader {

    private final Gson gson = new Gson();

    /**
     * Load configurations from classpath resources.
     *
     * @param resourcePath the resource directory (e.g., "tameable")
     * @return list of loaded configs
     */
    public List<TamingConfig> loadFromResources(String resourcePath) {
        List<TamingConfig> configs = new ArrayList<>();

        try {
            URL resourceUrl = getClass().getClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                System.err.println("[ConfigLoader] Resource path not found: " + resourcePath);
                return configs;
            }

            // Handle JAR vs filesystem resources differently
            if (resourceUrl.getProtocol().equals("file")) {
                Path resourceDir = Paths.get(resourceUrl.toURI());
                loadFromDirectory(resourceDir, configs);
            } else {
                // For JAR resources, we need to enumerate known files
                loadKnownResources(resourcePath, configs);
            }
        } catch (java.net.URISyntaxException | RuntimeException e) {
            System.err.println("[ConfigLoader] Failed to load resources: " + e.getMessage());
        }

        return configs;
    }

    /**
     * Load configurations from a filesystem directory.
     *
     * @param directory the directory to scan
     * @return list of loaded configs
     */
    public List<TamingConfig> loadFromDirectory(Path directory) {
        List<TamingConfig> configs = new ArrayList<>();
        loadFromDirectory(directory, configs);
        return configs;
    }

    private void loadFromDirectory(Path directory, List<TamingConfig> configs) {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        try (var stream = Files.list(directory)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(path -> {
                      try {
                          TamingConfig config = loadFromFile(path);
                          configs.add(config);
                      } catch (RuntimeException e) {
                          throw new RuntimeException("Failed to load config " + path + ": " + e.getMessage(), e);
                      }
                  });
        } catch (IOException e) {
            throw new RuntimeException("Failed to list config directory: " + directory, e);
        }
    }

    private void loadKnownResources(String resourcePath, List<TamingConfig> configs) {
        // Known species files - in a real implementation, this could be a manifest
        String[] knownSpecies = {"wolf", "horse", "cat", "bear", "trork"};

        for (String species : knownSpecies) {
            String fullPath = resourcePath + "/" + species + ".json";
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(fullPath)) {
                if (is != null) {
                    TamingConfig config = loadFromStream(is, species + ".json");
                    configs.add(config);
                }
            } catch (IOException e) {
                System.err.println("[ConfigLoader] Failed to load resource " + fullPath + ": " + e.getMessage());
            }
        }
    }

    /**
     * Load a single config from a file.
     *
     * @param path the file path
     * @return the loaded config
     * @throws RuntimeException if loading or validation fails
     */
    public TamingConfig loadFromFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return parseAndValidate(json, path.getFileName().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file: " + path, e);
        }
    }

    /**
     * Load a single config from an input stream.
     *
     * @param inputStream the input stream
     * @param sourceName name for error messages
     * @return the loaded config
     */
    public TamingConfig loadFromStream(InputStream inputStream, String sourceName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return parseAndValidate(sb.toString(), sourceName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config from stream: " + sourceName, e);
        }
    }

    /**
     * Parse and validate a config JSON string.
     *
     * @param json the JSON string
     * @param sourceName name for error messages
     * @return the validated config
     * @throws RuntimeException if parsing or validation fails
     */
    public TamingConfig parseAndValidate(String json, String sourceName) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // Extract and validate each field
            String speciesId = getRequiredString(obj, "speciesId", sourceName);
            DietType dietType = getRequiredEnum(obj, "dietType", DietType.class, sourceName);
            List<String> preferredFoods = getRequiredStringList(obj, "preferredFoods", sourceName);
            boolean canBeMounted = getRequiredBoolean(obj, "canBeMounted", sourceName);
            double calmingDistance = getRequiredDouble(obj, "calmingDistance", sourceName);
            int calmingTimeTicks = getRequiredInt(obj, "calmingTimeTicks", sourceName);
            int calmDurationTicks = getRequiredInt(obj, "calmDurationTicks", sourceName);
            int trustPerFeed = getRequiredInt(obj, "trustPerFeed", sourceName);
            int trustPerMountSecond = getRequiredInt(obj, "trustPerMountSecond", sourceName);
            int requiredTrustLevel = getRequiredInt(obj, "requiredTrustLevel", sourceName);
            double maxFollowDistance = getRequiredDouble(obj, "maxFollowDistance", sourceName);

            // TamingConfig constructor validates the data
            return new TamingConfig(
                    speciesId,
                    dietType,
                    preferredFoods,
                    canBeMounted,
                    calmingDistance,
                    calmingTimeTicks,
                    calmDurationTicks,
                    trustPerFeed,
                    trustPerMountSecond,
                    requiredTrustLevel,
                    maxFollowDistance
            );
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Invalid JSON in " + sourceName + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Validation failed for " + sourceName + ": " + e.getMessage(), e);
        }
    }

    private String getRequiredString(JsonObject obj, String field, String source) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + source);
        }
        return obj.get(field).getAsString();
    }

    private int getRequiredInt(JsonObject obj, String field, String source) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + source);
        }
        return obj.get(field).getAsInt();
    }

    private double getRequiredDouble(JsonObject obj, String field, String source) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + source);
        }
        return obj.get(field).getAsDouble();
    }

    private boolean getRequiredBoolean(JsonObject obj, String field, String source) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + source);
        }
        return obj.get(field).getAsBoolean();
    }

    private <T extends Enum<T>> T getRequiredEnum(JsonObject obj, String field, Class<T> enumClass, String source) {
        String value = getRequiredString(obj, field, source);
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for field '" + field + "' in " + source);
        }
    }

    private List<String> getRequiredStringList(JsonObject obj, String field, String source) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in " + source);
        }

        JsonArray array = obj.getAsJsonArray(field);
        List<String> list = new ArrayList<>();
        for (JsonElement element : array) {
            list.add(element.getAsString());
        }
        return list;
    }
}
