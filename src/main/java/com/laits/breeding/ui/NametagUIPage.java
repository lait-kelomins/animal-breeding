package com.laits.breeding.ui;

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

import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.AnimalNameGenerator;

import java.util.UUID;

/**
 * Interactive UI panel for naming animals with a text input field.
 * Opens when player uses a Name Tag on an animal.
 *
 * UI File: src/main/resources/Common/UI/Custom/Pages/NametagPage.ui
 */
public class NametagUIPage extends InteractiveCustomUIPage<NametagUIPage.NametagEventData> {

    private final Ref<EntityStore> targetAnimalRef;
    private final UUID playerUuid;
    private final String animalType;
    private final String existingName;

    /**
     * Event data received when user submits the nametag form.
     * The @animalName field binds to the UI text input via the codec.
     */
    public static class NametagEventData {
        public String animalName;

        public static final BuilderCodec<NametagEventData> CODEC = BuilderCodec
            .builder(NametagEventData.class, NametagEventData::new)
            .append(new KeyedCodec<>("@animalName", new StringCodec()),
                (obj, val) -> obj.animalName = val,
                obj -> obj.animalName)
            .add()
            .build();
    }

    public NametagUIPage(PlayerRef playerRef, Ref<EntityStore> targetAnimalRef, UUID playerUuid, String animalType) {
        this(playerRef, targetAnimalRef, playerUuid, animalType, null);
    }

    public NametagUIPage(PlayerRef playerRef, Ref<EntityStore> targetAnimalRef, UUID playerUuid, String animalType, String existingName) {
        super(playerRef, CustomPageLifetime.CanDismiss, NametagEventData.CODEC);
        this.targetAnimalRef = targetAnimalRef;
        this.playerUuid = playerUuid;
        this.animalType = animalType;
        this.existingName = existingName;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd, UIEventBuilder events, Store<EntityStore> store) {
        // Load the UI layout file
        cmd.append("Pages/NametagPage.ui");

        // Set the title to show animal type
        String title = "Name Your " + (animalType != null ? formatAnimalType(animalType) : "Animal");
        cmd.set("#animalTypeLabel.Text", title);

        // Update subtitle if renaming
        if (existingName != null && !existingName.isEmpty()) {
            cmd.set("#subtitleLabel.Text", "Current name: " + existingName);
            cmd.set("#nameInput.Value", existingName);
        }

        // Bind confirm button - captures the text input value
        events.addEventBinding(CustomUIEventBindingType.Activating, "#confirmButton",
            new EventData().append("@animalName", "#nameInput.Value"));

        // Bind cancel button - empty event data (animalName will be null)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#cancelButton", new EventData());
    }

    /**
     * Format animal type for display (e.g., "Cow_Calf" -> "Cow")
     */
    private String formatAnimalType(String rawType) {
        if (rawType == null) return "Animal";

        // Remove baby suffixes
        String formatted = rawType
            .replace("_Calf", "")
            .replace("_Piglet", "")
            .replace("_Chick", "")
            .replace("_Lamb", "")
            .replace("_Foal", "")
            .replace("_", " ");

        // Title case
        if (!formatted.isEmpty()) {
            return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1).toLowerCase();
        }
        return formatted;
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, NametagEventData data) {
        try {
            // Get player to send messages and close page
            Player player = store.getComponent(ref, Player.getComponentType());

            // Check if confirm was clicked (animalName will have a value)
            if (data.animalName != null && !data.animalName.trim().isEmpty()) {
                // Validate and sanitize the name
                String name = AnimalNameGenerator.validateName(data.animalName);

                if (name == null) {
                    if (player != null) {
                        player.sendMessage(Message.raw("Invalid name. Please try again.").color("#FF5555"));
                    }
                    return; // Don't close the page - let them try again
                }

                // Get plugin and taming manager
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin != null) {
                    TamingManager tamingManager = plugin.getTamingManager();
                    if (tamingManager != null) {
                        // Get animal UUID from the stored ref
                        UUID animalUuid = getAnimalUuidFromRef(targetAnimalRef);
                        if (animalUuid != null) {
                            // Parse animal type if possible
                            AnimalType type = AnimalType.fromModelAssetId(animalType);

                            // Check if this is a rename
                            TamedAnimalData existingData = tamingManager.getTamedData(animalUuid);
                            boolean isRename = existingData != null;

                            // Tame or rename the animal
                            TamedAnimalData tamedData = tamingManager.tameAnimal(animalUuid, playerUuid, name, type);

                            if (tamedData != null) {
                                // Send success message
                                if (player != null) {
                                    if (isRename) {
                                        player.sendMessage(Message.raw("Renamed to " + name + "!").color("#55FF55"));
                                    } else {
                                        player.sendMessage(Message.raw(name + " is now yours!").color("#55FF55"));
                                    }
                                }
                                log((isRename ? "Renamed" : "Tamed") + " " + animalType + " as '" + name + "' for player " + playerUuid);
                            } else {
                                if (player != null) {
                                    player.sendMessage(Message.raw("Failed to tame the animal.").color("#FF5555"));
                                }
                            }
                        }
                    }
                }
            } else {
                // Cancel was clicked - just close without taming
                log("Nametag UI cancelled");
            }

            // Close the page
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
        } catch (Exception e) {
            log("Error in handleDataEvent: " + e.getMessage());
        }
    }

    private UUID getAnimalUuidFromRef(Ref<EntityStore> animalRef) {
        try {
            if (animalRef == null) return null;
            Store<EntityStore> store = animalRef.getStore();
            if (store == null) return null;

            var uuidComp = store.getComponent(animalRef,
                com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if (uuidComp != null && uuidComp.getUuid() != null) {
                return uuidComp.getUuid();
            }
        } catch (Exception e) {
            // Entity may have despawned
        }
        return UUID.nameUUIDFromBytes(animalRef.toString().getBytes());
    }

    private void log(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[NametagUI] " + message);
        }
    }

    public Ref<EntityStore> getTargetAnimalRef() {
        return targetAnimalRef;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getAnimalType() {
        return animalType;
    }

    public String getExistingName() {
        return existingName;
    }
}
