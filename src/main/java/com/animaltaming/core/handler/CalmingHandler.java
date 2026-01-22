package com.animaltaming.core.handler;

import com.animaltaming.api.event.TamingEvents.*;
import com.animaltaming.api.model.TamingConfig;
import com.animaltaming.api.model.TamingProgress;
import com.animaltaming.api.model.TamingState;
import com.animaltaming.core.registry.TamingConfigRegistry;
import com.animaltaming.core.service.PlayerLookupService;
import com.animaltaming.system.SystemContext;
import com.animaltaming.util.EventBus;

import java.util.*;

/**
 * Handler for the calming phase of taming.
 * Detects sneaking players near wild animals and manages the calm timer.
 */
public class CalmingHandler {

    private final PlayerLookupService playerLookup;
    private final TamingConfigRegistry configRegistry;
    private final EventBus eventBus;

    // Track in-progress taming by entity ID
    private final Map<Long, TamingProgress> progressByEntityId = new HashMap<>();

    public CalmingHandler(
            PlayerLookupService playerLookup,
            TamingConfigRegistry configRegistry,
            EventBus eventBus
    ) {
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup required");
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus required");
    }

    /**
     * Process calming logic for all tameable animals.
     *
     * @param context the system context
     * @param currentTick the current game tick
     */
    public void process(SystemContext context, long currentTick) {
        // Process animals with existing taming progress
        processExistingProgress(context, currentTick);

        // Check for new calming attempts on wild animals
        checkForNewCalmingAttempts(context, currentTick);

        // Clean up expired calm states
        cleanupExpiredCalm(context, currentTick);
    }

