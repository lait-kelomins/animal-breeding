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
import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.AnimalNameGenerator;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.NameplateUtil;

import java.util.List;
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

        // Determine initial value for the text input
        if (existingName != null && !existingName.isEmpty() && !existingName.equalsIgnoreCase(NameplateUtil.UNDEFINED_NAME)) {
            // Renaming - show existing name
            cmd.set("#subtitleLabel.Text", "Current name: " + existingName);
            cmd.set("#nameInput.Value", existingName);
        } else {
            // New taming - pre-fill with a suggested name
            AnimalType type = AnimalType.fromModelAssetId(animalType);
            List<String> suggestedNames = AnimalNameGenerator.getSuggestedNames(type);
            if (!suggestedNames.isEmpty()) {
                cmd.set("#nameInput.Value", suggestedNames.get(0));
            }
        }

        // Bind confirm button - captures the text input value
        events.addEventBinding(CustomUIEventBindingType.Activating, "#confirmButton",
            new EventData().append("@animalName", "#nameInput.Value"));

        // Bind cancel button - empty event data
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
            Player player = store.getComponent(ref, EcsReflectionUtil.PLAYER_TYPE);

            // Check if confirm was clicked (animalName will have a value)
            if (data.animalName != null && !data.animalName.trim().isEmpty() && !data.animalName.equalsIgnoreCase(NameplateUtil.UNDEFINED_NAME)) {
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

                            boolean success;
                            if (isRename) {
                                // Rename existing tamed animal
                                success = tamingManager.renameAnimal(animalUuid, playerUuid, name);
                            } else {
                                // Get position for taming
                                double px = 0, py = 0, pz = 0;
                                try {
                                    com.hypixel.hytale.math.vector.Vector3d pos = plugin.getPositionFromRef(targetAnimalRef);
                                    if (pos != null) {
                                        px = pos.getX();
                                        py = pos.getY();
                                        pz = pos.getZ();
                                    }
                                } catch (Exception e) {
                                    // Silent - position not available
                                }

                                // Check if animal already has BreedingData (e.g., if it's a baby from breeding)
                                GrowthStage existingGrowthStage = null;
                                long existingBirthTime = 0;
                                BreedingManager breedingManager = plugin.getBreedingManager();
                                if (breedingManager != null) {
                                    log("Looking up BreedingData for UUID: " + animalUuid);
                                    BreedingData existingBreedingData = breedingManager.getData(animalUuid);
                                    if (existingBreedingData != null) {
                                        log("BreedingData lookup result: found (growthStage=" + existingBreedingData.getGrowthStage() + ")");
                                    } else {
                                        log("BreedingData lookup result: NOT FOUND by UUID");
                                        log(breedingManager.getRegisteredBabiesDebug());

                                        // UUID lookup failed - try ref-based lookup for babies
                                        // The UUID can change between baby spawn and naming due to Ref instability
                                        existingBreedingData = breedingManager.findBabyByRef(targetAnimalRef);
                                        if (existingBreedingData != null) {
                                            log("Found baby via ref-based lookup (UUID mismatch resolved)");
                                            log("Resolved growthStage=" + existingBreedingData.getGrowthStage());
                                        }
                                    }
                                    if (existingBreedingData != null && existingBreedingData.getGrowthStage() != GrowthStage.ADULT) {
                                        existingGrowthStage = existingBreedingData.getGrowthStage();
                                        existingBirthTime = existingBreedingData.getBirthTime();
                                        log("Preserving existing growth stage: " + existingGrowthStage);
                                    }
                                }

                                // Also check model asset ID for baby variants (e.g., "_Calf", "_Piglet")
                                if (existingGrowthStage == null && animalType != null) {
                                    if (animalType.contains("_Calf") || animalType.contains("_Piglet") ||
                                        animalType.contains("_Chick") || animalType.contains("_Lamb") ||
                                        animalType.contains("_Foal") || animalType.contains("_Bunny")) {
                                        existingGrowthStage = GrowthStage.BABY;
                                        existingBirthTime = System.currentTimeMillis();
                                        log("Detected baby from model ID: " + animalType);
                                    }
                                }

                                // Tame new animal with position and correct growth stage
                                GrowthStage stageToUse = existingGrowthStage != null ? existingGrowthStage : GrowthStage.ADULT;
                                TamedAnimalData tamedData = tamingManager.tameAnimal(animalUuid, playerUuid, name, type, targetAnimalRef, px, py, pz, stageToUse);
                                success = tamedData != null;

                                // Set birth time if this is a baby
                                if (success && tamedData != null && existingGrowthStage != null && existingBirthTime > 0) {
                                    tamedData.setBirthTime(existingBirthTime);
                                    tamingManager.notifyDataChanged();
                                    log("Tamed baby with growth stage: " + stageToUse);
                                }
                            }

                            if (success) {
                                // Set the nameplate above the animal
                                NameplateUtil.setEntityNameplate(targetAnimalRef, name);

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
        if (animalRef == null) return null;
        // Delegate to EcsReflectionUtil for consistent UUID handling across all systems
        // This uses UUIDComponent first (stable), falling back to ref-based UUID
        return EcsReflectionUtil.getUuidFromRef(animalRef);
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
