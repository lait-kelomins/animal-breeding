package com.hytame.tame.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

/**
 * Builder for ActionClearBreedCooldownFlag.
 *
 * JSON Config (no parameters needed):
 * {
 *   "Type": "ClearBreedCooldownFlag"
 * }
 */
public class BuilderActionClearBreedCooldownFlag extends BuilderActionBase {

    public BuilderActionClearBreedCooldownFlag() {
        super();
    }

    @Nonnull
    public String getShortDescription() {
        return "Clear breed cooldown flag on entity";
    }

    @Nonnull
    public String getLongDescription() {
        return "Clears the needsBreedCooldown flag on HyTameComponent. " +
               "Used after setting Breed_Cooldown alarm and as no-op when alarm passes.";
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public ActionClearBreedCooldownFlag build(@Nonnull BuilderSupport builderSupport) {
        return new ActionClearBreedCooldownFlag(this, builderSupport);
    }

    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        return super.readConfig(data);
    }
}
