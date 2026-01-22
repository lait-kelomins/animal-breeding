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
 * Configuration panel for Animal Breeding plugin.
 *
 * Layout:
 * - Left sidebar: Preset list with search
 * - Middle: Current preset global settings
 * - Right: Animal grid + selected animal config
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/ConfigPanel.ui
 */
public class ConfigPanelUIPage extends InteractiveCustomUIPage<ConfigPanelUIPage.ConfigEventData> {

    /**
     * Event data received from UI interactions.
     */
    public static class ConfigEventData {
        public String action;
        public String growthTime;
        public String cooldown;

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

        // Populate with current config values
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            ConfigManager config = plugin.getConfigManager();
            if (config != null) {
                // Set preset name
                String activePreset = config.getActivePreset();
                cmd.set("#presetNameLabel.Text", activePreset != null ? activePreset : "none");

                // Set growth toggle button text
                boolean growthEnabled = config.isGrowthEnabled();
                cmd.set("#growthToggleBtn.Text", growthEnabled ? "ON" : "OFF");

                // Set default values
                cmd.set("#growthTimeInput.Value", String.valueOf(config.getDefaultGrowthTimeMinutes()));
                cmd.set("#cooldownInput.Value", String.valueOf(config.getDefaultBreedCooldownMinutes()));
            }
        }

        // Set hidden action marker values
        cmd.set("#actionToggleGrowth.Value", "TOGGLE_GROWTH");
        cmd.set("#actionSave.Value", "SAVE");

        // Bind growth toggle button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#growthToggleBtn",
            new EventData()
                .append("@action", "#actionToggleGrowth.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value"));

        // Bind save button
        events.addEventBinding(CustomUIEventBindingType.Activating, "#saveBtn",
            new EventData()
                .append("@action", "#actionSave.Value")
                .append("@growthTime", "#growthTimeInput.Value")
                .append("@cooldown", "#cooldownInput.Value"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, ConfigEventData data) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();

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

            log("Config panel action: " + action);

            if (action.equals("TOGGLE_GROWTH")) {
                // Toggle growth enabled
                boolean newState = !config.isGrowthEnabled();
                config.setGrowthEnabled(newState);
                if (player != null) {
                    player.sendMessage(Message.raw("Baby growth " + (newState ? "enabled" : "disabled")).color(newState ? "#55FF55" : "#FF9900"));
                }
                // Refresh the page
                reopenPage(player, ref, store);
                return;

            } else if (action.equals("SAVE")) {
                // Save current values
                boolean hasErrors = false;

                // Parse growth time
                if (data.growthTime != null && !data.growthTime.isEmpty()) {
                    try {
                        double growthTime = Double.parseDouble(data.growthTime.trim());
                        if (growthTime > 0) {
                            config.setDefaultGrowthTime(growthTime);
                        } else {
                            hasErrors = true;
                        }
                    } catch (NumberFormatException e) {
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
                            hasErrors = true;
                        }
                    } catch (NumberFormatException e) {
                        hasErrors = true;
                    }
                }

                // Save to file
                config.saveToFile();

                if (player != null) {
                    if (hasErrors) {
                        player.sendMessage(Message.raw("Saved with some invalid values ignored.").color("#FFAA00"));
                    } else {
                        player.sendMessage(Message.raw("Configuration saved!").color("#55FF55"));
                    }
                }
                // Don't close - stay on page
                return;
            }

            // Unknown action or close - close the page
            closePage(player, ref, store);

        } catch (Exception e) {
            log("Error in handleDataEvent: " + e.getMessage());
        }
    }

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
