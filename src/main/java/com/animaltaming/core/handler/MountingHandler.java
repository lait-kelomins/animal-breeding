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
 * Handler for mount-based trust building.
 * Tracks time spent riding mountable animals and awards trust.
 */
public class MountingHandler {

    private final PlayerLookupService playerLookup;
    private final TamingConfigRegistry configRegistry;
    private final EventBus eventBus;
    private final CalmingHandler calmingHandler;

    // Track last processed tick for mount trust calculation
    private final Map<Long, Integer> lastProcessedSecond = new HashMap<>();

    public MountingHandler(
            PlayerLookupService playerLookup,
            TamingConfigRegistry configRegistry,
            EventBus eventBus,
            CalmingHandler calmingHandler
    ) {
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup required");
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus required");
        this.calmingHandler = Objects.requireNonNull(calmingHandler, "calmingHandler required");
    }

    /**
     * Process mount trust for all animals being ridden.
     *
     * @param context the system context
     * @param currentTick the current game tick
     */
    public void process(SystemContext context, long currentTick) {
        int tickRate = context.getTickRate();

        // Check all animals with taming progress
        for (SystemContext.TameableAnimalInfo animal : context.getTameableAnimals()) {
            Optional<TamingProgress> progressOpt = calmingHandler.getProgress(animal.entityId());
            if (progressOpt.isEmpty()) {
                continue;
            }

            TamingProgress progress = progressOpt.get();
            processMountProgress(context, animal.entityId(), progress, currentTick, tickRate);
        }
    }

    private void processMountProgress(
            SystemContext context,
            long entityId,
            TamingProgress progress,
            long currentTick,
            int tickRate
    ) {
        TamingState state = progress.state();

        // Handle CALMED animals being mounted
        if (state == TamingState.CALMED) {
            checkForNewMount(context, entityId, progress, currentTick);
            return;
        }

        // Handle BONDING_MOUNT state - track time and award trust
        if (state == TamingState.BONDING_MOUNT) {
            processBondingMount(context, entityId, progress, currentTick, tickRate);
        }
    }

    private void checkForNewMount(
            SystemContext context,
            long entityId,
            TamingProgress progress,
            long currentTick
    ) {
        // Check if species is mountable
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty() || !configOpt.get().canBeMounted()) {
            return;
        }

        // Check if anyone is riding this animal
        List<Long> riders = context.getRiders(entityId);
        if (riders.isEmpty()) {
            return;
        }

        // Check if the rider is the taming player
        for (long riderId : riders) {
            Optional<UUID> riderUuidOpt = playerLookup.getPlayerUUID(riderId);
            if (riderUuidOpt.isEmpty()) {
                continue;
            }

            if (riderUuidOpt.get().equals(progress.attemptingPlayerId())) {
                // Start mount bonding
                TamingProgress updated = progress.withBondingMount(currentTick);
                calmingHandler.setProgress(entityId, updated);
                lastProcessedSecond.put(entityId, 0);

                eventBus.publish(new TamingStateChangedEvent(
                        entityId,
                        progress.animalId(),
                        TamingState.CALMED,
                        TamingState.BONDING_MOUNT
                ));

                context.sendMessage(riderId, "You begin bonding with the " + progress.speciesId() + " while riding...");
                return;
            }
        }
    }

    private void processBondingMount(
            SystemContext context,
            long entityId,
            TamingProgress progress,
            long currentTick,
            int tickRate
    ) {
        // Check if still being ridden by the taming player
        List<Long> riders = context.getRiders(entityId);
        boolean validRiderFound = false;

        for (long riderId : riders) {
            Optional<UUID> riderUuidOpt = playerLookup.getPlayerUUID(riderId);
            if (riderUuidOpt.isPresent() && riderUuidOpt.get().equals(progress.attemptingPlayerId())) {
                validRiderFound = true;
                break;
            }
        }

        if (!validRiderFound) {
            // Player dismounted - reset to CALMED
            TamingProgress updated = progress.withMountReset();
            calmingHandler.setProgress(entityId, updated);
            lastProcessedSecond.remove(entityId);

            eventBus.publish(new TamingStateChangedEvent(
                    entityId,
                    progress.animalId(),
                    TamingState.BONDING_MOUNT,
                    TamingState.CALMED
            ));
            return;
        }

        // Calculate seconds ridden
        double secondsRidden = progress.getMountDurationSeconds(currentTick, tickRate);
        int wholeSeconds = (int) secondsRidden;

        // Get last processed second
        int lastSecond = lastProcessedSecond.getOrDefault(entityId, 0);

        // Award trust for each new second
        if (wholeSeconds > lastSecond) {
            int newSeconds = wholeSeconds - lastSecond;

            Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
            if (configOpt.isEmpty()) {
                return;
            }

            TamingConfig config = configOpt.get();
            int trustGain = newSeconds * config.trustPerMountSecond();

            if (trustGain > 0) {
                int oldTrust = progress.trustLevel();
                TamingProgress updated = progress.withTrustGain(trustGain, currentTick);
                calmingHandler.setProgress(entityId, updated);
                lastProcessedSecond.put(entityId, wholeSeconds);

                eventBus.publish(new TrustChangedEvent(
                        entityId,
                        progress.animalId(),
                        progress.speciesId(),
                        oldTrust,
                        updated.trustLevel(),
                        "mounting"
                ));

                // Periodic feedback
                if (wholeSeconds % 5 == 0) {
                    Optional<Long> playerIdOpt = playerLookup.getEntityId(progress.attemptingPlayerId());
                    if (playerIdOpt.isPresent()) {
                        int remaining = config.requiredTrustLevel() - updated.trustLevel();
                        if (remaining > 0) {
                            context.sendMessage(playerIdOpt.get(),
                                    "Trust: " + updated.trustLevel() + "/" + config.requiredTrustLevel());
                        }
                    }
                }
            }
        }
    }

    /**
     * Clean up tracking data when an animal's progress is removed.
     */
    public void cleanupEntity(long entityId) {
        lastProcessedSecond.remove(entityId);
    }
}
