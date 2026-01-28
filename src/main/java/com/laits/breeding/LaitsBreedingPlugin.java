package com.laits.breeding;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerCreativeSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.BreedingTickManager;
import com.laits.breeding.managers.GrowthManager;
import com.laits.breeding.managers.RespawnManager;
import com.laits.breeding.managers.SpawningManager;
import com.laits.breeding.managers.InteractionSetupManager;
import com.laits.breeding.handlers.MouseInteractionHandler;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.managers.PersistenceManager;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.ui.NametagUIPage;
import com.laits.breeding.listeners.UseBlockHandler;
import com.laits.breeding.listeners.DetectTamedDeath;
import com.laits.breeding.listeners.DetectTamedDespawn;
import com.laits.breeding.listeners.CoopResidentTracker;
import com.laits.breeding.listeners.CaptureCratePacketListener;
import com.laits.breeding.listeners.NewAnimalSpawnDetector;
import com.laits.breeding.interactions.FeedAnimalInteraction;
import com.laits.breeding.interactions.NameAnimalInteraction;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.AnimalFinder;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.EntityUtil;
import com.laits.breeding.util.NameplateUtil;
import com.laits.breeding.interactions.InteractionStateCache;
import com.laits.breeding.effects.EffectsManager;
import com.laits.breeding.components.HyTameInteractionComponent;

import com.laits.breeding.commands.BreedCommand;
import com.laits.breeding.commands.BreedingConfigCommand;
import com.laits.breeding.commands.CustomAnimalCommand;
import com.laits.breeding.commands.LegacyCommands;

// Taming integration imports
import com.tameableanimals.tame.HyTameComponent;
import com.tameableanimals.tame.HyTameSystems;
import com.tameableanimals.actions.BuilderActionHyTameOrFeed;
import com.tameableanimals.actions.BuilderActionRemovePlayerHeldItems;
import com.tameableanimals.sensors.BuilderSensorTamed;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Main plugin class for Lait's Animal Breeding.
 */
public class LaitsBreedingPlugin extends JavaPlugin {

    public static final String VERSION = "1.4.3";

    private static LaitsBreedingPlugin instance;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();

    // Static reflection field for setting NPC attitude to REVERED when tamed
    private static final Field ATTITUDE_FIELD;
    static {
        try {
            ATTITUDE_FIELD = WorldSupport.class.getDeclaredField("defaultPlayerAttitude");
            ATTITUDE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access defaultPlayerAttitude", e);
        }
    }

    /**
     * Get the attitude field for setting NPC attitude on taming.
     */
    public static Field getAttitudeField() {
        return ATTITUDE_FIELD;
    }

    // NOTE: Breeding constants (BREEDING_DISTANCE, LOVE_DURATION) moved to BreedingTickManager

    private ConfigManager configManager;
    private BreedingManager breedingManager;
    private GrowthManager growthManager;
    private TamingManager tamingManager;
    private PersistenceManager persistenceManager;
    private BreedingTickManager breedingTickManager;
    private EffectsManager effectsManager;
    private SpawningManager spawningManager;
    private RespawnManager respawnManager;
    private InteractionSetupManager interactionSetupManager;
    private MouseInteractionHandler mouseInteractionHandler;

    // HyTameComponent type for ECS integration
    private ComponentType<EntityStore, HyTameComponent> hyTameComponentType;
    // HyTameInteractionComponent type for persisting original interactions across
    // restarts
    private ComponentType<EntityStore, HyTameInteractionComponent> hyTameInteractionComponentType;
    private ScheduledExecutorService tickScheduler;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    private NewAnimalSpawnDetector spawnDetector;

    // Getter for tick scheduler (used by commands)
    public ScheduledExecutorService getTickScheduler() {
        return tickScheduler;
    }

    // Getter for spawn detector (used by commands)
    public NewAnimalSpawnDetector getSpawnDetector() {
        return spawnDetector;
    }

    // ECS component types are now in EcsReflectionUtil

