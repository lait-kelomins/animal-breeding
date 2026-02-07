package com.tameableanimals.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

import javax.annotation.Nonnull;

/**
 * Builder for SensorScaledBaby.
 *
 * JSON Config (no parameters needed):
 * {
 *   "Type": "ScaledBaby"
 * }
 *
 * Returns true if:
 * 1. The animal uses scaling for growth (no baby NPC variant)
 * 2. The HyTameComponent.growthStage is BABY or JUVENILE (not ADULT)
 *
 * Used to differentiate between:
 * - Livestock babies (Lamb, Calf, Chick) - use baby NPC roles
 * - Scaled babies (Wolf, Bear, Fox) - use adult NPC with scaled model
 */
public class BuilderSensorScaledBaby extends BuilderSensorBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Check if entity is a scaled baby";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Returns true if the entity is a 'scaled baby' - an animal that uses " +
               "model scaling for growth instead of a dedicated baby NPC variant. " +
               "Examples: wolves, bears, foxes (use scaling). " +
               "Counter-examples: sheep lambs, cow calves (use baby NPC roles).";
    }

    @Nonnull
    public Sensor build(@Nonnull BuilderSupport builderSupport) {
        return new SensorScaledBaby(this, builderSupport);
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        // No additional parameters needed
        return this;
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
