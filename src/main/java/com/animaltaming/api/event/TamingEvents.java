package com.animaltaming.api.event;

import com.animaltaming.api.model.BehaviorMode;
import com.animaltaming.api.model.TamingState;

import java.util.UUID;

/**
 * All taming-related events as inner record classes.
 * Events are published via EventBus for decoupled system communication.
 */
public final class TamingEvents {

    private TamingEvents() {
        // Utility class - no instantiation
    }

    /**
     * Fired when a player begins calming an animal.
     */
    public record TamingStartedEvent(
            long playerEntityId,
            UUID playerId,
            long animalEntityId,
            UUID animalId,
            String speciesId
    ) {}

    /**
     * Fired when an animal transitions to CALMED state.
     */
    public record AnimalCalmedEvent(
            long playerEntityId,
            UUID playerId,
            long animalEntityId,
            UUID animalId,
            String speciesId
    ) {}

    /**
     * Fired when calm state expires without taming completing.
     */
    public record CalmExpiredEvent(
            long animalEntityId,
            UUID animalId,
            String speciesId
    ) {}

    /**
     * Fired when trust level changes (from feeding or mounting).
     */
    public record TrustChangedEvent(
            long animalEntityId,
            UUID animalId,
            String speciesId,
            int oldTrust,
            int newTrust,
            String reason
    ) {}

    /**
     * Fired when taming state changes.
     */
    public record TamingStateChangedEvent(
            long animalEntityId,
            UUID animalId,
            TamingState oldState,
            TamingState newState
    ) {}

    /**
     * Fired when an animal is successfully tamed.
     */
    public record AnimalTamedEvent(
            UUID playerId,
            String ownerName,
            long animalEntityId,
            UUID animalId,
            String speciesId
    ) {}

    /**
     * Fired when a tamed animal's behavior mode changes.
     */
    public record BehaviorModeChangedEvent(
            long animalEntityId,
            UUID animalId,
            UUID ownerId,
            BehaviorMode oldMode,
            BehaviorMode newMode
    ) {}

    /**
     * Fired when a tamed animal teleports to its owner.
     */
    public record AnimalTeleportedEvent(
            long animalEntityId,
            UUID animalId,
            UUID ownerId,
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ
    ) {}

    /**
     * Fired when a tamed animal is lost (died, released, or despawned).
     */
    public record TamedAnimalLostEvent(
            UUID animalId,
            UUID ownerId,
            String speciesId,
            String reason
    ) {}

    /**
     * Fired when a player interacts with a tamed animal (pet action).
     */
    public record AnimalPettedEvent(
            long playerEntityId,
            UUID playerId,
            long animalEntityId,
            UUID animalId,
            String speciesId
    ) {}
}
