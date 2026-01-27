package com.tameableanimals;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.tameableanimals.commands.TameableAnimalsCommand;
import com.tameableanimals.config.ConfigManager;
import com.tameableanimals.actions.BuilderActionRemovePlayerHeldItems;
import com.tameableanimals.actions.BuilderActionTame;
import com.tameableanimals.sensors.BuilderSensorTamed;
import com.tameableanimals.tame.TameComponent;
import com.tameableanimals.tame.TameSystems;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

public class TameableAnimalsPlugin extends JavaPlugin {
    private static TameableAnimalsPlugin INSTANCE;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Field ATTITUDE_FIELD;

    // statically apply reflection change so we can alter the defaultPlayerAttitude var via ATTITUDE_FIELD
    static {
        try {
            ATTITUDE_FIELD = WorldSupport.class.getDeclaredField("defaultPlayerAttitude");
            ATTITUDE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access defaultPlayerAttitude", e);
        }
    }

    public static TameableAnimalsPlugin get() { return INSTANCE; }
    public static Field getAttitudeField() { return ATTITUDE_FIELD; }

    private ComponentType<EntityStore, TameComponent> tameComponentType;

    public TameableAnimalsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;
    }

    public ComponentType<EntityStore, TameComponent> getTameComponentType() { return tameComponentType; }

    @Override
    protected void setup() {
        ConfigManager.load(this.getDataDirectory().resolve("Configs/config.json").toFile());

        this.getCommandRegistry().registerCommand(new TameableAnimalsCommand());
        this.getCommandRegistry().registerCommand(new TameableAnimalsCommand("TA")); //shortcut

        tameComponentType = this.getEntityStoreRegistry().registerComponent(TameComponent.class, "Tame", TameComponent.CODEC);

        LOGGER.atInfo().log("Successfully loaded %s v%s", this.getName(), this.getManifest().getVersion().toString());
    }

    @Override
    protected void start() {
        // register tame systems
        this.getEntityStoreRegistry().registerSystem(new TameSystems.TameActivateSystem());

        // register core components
        NPCPlugin.get().registerCoreComponentType("Tame", BuilderActionTame::new);
        NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
        NPCPlugin.get().registerCoreComponentType("RemovePlayerHeldItems", BuilderActionRemovePlayerHeldItems::new);
    }
}
