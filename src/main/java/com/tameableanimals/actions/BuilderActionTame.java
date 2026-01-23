package com.tameableanimals.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringArrayHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

public class BuilderActionTame extends BuilderActionBase {

    protected StringArrayHolder lovedFoodHolder = new StringArrayHolder();

    public String[] getLovedFood(@Nonnull BuilderSupport support) { return this.lovedFoodHolder.get(support.getExecutionContext()); }

    public BuilderActionTame() { super(); }

    @Nonnull
    public String getShortDescription() { return "Tame the entity"; }

    @Nonnull
    public String getLongDescription() { return this.getShortDescription(); }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Stable; }


    @Nonnull
    public ActionTame build(@Nonnull BuilderSupport builderSupport) {
        return new ActionTame(this, builderSupport);
    }

    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.requireStringArray(data, "Food", this.lovedFoodHolder, 1, Integer.MAX_VALUE, null , BuilderDescriptorState.Stable, "The food used for taming.", "The NPC's loved food item type that was used for this tame.");
        return super.readConfig(data);
    }
}
