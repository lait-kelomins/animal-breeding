package com.animaltaming.core.service;

import com.animaltaming.api.TamingService;
import com.animaltaming.api.event.TamingEvents.*;
import com.animaltaming.api.model.*;
import com.animaltaming.core.handler.BehaviorHandler;
import com.animaltaming.core.handler.CalmingHandler;
import com.animaltaming.core.handler.FeedingHandler;
import com.animaltaming.core.registry.TamedAnimalRegistry;
import com.animaltaming.core.registry.TamingConfigRegistry;
import com.animaltaming.system.SystemContext;
import com.animaltaming.util.EventBus;

import java.util.*;

/**
 * Default implementation of TamingService.
 * Orchestrates handlers and provides the public API.
 */
public class DefaultTamingService implements TamingService {

    private final TamedAnimalRegistry animalRegistry;
    private final TamingConfigRegistry configRegistry;
    private final PlayerLookupService playerLookup;
    private final EventBus eventBus;
    private final CalmingHandler calmingHandler;
    private final FeedingHandler feedingHandler;
    private final BehaviorHandler behaviorHandler;

    // Current context (set each tick)
    private SystemContext currentContext;

    public DefaultTamingService(
            TamedAnimalRegistry animalRegistry,
            TamingConfigRegistry configRegistry,
            PlayerLookupService playerLookup,
            EventBus eventBus,
            CalmingHandler calmingHandler,
            FeedingHandler feedingHandler,
            BehaviorHandler behaviorHandler
    ) {
        this.animalRegistry = Objects.requireNonNull(animalRegistry, "animalRegistry required");
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup required");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus required");
        this.calmingHandler = Objects.requireNonNull(calmingHandler, "calmingHandler required");
        this.feedingHandler = Objects.requireNonNull(feedingHandler, "feedingHandler required");
        this.behaviorHandler = Objects.requireNonNull(behaviorHandler, "behaviorHandler required");
    }

    /**
     * Set the current context for this tick.
     * Must be called before using the service.
     */
    public void setContext(SystemContext context) {
        this.currentContext = context;
    }

    @Override
    public Optional<TamingProgress> startCalming(long animalEntityId, UUID animalId, String speciesId, UUID playerId) {
        // Verify species is valid
        if (!configRegistry.contains(speciesId)) {
            return Optional.empty();
        }

        // Check if already has progress
        if (calmingHandler.hasProgress(animalEntityId)) {
            return Optional.empty();
        }

        long currentTick = currentContext.getCurrentTick();
        TamingProgress progress = TamingProgress.startCalming(animalId, speciesId, playerId, currentTick);
        calmingHandler.setProgress(animalEntityId, progress);

        return Optional.of(progress);
    }

    @Override
    public Optional<TamingProgress> feed(long animalEntityId, UUID playerId, String foodId) {
        long currentTick = currentContext.getCurrentTick();
        return feedingHandler.feed(currentContext, animalEntityId, playerId, foodId, currentTick);
    }

    @Override
    public Optional<TamedAnimal> completeTaming(long animalEntityId, String ownerName, double x, double y, double z) {
        Optional<TamingProgress> progressOpt = calmingHandler.getProgress(animalEntityId);
        if (progressOpt.isEmpty()) {
            return Optional.empty();
        }

        TamingProgress progress = progressOpt.get();

        // Verify trust level is sufficient
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty()) {
            return Optional.empty();
        }

        TamingConfig config = configOpt.get();
        if (progress.trustLevel() < config.requiredTrustLevel()) {
            return Optional.empty();
        }

        // Create tamed animal
        TamedAnimal tamedAnimal = TamedAnimal.create(
                progress.animalId(),
                progress.attemptingPlayerId(),
                ownerName,
                progress.speciesId(),
                x, y, z,
                config.maxFollowDistance()
        );

        // Register in registry
        animalRegistry.register(tamedAnimal, animalEntityId);

        // Remove taming progress
        calmingHandler.removeProgress(animalEntityId);

        // Publish event
        eventBus.publish(new AnimalTamedEvent(
                progress.attemptingPlayerId(),
                ownerName,
                animalEntityId,
                tamedAnimal.id(),
                progress.speciesId()
        ));

        return Optional.of(tamedAnimal);
    }

    @Override
    public boolean toggleBehaviorMode(UUID animalId, UUID playerId, double x, double y, double z) {
        return behaviorHandler.toggleMode(currentContext, animalId, playerId, x, y, z);
    }

    @Override
    public Optional<TamedAnimal> getTamedAnimal(UUID animalId) {
        return animalRegistry.getByAnimalId(animalId);
    }

    @Override
    public Set<TamedAnimal> getAnimalsOwnedBy(UUID playerId) {
        return animalRegistry.getByOwnerId(playerId);
    }

    @Override
    public Optional<TamingProgress> getTamingProgress(long animalEntityId) {
        return calmingHandler.getProgress(animalEntityId);
    }

    @Override
    public void removeTamingProgress(long animalEntityId) {
        calmingHandler.removeProgress(animalEntityId);
    }

    @Override
    public boolean releaseTamedAnimal(UUID animalId, UUID playerId) {
        Optional<TamedAnimal> animalOpt = animalRegistry.getByAnimalId(animalId);
        if (animalOpt.isEmpty()) {
            return false;
        }

        TamedAnimal animal = animalOpt.get();
        if (!animal.isOwnedBy(playerId)) {
            return false;
        }

        animalRegistry.unregister(animalId);

        eventBus.publish(new TamedAnimalLostEvent(
                animalId,
                playerId,
                animal.speciesId(),
                "released"
        ));

        return true;
    }

    @Override
    public void updateTamedAnimal(TamedAnimal animal) {
        animalRegistry.update(animal);
    }

    /**
     * Check if an entity's trust level is sufficient for taming.
     */
    public boolean canCompleteTaming(long animalEntityId) {
        Optional<TamingProgress> progressOpt = calmingHandler.getProgress(animalEntityId);
        if (progressOpt.isEmpty()) {
            return false;
        }

        TamingProgress progress = progressOpt.get();
        Optional<TamingConfig> configOpt = configRegistry.get(progress.speciesId());
        if (configOpt.isEmpty()) {
            return false;
        }

        return progress.trustLevel() >= configOpt.get().requiredTrustLevel();
    }
}
