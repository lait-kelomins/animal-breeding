package com.tameableanimals.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tameableanimals.TameableAnimalsPlugin;
import com.tameableanimals.tame.TameComponent;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nonnull;

public class DebugCommand extends AbstractTargetEntityCommand {

    public DebugCommand() {
        super("Debug", "Display debug information");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull ObjectList<Ref<EntityStore>> entities, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        for (Ref<EntityStore> ref : entities) {
            TameComponent tameComponent = store.getComponent(ref, TameComponent.getComponentType());
            if (tameComponent == null) TameableAnimalsPlugin.get().getLogger().atInfo().log("Does not have tame component");
            else TameableAnimalsPlugin.get().getLogger().atInfo().log("Has tame component");
        }
    }
}