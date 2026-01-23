package com.tameableanimals.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.validators.IntRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

public class BuilderActionRemovePlayerHeldItems extends BuilderActionBase {
    protected int count;

    public int getCount(@Nonnull BuilderSupport support) { return this.count; }

    public BuilderActionRemovePlayerHeldItems() { super(); }

    @Nonnull
    public String getShortDescription() { return "Remove held item(s) from the player"; }

    @Nonnull
    public String getLongDescription() { return this.getShortDescription(); }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Stable; }


    @Nonnull
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionRemovePlayerHeldItems(this, builderSupport);
    }

    public BuilderActionRemovePlayerHeldItems readConfig(@Nonnull JsonElement data) {
        this.getInt(data, "Count", (c) -> this.count = c, 1, IntRangeValidator.fromExclToIncl(0, 100), BuilderDescriptorState.Stable, "The amount of items to remove", null);
        return this;
    }
}
