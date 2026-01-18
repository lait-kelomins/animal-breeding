package com.laits.breeding.managers;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages breeding state and logic for all tracked animals.
 */
public class BreedingManager {

    private final ConfigManager config;
    private final Map<UUID, BreedingData> breedingDataMap = new ConcurrentHashMap<>();

    // Callbacks for game integration
    private Consumer<BirthEvent> onBirthCallback;
    private Consumer<String> debugLogger;

    public BreedingManager(ConfigManager config) {
        this.config = config;
    }

    /**
     * Get or create breeding data for an animal.
     * @param animalId The animal's UUID
     * @param animalType The type of animal
     * @return The breeding data for this animal
     */
    public BreedingData getOrCreateData(UUID animalId, AnimalType animalType) {
        return breedingDataMap.computeIfAbsent(animalId, id -> new BreedingData(id, animalType));
    }

    /**
     * Get breeding data for an animal if it exists.
     * @param animalId The animal's UUID
     * @return The breeding data, or null if not tracked
     */
    public BreedingData getData(UUID animalId) {
        return breedingDataMap.get(animalId);
    }

    /**
     * Remove breeding data when an animal is removed from the world.
     * @param animalId The animal's UUID
     */
    public void removeData(UUID animalId) {
        breedingDataMap.remove(animalId);
    }

    /**
     * Clear all breeding data (used on plugin shutdown).
     */
    public void clearAll() {
        breedingDataMap.clear();
    }

    /**
     * Register a spawned baby animal for growth tracking.
     * @param babyId The baby's UUID
     * @param animalType The type of animal
     * @param entityRef The entity reference for later model swapping
     * @return The created BreedingData
     */
    public BreedingData registerBaby(UUID babyId, AnimalType animalType, Object entityRef) {
        BreedingData babyData = BreedingData.createBaby(babyId, animalType);
        babyData.setEntityRef(entityRef);
        breedingDataMap.put(babyId, babyData);
        debug("Registered baby " + babyId + " (" + animalType + ") for growth tracking");
        return babyData;
    }

    /**
     * Check if an animal can breed.
     * @param animalId The animal's UUID
     * @param animalType The type of animal
     * @return true if the animal can breed
     */
    public boolean canBreed(UUID animalId, AnimalType animalType) {
        BreedingData data = getOrCreateData(animalId, animalType);
        long cooldown = config.getBreedingCooldown(animalType);
        return data.canBreed(cooldown);
    }

    /**
     * Try to put an animal in "love mode" after feeding.
     * @param animalId The animal's UUID
     * @param animalType The type of animal
     * @param foodItemId The item used to feed
     * @return Result of the feeding attempt
     */
    public FeedResult tryFeed(UUID animalId, AnimalType animalType, String foodItemId) {
        return tryFeed(animalId, animalType, foodItemId, null);
    }

    /**
     * Try to put an animal in "love mode" after feeding, storing the entity ref for particle effects.
     * @param animalId The animal's UUID
     * @param animalType The type of animal
     * @param foodItemId The item used to feed
     * @param entityRef The entity reference (for heart particles while in love)
     * @return Result of the feeding attempt
     */
    public FeedResult tryFeed(UUID animalId, AnimalType animalType, String foodItemId, Object entityRef) {
        // Check if this animal type is enabled for breeding
        if (!config.isAnimalEnabled(animalType)) {
            return FeedResult.DISABLED;
        }

        // Check if correct food (using config for runtime customization)
        if (!config.isBreedingFood(animalType, foodItemId)) {
            return FeedResult.WRONG_FOOD;
        }

        BreedingData data = getOrCreateData(animalId, animalType);

        // Store entity ref for heart particles (always update to latest ref)
        if (entityRef != null) {
            data.setEntityRef(entityRef);
            debug("Stored entityRef for " + animalId + " (" + animalType + "): " + entityRef);
        } else {
            debug("WARNING: entityRef is null for " + animalId + " (" + animalType + ")");
        }

        // Check if adult
        if (!data.getGrowthStage().canBreed()) {
            return FeedResult.NOT_ADULT;
        }

        // Check cooldown
        long cooldown = config.getBreedingCooldown(animalType);
        if (!data.canBreed(cooldown)) {
            return FeedResult.ON_COOLDOWN;
        }

        // Already in love
        if (data.isInLove()) {
            return FeedResult.ALREADY_IN_LOVE;
        }

        // Put in love mode
        data.setInLove(true);
        debug("Animal " + animalId + " (" + animalType + ") is now in love!");
        return FeedResult.SUCCESS;
    }

