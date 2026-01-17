package com.laits.breeding.models;

import java.util.UUID;

/**
 * Data class tracking breeding and growth state for an individual animal.
 */
public class BreedingData {
    private final UUID animalId;
    private final AnimalType animalType;
    private long lastBreedTime;
    private boolean isPregnant;
    private long pregnancyStartTime;
    private GrowthStage growthStage;
    private long birthTime;
    private boolean inLove;
    private long loveStartTime;
    private Object entityRef;  // Ref<EntityStore> for entity manipulation

    /**
     * Create breeding data for a new adult animal.
     */
    public BreedingData(UUID animalId, AnimalType animalType) {
        this.animalId = animalId;
        this.animalType = animalType;
        this.lastBreedTime = 0;
        this.isPregnant = false;
        this.pregnancyStartTime = 0;
        this.growthStage = GrowthStage.ADULT;
        this.birthTime = 0;
        this.inLove = false;
        this.loveStartTime = 0;
    }

    /**
     * Create breeding data for a baby animal.
     */
    public static BreedingData createBaby(UUID animalId, AnimalType animalType) {
        BreedingData data = new BreedingData(animalId, animalType);
        data.growthStage = GrowthStage.BABY;
        data.birthTime = System.currentTimeMillis();
        return data;
    }

    public UUID getAnimalId() {
        return animalId;
    }

    public AnimalType getAnimalType() {
        return animalType;
    }

    public long getLastBreedTime() {
        return lastBreedTime;
    }

    public void setLastBreedTime(long lastBreedTime) {
        this.lastBreedTime = lastBreedTime;
    }

    public boolean isPregnant() {
        return isPregnant;
    }

    public void setPregnant(boolean pregnant) {
        isPregnant = pregnant;
        if (pregnant) {
            pregnancyStartTime = System.currentTimeMillis();
        }
    }

    public long getPregnancyStartTime() {
        return pregnancyStartTime;
    }

    public GrowthStage getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(GrowthStage growthStage) {
        this.growthStage = growthStage;
    }

    public long getBirthTime() {
        return birthTime;
    }

    public boolean isInLove() {
        return inLove;
    }

    public void setInLove(boolean inLove) {
        this.inLove = inLove;
        if (inLove) {
            loveStartTime = System.currentTimeMillis();
        }
    }

    public long getLoveStartTime() {
        return loveStartTime;
    }

    /**
     * Check if this animal can breed (is adult and not on cooldown).
     * @param cooldownMillis The cooldown duration in milliseconds
     * @return true if the animal can breed
     */
    public boolean canBreed(long cooldownMillis) {
        if (!growthStage.canBreed()) {
            return false;
        }
        if (isPregnant) {
            return false;
        }
        long timeSinceLastBreed = System.currentTimeMillis() - lastBreedTime;
        return timeSinceLastBreed >= cooldownMillis;
    }

    /**
     * Get time remaining until breeding cooldown expires.
     * @param cooldownMillis The cooldown duration in milliseconds
     * @return Remaining time in milliseconds, or 0 if cooldown has expired
     */
    public long getCooldownRemaining(long cooldownMillis) {
        long timeSinceLastBreed = System.currentTimeMillis() - lastBreedTime;
        long remaining = cooldownMillis - timeSinceLastBreed;
        return Math.max(0, remaining);
    }

    /**
     * Get time remaining until pregnancy ends.
     * @param gestationMillis The gestation duration in milliseconds
     * @return Remaining time in milliseconds, or 0 if not pregnant or ready to give birth
     */
    public long getGestationRemaining(long gestationMillis) {
        if (!isPregnant) {
            return 0;
        }
        long timeSincePregnancy = System.currentTimeMillis() - pregnancyStartTime;
        long remaining = gestationMillis - timeSincePregnancy;
        return Math.max(0, remaining);
    }

    /**
     * Check if pregnancy has completed.
     * @param gestationMillis The gestation duration in milliseconds
     * @return true if pregnant and gestation period has passed
     */
    public boolean isReadyToGiveBirth(long gestationMillis) {
        if (!isPregnant) {
            return false;
        }
        return getGestationRemaining(gestationMillis) == 0;
    }

    /**
     * Get the age of this animal in milliseconds since birth.
     * @return Age in milliseconds, or 0 if birthTime not set
     */
    public long getAge() {
        if (birthTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - birthTime;
    }

    /**
     * Reset love state after mating.
     */
    public void resetLove() {
        this.inLove = false;
        this.loveStartTime = 0;
    }

    /**
     * Complete breeding - set cooldown and reset states.
     */
    public void completeBreeding() {
        this.lastBreedTime = System.currentTimeMillis();
        this.isPregnant = false;
        this.pregnancyStartTime = 0;
        resetLove();
    }

    /**
     * Get the entity reference for ECS manipulation.
     */
    public Object getEntityRef() {
        return entityRef;
    }

    /**
     * Set the entity reference for ECS manipulation.
     */
    public void setEntityRef(Object entityRef) {
        this.entityRef = entityRef;
    }
}
