package com.tameableanimals.actions;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.laits.breeding.LaitsBreedingPlugin;
import com.tameableanimals.tame.TameComponent;
import com.tameableanimals.utils.Debug;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class ActionTame extends ActionBase {
    protected final Set<String> lovedFood;

    public ActionTame(@Nonnull BuilderActionTame builderActionTame, @Nonnull BuilderSupport builderSupport) {
        super(builderActionTame);
        this.lovedFood = new HashSet<>(Arrays.asList(builderActionTame.getLovedFood(builderSupport)));
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        // Get player components
        Ref<EntityStore> refStore = role.getStateSupport().getInteractionIterationTarget();
        if (Debug.isNullLog(refStore, "RefStore is null, tame failed")) return false;

        PlayerRef playerRef = store.getComponent(refStore, PlayerRef.getComponentType());
        if (Debug.isNullLog(playerRef, "PlayerRef is null, cannot send messages"));

        Player player = store.getComponent(refStore, Player.getComponentType());
        if (Debug.isNullMsg(playerRef, player, "Player entity is null, tame failed")) return false;

        UUIDComponent playerUUIDComponent = store.getComponent(refStore, UUIDComponent.getComponentType());
        if (Debug.isNullMsg(playerRef, playerUUIDComponent, "Player UUID is null, tame failed")) return false;

        // Get npc components
        ComponentType<EntityStore, NPCEntity> componentType = NPCEntity.getComponentType();
        if (Debug.isNullMsg(playerRef, componentType, "Component type is null, tame failed")) return false;

        NPCEntity npcEntity = store.getComponent(ref, componentType);
        if (Debug.isNullMsg(playerRef, npcEntity, "Npc entity is null, tame failed")) return false;

        WorldSupport worldSupport = role.getWorldSupport();

        // Set tamed
        TameComponent tameComponent = store.getComponent(ref, TameComponent.getComponentType());
        if (Debug.isNullMsg(playerRef, tameComponent, "TameComponent is null, tame failed")) return false;

        tameComponent.setTamed(playerUUIDComponent.getUuid(), player.getDisplayName());

        try {
            LaitsBreedingPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);
        } catch (IllegalAccessException e) {
            Debug.msg(playerRef, "Failed to override attitude for NPC, tame failed", Level.SEVERE);
            Debug.log(e.getMessage(), Level.SEVERE);
            return false;
        }

        // Remove from over population checks
        boolean oldState = npcEntity.updateSpawnTrackingState(false);
        if (oldState == true) Debug.log("Stopped tacking entity " + npcEntity.getRoleName(), Level.INFO);

        Debug.msg(playerRef, npcEntity.getRoleName() + " successfully tamed", Level.INFO);
        return true;
    }
}