    /**
     * Try to start breeding between two animals.
     * @param animal1Id First animal's UUID
     * @param animal2Id Second animal's UUID
     * @param animalType The type of both animals (must match)
     * @return true if breeding started successfully
     */
    public boolean tryBreed(UUID animal1Id, UUID animal2Id, AnimalType animalType) {
        BreedingData data1 = getData(animal1Id);
        BreedingData data2 = getData(animal2Id);

        if (data1 == null || data2 == null) {
            debug("Cannot breed: one or both animals not tracked");
            return false;
        }

        // Both must be in love
        if (!data1.isInLove() || !data2.isInLove()) {
            debug("Cannot breed: both animals must be in love");
            return false;
        }

        // Neither can be pregnant
        if (data1.isPregnant() || data2.isPregnant()) {
            debug("Cannot breed: one or both animals already pregnant");
            return false;
        }

        // Start pregnancy (animal1 becomes pregnant)
        data1.setPregnant(true);
        data1.resetLove();
        data2.resetLove();

        debug("Breeding started! " + animal1Id + " is now pregnant with " + animalType);
        return true;
    }

    /**
     * Force-breed two animals (admin command, bypasses checks).
     * @param animal1Id First animal's UUID
     * @param animal2Id Second animal's UUID
     * @param animalType The type of both animals
     * @return true if breeding was initiated
     */
    public boolean forceBreed(UUID animal1Id, UUID animal2Id, AnimalType animalType) {
        BreedingData data1 = getOrCreateData(animal1Id, animalType);
        BreedingData data2 = getOrCreateData(animal2Id, animalType);

        // Force pregnancy on first animal
        data1.setPregnant(true);
        data1.resetLove();
        data2.resetLove();

        debug("Force breeding: " + animal1Id + " is now pregnant");
        return true;
    }

    /**
     * Check all pregnant animals and handle births.
     * Called periodically by the tick system.
     */
    public void tickPregnancies() {
        for (BreedingData data : breedingDataMap.values()) {
            if (data.isPregnant()) {
                long gestationTime = config.getGestationPeriod(data.getAnimalType());
                if (data.isReadyToGiveBirth(gestationTime)) {
                    handleBirth(data);
                }
            }
        }
    }

    /**
     * Handle the birth of a baby animal.
     */
    private void handleBirth(BreedingData motherData) {
        UUID motherId = motherData.getAnimalId();
        AnimalType animalType = motherData.getAnimalType();

        // Create baby data
        UUID babyId = UUID.randomUUID();
        BreedingData babyData = BreedingData.createBaby(babyId, animalType);
        breedingDataMap.put(babyId, babyData);

        // Complete breeding (sets cooldown, resets pregnancy)
        motherData.completeBreeding();

        debug("Birth! Mother " + motherId + " gave birth to baby " + babyId);

        // Notify callback for actual entity spawning
        if (onBirthCallback != null) {
            BirthEvent event = new BirthEvent(motherId, babyId, animalType);
            onBirthCallback.accept(event);
        }
    }

    /**
     * Set callback for when a birth occurs.
     * The game should spawn the actual baby entity.
     */
    public void setOnBirthCallback(Consumer<BirthEvent> callback) {
        this.onBirthCallback = callback;
    }

    /**
     * Set debug logger callback.
     */
    public void setDebugLogger(Consumer<String> logger) {
        this.debugLogger = logger;
    }

    private void debug(String message) {
        if (config.isDebugMode() && debugLogger != null) {
            debugLogger.accept("[Breeding] " + message);
        }
    }

    /**
     * Get the number of tracked animals.
     */
    public int getTrackedCount() {
        return breedingDataMap.size();
    }

    /**
     * Get the number of pregnant animals.
     */
    public int getPregnantCount() {
        return (int) breedingDataMap.values().stream()
                .filter(BreedingData::isPregnant)
                .count();
    }

    /**
     * Get the number of animals in love.
     */
    public int getInLoveCount() {
        return (int) breedingDataMap.values().stream()
                .filter(BreedingData::isInLove)
                .count();
    }

    /**
     * Get all tracked animal IDs.
     */
    public Iterable<UUID> getTrackedAnimalIds() {
        return breedingDataMap.keySet().stream().toList();
    }

    /**
     * Get all breeding data entries.
     */
    public Iterable<BreedingData> getAllBreedingData() {
        return breedingDataMap.values();
    }

    /**
     * Result of a feeding attempt.
     */
    public enum FeedResult {
        SUCCESS,
        WRONG_FOOD,
        NOT_ADULT,
        ON_COOLDOWN,
        ALREADY_IN_LOVE,
        DISABLED
    }

    /**
     * Event data for a birth.
     */
    public static class BirthEvent {
        private final UUID motherId;
        private final UUID babyId;
        private final AnimalType animalType;

        public BirthEvent(UUID motherId, UUID babyId, AnimalType animalType) {
            this.motherId = motherId;
            this.babyId = babyId;
            this.animalType = animalType;
        }

        public UUID getMotherId() {
            return motherId;
        }

        public UUID getBabyId() {
            return babyId;
        }

        public AnimalType getAnimalType() {
            return animalType;
        }
    }
}
