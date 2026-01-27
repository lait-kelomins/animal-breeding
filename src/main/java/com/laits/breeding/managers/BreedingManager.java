package com.laits.breeding.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages breeding state and logic for all tracked animals.
 */
public class BreedingManager {

    private final ConfigManager config;
    private final Map<UUID, BreedingData> breedingDataMap = new ConcurrentHashMap<>();

    // Custom animal love mode tracking (separate from enum-based animals)
    private final Map<UUID, CustomAnimalLoveData> customAnimalsInLove = new ConcurrentHashMap<>();

    // Callbacks for game integration
    private Consumer<BirthEvent> onBirthCallback;
    private Consumer<CustomBirthEvent> onCustomBirthCallback;
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
     * Find a baby animal by comparing entity refs directly.
     * This is a fallback when UUID-based lookup fails due to ref instability.
     * The UUID can change between baby spawn and naming because the Ref object
     * (store address or index) may differ at each point in time.
     *
     * @param ref The entity reference to find
     * @return The BreedingData for the baby, or null if not found
     */
    public BreedingData findBabyByRef(Ref<EntityStore> ref) {
        if (ref == null) return null;

        for (BreedingData data : breedingDataMap.values()) {
            if (data.getGrowthStage() != GrowthStage.ADULT) {
                Ref<EntityStore> storedRef = data.getEntityRef();
                if (storedRef != null && refsMatch(ref, storedRef)) {
                    debug("Found baby by ref match: " + data.getAnimalId());
                    return data;
                }
            }
        }
        return null;
    }

    /**
     * Check if two entity refs match by comparing their indices.
     * Indices are more stable than full ref comparison since store addresses can change.
     */
    @SuppressWarnings("unchecked")
    private boolean refsMatch(Ref<EntityStore> ref1, Ref<EntityStore> ref2) {
        if (!(ref2 instanceof Ref)) return false;
        try {
            Ref<EntityStore> typedRef2 = (Ref<EntityStore>) ref2;
            Integer index1 = ref1.getIndex();
            Integer index2 = typedRef2.getIndex();
            return index1 != null && index1.equals(index2);
        } catch (Exception e) {
            return false;
        }
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
        customAnimalsInLove.clear();
    }

