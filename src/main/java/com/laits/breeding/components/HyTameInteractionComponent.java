package com.laits.breeding.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import javax.annotation.Nullable;

/**
 * ECS Component for persisting original entity interaction state.
 *
 * Solves the restart persistence problem: when server restarts, the game loads
 * entities with their already-modified interactions. Without this component,
 * the plugin would capture the modified interaction as the "original", leading
 * to warnings like "Missing root interaction animalbreeding.interactionHints.legacyFeedOrMount".
 *
 * This component persists with the entity in world save data, storing the true
 * original interaction before we override it with Root_FeedAnimal.
 */
public class HyTameInteractionComponent implements Component<EntityStore> {

    public static final BuilderCodec<HyTameInteractionComponent> CODEC = BuilderCodec
            .builder(HyTameInteractionComponent.class, HyTameInteractionComponent::new)
            .append(new KeyedCodec<>("InteractionId", Codec.STRING),
                    (data, value) -> data.originalInteractionId = value,
                    data -> data.originalInteractionId)
            .add()
            .append(new KeyedCodec<>("Hint", Codec.STRING),
                    (data, value) -> data.originalHint = value,
                    data -> data.originalHint)
            .add()
            .append(new KeyedCodec<>("Captured", Codec.BOOLEAN),
                    (data, value) -> data.captured = value,
                    data -> data.captured)
            .add()
            .build();

    /**
     * Get the component type from the plugin registry.
     * @return The registered ComponentType, or null if plugin not initialized
     */
    public static ComponentType<EntityStore, HyTameInteractionComponent> getComponentType() {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        return plugin != null ? plugin.getHyTameInteractionComponentType() : null;
    }

    // The original interaction ID before we set Root_FeedAnimal
    // null is a valid value - some entities (like horses) have no Use interaction originally
    private String originalInteractionId;

    // The original interaction hint text
    private String originalHint;

    // Flag indicating we've already captured the true original
    // Prevents re-capture on server restart when entity loads with modified interactions
    private Boolean captured = false;

    /**
     * Default constructor for codec deserialization.
     */
    public HyTameInteractionComponent() {
    }

    /**
     * Get the original interaction ID.
     * @return The original interaction ID, may be null (valid for some entity types)
     */
    public String getOriginalInteractionId() {
        return originalInteractionId;
    }

    /**
     * Set the original interaction ID.
     * @param originalInteractionId The interaction ID to store
     */
    public void setOriginalInteractionId(String originalInteractionId) {
        this.originalInteractionId = originalInteractionId;
    }

    /**
     * Get the original interaction hint.
     * @return The original hint text, may be null
     */
    public String getOriginalHint() {
        return originalHint;
    }

    /**
     * Set the original interaction hint.
     * @param originalHint The hint text to store
     */
    public void setOriginalHint(String originalHint) {
        this.originalHint = originalHint;
    }

    /**
     * Check if the original interaction has been captured.
     * @return true if we've already captured the true original
     */
    public boolean isCaptured() {
        return Boolean.TRUE.equals(captured);
    }

    /**
     * Mark the original interaction as captured.
     * @param captured true to mark as captured
     */
    public void setCaptured(boolean captured) {
        this.captured = captured;
    }

    /**
     * Check if this component has a stored interaction (even if null).
     * The captured flag indicates whether we've recorded the original state.
     * @return true if original state has been recorded
     */
    public boolean hasStoredInteraction() {
        return isCaptured();
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        HyTameInteractionComponent component = new HyTameInteractionComponent();
        component.originalInteractionId = this.originalInteractionId;
        component.originalHint = this.originalHint;
        component.captured = this.captured;
        return component;
    }
}