    // Track last detected despawn UUIDs for debugging (max 10)
    private static final java.util.List<UUID> lastDetectedDespawns = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());
    private static final int MAX_DESPAWN_TRACKED = 10;

    /**
     * Get the last detected despawn UUIDs (most recent first).
     */
    public static java.util.List<UUID> getLastDetectedDespawns() {
        synchronized (lastDetectedDespawns) {
            return new java.util.ArrayList<>(lastDetectedDespawns);
        }
    }

    /**
     * Clear the tracked despawn UUIDs.
     */
    public static void clearTrackedDespawns() {
        lastDetectedDespawns.clear();
    }

    private static void trackDetectedDespawn(UUID uuid) {
        synchronized (lastDetectedDespawns) {
            // Add at beginning (most recent first)
            lastDetectedDespawns.add(0, uuid);
            // Keep only max entries
            while (lastDetectedDespawns.size() > MAX_DESPAWN_TRACKED) {
                lastDetectedDespawns.remove(lastDetectedDespawns.size() - 1);
            }
        }
    }

    // Verbose logging toggle (controlled by /breedlogs command)
    private static boolean verboseLogging = false;

    public static boolean isVerboseLogging() {
        return verboseLogging;
    }

    public static void setVerboseLogging(boolean enabled) {
        verboseLogging = enabled;
    }

    // Development debug mode - broadcasts to all players in-game (controlled by
    // /breeddev command)
    // Set to false for production builds
    private static boolean devMode = false;

    public static boolean isDevMode() {
        return devMode;
    }

    public static void setDevMode(boolean enabled) {
        devMode = enabled;
    }

    // Entity-based interaction system - controlled by build variant (see
    // BuildConfig)
    // When true: Sets "Press [F] to Feed" hints directly on animal entities (Use
    // key)
    // When false: Uses item-based Ability2 interactions (food templates have
    // Ability2: Root_FeedAnimal)
    // Value is set at build time via Gradle: buildAbility2 (false) or
    // buildEntityBased (true)
    private static final boolean USE_ENTITY_BASED_INTERACTIONS = BuildConfig.USE_ENTITY_BASED_INTERACTIONS;

    // Legacy FeedAnimalInteraction system toggle
    // When true: Uses FeedAnimalInteraction to handle feeding via Use key on
    // animals
    // When false: Disables interaction-based feeding (ActionHyTameOrFeed via
    // behavior tree)
    // Note: Action-based system had issues, reverted to legacy interaction system
    private static final boolean USE_LEGACY_FEED_INTERACTION = true;

    // Show interaction hints on animals even when using item-based Ability2
    // When true: Animals show "Press [Ability2] to Feed" hint (but actual feeding
    // is via item)
    // When false: No hints on animals (player must know to use Ability2)
    // Only applies when USE_ENTITY_BASED_INTERACTIONS is false
    private static final boolean SHOW_ABILITY2_HINTS_ON_ENTITIES = true;

    /** Broadcast a message to all online players in chat (all worlds) */
    private void broadcastToChat(String message) {
        try {
            // Broadcast to all worlds for multi-world support
            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null) continue;

                world.getPlayers().forEach(player -> {
                    try {
                        player.sendMessage(Message.raw("[Breeding] " + message).color("#AAAAAA"));
                    } catch (Exception e) {
                        // Silent
                    }
                });
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /** Log verbose/debug message (only when verbose logging is enabled) */
    private void logVerbose(String message) {
        if (verboseLogging) {
            LOGGER.atInfo().log("[Lait:AnimalBreeding] " + message);
            // Also broadcast to chat if devMode is enabled
            if (devMode) {
                broadcastToChat(message);
            }
        }
    }

    /** Log warning message */
    private void logWarning(String message) {
        LOGGER.atWarning().log("[Lait:AnimalBreeding] " + message);
    }

    // NOTE: logError() and devLog() removed - unused dead code

    public LaitsBreedingPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        // Log build variant info
        getLogger().atInfo().log("=== Lait's Animal Breeding v%s ===", BuildConfig.VERSION);
        getLogger().atInfo().log("Build variant: %s", BuildConfig.VARIANT);
        getLogger().atInfo().log("Feeding mode: %s",
                USE_ENTITY_BASED_INTERACTIONS ? "Entity-based (F key)" : "Item Ability2 (E key)");

        // Initialize config manager and load from file
        configManager = new ConfigManager();
        configManager.setLogger(msg -> {
            if (verboseLogging)
                getLogger().atInfo().log(msg);
        });

        // Load config from plugin's data directory (created automatically by the
        // server)
        java.nio.file.Path configPath = getDataDirectory().resolve("config.json");
        configManager.loadFromFile(configPath);

        breedingManager = new BreedingManager(configManager);
        growthManager = new GrowthManager(configManager, breedingManager);

        // Initialize taming and persistence managers
        persistenceManager = new PersistenceManager();
        persistenceManager.setLogger(msg -> {
            if (verboseLogging)
                LOGGER.atInfo().log("[Taming] " + msg);
        });
        persistenceManager.initialize(getDataDirectory());

        tamingManager = new TamingManager();
        tamingManager.setLogger(msg -> {
            if (verboseLogging)
                LOGGER.atInfo().log("[Taming] " + msg);
        });
        tamingManager.setPersistenceManager(persistenceManager);
        // Set grace period from config (convert seconds to milliseconds)
        tamingManager.setGracePeriodMs(configManager.getInitializationGracePeriodSeconds() * 1000);

        // Load saved tamed animals
        java.util.List<TamedAnimalData> savedAnimals = persistenceManager.loadData();
        tamingManager.loadFromPersistence(savedAnimals);

        // Initialize breeding tick manager
        breedingTickManager = new BreedingTickManager(breedingManager, configManager);
        breedingTickManager.setVerboseLogging(verboseLogging);
        breedingTickManager.setLogger(msg -> getLogger().atInfo().log(msg));
        breedingTickManager.setWarningLogger(msg -> getLogger().atWarning().log(msg));

        // Initialize effects manager
        effectsManager = new EffectsManager();
        effectsManager.setVerboseLogging(verboseLogging);
        effectsManager.setLogger(msg -> getLogger().atInfo().log(msg));
        effectsManager.setWarningLogger(msg -> getLogger().atWarning().log(msg));

        // Initialize spawning manager
        spawningManager = new SpawningManager();
        spawningManager.setBreedingManager(breedingManager);
        spawningManager.setTamingManager(tamingManager);
        spawningManager.setHyTameTypeSupplier(() -> hyTameComponentType);
        spawningManager.setModelAssetIdGetter(
                args -> getEntityModelAssetId((Store<EntityStore>) args[0], (Ref<EntityStore>) args[1]));
        spawningManager.setVerboseLogging(verboseLogging);
        spawningManager.setLogger(msg -> getLogger().atInfo().log(msg));
        spawningManager.setWarningLogger(msg -> getLogger().atWarning().log(msg));
        spawningManager.setErrorLogger(msg -> getLogger().atSevere().log(msg));

        // Initialize respawn manager
        respawnManager = new RespawnManager();
        respawnManager.setTamingManager(tamingManager);
        respawnManager.setBreedingManager(breedingManager);
        respawnManager.setHyTameTypeSupplier(() -> hyTameComponentType);
        respawnManager.setPositionGetter(ref -> EntityUtil.getPositionFromRef(ref));
        respawnManager.setVerboseLogging(verboseLogging);
        respawnManager.setLogger(msg -> getLogger().atInfo().log(msg));
        respawnManager.setWarningLogger(msg -> getLogger().atWarning().log(msg));

        // Initialize interaction setup manager
        interactionSetupManager = new InteractionSetupManager(configManager, breedingManager);
        interactionSetupManager.setHyTameInteractionTypeSupplier(() -> hyTameInteractionComponentType);
        interactionSetupManager.setUseEntityBasedInteractions(USE_ENTITY_BASED_INTERACTIONS);
        interactionSetupManager.setUseLegacyFeedInteraction(USE_LEGACY_FEED_INTERACTION);
        interactionSetupManager.setShowAbility2HintsOnEntities(SHOW_ABILITY2_HINTS_ON_ENTITIES);
        interactionSetupManager.setVerboseLogging(verboseLogging);
        interactionSetupManager.setLogger(msg -> getLogger().atInfo().log(msg));
        interactionSetupManager.setWarningLogger(msg -> getLogger().atWarning().log(msg));

        // Initialize mouse interaction handler
        mouseInteractionHandler = new MouseInteractionHandler(configManager, breedingManager, effectsManager, interactionSetupManager);
        mouseInteractionHandler.setVerboseLogging(verboseLogging);
        mouseInteractionHandler.setLogger(msg -> getLogger().atInfo().log(msg));
        mouseInteractionHandler.setTamingManager(tamingManager);

        // Set up breeding callbacks
        breedingTickManager.setOnBreedingComplete((type, animals) -> {
            // Get positions for midpoint calculation
            Vector3d pos1 = spawningManager.getPositionFromBreedingData(animals[0]);
            Vector3d pos2 = spawningManager.getPositionFromBreedingData(animals[1]);
            if (pos1 != null && pos2 != null) {
                Vector3d midpoint = new Vector3d(
                        (pos1.getX() + pos2.getX()) / 2.0,
                        (pos1.getY() + pos2.getY()) / 2.0,
                        (pos1.getZ() + pos2.getZ()) / 2.0);
                // Use world name from either parent (prefer first, fall back to second)
                String worldName = animals[0].getWorldName();
                if (worldName == null) {
                    worldName = animals[1].getWorldName();
                }
                spawningManager.spawnBabyAnimal(type, midpoint, animals[0].getAnimalId(), animals[1].getAnimalId(), worldName);
            }
        });

        breedingTickManager.setOnCustomBreedingComplete((modelAssetId, animals) -> {
            // Get positions for midpoint calculation
            Vector3d pos1 = EntityUtil.getPositionFromRef(animals[0].getEntityRef());
            Vector3d pos2 = EntityUtil.getPositionFromRef(animals[1].getEntityRef());
            if (pos1 != null && pos2 != null) {
                Vector3d midpoint = new Vector3d(
                        (pos1.getX() + pos2.getX()) / 2.0,
                        (pos1.getY() + pos2.getY()) / 2.0,
                        (pos1.getZ() + pos2.getZ()) / 2.0);
                // Use world name from either parent (prefer first, fall back to second)
                String worldName = animals[0].getWorldName();
                if (worldName == null) {
                    worldName = animals[1].getWorldName();
                }
                CustomAnimalConfig customConfig = configManager.getCustomAnimal(modelAssetId);
                spawningManager.spawnCustomAnimalBaby(modelAssetId, customConfig, midpoint, worldName);
            }
        });

        // Heart particle spawner for custom animals (uses entity ref)
        breedingTickManager.setHeartParticleSpawner(entityRef -> {
            // Find the world that contains this entity by comparing stores
            World targetWorld = null;
            if (entityRef instanceof Ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                Store<EntityStore> entityStore = ref.getStore();
                if (entityStore != null) {
                    for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                        World w = entry.getValue();
                        if (w == null) continue;
                        try {
                            if (w.getEntityStore().getStore() == entityStore) {
                                targetWorld = w;
                                break;
                            }
                        } catch (Exception e) {
                            // Skip this world
                        }
                    }
                }
            }
            // Fallback to default world if not found
            if (targetWorld == null) {
                targetWorld = Universe.get().getDefaultWorld();
            }
            if (targetWorld != null) {
                final World finalWorld = targetWorld;
                finalWorld.execute(() -> {
                    try {
                        Store<EntityStore> store = finalWorld.getEntityStore().getStore();
                        effectsManager.spawnHeartParticlesAtRef(store, entityRef);
                    } catch (Exception e) {
                        // Silent
                    }
                });
            }
        });

        // Heart particle spawner for regular animals (uses position + store directly)
        breedingTickManager.setHeartParticlePositionSpawner((position, store) -> {
            try {
                Vector3d heartsPos = new Vector3d(position.getX(), position.getY() + 1.5, position.getZ());
                ParticleUtil.spawnParticleEffect("BreedingHearts", heartsPos, store);
            } catch (Exception e) {
                // Silent
            }
        });

        // Set up growth callback - handle growth stage changes
        growthManager.setOnGrowthCallback(event -> {
            if (event.usesScaling()) {
                // Creatures without baby variants: update scale at each stage
                spawningManager.updateEntityScale(event.getAnimalId(), event.getAnimalType(), event.getTargetScale());
                if (event.getNewStage() == GrowthStage.ADULT) {
                    // Clean up tracking data when fully grown
                    breedingManager.removeData(event.getAnimalId());
                }
            } else {
                // Animals with baby variants: replace entity when adult
                if (event.getNewStage() == GrowthStage.ADULT) {
                    spawningManager.transformBabyToAdult(event.getAnimalId(), event.getAnimalType());
                }
            }
        });

        // *** IMPORTANT: Register events in setup(), not start() ***
        // Per docs: "Setup Phase - Register commands, events, and initialize resources
        // here"
        registerInteractionHandler();

        // Register our custom FeedAnimalInteraction type with the codec
        try {
            getCodecRegistry(Interaction.CODEC)
                    .register("FeedAnimal", FeedAnimalInteraction.class, FeedAnimalInteraction.CODEC);
        } catch (Exception e) {
            logWarning("FeedAnimalInteraction codec registration skipped (may already exist): " + e.getMessage());
        }

        // Register our custom NameAnimalInteraction type with the codec
        try {
            getCodecRegistry(Interaction.CODEC)
                    .register("NameAnimal", NameAnimalInteraction.class, NameAnimalInteraction.CODEC);
        } catch (Exception e) {
            logWarning("NameAnimalInteraction codec registration skipped (may already exist): " + e.getMessage());
        }

        // Register HyTameComponent for ECS-based taming
        try {
            hyTameComponentType = getEntityStoreRegistry().registerComponent(
                    HyTameComponent.class, "HyTame", HyTameComponent.CODEC);
            getLogger().atInfo().log("HyTameComponent registered successfully");
        } catch (Exception e) {
            logWarning("HyTameComponent registration failed: " + e.getMessage());
        }

        // Register HyTameInteractionComponent for persisting original interactions
        // across restarts
        try {
            hyTameInteractionComponentType = getEntityStoreRegistry().registerComponent(
                    HyTameInteractionComponent.class, "OriginalInteraction", HyTameInteractionComponent.CODEC);
            getLogger().atInfo().log("HyTameInteractionComponent registered successfully");
        } catch (Exception e) {
            logWarning("HyTameInteractionComponent registration failed: " + e.getMessage());
        }

        // Register ECS system for block interactions
        try {
            getEntityStoreRegistry().registerSystem(new UseBlockHandler());
        } catch (Exception e) {
            // Silent
        }

        // Register death and despawn detection for tamed animals
        try {
            getEntityStoreRegistry().registerSystem(new DetectTamedDeath());
            getEntityStoreRegistry().registerSystem(new DetectTamedDespawn());
            logVerbose("DetectTamedDeath system registered");
        } catch (Exception e) {
            logWarning("Failed to register DetectTamedDeath: " + e.getMessage());
        }

        // Register coop/capture crate tracking to prevent duplication
        try {
            getEntityStoreRegistry().registerSystem(new CoopResidentTracker());
            logVerbose("CoopResidentTracker system registered");
        } catch (Exception e) {
            logWarning("Failed to register CoopResidentTracker: " + e.getMessage());
        }

        // Register NetworkId cache for O(1) entity lookup by network ID
        try {
            getEntityStoreRegistry().registerSystem(new com.laits.breeding.util.NetworkIdCache());
            logVerbose("NetworkIdCache system registered");
        } catch (Exception e) {
            logWarning("Failed to register NetworkIdCache: " + e.getMessage());
        }

        // Register capture crate packet listener for detecting captures/releases
        try {
            CaptureCratePacketListener captureCrateListener = new CaptureCratePacketListener(getLogger());
            captureCrateListener.register();
            logVerbose("CaptureCratePacketListener registered");
        } catch (Exception e) {
            logWarning("Failed to register CaptureCratePacketListener: " + e.getMessage());
        }

        // NOTE: NewAnimalSpawnDetector is registered in start() after world is ready

        // Register unified /breed command (recommended)
        getCommandRegistry().registerCommand(new BreedCommand());

        // Register legacy commands (with deprecation warnings)
        // These are kept for backwards compatibility but show deprecation notices
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingHelpCommand()); // Use /breed help
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingStatusCommand()); // Use /breed status
        getCommandRegistry().registerCommand(new BreedingConfigCommand()); // Use /breed config
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingGrowthCommand()); // Use /breed growth
        getCommandRegistry().registerCommand(new LegacyCommands.NameTagCommand()); // Use /breed tame
        getCommandRegistry().registerCommand(new LegacyCommands.TamingInfoCommand()); // Use /breed info
        getCommandRegistry().registerCommand(new LegacyCommands.TamingSettingsCommand()); // Use /breed settings
        getCommandRegistry().registerCommand(new LegacyCommands.UntameCommand()); // Use /breed untame
        getCommandRegistry().registerCommand(new CustomAnimalCommand()); // Use /breed custom

        // Dev/debug commands (no unified equivalent)
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingLogsCommand());
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingDevCommand());
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingHintCommand());
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingScanCommand());
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingCachesCommand());
        getCommandRegistry().registerCommand(new LegacyCommands.NoClipCommand());
    }

    @Override
    protected void start() {
        // Configure RootInteraction chain (after assets are loaded)
        RootInteraction rootInt = RootInteraction.getRootInteractionOrUnknown("Root_FeedAnimal");
        String[] ids = rootInt.getInteractionIds();

        if (ids == null || ids.length == 0) {
            String[] newIds = new String[] { "FeedAnimal" };
            rootInt.build(Set.of(newIds));
        }

        // Start tick scheduler for pregnancy and growth updates
        tickScheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
            try {
                breedingManager.tickPregnancies();
                growthManager.tickGrowth();
                breedingTickManager.tick(); // Handle love mode, heart particles, breeding
                interactionSetupManager.updateTrackedAnimalStates(); // Dynamic hint switching
            } catch (Exception e) {
                // Log tick errors for debugging
                logWarning("[Tick] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS));

        // Start taming persistence auto-save (every 5 minutes)
        if (persistenceManager != null && tamingManager != null) {
            persistenceManager.startAutoSave(tickScheduler,
                    () -> tamingManager.getAllTamedAnimals(),
                    5); // 5 minutes
        }

        // Start respawn check tick (every 5 seconds)
        scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
            try {
                respawnManager.checkAndRespawnTamedAnimals();
            } catch (Exception e) {
                getLogger().atWarning().log("[RespawnCheck] Exception in scheduler: " + e.getMessage());
                e.printStackTrace();
            }
        }, 5, 5, TimeUnit.SECONDS));

        // Attach Root_FeedAnimal interaction to all breedable animals
        attachInteractionsToAnimals();

        // Mark taming manager as initialized after initial entity scanning
        // This starts the grace period timer to prevent duplication on slow servers
        if (tamingManager != null) {
            tamingManager.markInitialized();
        }

        // Register entity removal listener to clean up breeding data when animals die
        registerEntityRemovalListener();

        // Register ECS systems for taming and breeding
        try {
            // Register NewAnimalSpawnDetector (RefSystem pattern for immediate detection)
            spawnDetector = new NewAnimalSpawnDetector();
            getEntityStoreRegistry().registerSystem(spawnDetector);
            logVerbose("NewAnimalSpawnDetector registered (RefSystem pattern)");

            // Register HyTameActivateSystem (sets REVERED attitude when tamed)
            getEntityStoreRegistry().registerSystem(new HyTameSystems.HyTameActivateSystem());
            logVerbose("HyTameActivateSystem registered");

            // Register HyTameTickSystem (manages actionReady state based on cooldowns)
            getEntityStoreRegistry().registerSystem(new HyTameSystems.HyTameTickSystem());
            logVerbose("HyTameTickSystem registered");

            // Register NPC core components for behavior tree support (only when not using
            // legacy interactions)
            // "HyTameOrFeed" routes feeding to taming (wild) or breeding (tamed)
            NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
            if (!USE_LEGACY_FEED_INTERACTION) {
                NPCPlugin.get().registerCoreComponentType("HyTameOrFeed", BuilderActionHyTameOrFeed::new);
                NPCPlugin.get().registerCoreComponentType("RemovePlayerHeldItems",
                        BuilderActionRemovePlayerHeldItems::new);
                logVerbose("NPC taming components registered (HyTameOrFeed, Tamed, RemovePlayerHeldItems)");
            } else {
                logVerbose("NPC taming components skipped (using legacy FeedAnimalInteraction)");
            }

            // Periodically update player UUIDs for the spawn detector to exclude players
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (spawnDetector != null) {
                        Set<UUID> currentPlayerUuids = ConcurrentHashMap.newKeySet();
                        // Collect player UUIDs from all worlds
                        for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                            World world = entry.getValue();
                            if (world == null) continue;
                            for (Player p : world.getPlayers()) {
                                UUID pUuid = EntityUtil.getEntityUUID(p);
                                if (pUuid != null) {
                                    currentPlayerUuids.add(pUuid);
                                }
                            }
                        }
                        spawnDetector.updatePlayerUuids(currentPlayerUuids);
                    }
                } catch (Exception e) {
                    // Silent
                }
            }, 0, 5, TimeUnit.SECONDS));

            // Periodically clear the processedEntities cache to prevent memory leak
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (spawnDetector != null) {
                        int cacheSize = spawnDetector.getProcessedCacheSize();
                        spawnDetector.clearProcessedCache();
                        if (cacheSize > 0) {
                            logVerbose("Cleared spawn detector cache (" + cacheSize + " entries)");
                        }
                    }
                } catch (Exception e) {
                    // Silent
                }
            }, 5, 5, TimeUnit.MINUTES));

            // Periodically clean up originalStates map (safety net for missed
            // EntityRemoveEvents)
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    int removed = InteractionStateCache.getInstance().cleanupStaleEntries();
                    if (removed > 0) {
                        logVerbose("Cleaned " + removed + " stale interaction entries");
                    }
                } catch (Exception e) {
                    // Silent
                }
            }, 10, 10, TimeUnit.MINUTES));

            // Periodically clean up stale breeding data
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    int removed = breedingManager.cleanupStaleEntries();
                    if (removed > 0) {
                        logVerbose("Cleaned " + removed + " stale breeding entries");
                    }
                } catch (Exception e) {
                    // Silent
                }
            }, 5, 5, TimeUnit.MINUTES));

            // Periodically update tamed animal positions (every 30 seconds)
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    respawnManager.updateTamedAnimalPositions();
                } catch (Exception e) {
                    // Silent
                }
            }, 30, 30, TimeUnit.SECONDS));

            // Periodically scan for untracked babies (every 30 seconds)
            // This catches babies that slipped through primary detection
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    scanForUntrackedBabies();
                } catch (Exception e) {
                    // Silent
                }
            }, 30, 30, TimeUnit.SECONDS));

        } catch (Exception e) {
            logWarning("ECS system registration failed: " + e.getMessage());
            spawnDetector = null;
        }

        getLogger().atInfo().log("[Lait:AnimalBreeding] Plugin started! Commands: /laitsbreeding, /breedstatus");
    }

    /**
     * Attaching interactions to animals via periodic scanning.
     * Note: Event-based detection (PrefabPlaceEntityEvent, LoadedNPCEvent) was
     * tested
     * but these events don't fire for natural animal spawns in Hytale.
     *
     * LEGACY: This method is disabled when USE_LEGACY_FEED_INTERACTION is false.
     * The new ActionHyTameOrFeed system handles feeding/taming via NPC behavior
     * tree.
     */
    public void attachInteractionsToAnimals() {
        // Skip if legacy feed interaction system is disabled
        if (!USE_LEGACY_FEED_INTERACTION) {
            logVerbose("Legacy FeedAnimalInteraction disabled - skipping interaction attachment");
            return;
        }

        // Scan when a player connects (entities spawn when chunks load around players)
        // Multiple scans to catch animals as they load
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            // Quick scan after 1 second
            tickScheduler.schedule(() -> {
                try {
                    autoSetupNearbyAnimals();
                } catch (Exception e) {
                }
            }, 1, TimeUnit.SECONDS);
            // Follow-up scan after 3 seconds (more entities loaded)
            tickScheduler.schedule(() -> {
                try {
                    autoSetupNearbyAnimals();
                } catch (Exception e) {
                }
            }, 3, TimeUnit.SECONDS);
            // Final scan after 5 seconds
            tickScheduler.schedule(() -> {
                try {
                    autoSetupNearbyAnimals();
                } catch (Exception e) {
                }
            }, 5, TimeUnit.SECONDS);
        });

        // Safety net: Periodic scan every 5 minutes (primary detection via
        // NewAnimalSpawnDetector)
        // Reduced from 30s - real-time detection handles spawns, this is just a
        // fallback
        scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
            try {
                autoSetupNearbyAnimals();
            } catch (Exception e) {
                // Silent
            }
        }, 5, 5, TimeUnit.MINUTES));
    }

    // NOTE: setupSingleEntity() moved to InteractionSetupManager

    /**
     * Callback from NewAnimalSpawnDetector when a new animal is detected.
     * Delegates to InteractionSetupManager.
     */
    public void onNewAnimalDetected(Store<EntityStore> store, Ref<EntityStore> entityRef,
            String modelAssetId, AnimalType animalType, World world) {
        interactionSetupManager.onNewAnimalDetected(store, entityRef, modelAssetId, animalType, world);
    }

    /**
     * Get the model asset ID from an entity reference.
     */
    private String getEntityModelAssetId(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        return EcsReflectionUtil.getEntityModelAssetId(store, entityRef);
    }

    // NOTE: setupEntityInteractions, setupCustomAnimalInteractions, updateAnimalInteractionState,
    // updateTrackedAnimalStates, setupAbility2HintOnly, storeOriginalInteractionIdForCustom
    // moved to InteractionSetupManager

    /**
     * Register listener to clean up breeding data when entities are removed (death,
     * despawn, etc.)
     */
    private void registerEntityRemovalListener() {
        try {
            getEventRegistry().registerGlobal(EntityRemoveEvent.class, event -> {
                try {
                    Entity entity = event.getEntity();
                    if (entity == null)
                        return;

                    UUID entityId = EntityUtil.getEntityUUID(entity);

                    // Debug: Log all entity removals to check if event is firing
                    try {
                        Ref<EntityStore> debugRef = entity.getReference();
                        Integer debugRefIndex = (debugRef != null) ? debugRef.getIndex() : null;
                        boolean debugIsTamed = (tamingManager != null && tamingManager.isTamed(entityId));
                        logVerbose("EntityRemoveEvent: entity removed - refIndex=" + debugRefIndex + ", uuid=" + entityId + ", isTamed=" + debugIsTamed);
                    } catch (Exception e) {
                        // Silent
                    }

                    // Check if this is a tamed animal - don't delete, mark for respawn
                    if (tamingManager != null && tamingManager.isTamed(entityId)) {
                        // Check if animal is entering coop/capture crate storage
                        // If so, don't mark as despawned - it's stored, not gone
                        if (CoopResidentTracker.isInStorage(entityId)) {
                            logVerbose("Tamed animal entering coop/crate storage (not marking as despawned): " + entityId);
                            // Don't remove breeding data for tamed animals in storage
                            return;
                        }

                        // Check if this animal is being captured by a capture crate
                        // Method 1: Check CoopResidentTracker by UUID (legacy path)
                        UUID capturingPlayer = CoopResidentTracker.consumePendingCapture(entityId);
                        if (capturingPlayer != null) {
                            // Track the capture for later restoration when released
                            CoopResidentTracker.trackCapture(capturingPlayer, entityId);
                            logVerbose("Tamed animal captured by capture crate (player=" + capturingPlayer + "): " + entityId);
                            // Don't remove breeding data, don't mark as despawned
                            return;
                        }

                        // Method 2: Check CaptureCratePacketListener by ref index (packet-based detection)
                        try {
                            Ref<EntityStore> ref = entity.getReference();
                            if (ref != null) {
                                Integer refIndex = ref.getIndex();
                                logVerbose("EntityRemoveEvent: tamed animal removed, checking packet capture (refIndex=" + refIndex + ", entityId=" + entityId + ")");
                                if (refIndex != null) {
                                    var pendingCapture = CaptureCratePacketListener.consumePendingCapture(refIndex);
                                    if (pendingCapture != null) {
                                        // Track the capture for later restoration when released
                                        CoopResidentTracker.trackCapture(pendingCapture.playerUuid, entityId);
                                        logVerbose("Tamed animal captured by capture crate via packet (player=" + pendingCapture.playerUuid + ", refIndex=" + refIndex + "): " + entityId);
                                        // Don't remove breeding data, don't mark as despawned
                                        return;
                                    } else {
                                        logVerbose("EntityRemoveEvent: no pending capture found for refIndex=" + refIndex);
                                    }
                                }
                            } else {
                                logVerbose("EntityRemoveEvent: entity ref is null");
                            }
                        } catch (Exception ex) {
                            logVerbose("EntityRemoveEvent: error checking packet capture: " + ex.getMessage());
                        }

                        // Get position before entity is fully removed
                        Vector3d pos = null;
                        try {
                            if (entity instanceof LivingEntity) {
                                LivingEntity le = (LivingEntity) entity;
                                pos = le.getTransformComponent().getPosition();
                            }
                        } catch (Exception e) {
                            // Try alternate method
                        }

                        double x = pos != null ? pos.getX() : 0;
                        double y = pos != null ? pos.getY() : 0;
                        double z = pos != null ? pos.getZ() : 0;

                        // Track despawn for debugging
                        trackDetectedDespawn(entityId);

                        // Mark as despawned but keep data for respawn
                        tamingManager.onTamedAnimalDespawn(entityId, x, y, z);
                        logVerbose("Tamed animal despawned (marked for respawn): " + entityId);

                        // Don't remove breeding data for tamed animals
                        return;
                    }

                    // Regular animal cleanup
                    BreedingData data = breedingManager.getData(entityId);
                    if (data != null) {
                        breedingManager.removeData(entityId);
                    }

                    // Clean up interaction state cache to prevent memory leak
                    try {
                        Ref<EntityStore> ref = entity.getReference();

                        if (ref != null) {
                            InteractionStateCache.getInstance().remove(ref);
                        }
                    } catch (Exception ex) {
                        // Silent - entity may not have getReference method
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Automatically set up interactions on all farm animals in all worlds.
     * Public so it can be called from command classes.
     */
    public void autoSetupNearbyAnimals() {
        if (verboseLogging)
            getLogger().atInfo().log("[AutoScan] autoSetupNearbyAnimals CALLED");
        try {
            // Collect player UUIDs from all worlds to exclude from animal detection
            final Set<UUID> playerUuids = new HashSet<>();
            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null) continue;
                for (Player p : world.getPlayers()) {
                    UUID pUuid = EntityUtil.getEntityUUID(p);
                    if (pUuid != null) {
                        playerUuids.add(pUuid);
                    }
                }
            }

            // Scan all worlds for animals
            if (verboseLogging)
                getLogger().atInfo().log("[AutoScan] Starting animal scan in all worlds (customAnimals registered: %d)",
                        configManager.getCustomAnimals().size());

            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                String worldName = entry.getKey();
                World world = entry.getValue();
                if (world == null) continue;

                if (verboseLogging)
                    getLogger().atInfo().log("[AutoScan] Scanning world: %s", worldName);

                AnimalFinder.findAnimals(world, false, animals -> {
                    try {
                        if (verboseLogging)
                            getLogger().atInfo().log("[AutoScan] Found %d animals in world %s", animals.size(), worldName);
                        if (animals.isEmpty())
                            return;

                        // Log the registered custom animals for debugging (only once)
                        if (verboseLogging && worldName.equals(Universe.get().getWorlds().keySet().iterator().next()))
                            getLogger().atInfo().log("[AutoScan] Registered custom animals: %s",
                                    String.join(", ", configManager.getCustomAnimals().keySet()));

                        for (AnimalFinder.FoundAnimal animal : animals) {
                            scanAnimal(animal, playerUuids);
                        }
                    } catch (Exception e) {
                        // Log errors from animal processing
                        logWarning("autoSetupNearbyAnimals callback error in " + worldName + ": " + e.getClass().getSimpleName() + ": "
                                + e.getMessage());
                        if (verboseLogging && e.getCause() != null) {
                            logWarning("  Caused by: " + e.getCause().getMessage());
                        }
                    }
                });
            }

        } catch (Exception e) {
            // Log errors from initial setup
            logWarning("autoSetupNearbyAnimals setup error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void scanAnimal(AnimalFinder.FoundAnimal animal, Set<UUID> playerUuids) {
        Ref<EntityStore> entityRef = animal.getEntityRef();
        AnimalType animalType = animal.getAnimalType();
        String modelId = animal.getModelAssetId();

        // Check if this modelId matches any registered custom animal
        if (configManager.isCustomAnimal(modelId)) {
            if (verboseLogging)
                getLogger().atInfo().log(
                        "[AutoScan] Processing potential custom animal: '%s' (animalType=%s)", modelId,
                        animalType);
        }

        // Skip if this is a player entity (prevents attaching interactions to players
        // with animal models)
        if (entityRef instanceof Ref) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            // Skip UUID check if ref is stale - entity despawned, just proceed
            if (!ref.isValid()) {
                logVerbose("[AnimalScan] Skipping stale entity ref for " + animal.getModelAssetId());
                return;
            }
            try {
                Store<EntityStore> refStore = ref.getStore();
                if (refStore != null) {
                    UUIDComponent uuidComp = refStore.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
                    if (uuidComp != null && uuidComp.getUuid() != null) {
                        if (playerUuids.contains(uuidComp.getUuid())) {
                            logVerbose("Skipping player entity with animal model: "
                                    + animal.getModelAssetId());
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // UUID check failed - this happens when UUIDComponent doesn't exist
                // This is OK: the check is only to filter players, not to validate animals
                // Don't skip any animals - proceed with interaction setup
                if (configManager.isCustomAnimal(modelId)) {
                    logVerbose("[CustomAnimal] " + modelId
                            + " has no UUID component (expected for custom NPCs), proceeding");
                } else {
                    logVerbose("[AnimalScan] UUID check failed for " + animal.getModelAssetId()
                            + " (proceeding anyway): " + e.getMessage());
                }
                // Note: Do NOT skip - both built-in and custom animals should proceed
            }
        }

        // Check if it's a custom animal (from config)
        CustomAnimalConfig customAnimal = null;
        if (animalType == null) {
            customAnimal = configManager.getCustomAnimal(modelId);
            // Debug: log custom animal lookup attempts
            if (customAnimal != null) {
                if (verboseLogging)
                    getLogger().atInfo().log("[CustomAnimal] Found match for '%s' (enabled=%s)",
                            modelId, customAnimal.isEnabled());
            } else if (configManager.getCustomAnimals().size() > 0) {
                // Only log if there are custom animals registered - ALWAYS LOG THIS
                if (verboseLogging)
                    getLogger().atInfo().log("[CustomAnimal] No match for '%s' (registered: %s)",
                            modelId, String.join(", ", configManager.getCustomAnimals().keySet()));
            }
        } else {
            // Log if a potential custom animal is being detected as built-in
            if (configManager.isCustomAnimal(modelId)) {
                getLogger().atWarning().log(
                        "[CustomAnimal] '%s' matched as built-in %s instead of custom!", modelId,
                        animalType);
            }
        }

        // Skip if not a recognized farm animal OR custom animal
        if (animalType == null && customAnimal == null) {
            return;
        }

        // Skip if breeding is disabled
        if (animalType != null && !configManager.isAnimalEnabled(animalType)) {
            logVerbose("Skipping disabled animal: " + animalType);
            return;
        }
        if (customAnimal != null && !customAnimal.isEnabled()) {
            logVerbose("Skipping disabled custom animal: " + animal.getModelAssetId());
            return;
        }

        // Check if this is a baby that needs growth tracking
        if (animal.isBaby() && animalType != null) {
            UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
            if (breedingManager.getData(babyId) == null) {
                breedingManager.registerBaby(babyId, animalType, entityRef);
            }
        }

        // Set up interactions for adults (babies can't breed)
        if (!animal.isBaby()) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            Store<EntityStore> refStore = ref.getStore();
            if (refStore != null) {
                if (USE_ENTITY_BASED_INTERACTIONS) {
                    // Legacy: Set up entity-based interactions (Use key)
                    if (animalType != null) {
                        logVerbose("Setting up interactions for adult: " + animal.getModelAssetId()
                                + " (type: "
                                + animalType + ")");
                        interactionSetupManager.setupEntityInteractions(refStore, ref, animalType);
                    } else if (customAnimal != null) {
                        if (verboseLogging)
                            getLogger().atInfo().log(
                                    "[CustomAnimal] ABOUT TO CALL setupCustomAnimalInteractions for: %s",
                                    animal.getModelAssetId());
                        interactionSetupManager.setupCustomAnimalInteractions(refStore, ref, customAnimal);
                    }
                } else if (SHOW_ABILITY2_HINTS_ON_ENTITIES) {
                    // Item-based with hints: Show Ability2 hint on animals
                    String hintKey = (animalType != null && animalType.isMountable())
                            ? "animalbreeding.interactionHints.feed"
                            : "animalbreeding.interactionHints.feed";
                    interactionSetupManager.setupAbility2HintOnly(refStore, ref, hintKey);
                }
            } else {
                getLogger().atWarning().log("[CustomAnimal] refStore is NULL for: %s",
                        animal.getModelAssetId());
            }
        } else {
            if (customAnimal != null) {
                if (verboseLogging)
                    getLogger().atInfo().log("[CustomAnimal] Skipping baby custom animal: %s",
                            animal.getModelAssetId());
            }
        }
    }

    /**
     * Register the player interaction event handler for breeding.
     * Delegates to MouseInteractionHandler for actual event processing.
     */
    private void registerInteractionHandler() {
        try {
            getEventRegistry().register(PlayerMouseButtonEvent.class, mouseInteractionHandler::onMouseButton);
        } catch (Exception e) {
        }
        try {
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, mouseInteractionHandler::onPlayerInteract);
        } catch (Exception e) {
        }
    }

    // NOTE: onMouseButton, onPlayerInteract, handleMouseClick, capitalize moved to MouseInteractionHandler
    // NOTE: consumeHeldItem(), playFeedingSound(), isNameTagItem(), spawnBabyAnimal() - removed as unused
    // NOTE: Spawning moved to SpawningManager, breeding tick to BreedingTickManager

    /**
     * Scan the world for untracked baby animals and register them.
     * This is a fallback detection system for babies that slipped through primary
     * registration.
     *
     * @return Number of newly registered babies
     */
    @SuppressWarnings("unchecked")
    public int scanForUntrackedBabies() {
        int registered = 0;
        try {
            java.util.List<BreedingManager.UntrackedBaby> untrackedBabies = new java.util.ArrayList<>();

            // Scan all worlds for untracked babies
            for (java.util.Map.Entry<String, World> entry : Universe.get().getWorlds().entrySet()) {
                World world = entry.getValue();
                if (world == null) continue;

                Store<EntityStore> store = world.getEntityStore().getStore();
                if (store == null) continue;

                world.execute(() -> {
                    store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                        int size = chunk.size();
                        for (int i = 0; i < size; i++) {
                            try {
                                Ref<EntityStore> ref = chunk.getReferenceTo(i);

                                String modelAssetId = getEntityModelAssetId(store, ref);
                                if (modelAssetId == null)
                                    continue;

                                // Check if this is a baby model
                                if (!AnimalType.isBabyVariant(modelAssetId))
                                    continue;

                                // Get the animal type for this baby
                                AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
                                if (animalType == null)
                                    continue;

                                // Check if already tracked
                                UUID refUuid = UUID.nameUUIDFromBytes(ref.toString().getBytes());
                                if (breedingManager.isBabyTracked(ref, refUuid))
                                    continue;

                                // Found an untracked baby
                                synchronized (untrackedBabies) {
                                    untrackedBabies.add(new BreedingManager.UntrackedBaby(ref, modelAssetId, animalType));
                                }
                            } catch (Exception e) {
                                // Skip invalid refs
                            }
                        }
                    });
                });
            }

            // Register all untracked babies
            for (BreedingManager.UntrackedBaby baby : untrackedBabies) {
                UUID babyId = UUID.nameUUIDFromBytes(baby.getEntityRef().toString().getBytes());
                breedingManager.registerBaby(babyId, baby.getAnimalType(), baby.getEntityRef());
                registered++;
                logVerbose("[BabyScan] Registered untracked baby: " + baby.getModelAssetId());
            }

            if (registered > 0 || verboseLogging) {
                if (registered > 0) {
                    getLogger().atInfo().log("[BabyScan] Found %d untracked babies across all worlds, registered all", registered);
                }
            }

        } catch (Exception e) {
            logVerbose("[BabyScan] Error: " + e.getMessage());
        }
        return registered;
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("[Lait:AnimalBreeding] Plugin shutdown");

        // Stop tick scheduler and wait for tasks to finish
        if (tickScheduler != null) {
            // Cancel all scheduled tasks first
            for (ScheduledFuture<?> task : scheduledTasks) {
                task.cancel(false);
            }
            scheduledTasks.clear();

            tickScheduler.shutdown();
            try {
                if (!tickScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    tickScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickScheduler.shutdownNow();
            }
        }

        // Save tamed animal data before shutdown (must be sync to ensure completion)
        if (persistenceManager != null && tamingManager != null) {
            getLogger().atInfo().log("[Taming] Saving tamed animals on shutdown...");
            persistenceManager.stopAutoSave();
            persistenceManager.forceSaveSync(tamingManager.getAllTamedAnimals());
        }

        // Clear breeding data
        if (breedingManager != null) {
            breedingManager.clearAll();
        }

        // Clear coop storage tracking
        CoopResidentTracker.clearStorage();

        // Clear static instance
        instance = null;
    }

    public static LaitsBreedingPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public BreedingManager getBreedingManager() {
        return breedingManager;
    }

    public TamingManager getTamingManager() {
        return tamingManager;
    }

    public PersistenceManager getPersistenceManager() {
        return persistenceManager;
    }

    public GrowthManager getGrowthManager() {
        return growthManager;
    }

    public SpawningManager getSpawningManager() {
        return spawningManager;
    }

    /**
     * Get the HyTameComponent type for ECS operations.
     * Used by TameHelper and other classes that need to check/set tame state.
     */
    public ComponentType<EntityStore, HyTameComponent> getHyTameComponentType() {
        return hyTameComponentType;
    }

    /**
     * Get the HyTameInteractionComponent type for ECS operations.
     * Used to persist original interaction state across server restarts.
     */
    public ComponentType<EntityStore, HyTameInteractionComponent> getHyTameInteractionComponentType() {
        return hyTameInteractionComponentType;
    }

}
