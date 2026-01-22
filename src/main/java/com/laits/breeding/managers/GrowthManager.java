package com.laits.breeding.managers;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages growth stages for baby animals.
 */
public class GrowthManager {

    private final ConfigManager config;
    private final BreedingManager breedingManager;

    // Callbacks for game integration
    private Consumer<GrowthEvent> onGrowthCallback;
    private Consumer<String> debugLogger;

    public GrowthManager(ConfigManager config, BreedingManager breedingManager) {
        this.config = config;
        this.breedingManager = breedingManager;
    }

    /**
     * Called periodically to update growth stages for all tracked animals.
     */
    public void tickGrowth() {
        // Skip if growth is disabled globally
        if (!config.isGrowthEnabled()) {
            return;
        }

        for (UUID animalId : getTrackedAnimalIds()) {
            BreedingData data = breedingManager.getData(animalId);
            if (data != null && data.getGrowthStage().hasNextStage()) {
                checkAndUpdateGrowth(data);
            }
        }
    }

    /**
     * Check if an animal should grow to the next stage.
     */
    private void checkAndUpdateGrowth(BreedingData data) {
        GrowthStage currentStage = data.getGrowthStage();
        if (!currentStage.hasNextStage()) {
            return;
        }

        long age = data.getAge();
        long timeToNextStage = getTimeToReachNextStage(data.getAnimalType(), currentStage);

        if (age >= timeToNextStage) {
            GrowthStage nextStage = currentStage.getNextStage();
            data.setGrowthStage(nextStage);

            debug("Animal " + data.getAnimalId() + " grew from " + currentStage + " to " + nextStage);

            // Notify callback for visual updates
            if (onGrowthCallback != null) {
                GrowthEvent event = new GrowthEvent(
                        data.getAnimalId(),
                        data.getAnimalType(),
                        currentStage,
                        nextStage
                );
                onGrowthCallback.accept(event);
            }
        }
    }

    /**
     * Get the total time required to reach the next growth stage for a specific animal type.
     * Uses the animal's configured growth time, not the global default.
     */
    private long getTimeToReachNextStage(AnimalType animalType, GrowthStage currentStage) {
        long totalTime = 0;
        for (GrowthStage stage : GrowthStage.values()) {
            totalTime += config.getGrowthStageDuration(animalType, stage);
            if (stage == currentStage) {
                break;
            }
        }
        return totalTime;
    }

    /**
     * Get growth progress as a percentage (0.0 to 1.0) towards adulthood.
     * @param animalId The animal's UUID
     * @return Progress percentage, or 1.0 if adult or not found
     */
    public float getGrowthProgress(UUID animalId) {
        BreedingData data = breedingManager.getData(animalId);
        if (data == null) {
            return 1.0f;
        }

        if (data.getGrowthStage() == GrowthStage.ADULT) {
            return 1.0f;
        }

        long age = data.getAge();
        // Use growth time from config based on animal type
        long totalTimeToAdult = config.getGrowthTime(data.getAnimalType());

        if (totalTimeToAdult == 0) {
            return 1.0f;
        }

        return Math.min(1.0f, (float) age / totalTimeToAdult);
    }

    /**
     * Get the current size multiplier for an animal.
     * @param animalId The animal's UUID
     * @return Size multiplier (0.5 - 1.0)
     */
    public float getSizeMultiplier(UUID animalId) {
        BreedingData data = breedingManager.getData(animalId);
        if (data == null) {
            return 1.0f;
        }
        return data.getGrowthStage().getSizeMultiplier();
    }

    /**
     * Check if an animal is fully grown.
     * @param animalId The animal's UUID
     * @return true if adult
     */
    public boolean isFullyGrown(UUID animalId) {
        BreedingData data = breedingManager.getData(animalId);
        return data == null || data.getGrowthStage() == GrowthStage.ADULT;
    }

