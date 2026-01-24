package com.tameableanimals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.tameableanimals.TameableAnimalsPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TameableAnimalsConfig config = new TameableAnimalsConfig();

    public static void load(File configFile) {
        if (!configFile.exists()) {
            save(configFile);
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            TameableAnimalsConfig loaded = GSON.fromJson(new JsonReader(reader), TameableAnimalsConfig.class);
            if (loaded != null) config.loadTameConfig(loaded);
            save(configFile);
        } catch (IOException e) {
            TameableAnimalsPlugin.get().getLogger().atSevere().log(e.getMessage());
        }
    }

    public static void save(File configFile) {
        File folder = configFile.getParentFile();
        if (!folder.exists() && !folder.mkdirs()) {
            TameableAnimalsPlugin.get().getLogger().atSevere().log("failed to make directory");
            return;
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            TameableAnimalsPlugin.get().getLogger().atSevere().log(e.getMessage());
        }
    }

    public static TameableAnimalsConfig getConfig() { return config; }
}
