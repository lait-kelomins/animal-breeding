package com.tameableanimals.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.tameableanimals.tame.TameComponent;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nonnull;

public class TamedCommand extends AbstractTargetEntityCommand {

    public TamedCommand() {
        super("Tamed", "Displays whether or not the targeted entity is tamed");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull ObjectList<Ref<EntityStore>> entities, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        for (Ref<EntityStore> entityRef : entities) {
            ComponentType<EntityStore, NPCEntity> componentType = NPCEntity.getComponentType();
            if (componentType == null) continue;

            NPCEntity npcComponent = store.getComponent(entityRef, componentType);
            if (npcComponent == null) continue;

            TameComponent tameComponent = store.getComponent(entityRef, TameComponent.getComponentType());
            if (tameComponent == null) {
                context.sendMessage(Message.raw(npcComponent.getRoleName() + " has no tame stats"));
                continue;
            }

            context.sendMessage(Message.raw(String.format("%s: IsTamed(%s), Owner(%s)", npcComponent.getRoleName(), tameComponent.isTamed(), tameComponent.getTamerName())));
        }
    }
}