package com.tameableanimals.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.jetbrains.annotations.NotNull;

public class FavouriteFoodCommand extends CommandBase {

    public FavouriteFoodCommand() {
        super("Foods", "Display the entities favourite food");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@NotNull CommandContext context) {
        context.sendMessage(Message.raw("Favourite Foods:"));
        context.sendMessage(Message.raw("Default  - Carrot"));
        context.sendMessage(Message.raw("Horse    - Carrot"));
        context.sendMessage(Message.raw("Rabbit   - Carrot"));
        context.sendMessage(Message.raw("Bunny    - Carrot"));
        context.sendMessage(Message.raw("Sheep    - Lettuce"));
        context.sendMessage(Message.raw("Cow      - Cauliflower"));
        context.sendMessage(Message.raw("Pig      - Brown Mushroom"));
        context.sendMessage(Message.raw("Chicken  - Corn"));
        context.sendMessage(Message.raw("Turkey   - Corn"));
        context.sendMessage(Message.raw("Goat     - Apple"));
        context.sendMessage(Message.raw("Ram      - Apple"));
        context.sendMessage(Message.raw("Mouflon  - Apple"));
        context.sendMessage(Message.raw("Camel    - Wheat"));
        context.sendMessage(Message.raw("Boar     - Red Mushroom"));
        context.sendMessage(Message.raw("Skrill   - Chilli"));
    }

}