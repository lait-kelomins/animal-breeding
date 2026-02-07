package com.tameableanimals.tame;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.GrowthStage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * ECS Component for tracking taming state on animals.
 * Renamed from TameComponent to HyTameComponent for clarity.
 *
 * Features:
 * - isTamed: Whether the animal is tamed
 * - tamerUUID/tamerName: Owner information
 * - hytameId: Stable ID for persistence (links ECS to tamed_animals.json)
 * - actionReady: Per-entity action state (disabled during cooldown/love mode)
 */
public class HyTameComponent implements Component<EntityStore> {
    public static final BuilderCodec<HyTameComponent> CODEC = BuilderCodec.builder(HyTameComponent.class, HyTameComponent::new)
            .append(new KeyedCodec<>("IsTamed", Codec.BOOLEAN), (data, value) -> data.isTamed = value, data -> data.isTamed)
            .add()
            .append(new KeyedCodec<>("TamerUUID", Codec.UUID_BINARY), (data, value) -> data.tamerUUID = value, data -> data.tamerUUID)
            .add()
            .append(new KeyedCodec<>("TamerName", Codec.STRING), (data, value) -> data.tamerName = value, data -> data.tamerName)
            .add()
            .append(new KeyedCodec<>("HytameId", Codec.UUID_BINARY), (data, value) -> data.hytameId = value, data -> data.hytameId)
            .add()
            .append(new KeyedCodec<>("ActionReady", Codec.BOOLEAN), (data, value) -> data.actionReady = value, data -> data.actionReady)
            .add()
            .append(new KeyedCodec<>("GrowthStageOrdinal", Codec.INTEGER), (data, value) -> data.growthStageOrdinal = value, data -> data.growthStageOrdinal)
            .add()
            .build();

    public static ComponentType<EntityStore, HyTameComponent> getComponentType() {
        return LaitsBreedingPlugin.getInstance().getHyTameComponentType();
    }

    private Boolean isTamed = false;
    private UUID tamerUUID = null;
    private String tamerName = null;
    private UUID hytameId = null;  // Stable ID linking ECS to tamed_animals.json
    private Boolean actionReady = true;  // Per-entity action state (disabled during cooldown/love mode)
    private Integer growthStageOrdinal = GrowthStage.ADULT.ordinal();  // Growth stage ordinal for persistence

    public boolean isTamed() {
        return Boolean.TRUE.equals(isTamed);
    }

    public UUID getTamerUUID() {
        return tamerUUID;
    }

    public String getTamerName() {
        return tamerName;
    }

    public UUID getHytameId() {
        return hytameId;
    }

    public void setHytameId(UUID hytameId) {
        this.hytameId = hytameId;
    }

    /**
     * Check if this entity's action is ready.
     * Returns false during breeding cooldown or love mode.
     */
    public boolean isActionReady() {
        return Boolean.TRUE.equals(actionReady);
    }

    /**
     * Set whether this entity's action is ready.
     * Called by HyTameTickSystem to manage cooldown states.
     */
    public void setActionReady(boolean ready) {
        this.actionReady = ready;
    }

    /**
     * Get the growth stage of this entity.
     * @return The current growth stage (BABY, JUVENILE, or ADULT)
     */
    public GrowthStage getGrowthStage() {
        if (growthStageOrdinal == null) {
            return GrowthStage.ADULT;
        }
        GrowthStage[] values = GrowthStage.values();
        int ordinal = growthStageOrdinal;
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : GrowthStage.ADULT;
    }

    /**
     * Set the growth stage of this entity.
     * @param stage The new growth stage
     */
    public void setGrowthStage(GrowthStage stage) {
        this.growthStageOrdinal = (stage != null) ? stage.ordinal() : GrowthStage.ADULT.ordinal();
    }

    /**
     * Check if this entity is ready to grow (not yet ADULT).
     * @return true if the entity can still grow
     */
    public boolean canGrow() {
        return getGrowthStage() != GrowthStage.ADULT;
    }

    /**
     * Advance to the next growth stage.
     * @return true if growth occurred, false if already ADULT
     */
    public boolean growToNextStage() {
        GrowthStage current = getGrowthStage();
        if (current.hasNextStage()) {
            setGrowthStage(current.getNextStage());
            return true;
        }
        return false;
    }

    public void setTamed(@Nonnull UUID player, @Nonnull String playerName) {
        this.isTamed = true;
        this.tamerUUID = player;
        this.tamerName = playerName;
        // Generate hytameId on first tame if not already set
        if (this.hytameId == null) {
            this.hytameId = UUID.randomUUID();
        }
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        HyTameComponent component = new HyTameComponent();
        component.isTamed = this.isTamed;
        component.tamerUUID = this.tamerUUID;
        component.tamerName = this.tamerName;
        component.hytameId = this.hytameId;
        component.actionReady = this.actionReady;
        component.growthStageOrdinal = this.growthStageOrdinal;
        return component;
    }
}
