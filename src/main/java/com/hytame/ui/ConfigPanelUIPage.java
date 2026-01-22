package com.hytame.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hytame.HyTamePlugin;
import com.hytame.util.ConfigManager;

/**
 * Interactive UI panel for configuring breeding settings.
 * Opens via /breed settings command.
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/ConfigPanel.ui
 */
public class ConfigPanelUIPage extends InteractiveCustomUIPage<ConfigPanelUIPage.ConfigEventData> {

    /**
     * Event data received when user interacts with config panel.
     */
    public static class ConfigEventData {
        public String action;        // "save", "reload", "close", "toggleGrowth"
        public String growthTime;    // Value from growth time input
        public String cooldown;      // Value from cooldown input

        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec
            .builder(ConfigEventData.class, ConfigEventData::new)
            .append(new KeyedCodec<>("@action", new StringCodec()),
                (obj, val) -> obj.action = val,
                obj -> obj.action)
            .add()
            .append(new KeyedCodec<>("@growthTime", new StringCodec()),
                (obj, val) -> obj.growthTime = val,
                obj -> obj.growthTime)
            .add()
            .append(new KeyedCodec<>("@cooldown", new StringCodec()),
                (obj, val) -> obj.cooldown = val,
                obj -> obj.cooldown)
            .add()
            .build();
    }

    public ConfigPanelUIPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigEventData.CODEC);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        // Load the UI layout file
        cmd.append("Pages/ConfigPanel.ui");

        // Get current config values
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            ConfigManager config = plugin.getConfigManager();
            if (config != null) {
                // Set growth enabled status
                boolean growthEnabled = config.isGrowthEnabled();
                cmd.set("#growthStatus.Text", growthEnabled ? "ON" : "OFF");
                cmd.set("#growthStatus.Style.TextColor", growthEnabled ? "#88ff88" : "#ff8888");

                // Set default growth time
                cmd.set("#growthTimeInput.Value", String.valueOf(config.getDefaultGrowthTimeMinutes()));

                // Set default cooldown
                cmd.set("#cooldownInput.Value", String.valueOf(config.getDefaultBreedCooldownMinutes()));
            }
        }

        // Set hidden action marker values
        cmd.set("#actionToggle.Value", "TOGGLE");
        cmd.set("#actionSave.Value", "SAVE");
        cmd.set("#actionClose.Value", "CLOSE");

        // Bind toggle button - read action from hidden TextField
        events.addEventBinding(CustomUIEventBindingType.Activating, "#toggleButton",
            new EventData()
                .append("@action", "#actionToggle.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value"));

        // Bind save button - read action from hidden TextField
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveButton",
            new EventData()
                .append("@action", "#actionSave.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value"));

        // Bind close button - read action from hidden TextField
        events.addEventBinding(CustomUIEventBindingType.Activating, "#closeButton",
            new EventData().append("@action", "#actionClose.Value"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ConfigEventData data) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            HyTamePlugin plugin = HyTamePlugin.getInstance();

            if (plugin == null) {
                closePage(player, ref, store);
                return;
            }

            ConfigManager config = plugin.getConfigManager();
            if (config == null) {
                closePage(player, ref, store);
                return;
            }

            String action = data.action;
            if (action == null) {
                action = "";
            }
            action = action.trim().toUpperCase();

            // Check action based on hidden TextField values
            if (action.equals("TOGGLE")) {
                // Toggle growth enabled
                boolean newState = !config.isGrowthEnabled();
                config.setGrowthEnabled(newState);
                if (player != null) {
                    player.sendMessage(Message.raw("Baby growth " + (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                }
                // Refresh the page to show new state
                reopenPage(player, ref, store);
                return;
            } else if (action.equals("SAVE")) {
                // Parse and save values
                boolean saved = saveConfigValues(config, data, player);
                if (saved) {
                    config.saveToFile();
                    if (player != null) {
                        player.sendMessage(Message.raw("Configuration saved!").color("#55FF55"));
                    }
                }
            } else if (action.equals("RELOAD")) {
                // Reload config from file
                config.reloadFromFile();
                if (player != null) {
                    player.sendMessage(Message.raw("Configuration reloaded from file.").color("#55FF55"));
                }
                // Refresh the page to show reloaded values
                reopenPage(player, ref, store);
                return;
            }
            // CLOSE or unknown - just close

            closePage(player, ref, store);

        } catch (Exception e) {
            log("Error in handleDataEvent: " + e.getMessage());
        }
    }

    /**
     * Parse and save config values from the form data.
     */
    private boolean saveConfigValues(ConfigManager config, ConfigEventData data, Player player) {
        boolean hasErrors = false;

        // Parse growth time
        if (data.growthTime != null && !data.growthTime.isEmpty()) {
            try {
                double growthTime = Double.parseDouble(data.growthTime.trim());
                if (growthTime > 0) {
                    config.setDefaultGrowthTime(growthTime);
                } else {
                    if (player != null) {
                        player.sendMessage(Message.raw("Growth time must be positive.").color("#FF5555"));
                    }
                    hasErrors = true;
                }
            } catch (NumberFormatException e) {
                if (player != null) {
                    player.sendMessage(Message.raw("Invalid growth time: " + data.growthTime).color("#FF5555"));
                }
                hasErrors = true;
            }
        }

        // Parse cooldown
        if (data.cooldown != null && !data.cooldown.isEmpty()) {
            try {
                double cooldown = Double.parseDouble(data.cooldown.trim());
                if (cooldown >= 0) {
                    config.setDefaultBreedCooldown(cooldown);
                } else {
                    if (player != null) {
                        player.sendMessage(Message.raw("Cooldown cannot be negative.").color("#FF5555"));
                    }
                    hasErrors = true;
                }
            } catch (NumberFormatException e) {
                if (player != null) {
                    player.sendMessage(Message.raw("Invalid cooldown: " + data.cooldown).color("#FF5555"));
                }
                hasErrors = true;
            }
        }

        return !hasErrors;
    }

    /**
     * Reopen the page to refresh displayed values.
     */
    private void reopenPage(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (player != null) {
            try {
                PlayerRef playerRef = player.getPlayerRef();
                player.getPageManager().openCustomPage(ref, store, new ConfigPanelUIPage(playerRef));
            } catch (Exception e) {
                log("Failed to reopen config page: " + e.getMessage());
            }
        }
    }

    /**
     * Close the config panel page.
     */
    private void closePage(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (player != null) {
            try {
                player.getPageManager().setPage(ref, store, Page.None);
            } catch (Exception e) {
                log("Failed to close page: " + e.getMessage());
            }
        }
    }

    private void log(String message) {
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null && HyTamePlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[ConfigPanel] " + message);
        }
    }
}
