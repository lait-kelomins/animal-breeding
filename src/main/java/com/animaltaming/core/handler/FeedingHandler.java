package com.animaltaming.core.handler;

import com.animaltaming.api.event.TamingEvents.*;
import com.animaltaming.api.model.TamingConfig;
import com.animaltaming.api.model.TamingProgress;
import com.animaltaming.api.model.TamingState;
import com.animaltaming.core.registry.TamingConfigRegistry;
import com.animaltaming.core.service.PlayerLookupService;
import com.animaltaming.system.SystemContext;
import com.animaltaming.util.EventBus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler for feeding interactions during taming.
 * Processes player interactions with calmed animals to build trust.
 */
public class FeedingHandler {

    private final PlayerLookupService playerLookup;
    private final TamingConfigRegistry configRegistry;
    private final EventBus eventBus;
    private final CalmingHandler calmingHandler;

    public FeedingHandler(
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
     * Process feeding interactions for all calmed animals.
     *
     * @param context the system context
     * @param currentTick the current game tick
     */
    public void process(SystemContext context, long currentTick) {
        // Check for feeding interactions
        for (SystemContext.InteractionEvent interaction : context.getPendingInteractions()) {
            processInteraction(context, interaction, currentTick);
        }
    }

    private void processInteraction(
            SystemContext context,
            SystemContext.InteractionEvent interaction,
            long currentTick
    ) {
        long playerId = interaction.playerEntityId();
        long targetId = interaction.targetEntityId();

        // Check if target has taming progress in CALMED or BONDING_FEED state
        Optional<TamingProgress> progressOpt = calmingHandler.getProgress(targetId);
        if (progressOpt.isEmpty()) {
            return;
        }

        TamingProgress progress = progressOpt.get();
        TamingState state = progress.state();

        // Only accept feeding in CALMED or BONDING_FEED states
        if (state != TamingState.CALMED && state != TamingState.BONDING_FEED) {
            return;
        }

        // Check if the interacting player is the one who calmed the animal
        Optional<UUID> playerUuidOpt = playerLookup.getPlayerUUID(playerId);
        if (playerUuidOpt.isEmpty() || !playerUuidOpt.get().equals(progress.attemptingPlayerId())) {
            context.sendMessage(playerId, "This animal is being calmed by another player!");
            return;
        }

        // Get held item
        Optional<String> heldItemOpt = context.getHeldItemId(playerId);
        if (heldItemOpt.isEmpty()) {
            return;
        }

        String heldItem = heldItemOpt.get();

        // Check if species accepts this food
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty()) {
            return;
        }

        TamingConfig config = configOpt.get();
        if (!config.acceptsFood(heldItem)) {
            context.sendMessage(playerId, "The " + config.speciesId() + " doesn't want that food.");
            return;
        }

        // Consume the food
        if (!context.consumeHeldItem(playerId)) {
            return;
        }

        // Add trust
        int oldTrust = progress.trustLevel();
        int trustGain = config.trustPerFeed();

        TamingProgress updated;
        if (state == TamingState.CALMED) {
            // Transition to BONDING_FEED
            updated = progress.withBondingFeed().withTrustGain(trustGain, currentTick);
        } else {
            updated = progress.withTrustGain(trustGain, currentTick);
        }

        calmingHandler.setProgress(targetId, updated);

        // Publish trust change event
        eventBus.publish(new TrustChangedEvent(
                targetId,
                progress.animalId(),
                progress.speciesId(),
                oldTrust,
                updated.trustLevel(),
                "feeding"
        ));

        // Visual feedback
        double x = context.getEntityX(targetId);
        double y = context.getEntityY(targetId);
        double z = context.getEntityZ(targetId);
        context.spawnParticle(x, y + 1, z, "heart");
        context.playSound(x, y, z, "eat");

        // Notify player
        int remaining = config.requiredTrustLevel() - updated.trustLevel();
        if (remaining > 0) {
            context.sendMessage(playerId, "Trust: " + updated.trustLevel() + "/" + config.requiredTrustLevel());
        }
    }

    /**
     * Attempt to feed an animal directly (called by service).
     *
     * @return updated progress, or empty if feeding failed
     */
    public Optional<TamingProgress> feed(
            SystemContext context,
            long animalEntityId,
            UUID playerId,
            String foodId,
            long currentTick
    ) {
        Optional<TamingProgress> progressOpt = calmingHandler.getProgress(animalEntityId);
        if (progressOpt.isEmpty()) {
            return Optional.empty();
        }

        TamingProgress progress = progressOpt.get();

        // Verify player
        if (!playerId.equals(progress.attemptingPlayerId())) {
            return Optional.empty();
        }

        // Verify state
        TamingState state = progress.state();
        if (state != TamingState.CALMED && state != TamingState.BONDING_FEED) {
            return Optional.empty();
        }

        // Verify food
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty() || !configOpt.get().acceptsFood(foodId)) {
            return Optional.empty();
        }

        TamingConfig config = configOpt.get();
        int oldTrust = progress.trustLevel();

        TamingProgress updated;
        if (state == TamingState.CALMED) {
            updated = progress.withBondingFeed().withTrustGain(config.trustPerFeed(), currentTick);
        } else {
            updated = progress.withTrustGain(config.trustPerFeed(), currentTick);
        }

        calmingHandler.setProgress(animalEntityId, updated);

        eventBus.publish(new TrustChangedEvent(
                animalEntityId,
                progress.animalId(),
                progress.speciesId(),
                oldTrust,
                updated.trustLevel(),
                "feeding"
        ));

        return Optional.of(updated);
    }
}
