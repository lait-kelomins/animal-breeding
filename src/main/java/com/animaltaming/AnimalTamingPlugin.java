package com.animaltaming;

import com.animaltaming.api.TamingService;
import com.animaltaming.api.event.TamingEvents.*;
import com.animaltaming.api.model.TamedAnimal;
import com.animaltaming.config.ConfigLoader;
import com.animaltaming.core.handler.*;
import com.animaltaming.core.registry.*;
import com.animaltaming.core.service.*;
import com.animaltaming.persistence.*;
import com.animaltaming.persistence.codec.TamedAnimalCodec;
import com.animaltaming.system.*;
import com.animaltaming.util.EventBus;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Animal Taming Plugin - Main entry point.
 *
 * This class performs WIRING ONLY - no business logic.
 * All dependencies are created and connected via constructor injection.
 *
 * NO STATIC INSTANCE - use dependency injection to access services.
 */
public class AnimalTamingPlugin {

    private final Path pluginFolder;

    // Core infrastructure
    private final EventBus eventBus;
    private final TamingConfigRegistry configRegistry;
    private final TamedAnimalRegistry animalRegistry;
    private final PlayerLookupService playerLookup;
    private final TamingRepository repository;

    // Handlers
    private final CalmingHandler calmingHandler;
    private final FeedingHandler feedingHandler;
    private final MountingHandler mountingHandler;
    private final BehaviorHandler behaviorHandler;

    // Services
    private final DefaultTamingService tamingService;

    // System
    private final TamingTickSystem tickSystem;

    private boolean enabled = false;

    /**
     * Create a new Animal Taming Plugin instance.
     * All dependencies are wired via constructor injection.
     *
     * @param pluginFolder the plugin's data folder
     */
    public AnimalTamingPlugin(Path pluginFolder) {
        this.pluginFolder = Objects.requireNonNull(pluginFolder, "pluginFolder required");

        // Create core infrastructure
        this.eventBus = new EventBus();
        this.configRegistry = new TamingConfigRegistry();
        this.animalRegistry = new TamedAnimalRegistry();
        this.playerLookup = new CachedPlayerLookupService();
        this.repository = new JsonTamingRepository(pluginFolder, new TamedAnimalCodec());

        // Create handlers (with dependencies injected)
        this.calmingHandler = new CalmingHandler(playerLookup, configRegistry, eventBus);
        this.feedingHandler = new FeedingHandler(playerLookup, configRegistry, eventBus, calmingHandler);
        this.mountingHandler = new MountingHandler(playerLookup, configRegistry, eventBus, calmingHandler);
        this.behaviorHandler = new BehaviorHandler(playerLookup, animalRegistry, eventBus);

        // Create service (with all dependencies)
        this.tamingService = new DefaultTamingService(
                animalRegistry,
                configRegistry,
                playerLookup,
                eventBus,
                calmingHandler,
                feedingHandler,
                behaviorHandler
        );

        // Create tick system (thin orchestrator)
        this.tickSystem = new TamingTickSystem(
                playerLookup,
                calmingHandler,
                feedingHandler,
                mountingHandler,
                behaviorHandler,
                tamingService
        );
    }

    /**
     * Enable the plugin.
     * Called during server startup.
     */
    public void onEnable() {
        System.out.println("[AnimalTaming] Enabling Animal Taming Plugin v1.0.0...");

        // Load species configurations
        loadConfigurations();

        // Load persisted tamed animals
        loadPersistedAnimals();

        // Subscribe to events for logging
        subscribeToEvents();

        enabled = true;

        System.out.println("[AnimalTaming] Animal Taming Plugin enabled!");
        System.out.println("[AnimalTaming] Loaded " + configRegistry.size() + " tameable species.");
        System.out.println("[AnimalTaming] Loaded " + animalRegistry.size() + " tamed animals.");
    }

    /**
     * Disable the plugin.
     * Called during server shutdown.
     */
    public void onDisable() {
        System.out.println("[AnimalTaming] Disabling Animal Taming Plugin...");

        // Save all tamed animals
        savePersistedAnimals();

        // Clear event handlers
        eventBus.clear();

        enabled = false;

        System.out.println("[AnimalTaming] Animal Taming Plugin disabled!");
    }

    /**
     * Called every server tick.
     *
     * @param context the system context
     * @param deltaTime time since last tick in seconds
     */
    public void onTick(SystemContext context, float deltaTime) {
        if (!enabled) {
            return;
        }

        tickSystem.update(context, deltaTime);
    }

    private void loadConfigurations() {
        ConfigLoader loader = new ConfigLoader();

        // Load from classpath resources
        var resourceConfigs = loader.loadFromResources("tameable");
        configRegistry.registerAll(resourceConfigs);

        // Load from plugin folder (overrides/additions)
        Path customConfigFolder = pluginFolder.resolve("tameable");
        var customConfigs = loader.loadFromDirectory(customConfigFolder);
        for (var config : customConfigs) {
            if (!configRegistry.contains(config.speciesId())) {
                configRegistry.register(config);
            }
        }
    }

    private void loadPersistedAnimals() {
        var animals = repository.loadAll();
        for (TamedAnimal animal : animals) {
            // Register with entity ID 0 - will be updated when entity spawns
            animalRegistry.register(animal, 0);
        }
    }

    private void savePersistedAnimals() {
        for (TamedAnimal animal : animalRegistry.getAll()) {
            repository.save(animal);
        }
        System.out.println("[AnimalTaming] Saved " + animalRegistry.size() + " tamed animals.");
    }

    private void subscribeToEvents() {
        // Log taming events
        eventBus.subscribe(AnimalTamedEvent.class, event ->
                System.out.println("[AnimalTaming] " + event.ownerName() + " tamed a " + event.speciesId() + "!")
        );

        eventBus.subscribe(TamedAnimalLostEvent.class, event ->
                System.out.println("[AnimalTaming] Tamed animal lost: " + event.speciesId() + " (" + event.reason() + ")")
        );

        // Save animal on tame
        eventBus.subscribe(AnimalTamedEvent.class, event -> {
            animalRegistry.getByAnimalId(event.animalId()).ifPresent(repository::save);
        });

        // Save animal on mode change
        eventBus.subscribe(BehaviorModeChangedEvent.class, event -> {
            animalRegistry.getByAnimalId(event.animalId()).ifPresent(repository::save);
        });
    }

    // ==================== ACCESSORS ====================
    // Provided for integration - use dependency injection when possible

    /**
     * Get the taming service.
     */
    public TamingService getTamingService() {
        return tamingService;
    }

    /**
     * Get the event bus for subscribing to events.
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Get the config registry.
     */
    public TamingConfigRegistry getConfigRegistry() {
        return configRegistry;
    }

    /**
     * Get the tamed animal registry.
     */
    public TamedAnimalRegistry getAnimalRegistry() {
        return animalRegistry;
    }

    /**
     * Check if the plugin is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the plugin folder.
     */
    public Path getPluginFolder() {
        return pluginFolder;
    }
}
