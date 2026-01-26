package com.tameableanimals.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringArrayHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

/**
 * Builder for ActionHyTameOrFeed - routes feeding to taming or breeding based on state.
 *
 * JSON Config:
 * {
 *   "TamingFood": ["hytale:raw_meat", ...],  // Food for taming wild animals
 *   "BreedingFood": ["hytale:wheat", ...]    // Food for breeding tamed animals
 * }
 */
public class BuilderActionHyTameOrFeed extends BuilderActionBase {

    protected StringArrayHolder tamingFoodHolder = new StringArrayHolder();
    protected StringArrayHolder breedingFoodHolder = new StringArrayHolder();

    public String[] getTamingFood(@Nonnull BuilderSupport support) {
        return this.tamingFoodHolder.get(support.getExecutionContext());
    }

    public String[] getBreedingFood(@Nonnull BuilderSupport support) {
        return this.breedingFoodHolder.get(support.getExecutionContext());
    }

    public BuilderActionHyTameOrFeed() {
        super();
    }

    @Nonnull
    public String getShortDescription() {
        return "Route feeding to taming or breeding based on state";
    }

    @Nonnull
    public String getLongDescription() {
        return "Routes feeding interactions: if wild animal + taming food -> tame; if tamed + breeding food -> breed";
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public ActionHyTameOrFeed build(@Nonnull BuilderSupport builderSupport) {
        return new ActionHyTameOrFeed(this, builderSupport);
    }

    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.requireStringArray(data, "TamingFood", this.tamingFoodHolder,
                1, Integer.MAX_VALUE, null, BuilderDescriptorState.Stable,
                "Food items for taming wild animals.",
                "The taming food used.");

        this.requireStringArray(data, "BreedingFood", this.breedingFoodHolder,
                1, Integer.MAX_VALUE, null, BuilderDescriptorState.Stable,
                "Food items for breeding tamed animals.",
                "The breeding food used.");

        return super.readConfig(data);
    }
}
