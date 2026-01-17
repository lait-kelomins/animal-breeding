package com.laits.breeding.models;

/**
 * Enum representing the growth stages of baby animals.
 */
public enum GrowthStage {
    BABY(0.5f, 0),
    JUVENILE(0.75f, 1),
    ADULT(1.0f, 2);

    private final float sizeMultiplier;
    private final int stageIndex;

    GrowthStage(float sizeMultiplier, int stageIndex) {
        this.sizeMultiplier = sizeMultiplier;
        this.stageIndex = stageIndex;
    }

    /**
     * Get the size multiplier for this growth stage.
     * @return Size multiplier (0.5 for baby, 0.75 for juvenile, 1.0 for adult)
     */
    public float getSizeMultiplier() {
        return sizeMultiplier;
    }

    /**
     * Get the stage index (0 = baby, 1 = juvenile, 2 = adult).
     * @return The stage index
     */
    public int getStageIndex() {
        return stageIndex;
    }

    /**
     * Check if this stage allows breeding.
     * @return true if the animal can breed at this stage
     */
    public boolean canBreed() {
        return this == ADULT;
    }

    /**
     * Get the next growth stage.
     * @return The next stage, or ADULT if already adult
     */
    public GrowthStage getNextStage() {
        return switch (this) {
            case BABY -> JUVENILE;
            case JUVENILE -> ADULT;
            case ADULT -> ADULT;
        };
    }

    /**
     * Check if there is a next growth stage.
     * @return true if the animal can still grow
     */
    public boolean hasNextStage() {
        return this != ADULT;
    }

    /**
     * Get the display name for this stage.
     * @return Human-readable stage name
     */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
