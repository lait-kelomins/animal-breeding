package com.animaltaming.core.handler;

import com.animaltaming.api.event.TamingEvents.*;
import com.animaltaming.api.model.BehaviorMode;
import com.animaltaming.api.model.TamedAnimal;
import com.animaltaming.core.registry.TamedAnimalRegistry;
import com.animaltaming.core.service.PlayerLookupService;
import com.animaltaming.system.SystemContext;
import com.animaltaming.util.EventBus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler for tamed animal behavior (follow/stay modes).
 * Processes movement logic and mode toggles.
 */
public class BehaviorHandler {

    private static final double FOLLOW_ACTIVATION_DISTANCE = 6.0;
    private static final double FOLLOW_TARGET_DISTANCE = 3.0;
    private static final double STAY_WANDER_RADIUS = 5.0;

    private final PlayerLookupService playerLookup;
    private final TamedAnimalRegistry animalRegistry;
    private final EventBus eventBus;

    public BehaviorHandler(
            PlayerLookupService playerLookup,
            TamedAnimalRegistry animalRegistry,
            EventBus eventBus
    ) {
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup required");
        this.animalRegistry = Objects.requireNonNull(animalRegistry, "animalRegistry required");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus required");
    }

    /**
     * Process behavior for all tamed animals.
     *
     * @param context the system context
     */
    public void process(SystemContext context) {
        for (TamedAnimal animal : animalRegistry.getAll()) {
            // Find entity ID for this animal
            // In a real implementation, we'd track entity IDs
            // For now, we assume the registry maintains entity mappings
            processAnimalBehavior(context, animal);
        }
    }

    private void processAnimalBehavior(SystemContext context, TamedAnimal animal) {
        switch (animal.mode()) {
            case FOLLOW -> processFollowBehavior(context, animal);
            case STAY -> processStayBehavior(context, animal);
        }
    }

    private void processFollowBehavior(SystemContext context, TamedAnimal animal) {
        // Find owner using O(1) lookup
        Optional<Long> ownerEntityOpt = playerLookup.getEntityId(animal.ownerId());
        if (ownerEntityOpt.isEmpty()) {
            // Owner offline - animal waits
            return;
        }

        long ownerEntityId = ownerEntityOpt.get();

        // We need to find the animal's entity ID
        // This would come from the registry's entity mapping in a full implementation
        // For now, we'll use a context method to find by animal ID
        Optional<Long> animalEntityOpt = context.getEntityIdForAnimal(animal.id());
        if (animalEntityOpt.isEmpty()) {
            return;
        }

        long animalEntityId = animalEntityOpt.get();

        double animalX = context.getEntityX(animalEntityId);
        double animalY = context.getEntityY(animalEntityId);
        double animalZ = context.getEntityZ(animalEntityId);

        double ownerX = context.getEntityX(ownerEntityId);
        double ownerY = context.getEntityY(ownerEntityId);
        double ownerZ = context.getEntityZ(ownerEntityId);

        double distance = Math.sqrt(
                Math.pow(animalX - ownerX, 2) +
                Math.pow(animalY - ownerY, 2) +
                Math.pow(animalZ - ownerZ, 2)
        );

        // Teleport if too far
        if (distance > animal.maxFollowDistance()) {
            double teleX = ownerX + (Math.random() - 0.5) * 4;
            double teleY = ownerY;
            double teleZ = ownerZ + (Math.random() - 0.5) * 4;

            context.teleport(animalEntityId, teleX, teleY, teleZ);

            eventBus.publish(new AnimalTeleportedEvent(
                    animalEntityId,
                    animal.id(),
                    animal.ownerId(),
                    animalX, animalY, animalZ,
                    teleX, teleY, teleZ
            ));

            context.spawnParticle(teleX, teleY + 0.5, teleZ, "portal");
            return;
        }

        // ACTIVE FOLLOW: Move toward owner if within activation range but not close enough
        if (distance > FOLLOW_TARGET_DISTANCE && distance <= FOLLOW_ACTIVATION_DISTANCE) {
            // Default walking speed: ~4 blocks/second for most animals
            // Could be made configurable per species in future
            double followSpeed = 4.0;
            context.moveEntityToward(animalEntityId, ownerX, ownerY, ownerZ, followSpeed);
        }
        // If within FOLLOW_TARGET_DISTANCE (3 blocks), animal stays in place
    }

