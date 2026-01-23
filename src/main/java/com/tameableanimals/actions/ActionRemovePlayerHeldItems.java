package com.tameableanimals.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.tameableanimals.utils.Debug;

import javax.annotation.Nonnull;

public class ActionRemovePlayerHeldItems extends ActionBase {
    protected final int count;

    public ActionRemovePlayerHeldItems(@Nonnull BuilderActionRemovePlayerHeldItems builderActionFeed, @Nonnull BuilderSupport builderSupport) {
        super(builderActionFeed);
        this.count = builderActionFeed.count;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        // Get player components
        Ref<EntityStore> refStore = role.getStateSupport().getInteractionIterationTarget();
        if (Debug.isNullLog(refStore, "RefStore is null, remove item failed")) return false;

        Player player = store.getComponent(refStore, Player.getComponentType());
        if (Debug.isNullLog(player, "Player entity is null, remove item failed")) return false;

        UUIDComponent playerUUIDComponent = store.getComponent(refStore, UUIDComponent.getComponentType());
        if (Debug.isNullLog(playerUUIDComponent, "Player UUID is null, remove item failed")) return false;

        // Remove item
        Inventory inventory = player.getInventory();
        if (Debug.isNullLog(inventory, "Inventory is null, remove item failed")) return false;

        byte slot = inventory.getActiveHotbarSlot();
        ItemStack itemStack = inventory.getHotbar().getItemStack(slot);
        if (Debug.isNullLog(itemStack, "ItemStack is null, remove item failed")) return false;

        if (itemStack.getQuantity() > count) inventory.getHotbar().removeItemStackFromSlot(slot, count);

        player.sendInventory();

        return true;
    }
}
