package com.hytame.tame.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

import javax.annotation.Nonnull;

/**
 * Builder for SensorGrowthReady.
 *
 * JSON Config (no parameters needed):
 * {
 *   "Type": "GrowthReady"
 * }
 *
 * Returns true if the entity's growth stage is NOT ADULT (can still grow).
 */
public class BuilderSensorGrowthReady extends BuilderSensorBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Check if entity can grow (not adult)";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Returns true if the entity's growth stage is BABY or JUVENILE (not yet ADULT). " +
               "Used with alarm sensor to trigger growth transitions.";
    }

    @Nonnull
    public Sensor build(@Nonnull BuilderSupport builderSupport) {
        return new SensorGrowthReady(this, builderSupport);
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        // No additional parameters needed - just checks if growthStage != ADULT
        return this;
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
