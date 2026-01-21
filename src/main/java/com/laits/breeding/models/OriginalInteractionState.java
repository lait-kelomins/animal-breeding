package com.laits.breeding.models;

/**
 * Stores the original interaction state of an entity before we override it.
 * Used to restore original behavior when feeding doesn't make sense
 * (e.g., animal in love mode or on breeding cooldown).
 */
public class OriginalInteractionState {
    private final String interactionId;  // The original interaction ID before we overrode it
    private final String hint;           // e.g., "Press F to Mount"

    public OriginalInteractionState(String interactionId, String hint) {
        this.interactionId = interactionId;
        this.hint = hint;
    }

    public String getInteractionId() {
        return interactionId;
    }

    public String getHint() {
        return hint;
    }

    /**
     * Check if this state has a valid interaction to restore.
     */
    public boolean hasInteraction() {
        return interactionId != null && !interactionId.isEmpty();
    }

    /**
     * Check if this state has a valid hint to restore.
     */
    public boolean hasHint() {
        return hint != null && !hint.isEmpty();
    }

    @Override
    public String toString() {
        return "OriginalInteractionState{" +
                "interactionId='" + interactionId + '\'' +
                ", hint='" + hint + '\'' +
                '}';
    }
}