    /**
     * Force an animal to grow to the next stage (admin command).
     * @param animalId The animal's UUID
     * @return true if growth occurred
     */
    public boolean forceGrowth(UUID animalId) {
        BreedingData data = breedingManager.getData(animalId);
        if (data == null) {
            return false;
        }

        GrowthStage currentStage = data.getGrowthStage();
        if (!currentStage.hasNextStage()) {
            return false;
        }

        GrowthStage nextStage = currentStage.getNextStage();
        data.setGrowthStage(nextStage);

        debug("Force growth: " + animalId + " grew from " + currentStage + " to " + nextStage);

        if (onGrowthCallback != null) {
            GrowthEvent event = new GrowthEvent(animalId, data.getAnimalType(), currentStage, nextStage);
            onGrowthCallback.accept(event);
        }

        return true;
    }

    /**
     * Get time remaining until an animal becomes an adult.
     * @param animalId The animal's UUID
     * @return Time remaining in milliseconds, or 0 if adult
     */
    public long getTimeToAdult(UUID animalId) {
        BreedingData data = breedingManager.getData(animalId);
        if (data == null || data.getGrowthStage() == GrowthStage.ADULT) {
            return 0;
        }

        long age = data.getAge();
        // Use growth time from config based on animal type
        long totalTimeToAdult = config.getGrowthTime(data.getAnimalType());
        return Math.max(0, totalTimeToAdult - age);
    }

    /**
     * Get all tracked animal IDs.
     */
    private Iterable<UUID> getTrackedAnimalIds() {
        return breedingManager.getTrackedAnimalIds();
    }

    /**
     * Set callback for when an animal grows.
     */
    public void setOnGrowthCallback(Consumer<GrowthEvent> callback) {
        this.onGrowthCallback = callback;
    }

    /**
     * Set debug logger callback.
     */
    public void setDebugLogger(Consumer<String> logger) {
        this.debugLogger = logger;
    }

    private void debug(String message) {
        if (config.isDebugMode() && debugLogger != null) {
            debugLogger.accept("[Growth] " + message);
        }
    }

    /**
     * Get count of animals at each growth stage.
     */
    public Map<GrowthStage, Integer> getGrowthStageCounts() {
        Map<GrowthStage, Integer> counts = new java.util.EnumMap<>(GrowthStage.class);
        for (GrowthStage stage : GrowthStage.values()) {
            counts.put(stage, 0);
        }

        for (UUID animalId : getTrackedAnimalIds()) {
            BreedingData data = breedingManager.getData(animalId);
            if (data != null) {
                GrowthStage stage = data.getGrowthStage();
                counts.put(stage, counts.get(stage) + 1);
            }
        }

        return counts;
    }

    /**
     * Event data for a growth stage change.
     */
    public static class GrowthEvent {
        private final UUID animalId;
        private final AnimalType animalType;
        private final GrowthStage previousStage;
        private final GrowthStage newStage;

        public GrowthEvent(UUID animalId, AnimalType animalType, GrowthStage previousStage, GrowthStage newStage) {
            this.animalId = animalId;
            this.animalType = animalType;
            this.previousStage = previousStage;
            this.newStage = newStage;
        }

        public UUID getAnimalId() {
            return animalId;
        }

        public AnimalType getAnimalType() {
            return animalType;
        }

        public GrowthStage getPreviousStage() {
            return previousStage;
        }

        public GrowthStage getNewStage() {
            return newStage;
        }

        /**
         * Check if this animal uses scaling instead of NPC replacement.
         * @return true if the animal should be scaled, false if it should be replaced
         */
        public boolean usesScaling() {
            return !animalType.hasBabyVariant();
        }

        /**
         * Get the target scale for the new growth stage.
         * Only relevant for animals that use scaling (no baby variant).
         * @return Scale factor (0.4 for baby, 0.7 for juvenile, 1.0 for adult)
         */
        public float getTargetScale() {
            return animalType.getScaleForStage(newStage);
        }
    }
}
