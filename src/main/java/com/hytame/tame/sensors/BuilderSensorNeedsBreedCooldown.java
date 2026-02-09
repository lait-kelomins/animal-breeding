package com.hytame.tame.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

import javax.annotation.Nonnull;

/**
 * Builder for SensorNeedsBreedCooldown.
 *
 * JSON Config (no parameters needed):
 * {
 *   "Type": "NeedsBreedCooldown"
 * }
 *
 * Returns true if the entity's needsBreedCooldown flag is set.
 */
public class BuilderSensorNeedsBreedCooldown extends BuilderSensorBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Check if entity needs breed cooldown";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Returns true if the entity's needsBreedCooldown flag is set. " +
               "Used with alarm sensor to trigger Breed_Cooldown alarm after breeding.";
    }

    @Nonnull
    public Sensor build(@Nonnull BuilderSupport builderSupport) {
        return new SensorNeedsBreedCooldown(this, builderSupport);
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