    /**
     * Get a debug string listing all registered babies (non-adult growth stages).
     * @return Debug string with baby UUIDs and growth stages
     */
    public String getRegisteredBabiesDebug() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered babies: ");
        int count = 0;
        for (Map.Entry<UUID, BreedingData> entry : breedingDataMap.entrySet()) {
            if (entry.getValue().getGrowthStage() != GrowthStage.ADULT) {
                if (count > 0) sb.append(", ");
                sb.append(entry.getKey().toString().substring(0, 8)).append("...(")
                  .append(entry.getValue().getGrowthStage()).append(")");
                count++;
            }
        }
        if (count == 0) sb.append("none");
        return sb.toString();
    }

    /**
     * Clean up stale entries where entity refs are no longer valid.
     * This is a safety net for missed EntityRemoveEvents.
     * @return Number of entries removed
     */
    public int cleanupStaleEntries() {
        int removed = 0;

        // Clean up breedingDataMap
        java.util.Iterator<Map.Entry<UUID, BreedingData>> it = breedingDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BreedingData> entry = it.next();
            BreedingData data = entry.getValue();
            Ref<EntityStore> entityRef = data.getEntityRef();
            if (entityRef != null) {
                try {
                    // Use cached Method if available, fall back to per-call if not
                    Store<EntityStore> store = entityRef.getStore();

                    if (store == null) {
                        it.remove();
                        removed++;
                        debug("Removed stale breeding entry: " + entry.getKey());
                    }
                } catch (Exception e) {
                    // Entity ref is invalid - remove the entry
                    it.remove();
                    removed++;
                    debug("Removed stale breeding entry (exception): " + entry.getKey());
                }
            }
        }

        // Clean up customAnimalsInLove
        removed += cleanupStaleCustomAnimals();

        return removed;
    }

    /**
     * Clean up stale custom animal entries where entity refs are no longer valid.
     * @return Number of entries removed
     */
    private int cleanupStaleCustomAnimals() {
        int removed = 0;
        java.util.Iterator<Map.Entry<UUID, CustomAnimalLoveData>> it = customAnimalsInLove.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CustomAnimalLoveData> entry = it.next();
            CustomAnimalLoveData data = entry.getValue();
            Ref<EntityStore> entityRef = data.getEntityRef();
            if (entityRef != null) {
                try {
                    // Use cached Method if available, fall back to per-call if not
                    Store<EntityStore> store = entityRef.getStore();

                    if (store == null) {
                        it.remove();
                        removed++;
                        debug("Removed stale custom animal entry: " + entry.getKey());
                    }
                } catch (Exception e) {
                    it.remove();
                    removed++;
                    debug("Removed stale custom animal entry (exception): " + entry.getKey());
                }
            }
        }
        return removed;
    }

    /**
     * Register a spawned baby animal for growth tracking.
     * @param babyId The baby's UUID
     * @param animalType The type of animal
     * @param entityRef The entity reference for later model swapping
     * @return The created BreedingData
     */
    public BreedingData registerBaby(UUID babyId, AnimalType animalType, Ref<EntityStore> entityRef) {
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
    public FeedResult tryFeed(UUID animalId, AnimalType animalType, String foodItemId, Ref<EntityStore> entityRef) {
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

    // ==================== CUSTOM ANIMAL BREEDING ====================

    /**
     * Try to feed a custom animal (put it in love mode).
     * @param animalId The animal's UUID
     * @param modelAssetId The custom animal's model asset ID
     * @param entityRef The entity reference (for heart particles)
     * @return Result of the feeding attempt
     */
    public FeedResult tryFeedCustomAnimal(UUID animalId, String modelAssetId, Ref<EntityStore> entityRef) {
        // Check if already in love
        CustomAnimalLoveData existing = customAnimalsInLove.get(animalId);
        if (existing != null && existing.isInLove()) {
            return FeedResult.ALREADY_IN_LOVE;
        }

        // Check cooldown
        if (existing != null && !existing.canBreed(config.getCustomAnimalBreedingCooldown(modelAssetId))) {
            return FeedResult.ON_COOLDOWN;
        }

        // Put in love mode
        CustomAnimalLoveData loveData = new CustomAnimalLoveData(animalId, modelAssetId, entityRef);
        loveData.setInLove(true);
        customAnimalsInLove.put(animalId, loveData);

        debug("Custom animal " + animalId + " (" + modelAssetId + ") is now in love!");
        return FeedResult.SUCCESS;
    }

    /**
     * Check if a custom animal is in love mode.
     */
    public boolean isCustomAnimalInLove(UUID animalId) {
        CustomAnimalLoveData data = customAnimalsInLove.get(animalId);
        return data != null && data.isInLove();
    }

    /**
     * Get custom animal love data.
     */
    public CustomAnimalLoveData getCustomAnimalLoveData(UUID animalId) {
        return customAnimalsInLove.get(animalId);
    }

    /**
     * Get all custom animals currently in love mode (for heart particle spawning).
     */
    public Iterable<CustomAnimalLoveData> getCustomAnimalsInLove() {
        return customAnimalsInLove.values().stream()
            .filter(CustomAnimalLoveData::isInLove)
            .toList();
    }

    /**
     * Try to breed two custom animals of the same type.
     */
    public boolean tryBreedCustomAnimals(UUID animal1Id, UUID animal2Id, String modelAssetId) {
        CustomAnimalLoveData data1 = customAnimalsInLove.get(animal1Id);
        CustomAnimalLoveData data2 = customAnimalsInLove.get(animal2Id);

        if (data1 == null || data2 == null) {
            debug("Cannot breed custom animals: one or both not tracked");
            return false;
        }

        // Both must be in love
        if (!data1.isInLove() || !data2.isInLove()) {
            debug("Cannot breed custom animals: both must be in love");
            return false;
        }

        // Must be same type
        if (!data1.getModelAssetId().equals(data2.getModelAssetId())) {
            debug("Cannot breed custom animals: different types");
            return false;
        }

        // Complete breeding - reset love mode and set cooldown
        data1.completeBreeding();
        data2.completeBreeding();

        debug("Custom animal breeding complete! " + modelAssetId);

        // Fire callback for baby spawning
        if (onCustomBirthCallback != null) {
            UUID babyId = UUID.randomUUID();
            CustomBirthEvent event = new CustomBirthEvent(animal1Id, animal2Id, babyId, modelAssetId, data1.getEntityRef());
            onCustomBirthCallback.accept(event);
        }

        return true;
    }

    /**
     * Set callback for custom animal births.
     */
    public void setOnCustomBirthCallback(Consumer<CustomBirthEvent> callback) {
        this.onCustomBirthCallback = callback;
    }

    /**
     * Clean up expired love modes for custom animals.
     * Love mode expires after 30 seconds by default.
     */
    public void tickCustomAnimalLove() {
        long now = System.currentTimeMillis();
        long loveDuration = 30_000; // 30 seconds

        for (CustomAnimalLoveData data : customAnimalsInLove.values()) {
            if (data.isInLove() && (now - data.getLoveStartTime()) > loveDuration) {
                data.setInLove(false);
                debug("Custom animal " + data.getAnimalId() + " love mode expired");
            }
        }
    }

    /**
     * Data class for tracking custom animal love mode.
     */
    public static class CustomAnimalLoveData {
        private final UUID animalId;
        private final String modelAssetId;
        private Ref<EntityStore> entityRef;
        private boolean inLove;
        private long loveStartTime;
        private long lastBreedTime;

        public CustomAnimalLoveData(UUID animalId, String modelAssetId, Ref<EntityStore> entityRef) {
            this.animalId = animalId;
            this.modelAssetId = modelAssetId;
            this.entityRef = entityRef;
            this.inLove = false;
            this.loveStartTime = 0;
            this.lastBreedTime = 0;
        }

        public UUID getAnimalId() { return animalId; }
        public String getModelAssetId() { return modelAssetId; }
        public Ref<EntityStore> getEntityRef() { return entityRef; }
        public void setEntityRef(Ref<EntityStore> ref) { this.entityRef = ref; }

        public boolean isInLove() { return inLove; }
        public long getLoveStartTime() { return loveStartTime; }

        public void setInLove(boolean inLove) {
            this.inLove = inLove;
            if (inLove) {
                this.loveStartTime = System.currentTimeMillis();
            }
        }

        public boolean canBreed(long cooldownMs) {
            return (System.currentTimeMillis() - lastBreedTime) >= cooldownMs;
        }

        public void completeBreeding() {
            this.inLove = false;
            this.lastBreedTime = System.currentTimeMillis();
        }
    }

    /**
     * Event data for a custom animal birth.
     */
    public static class CustomBirthEvent {
        private final UUID parent1Id;
        private final UUID parent2Id;
        private final UUID babyId;
        private final String modelAssetId;
        private final Ref<EntityStore> parentEntityRef;

        public CustomBirthEvent(UUID parent1Id, UUID parent2Id, UUID babyId, String modelAssetId, Ref<EntityStore> parentEntityRef) {
            this.parent1Id = parent1Id;
            this.parent2Id = parent2Id;
            this.babyId = babyId;
            this.modelAssetId = modelAssetId;
            this.parentEntityRef = parentEntityRef;
        }

        public UUID getParent1Id() { return parent1Id; }
        public UUID getParent2Id() { return parent2Id; }
        public UUID getBabyId() { return babyId; }
        public String getModelAssetId() { return modelAssetId; }
        public Ref<EntityStore> getParentEntityRef() { return parentEntityRef; }
    }

    // ==================== BABY DETECTION FALLBACK ====================

    /**
     * Get all UUIDs of currently tracked babies (non-adult growth stage).
     * Used by the fallback detection system to identify untracked babies.
     */
    public Set<UUID> getTrackedBabyUuids() {
        Set<UUID> babyUuids = new java.util.HashSet<>();
        for (Map.Entry<UUID, BreedingData> entry : breedingDataMap.entrySet()) {
            if (entry.getValue().getGrowthStage() != GrowthStage.ADULT) {
                babyUuids.add(entry.getKey());
            }
        }
        return babyUuids;
    }

    /**
     * Check if a baby with the given ref is already tracked.
     * Uses both UUID matching and ref index comparison.
     *
     * @param ref The entity reference to check
     * @param refUuid The UUID generated from the ref (for direct lookup)
     * @return true if this baby is already tracked
     */
    public boolean isBabyTracked(Object ref, UUID refUuid) {
        // Check by UUID first (fast path)
        BreedingData data = breedingDataMap.get(refUuid);
        if (data != null && data.getGrowthStage() != GrowthStage.ADULT) {
            return true;
        }

        // Check by ref comparison (handles UUID instability)
        if (ref instanceof com.hypixel.hytale.component.Ref) {
            @SuppressWarnings("unchecked")
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> typedRef =
                (com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>) ref;
            return findBabyByRef(typedRef) != null;
        }
        return false;
    }

    /**
     * Data class for untracked babies found during world scan.
     */
    public static class UntrackedBaby {
        private final Ref<EntityStore> entityRef;
        private final String modelAssetId;
        private final AnimalType animalType;

        public UntrackedBaby(Ref<EntityStore> entityRef, String modelAssetId, AnimalType animalType) {
            this.entityRef = entityRef;
            this.modelAssetId = modelAssetId;
            this.animalType = animalType;
        }

        public Ref<EntityStore> getEntityRef() { return entityRef; }
        public String getModelAssetId() { return modelAssetId; }
        public AnimalType getAnimalType() { return animalType; }
    }

    // ==================== BABY DETECTION FALLBACK ====================

    /**
     * Get all UUIDs of currently tracked babies (non-adult growth stage).
     * Used by the fallback detection system to identify untracked babies.
     */
    public Set<UUID> getTrackedBabyUuids() {
        Set<UUID> babyUuids = new java.util.HashSet<>();
        for (Map.Entry<UUID, BreedingData> entry : breedingDataMap.entrySet()) {
            if (entry.getValue().getGrowthStage() != GrowthStage.ADULT) {
                babyUuids.add(entry.getKey());
            }
        }
        return babyUuids;
    }

    /**
     * Check if a baby with the given ref is already tracked.
     * Uses both UUID matching and ref index comparison.
     *
     * @param ref The entity reference to check
     * @param refUuid The UUID generated from the ref (for direct lookup)
     * @return true if this baby is already tracked
     */
    public boolean isBabyTracked(Object ref, UUID refUuid) {
        // Check by UUID first (fast path)
        BreedingData data = breedingDataMap.get(refUuid);
        if (data != null && data.getGrowthStage() != GrowthStage.ADULT) {
            return true;
        }

        // Check by ref comparison (handles UUID instability)
        if (ref instanceof com.hypixel.hytale.component.Ref) {
            @SuppressWarnings("unchecked")
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> typedRef =
                (com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>) ref;
            return findBabyByRef(typedRef) != null;
        }
        return false;
    }

    /**
     * Data class for untracked babies found during world scan.
     */
    public static class UntrackedBaby {
        private final Object entityRef;
        private final String modelAssetId;
        private final AnimalType animalType;

        public UntrackedBaby(Object entityRef, String modelAssetId, AnimalType animalType) {
            this.entityRef = entityRef;
            this.modelAssetId = modelAssetId;
            this.animalType = animalType;
        }

        public Object getEntityRef() { return entityRef; }
        public String getModelAssetId() { return modelAssetId; }
        public AnimalType getAnimalType() { return animalType; }
    }
}