    private void processStayBehavior(SystemContext context, TamedAnimal animal) {
        Optional<Long> animalEntityOpt = context.getEntityIdForAnimal(animal.id());
        if (animalEntityOpt.isEmpty()) {
            return;
        }

        long animalEntityId = animalEntityOpt.get();

        // Allow movement while mounted (e.g., player riding the pet)
        java.util.List<Long> riders = context.getRiders(animalEntityId);
        if (!riders.isEmpty()) {
            return;
        }

        double animalX = context.getEntityX(animalEntityId);
        double animalY = context.getEntityY(animalEntityId);
        double animalZ = context.getEntityZ(animalEntityId);

        double dx = animalX - animal.homeX();
        double dy = animalY - animal.homeY();
        double dz = animalZ - animal.homeZ();
        double distanceFromHome = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // FREEZE BEHAVIOR: If moved at all from home, teleport back immediately
        // Threshold of 0.5 blocks accounts for floating point and minor physics adjustments
        if (distanceFromHome > 0.5) {
            context.teleport(animalEntityId, animal.homeX(), animal.homeY(), animal.homeZ());
        }
    }

    /**
     * Toggle an animal's behavior mode.
     *
     * @param context the system context
     * @param animalId the animal's unique ID
     * @param playerId the player attempting to toggle (must be owner)
     * @param x current X position
     * @param y current Y position
     * @param z current Z position
     * @return true if toggled successfully
     */
    public boolean toggleMode(
            SystemContext context,
            UUID animalId,
            UUID playerId,
            double x, double y, double z
    ) {
        Optional<TamedAnimal> animalOpt = animalRegistry.getByAnimalId(animalId);
        if (animalOpt.isEmpty()) {
            return false;
        }

        TamedAnimal animal = animalOpt.get();

        // Verify ownership
        if (!animal.isOwnedBy(playerId)) {
            Optional<Long> playerEntityOpt = playerLookup.getEntityId(playerId);
            if (playerEntityOpt.isPresent()) {
                context.sendMessage(playerEntityOpt.get(), "This is not your pet!");
            }
            return false;
        }

        BehaviorMode oldMode = animal.mode();
        BehaviorMode newMode = oldMode.toggle();

        // Update animal
        TamedAnimal updated;
        if (newMode == BehaviorMode.STAY) {
            // Set home position to current location
            updated = animal.withToggledMode().withHome(x, y, z);
        } else {
            updated = animal.withToggledMode();
        }

        animalRegistry.update(updated);

        eventBus.publish(new BehaviorModeChangedEvent(
                0, // Entity ID would be looked up in full implementation
                animalId,
                playerId,
                oldMode,
                newMode
        ));

        // Notify player
        Optional<Long> playerEntityOpt = playerLookup.getEntityId(playerId);
        if (playerEntityOpt.isPresent()) {
            context.sendMessage(playerEntityOpt.get(),
                    "Pet is now " + newMode.getDisplayName().toLowerCase() + ".");

            // Visual feedback
            context.spawnParticle(x, y + 1, z, newMode == BehaviorMode.FOLLOW ? "note" : "smoke");
        }

        return true;
    }

    /**
     * Handle petting interaction with a tamed animal.
     */
    public void handlePetInteraction(SystemContext context, long playerEntityId, long animalEntityId) {
        Optional<UUID> playerUuidOpt = playerLookup.getPlayerUUID(playerEntityId);
        if (playerUuidOpt.isEmpty()) {
            return;
        }

        Optional<TamedAnimal> animalOpt = animalRegistry.getByEntityId(animalEntityId);
        if (animalOpt.isEmpty()) {
            return;
        }

        TamedAnimal animal = animalOpt.get();

        // Pet visual/sound feedback
        double x = context.getEntityX(animalEntityId);
        double y = context.getEntityY(animalEntityId);
        double z = context.getEntityZ(animalEntityId);

        context.spawnParticle(x, y + 1, z, "heart");
        context.playSound(x, y, z, "purr");

        eventBus.publish(new AnimalPettedEvent(
                playerEntityId,
                playerUuidOpt.get(),
                animalEntityId,
                animal.id(),
                animal.speciesId()
        ));
    }
}