    private void processExistingProgress(SystemContext context, long currentTick) {
        Iterator<Map.Entry<Long, TamingProgress>> iter = progressByEntityId.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<Long, TamingProgress> entry = iter.next();
            long entityId = entry.getKey();
            TamingProgress progress = entry.getValue();

            // Skip if entity no longer exists
            if (!context.entityExists(entityId)) {
                iter.remove();
                continue;
            }

            if (progress.state() == TamingState.CALMING) {
                processCalmingState(context, entityId, progress, currentTick, iter);
            }
        }
    }

    private void processCalmingState(
            SystemContext context,
            long entityId,
            TamingProgress progress,
            long currentTick,
            Iterator<Map.Entry<Long, TamingProgress>> iter
    ) {
        UUID attemptingPlayer = progress.attemptingPlayerId();
        if (attemptingPlayer == null) {
            iter.remove();
            return;
        }

        // Find player using O(1) lookup
        Optional<Long> playerEntityOpt = playerLookup.getEntityId(attemptingPlayer);
        if (playerEntityOpt.isEmpty()) {
            // Player offline - interrupt calming
            interruptCalming(context, entityId, progress, "Player offline", iter);
            return;
        }

        long playerId = playerEntityOpt.get();

        // Check if player is still sneaking
        if (!context.isPlayerSneaking(playerId)) {
            interruptCalming(context, entityId, progress, "Player stopped sneaking", iter);
            return;
        }

        // Check distance
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty()) {
            iter.remove();
            return;
        }

        TamingConfig config = configOpt.get();
        double distance = context.getDistance(playerId, entityId);
        if (distance > config.calmingDistance()) {
            interruptCalming(context, entityId, progress, "Player too far", iter);
            return;
        }

        // Check if calm timer completed
        long calmingDuration = currentTick - progress.calmingStartTick();
        if (calmingDuration >= config.calmingTimeTicks()) {
            // Transition to CALMED state
            TamingProgress updated = progress.withCalmed(currentTick, config.calmDurationTicks());
            progressByEntityId.put(entityId, updated);

            eventBus.publish(new AnimalCalmedEvent(
                    playerId,
                    attemptingPlayer,
                    entityId,
                    progress.animalId(),
                    progress.speciesId()
            ));

            context.sendMessage(playerId, "The " + config.speciesId() + " has calmed down!");
            context.spawnParticle(context.getEntityX(entityId), context.getEntityY(entityId) + 1, context.getEntityZ(entityId), "heart");
        }
    }

    private void interruptCalming(
            SystemContext context,
            long entityId,
            TamingProgress progress,
            String reason,
            Iterator<Map.Entry<Long, TamingProgress>> iter
    ) {
        iter.remove();

        eventBus.publish(new TamingStateChangedEvent(
                entityId,
                progress.animalId(),
                progress.state(),
                TamingState.WILD
        ));
    }

    private void checkForNewCalmingAttempts(SystemContext context, long currentTick) {
        // Get all tameable animals that don't have progress
        for (SystemContext.TameableAnimalInfo animal : context.getTameableAnimals()) {
            if (progressByEntityId.containsKey(animal.entityId())) {
                continue; // Already has progress
            }

            Optional<TamingConfig> configOpt = configRegistry.get(animal.speciesId());
            if (configOpt.isEmpty()) {
                continue;
            }

            TamingConfig config = configOpt.get();
            checkForSneakingPlayers(context, animal, config, currentTick);
        }
    }

    private void checkForSneakingPlayers(
            SystemContext context,
            SystemContext.TameableAnimalInfo animal,
            TamingConfig config,
            long currentTick
    ) {
        double animalX = context.getEntityX(animal.entityId());
        double animalY = context.getEntityY(animal.entityId());
        double animalZ = context.getEntityZ(animal.entityId());

        List<Long> nearbyPlayers = context.getPlayersInRadius(animalX, animalY, animalZ, config.calmingDistance());

        for (long playerId : nearbyPlayers) {
            if (!context.isPlayerSneaking(playerId)) {
                continue;
            }

            // Start calming
            Optional<UUID> playerUuidOpt = playerLookup.getPlayerUUID(playerId);
            if (playerUuidOpt.isEmpty()) {
                continue;
            }

            UUID playerUuid = playerUuidOpt.get();
            TamingProgress progress = TamingProgress.startCalming(
                    animal.animalId(),
                    animal.speciesId(),
                    playerUuid,
                    currentTick
            );

            progressByEntityId.put(animal.entityId(), progress);

            eventBus.publish(new TamingStartedEvent(
                    playerId,
                    playerUuid,
                    animal.entityId(),
                    animal.animalId(),
                    animal.speciesId()
            ));

            context.sendMessage(playerId, "You begin calming the " + config.speciesId() + "...");

            // Only one player can calm at a time
            break;
        }
    }

    private void cleanupExpiredCalm(SystemContext context, long currentTick) {
        Iterator<Map.Entry<Long, TamingProgress>> iter = progressByEntityId.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<Long, TamingProgress> entry = iter.next();
            TamingProgress progress = entry.getValue();

            if (progress.state() == TamingState.CALMED && progress.isCalmExpired(currentTick)) {
                eventBus.publish(new CalmExpiredEvent(
                        entry.getKey(),
                        progress.animalId(),
                        progress.speciesId()
                ));

                eventBus.publish(new TamingStateChangedEvent(
                        entry.getKey(),
                        progress.animalId(),
                        TamingState.CALMED,
                        TamingState.WILD
                ));

                iter.remove();
            }
        }
    }

    /**
     * Get current taming progress for an entity.
     */
    public Optional<TamingProgress> getProgress(long entityId) {
        return Optional.ofNullable(progressByEntityId.get(entityId));
    }

    /**
     * Update taming progress (called by other handlers).
     */
    public void setProgress(long entityId, TamingProgress progress) {
        if (progress == null) {
            progressByEntityId.remove(entityId);
        } else {
            progressByEntityId.put(entityId, progress);
        }
    }

    /**
     * Remove taming progress.
     */
    public void removeProgress(long entityId) {
        progressByEntityId.remove(entityId);
    }

    /**
     * Check if an entity has taming progress.
     */
    public boolean hasProgress(long entityId) {
        return progressByEntityId.containsKey(entityId);
    }
}
