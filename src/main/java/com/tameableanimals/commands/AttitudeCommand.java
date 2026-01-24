package com.tameableanimals.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractTargetEntityCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import it.unimi.dsi.fastutil.objects.ObjectList;

import javax.annotation.Nonnull;

public class AttitudeCommand extends AbstractTargetEntityCommand {

    public AttitudeCommand() {
        super("Attitude", "Displays NPC's attitude toward the player");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull ObjectList<Ref<EntityStore>> entities, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        if (playerRef == null) return;

        for (Ref<EntityStore> entityRef : entities) {
            ComponentType<EntityStore, NPCEntity> componentType = NPCEntity.getComponentType();
            if (componentType == null) continue;

            NPCEntity npcComponent = store.getComponent(entityRef, componentType);
            if (npcComponent == null) continue;

            Role role = npcComponent.getRole();
            if (role == null) continue;

            WorldSupport worldSupport = role.getWorldSupport();
            Attitude defaultAttitude = worldSupport.getDefaultPlayerAttitude();

            Attitude currentAttitude;
            try {
                currentAttitude = worldSupport.getAttitude(entityRef, playerRef, store);
            } catch (NullPointerException e) {
                context.sendMessage(Message.raw(role.getRoleName() +  " attitude not initialized"));
                continue;
            }

            context.sendMessage(Message.raw(npcComponent.getRoleName() + ": Default(" + defaultAttitude + "), Current(" + currentAttitude + ")"));
        }
    }
}