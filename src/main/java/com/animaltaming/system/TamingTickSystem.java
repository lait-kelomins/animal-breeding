package com.animaltaming.system;

import com.animaltaming.core.handler.*;
import com.animaltaming.core.service.DefaultTamingService;
import com.animaltaming.core.service.PlayerLookupService;

import java.util.Objects;

/**
 * Single thin system that orchestrates all taming logic.
 * Delegates to handlers - contains no business logic itself.
 */
public class TamingTickSystem implements GameSystem {

    private final PlayerLookupService playerLookup;
    private final CalmingHandler calmingHandler;
    private final FeedingHandler feedingHandler;
    private final MountingHandler mountingHandler;
    private final BehaviorHandler behaviorHandler;
    private final DefaultTamingService tamingService;

    private boolean enabled = true;

    public TamingTickSystem(
            PlayerLookupService playerLookup,
            CalmingHandler calmingHandler,
            FeedingHandler feedingHandler,
            MountingHandler mountingHandler,
            BehaviorHandler behaviorHandler,
            DefaultTamingService tamingService
    ) {
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup required");
        this.calmingHandler = Objects.requireNonNull(calmingHandler, "calmingHandler required");
        this.feedingHandler = Objects.requireNonNull(feedingHandler, "feedingHandler required");
        this.mountingHandler = Objects.requireNonNull(mountingHandler, "mountingHandler required");
        this.behaviorHandler = Objects.requireNonNull(behaviorHandler, "behaviorHandler required");
        this.tamingService = Objects.requireNonNull(tamingService, "tamingService required");
    }

    @Override
    public void update(SystemContext context, float deltaTime) {
        long currentTick = context.getCurrentTick();

        // Step 1: Refresh player cache ONCE at the start of the tick
        // This ensures O(1) lookups for the rest of the tick
        playerLookup.refreshCache(context);

        // Step 2: Update service context
        tamingService.setContext(context);

        // Step 3: Process calming (wild animals -> calmed)
        calmingHandler.process(context, currentTick);

        // Step 4: Process feeding interactions (calmed -> bonding -> tamed)
        feedingHandler.process(context, currentTick);

        // Step 5: Process mount trust (for mountable species)
        mountingHandler.process(context, currentTick);

        // Step 6: Check for taming completion
        checkTamingCompletion(context, currentTick);

        // Step 7: Process tamed animal behavior (follow/stay)
        behaviorHandler.process(context);
    }

    private void checkTamingCompletion(SystemContext context, long currentTick) {
        // Check all animals with taming progress
        for (SystemContext.TameableAnimalInfo animal : context.getTameableAnimals()) {
            if (!tamingService.canCompleteTaming(animal.entityId())) {
                continue;
            }

            // Get owner name from player lookup
            var progressOpt = tamingService.getTamingProgress(animal.entityId());
            if (progressOpt.isEmpty()) {
                continue;
            }

            var progress = progressOpt.get();
            var playerEntityOpt = playerLookup.getEntityId(progress.attemptingPlayerId());
            if (playerEntityOpt.isEmpty()) {
                continue;
            }

            // Get player name from context
            String ownerName = getPlayerName(context, progress.attemptingPlayerId());

            // Complete taming
            double x = context.getEntityX(animal.entityId());
            double y = context.getEntityY(animal.entityId());
            double z = context.getEntityZ(animal.entityId());

            tamingService.completeTaming(animal.entityId(), ownerName, x, y, z);

            // Notify player
            context.sendMessage(playerEntityOpt.get(),
                    "Congratulations! You have tamed the " + animal.speciesId() + "!");
            context.spawnParticle(x, y + 1, z, "firework");
        }
    }

    private String getPlayerName(SystemContext context, java.util.UUID playerId) {
        for (SystemContext.PlayerInfo player : context.getAllPlayers()) {
            if (player.uuid().equals(playerId)) {
                return player.name();
            }
        }
        return "Unknown";
    }

    @Override
    public int priority() {
        return 10; // Run after core systems
    }

    @Override
    public String getName() {
        return "TamingTickSystem";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable or disable the system.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
