package com.animaltaming.api.model;

/**
 * Behavior modes for tamed animals.
 */
public enum BehaviorMode {
    /**
     * Animal follows the owner and teleports if too far.
     */
    FOLLOW("Following"),

    /**
     * Animal stays at its home position.
     */
    STAY("Staying");

    private final String displayName;

    BehaviorMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Toggle between FOLLOW and STAY modes.
     *
     * @return the opposite mode
     */
    public BehaviorMode toggle() {
        return this == FOLLOW ? STAY : FOLLOW;
    }

    /**
     * @return human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
