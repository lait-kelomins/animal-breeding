package com.tameableanimals.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

/**
 * Builder for ActionGrowToNextStage - triggers growth transition for baby animals.
 *
 * JSON Config (no parameters needed):
 * {
 *   "Type": "GrowToNextStage"
 * }
 *
 * Growth Logic:
 * - For Baby NPC roles (Lamb, Calf, Piglet, etc.): despawn baby, spawn adult
 * - For Scaled babies (Wolf, Bear, Fox, etc.): update model scale
 *
 * Typically used with alarm sensor:
 * {
 *   "Sensor": {
 *     "Type": "And",
 *     "Sensors": [
 *       { "Type": "Alarm", "Name": "Growth_Ready", "State": "Passed", "Clear": true },
 *       { "Type": "GrowthReady" }
 *     ]
 *   },
 *   "Actions": [
 *     { "Type": "GrowToNextStage" }
 *   ]
 * }
 */
public class BuilderActionGrowToNextStage extends BuilderActionBase {

    public BuilderActionGrowToNextStage() {
        super();
    }

    @Nonnull
    public String getShortDescription() {
        return "Trigger growth transition for baby animals";
    }

    @Nonnull
    public String getLongDescription() {
        return "Triggers growth transition for baby animals. " +
               "For livestock (baby NPC roles like Lamb, Calf): despawns baby and spawns adult. " +
               "For wild animals (scaled babies like Wolf, Bear): updates model scale.";
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public ActionGrowToNextStage build(@Nonnull BuilderSupport builderSupport) {
        return new ActionGrowToNextStage(this, builderSupport);
    }

    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        // No additional parameters needed
        return super.readConfig(data);
    }
}
