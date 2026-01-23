package com.tameableanimals.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class TameableAnimalsCommand extends AbstractCommandCollection {

    public TameableAnimalsCommand() { this("TameableAnimals"); }

    public TameableAnimalsCommand(String commandString) {
        super(commandString, "Commands for TameableAnimals");
        this.setPermissionGroup(GameMode.Adventure);
        this.addSubCommand(new AttitudeCommand());
        this.addSubCommand(new TamedCommand());
        this.addSubCommand(new FavouriteFoodCommand());
        this.addSubCommand(new DebugCommand());
    }
}