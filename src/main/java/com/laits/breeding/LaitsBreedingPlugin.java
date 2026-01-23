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
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.CameraSettings;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.PickupLocation;
import com.hypixel.hytale.protocol.packets.camera.SetFlyCameraMode;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerCreativeSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.RespondToHit;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.GrowthManager;
import com.laits.breeding.managers.TamingManager;
import com.laits.breeding.managers.PersistenceManager;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.listeners.UseBlockHandler;
import com.laits.breeding.listeners.LaitDamageDisabler;
import com.laits.breeding.listeners.NewAnimalSpawnDetector;
import com.laits.breeding.interactions.FeedAnimalInteraction;
import com.laits.breeding.interactions.NameAnimalInteraction;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.OriginalInteractionState;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.AnimalFinder;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.commands.BreedCommand;
import com.laits.breeding.commands.BreedingConfigCommand;
import com.laits.breeding.commands.CustomAnimalCommand;
import com.laits.breeding.commands.LegacyCommands;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for Lait's Animal Breeding.
 */
public class LaitsBreedingPlugin extends JavaPlugin {

    public static final String VERSION = "1.3.1";

    private static LaitsBreedingPlugin instance;

    // Heart particle system ID for breeding love effect
    // Custom particle with shorter duration (extends vanilla Hearts)
    // Asset location: Server/Particles/BreedingHearts.particlesystem
    private static final String HEARTS_PARTICLE = "BreedingHearts";

    // Breeding distance - animals must be within this range to breed
    private static final double BREEDING_DISTANCE = 5.0;
    // How long animals stay in love (milliseconds)
    private static final long LOVE_DURATION = 30000; // 30 seconds

    private ConfigManager configManager;
    private BreedingManager breedingManager;
    private GrowthManager growthManager;
    private TamingManager tamingManager;
    private PersistenceManager persistenceManager;
    private ScheduledExecutorService tickScheduler;
    private final List<ScheduledFuture<?>> scheduledTasks = new ArrayList<>();
    private NewAnimalSpawnDetector spawnDetector;

    // Reusable collections for tickLoveAnimals (avoid allocation per tick)
    private final java.util.Map<AnimalType, java.util.List<BreedingData>> tickLoveByType = new java.util.HashMap<>();
    private final java.util.List<Object> tickLoveEntityRefs = new java.util.ArrayList<>();

    // Getter for tick scheduler (used by commands)
    public ScheduledExecutorService getTickScheduler() {
        return tickScheduler;
    }

    // Getter for spawn detector (used by commands)
    public NewAnimalSpawnDetector getSpawnDetector() {
        return spawnDetector;
    }

    // ECS component types are now in EcsReflectionUtil

    // Event counters for diagnostics
    private static int playerReadyCount = 0;
    private static int mouseClickCount = 0;
    private static int useBlockPreCount = 0;
    private static int useBlockPostCount = 0;

    public static int getPlayerReadyCount() {
        return playerReadyCount;
    }

    public static int getMouseClickCount() {
        return mouseClickCount;
    }

    public static int getUseBlockPreCount() {
        return useBlockPreCount;
    }

    public static int getUseBlockPostCount() {
        return useBlockPostCount;
    }

    // Interaction cache is now typed via static final fields above
    private boolean interactionCacheInitialized = true; // Always true with direct imports

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

    // Entity-based interaction system - controlled by build variant (see BuildConfig)
    // When true: Sets "Press [F] to Feed" hints directly on animal entities (Use key)
    // When false: Uses item-based Ability2 interactions (food templates have Ability2: Root_FeedAnimal)
    // Value is set at build time via Gradle: buildAbility2 (false) or buildEntityBased (true)
    private static final boolean USE_ENTITY_BASED_INTERACTIONS = BuildConfig.USE_ENTITY_BASED_INTERACTIONS;

    // Show interaction hints on animals even when using item-based Ability2
    // When true: Animals show "Press [Ability2] to Feed" hint (but actual feeding is via item)
    // When false: No hints on animals (player must know to use Ability2)
    // Only applies when USE_ENTITY_BASED_INTERACTIONS is false
    private static final boolean SHOW_ABILITY2_HINTS_ON_ENTITIES = true;

    // Store original interaction state (ID + hint) before we override them
    // Used to restore original behavior when feeding doesn't make sense (love mode, cooldown)
    // Key is entity UUID string (stable across different Ref objects for same entity)
    private static final Map<String, OriginalInteractionState> originalStates = new ConcurrentHashMap<>();

    /**
     * Check if an entity ref corresponds to a player (prevents treating players with animal models as animals).
     * COPIED FROM FeedAnimalInteraction.isPlayerEntity() - known working implementation.
     */
    private boolean isPlayerEntity(Ref<EntityStore> ref) {
        try {
            UUID entityUuid = getUuidFromRef(ref);
            if (entityUuid == null) return false;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return false;

            for (Player player : world.getPlayers()) {
                UUID playerUuid = getPlayerUuidFromPlayer(player);
                if (entityUuid.equals(playerUuid)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Silent - assume not a player if we can't check
        }
        return false;
    }

    /**
     * Get UUID from an entity ref.
     * Delegates to EcsReflectionUtil.
     */
    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        return EcsReflectionUtil.getUuidFromRef(ref);
    }

    /**
     * Get UUID from a Player entity.
     * COPIED FROM FeedAnimalInteraction.getPlayerUuidFromPlayer() - known working implementation.
     */
    @SuppressWarnings("unchecked")
    private UUID getPlayerUuidFromPlayer(Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                Store<EntityStore> store = ((Ref<EntityStore>) entityRef).getStore();
                if (store != null) {
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, EcsReflectionUtil.UUID_TYPE);
                    if (uuidComp != null) {
                        return uuidComp.getUuid();
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get the original interaction ID for an entity (before we set Root_FeedAnimal).
     * Used by FeedAnimalInteraction to fall back to default behavior (e.g., mounting).
     */
    public static String getOriginalInteractionId(Object entityRef) {
        return getOriginalInteractionId(entityRef, null);
    }

    /**
     * Get the original interaction ID for an entity.
     * Only returns an ID if we actually saved the original interaction when setting up the entity.
     * Does NOT assume a fallback like "Root_Mount" as it may not exist in all versions.
     */
    public static String getOriginalInteractionId(Object entityRef, AnimalType animalType) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key != null) {
            OriginalInteractionState stored = originalStates.get(key);
            if (stored != null && stored.hasInteraction()) {
                return stored.getInteractionId();
            }
        }
        // No fallback - if we didn't save the original, we don't know what it was
        return null;
    }

    /**
     * Get the full original interaction state for an entity.
     */
    public static OriginalInteractionState getOriginalState(Object entityRef) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key != null) {
            return originalStates.get(key);
        }
        return null;
    }

    /**
     * Store the original interaction state (ID + hint) for an entity.
     * ALWAYS stores, even if interactionId is null - that's the correct original state for horses
     * (null Use interaction allows mounting to work via default behavior).
     */
    private static void storeOriginalState(Ref<EntityStore> entityRef, String interactionId, String hint,
            AnimalType animalType) {
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key == null)
            return;

        // Always store the original state, even if interactionId is null
        // For horses, null is the correct original state - it allows mounting to work
        originalStates.put(key, new OriginalInteractionState(interactionId, hint));
    }

    /**
     * Legacy overload for backwards compatibility - stores interaction ID without hint.
     */
    private static void storeOriginalInteractionId(Ref<EntityStore> entityRef, String interactionId,
            AnimalType animalType) {
        storeOriginalState(entityRef, interactionId, null, animalType);
    }

    /**
     * Legacy overload for backwards compatibility.
     */
    private static void storeOriginalInteractionId(Ref<EntityStore> entityRef, String interactionId) {
        storeOriginalState(entityRef, interactionId, null, null);
    }

    /**
     * Clean up stale entries in originalStates map.
     * Removes index-based keys (ephemeral) and validates UUID-based keys.
     * @return Number of entries removed
     */
    private int cleanupStaleOriginalInteractions() {
        int removed = 0;
        Iterator<Map.Entry<String, OriginalInteractionState>> it = originalStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, OriginalInteractionState> entry = it.next();
            String key = entry.getKey();
            // Index-based keys are ephemeral and should be cleaned up periodically
            if (key.startsWith("idx:")) {
                it.remove();
                removed++;
            }
            // UUID-based keys could be validated, but for simplicity we rely on EntityRemoveEvent
        }
        return removed;
    }

    /**
     * Get the current size of the originalStates cache (for debugging).
     */
    public static int getOriginalInteractionsCacheSize() {
        return originalStates.size();
    }

    /** Log verbose/debug message (only when verbose logging is enabled) */
    private void logVerbose(String message) {
        if (verboseLogging) {
            getLogger().atInfo().log("[Lait:AnimalBreeding] " + message);
            // Also broadcast to chat if devMode is enabled
            if (devMode) {
                broadcastToChat(message);
            }
        }
    }

    /** Broadcast a message to all online players in chat */
    private void broadcastToChat(String message) {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            world.getPlayers().forEach(player -> {
                try {
                    player.sendMessage(Message.raw("[Breeding] " + message).color("#AAAAAA"));
                } catch (Exception e) {
                    // Silent
                }
            });
        } catch (Exception e) {
            // Silent
        }
    }

    /** Log warning message */
    private void logWarning(String message) {
        getLogger().atWarning().log("[Lait:AnimalBreeding] " + message);
    }

    /** Log error message */
    private void logError(String message) {
        getLogger().atSevere().log("[Lait:AnimalBreeding] " + message);
    }

    /**
     * Broadcast a development debug message to all online players.
     * Only works when devMode is enabled via /breeddev command.
     * Use this for in-game debugging during development.
     */
    private void devLog(String message) {
        if (!devMode)
            return;

        try {
            // Also log to server console
            getLogger().atInfo().log("[DEV] " + message);

            // Broadcast to all online players
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            // Get all players from the world
            Object entityStore = world.getClass().getMethod("getEntityStore").invoke(world);
            Object store = entityStore.getClass().getMethod("getStore").invoke(entityStore);

            // Use forEachChunk to find all players
            java.lang.reflect.Method forEachMethod = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("forEachChunk") && m.getParameterCount() == 1) {
                    forEachMethod = m;
                    break;
                }
            }

            if (forEachMethod == null)
                return;

            Class<?> consumerClass = forEachMethod.getParameterTypes()[0];
            final String devMessage = "[DEV] " + message;

            Object consumer = java.lang.reflect.Proxy.newProxyInstance(
                    consumerClass.getClassLoader(),
                    new Class<?>[] { consumerClass },
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept") && args != null && args.length >= 2) {
                            Object chunk = args[0];
                            try {
                                int size = (Integer) chunk.getClass().getMethod("size").invoke(chunk);
                                for (int i = 0; i < size; i++) {
                                    // Try to get player and send message
                                    Object ref = chunk.getClass().getMethod("getReferenceTo", int.class).invoke(chunk,
                                            i);
                                    if (ref != null) {
                                        // Check if this is a player by trying to call sendMessage
                                        try {
                                            java.lang.reflect.Method sendMsg = ref.getClass().getMethod("sendMessage",
                                                    Message.class);
                                            sendMsg.invoke(ref, Message.raw(devMessage).color("#FFAA00"));
                                        } catch (NoSuchMethodException e) {
                                            // Not a player, skip
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Silent
                            }
                        }
                        return null;
                    });

            forEachMethod.invoke(store, consumer);

        } catch (Exception e) {
            // Silent - dev logging should never crash
        }
    }

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
        configManager.setLogger(msg -> { if (verboseLogging) getLogger().atInfo().log(msg); });

        // Load config from plugin's data directory (created automatically by the
        // server)
        java.nio.file.Path configPath = getDataDirectory().resolve("config.json");
        configManager.loadFromFile(configPath);

        breedingManager = new BreedingManager(configManager);
        growthManager = new GrowthManager(configManager, breedingManager);

        // Initialize taming and persistence managers
        persistenceManager = new PersistenceManager();
        persistenceManager.setLogger(msg -> { if (verboseLogging) getLogger().atInfo().log("[Taming] " + msg); });
        persistenceManager.initialize(getDataDirectory());

        tamingManager = new TamingManager();
        tamingManager.setLogger(msg -> { if (verboseLogging) getLogger().atInfo().log("[Taming] " + msg); });
        tamingManager.setPersistenceManager(persistenceManager);

        // Load saved tamed animals
        java.util.List<TamedAnimalData> savedAnimals = persistenceManager.loadData();
        tamingManager.loadFromPersistence(savedAnimals);

        // Set up growth callback - handle growth stage changes
        growthManager.setOnGrowthCallback(event -> {
            if (event.usesScaling()) {
                // Creatures without baby variants: update scale at each stage
                updateEntityScale(event.getAnimalId(), event.getAnimalType(), event.getTargetScale());
                if (event.getNewStage() == GrowthStage.ADULT) {
                    // Clean up tracking data when fully grown
                    breedingManager.removeData(event.getAnimalId());
                }
            } else {
                // Animals with baby variants: replace entity when adult
                if (event.getNewStage() == GrowthStage.ADULT) {
                    transformBabyToAdult(event.getAnimalId(), event.getAnimalType());
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

        // Register ECS system for block interactions
        try {
            getEntityStoreRegistry().registerSystem(new UseBlockHandler());
            // TODO: LaitDamageDisabler needs to implement ISystem<EntityStore> - commenting out for now
            // getEntityStoreRegistry().registerSystem(new LaitDamageDisabler());
        } catch (Exception e) {
            // Silent
        }

        // NOTE: NewAnimalSpawnDetector is registered in start() after world is ready

        // Register unified /breed command (recommended)
        getCommandRegistry().registerCommand(new BreedCommand());

        // Register legacy commands (with deprecation warnings)
        // These are kept for backwards compatibility but show deprecation notices
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingHelpCommand());      // Use /breed help
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingStatusCommand());    // Use /breed status
        getCommandRegistry().registerCommand(new BreedingConfigCommand());    // Use /breed config
        getCommandRegistry().registerCommand(new LegacyCommands.BreedingGrowthCommand());    // Use /breed growth
        getCommandRegistry().registerCommand(new LegacyCommands.NameTagCommand());           // Use /breed tame
        getCommandRegistry().registerCommand(new LegacyCommands.TamingInfoCommand());        // Use /breed info
        getCommandRegistry().registerCommand(new LegacyCommands.TamingSettingsCommand());    // Use /breed settings
        getCommandRegistry().registerCommand(new LegacyCommands.UntameCommand());            // Use /breed untame
        getCommandRegistry().registerCommand(new CustomAnimalCommand());      // Use /breed custom

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
        try {
            Class<?> rootIntClass = Class
                    .forName("com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction");
            java.lang.reflect.Method preloadMethod = rootIntClass.getMethod("getRootInteractionOrUnknown",
                    String.class);
            Object rootInt = preloadMethod.invoke(null, "Root_FeedAnimal");

            java.lang.reflect.Field idsField = rootIntClass.getDeclaredField("interactionIds");
            idsField.setAccessible(true);
            String[] ids = (String[]) idsField.get(rootInt);

            if (ids == null || ids.length == 0) {
                String[] newIds = new String[] { "FeedAnimal" };
                idsField.set(rootInt, newIds);
            }
        } catch (Exception e) {
            logWarning("RootInteraction configuration failed: " + e.getMessage());
        }

        // Initialize reflection cache for performance
        initReflectionCache();

        // Start tick scheduler for pregnancy and growth updates
        tickScheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
            try {
                breedingManager.tickPregnancies();
                growthManager.tickGrowth();
                tickLoveAnimals();
                updateTrackedAnimalStates(); // Dynamic hint switching based on love/cooldown
            } catch (Exception e) {
                // Log tick errors for debugging
                getLogger().atWarning().log("[Tick] Error: " + e.getMessage());
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
                checkAndRespawnTamedAnimals();
            } catch (Exception e) {
                // Silent - respawn errors shouldn't crash the plugin
            }
        }, 5, 5, TimeUnit.SECONDS));

        // Attach Root_FeedAnimal interaction to all breedable animals
        attachInteractionsToAnimals();

        // Register entity removal listener to clean up breeding data when animals die
        registerEntityRemovalListener();

        // Register ECS system for detecting new animal spawns (must be in start() after
        // world is ready)
        try {
            spawnDetector = new NewAnimalSpawnDetector();
            getEntityStoreRegistry().registerSystem(spawnDetector);
            logVerbose("NewAnimalSpawnDetector system registered in start()");

            // Periodically update player UUIDs for the spawn detector to exclude players
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (spawnDetector != null) {
                        Set<UUID> currentPlayerUuids = ConcurrentHashMap.newKeySet();
                        World world = Universe.get().getDefaultWorld();
                        if (world != null) {
                            for (Player p : world.getPlayers()) {
                                UUID pUuid = getEntityUUID(p);
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

            // Periodically clean up originalStates map (safety net for missed EntityRemoveEvents)
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    int removed = cleanupStaleOriginalInteractions();
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

            // Periodically clean up expired pending taming entries
            scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (tamingManager != null) {
                        int removed = tamingManager.cleanupExpiredPending();
                        if (removed > 0) {
                            logVerbose("Cleaned " + removed + " expired pending taming entries");
                        }
                    }
                } catch (Exception e) {
                    // Silent
                }
            }, 5, 5, TimeUnit.MINUTES));
        } catch (Exception e) {
            logWarning("NewAnimalSpawnDetector registration failed: " + e.getMessage());
            spawnDetector = null;
        }

        getLogger().atInfo().log("[Lait:AnimalBreeding] Plugin started! Commands: /laitsbreeding, /breedstatus");
    }

    /**
     * Initialize cached objects - now mostly done via static final fields.
     * This method is kept for any remaining runtime initialization.
     */
    private void initReflectionCache() {
        // Component types are now static final fields using direct imports
        // No reflection needed - all types are initialized at class load time
        logVerbose("ECS component types initialized via direct imports");
    }

    /**
     * Attaching interactions to animals via periodic scanning.
     * Note: Event-based detection (PrefabPlaceEntityEvent, LoadedNPCEvent) was
     * tested
     * but these events don't fire for natural animal spawns in Hytale.
     */
    public void attachInteractionsToAnimals() {
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

        // Safety net: Periodic scan every 30 seconds (primary detection via
        // NewAnimalSpawnDetector)
        scheduledTasks.add(tickScheduler.scheduleAtFixedRate(() -> {
            try {
                autoSetupNearbyAnimals();
            } catch (Exception e) {
                // Silent
            }
        }, 30, 30, TimeUnit.SECONDS));
    }

    /**
     * Set up interactions for a single entity if it's a breedable animal.
     * Must be called from the world thread.
     *
     * @param world     The world containing the entity
     * @param entityRef The entity reference
     */
    private void setupSingleEntity(World world, Ref<EntityStore> entityRef) {
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();

            // Get the model asset ID to identify the animal type
            String modelAssetId = getEntityModelAssetId(store, entityRef);
            if (modelAssetId == null)
                return;

            // Check if it's a farm animal
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            if (animalType == null)
                return; // Not a recognized animal

            logVerbose("Setting up animal: " + modelAssetId + " (" + animalType + ")");

            // Skip if breeding is disabled for this animal type
            if (!configManager.isAnimalEnabled(animalType))
                return;

            boolean isBaby = AnimalType.isBabyVariant(modelAssetId);

            // Register babies for growth tracking
            if (isBaby) {
                UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
                if (breedingManager.getData(babyId) == null) {
                    breedingManager.registerBaby(babyId, animalType, entityRef);
                    logVerbose("Registered baby for growth tracking: " + modelAssetId);
                }
            }

            // Set up interactions for adults (babies can't breed)
            if (!isBaby) {
                setupEntityInteractions(store, entityRef, animalType);
            }

        } catch (IllegalStateException e) {
            // Check for stale ref error
            if (e.getMessage() != null && e.getMessage().contains("Invalid entity")) {
                // Entity was despawned - ignore
                return;
            }
            logVerbose("setupSingleEntity error: " + e.getMessage());
        } catch (Exception e) {
            logVerbose("setupSingleEntity error: " + e.getMessage());
        }
    }

    /**
     * Callback from NewAnimalSpawnDetector when a new animal is detected.
     * This provides immediate detection instead of waiting for periodic scans.
     *
     * NOTE: This is called from within the ECS tick, so we must defer interaction
     * setup to after the tick completes via world.execute().
     *
     * @param store        The entity store
     * @param entityRef    The newly spawned entity reference
     * @param modelAssetId The model asset ID (e.g., "Cow", "Sheep")
     * @param animalType   The detected animal type
     */
    public void onNewAnimalDetected(Store<EntityStore> store, Ref<EntityStore> entityRef,
            String modelAssetId, AnimalType animalType) {
        try {
            if (entityRef == null || !entityRef.isValid())
                return;

            logVerbose("NewAnimalSpawnDetector: Immediate detection of " + modelAssetId);

            // Check if it's a custom animal (animalType will be null for custom)
            CustomAnimalConfig customAnimal = null;
            if (animalType == null) {
                customAnimal = configManager.getCustomAnimal(modelAssetId);
            }

            // Skip if breeding is disabled for this animal type
            if (animalType != null && !configManager.isAnimalEnabled(animalType)) {
                logVerbose("Skipping disabled animal: " + animalType);
                return;
            }
            if (customAnimal != null && !customAnimal.isEnabled()) {
                logVerbose("Skipping disabled custom animal: " + modelAssetId);
                return;
            }

            boolean isBaby = AnimalType.isBabyVariant(modelAssetId);

            // Register babies for growth tracking (safe to do during tick)
            if (isBaby && animalType != null) {
                UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
                if (breedingManager.getData(babyId) == null) {
                    breedingManager.registerBaby(babyId, animalType, entityRef);
                    logVerbose("Registered new baby for growth tracking: " + modelAssetId);
                }
            }

            // Set up interactions for adults - must be deferred to after the tick
            // Component modifications during ECS tick may not work correctly
            if (!isBaby) {
                final Ref<EntityStore> finalEntityRef = entityRef;
                final AnimalType finalAnimalType = animalType;
                final CustomAnimalConfig finalCustomAnimal = customAnimal;
                final String finalModelAssetId = modelAssetId;

                World world = Universe.get().getDefaultWorld();
                if (world != null) {
                    world.execute(() -> {
                        try {
                            if (!finalEntityRef.isValid())
                                return;
                            Store<EntityStore> worldStore = world.getEntityStore().getStore();

                            if (USE_ENTITY_BASED_INTERACTIONS) {
                                // Legacy: Set up entity-based interactions (Use key)
                                if (finalAnimalType != null) {
                                    setupEntityInteractions(worldStore, finalEntityRef, finalAnimalType);
                                    logVerbose("Interactions set up for new animal: " + finalModelAssetId);
                                } else if (finalCustomAnimal != null) {
                                    setupCustomAnimalInteractions(worldStore, finalEntityRef, finalCustomAnimal);
                                    if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] Interactions set up for: %s", finalModelAssetId);
                                }
                            } else if (SHOW_ABILITY2_HINTS_ON_ENTITIES) {
                                // Item-based with hints: Show Ability2 hint on animals
                                String hintKey = (finalAnimalType != null && finalAnimalType.isMountable())
                                        ? "animalbreeding.interactionHints.feed"
                                        : "animalbreeding.interactionHints.feed";
                                setupAbility2HintOnly(worldStore, finalEntityRef, hintKey);
                                logVerbose("Ability2 hint set up for: " + finalModelAssetId);
                            } else {
                                logVerbose("New adult animal detected: " + finalModelAssetId + " (no hints)");
                            }
                        } catch (Exception e) {
                            logVerbose("Deferred interaction setup error: " + e.getMessage());
                        }
                    });
                }
            }

        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("Invalid entity")) {
                return; // Entity already despawned
            }
            logVerbose("onNewAnimalDetected error: " + e.getMessage());
        } catch (Exception e) {
            logVerbose("onNewAnimalDetected error: " + e.getMessage());
        }
    }

    /**
     * Get the model asset ID from an entity reference.
     */
    private String getEntityModelAssetId(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        return EcsReflectionUtil.getEntityModelAssetId(store, entityRef);
    }

    /**
     * Set up breeding interactions on a single entity.
     * Note: Interactions methods are not public, so we must use reflection.
     */
    private void setupEntityInteractions(Store<EntityStore> store, Ref<EntityStore> entityRef, AnimalType animalType) {
        try {
            // Skip players (even if they have animal models) - same check as FeedAnimalInteraction
            if (isPlayerEntity(entityRef)) {
                logVerbose("[SetupInteraction] Skipping player entity with animal model");
                return;
            }

            // Skip babies - they can't breed
            String modelAssetId = getEntityModelAssetId(store, entityRef);
            if (modelAssetId != null && AnimalType.isBabyVariant(modelAssetId)) {
                logVerbose("[SetupInteraction] Skipping baby animal: " + modelAssetId);
                return;
            }

            // Get component types via reflection
            Object interactableType = EcsReflectionUtil.getInteractableComponentType();
            Object interactionsType = EcsReflectionUtil.getInteractionsComponentType();
            if (interactionsType == null) {
                getLogger().atWarning().log("[SetupInteraction] interactionsType is NULL for %s", animalType);
                return;
            }

            // Ensure entity has Interactable component (required for hints to display in solo mode)
            if (interactableType != null) {
                try {
                    java.lang.reflect.Method ensureMethod = store.getClass().getMethod(
                            "ensureAndGetComponent", Ref.class, ComponentType.class);
                    ensureMethod.invoke(store, entityRef, interactableType);
                } catch (Exception e) {
                    // Silent - component may already exist
                }
            }

            // Check if entity already has Interactions component (real NPCs have this)
            // Use getComponent instead of ensureAndGetComponent to avoid adding to non-NPCs
            java.lang.reflect.Method getCompMethod = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    getCompMethod = m;
                    break;
                }
            }
            if (getCompMethod == null) return;

            Object interactions = getCompMethod.invoke(store, entityRef, interactionsType);
            if (interactions == null) {
                // Entity doesn't have Interactions component - not a real NPC, skip
                logVerbose("[SetupInteraction] Skipping non-NPC entity (no Interactions component)");
                return;
            }

            String feedInteractionId = "Root_FeedAnimal";

            // Use reflection for non-public methods - use Class.forName to avoid
            // classloader issues
            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            Object useType = null;
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            java.lang.reflect.Method getIntId = interactions.getClass().getMethod(
                    "getInteractionId", interactionTypeClass);
            String currentUse = (String) getIntId.invoke(interactions, useType);

            // Get current hint BEFORE overwriting (for restoration later)
            java.lang.reflect.Method getHint = interactions.getClass().getMethod("getInteractionHint");
            String currentHint = (String) getHint.invoke(interactions);

            if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                // Save original interaction ID AND hint for fallback (e.g., horse mounting)
                storeOriginalState(entityRef, currentUse, currentHint, animalType);
                if (verboseLogging) getLogger().atInfo().log("[BuiltIn] %s: set interaction to %s (was: %s, hint was: %s)",
                    animalType, feedInteractionId, currentUse, currentHint);

                java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                        "setInteractionId", interactionTypeClass, String.class);
                setIntId.invoke(interactions, useType, feedInteractionId);
            }

            // ALWAYS set the interaction hint (even if interaction was already set)
            java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                    "setInteractionHint", String.class);
            // Use combined hint for mountable animals (Feed / Mount)
            String hintKey = animalType.isMountable()
                    ? "animalbreeding.interactionHints.legacyFeedOrMount"
                    : "animalbreeding.interactionHints.legacyFeed";
            setHint.invoke(interactions, hintKey);
            if (verboseLogging) getLogger().atInfo().log("[SetupInteraction] SUCCESS for %s: interactionId=%s, hint=%s", animalType, feedInteractionId, hintKey);

        } catch (Exception e) {
            getLogger().atWarning().log("[SetupInteraction] ERROR for %s: %s", animalType, e.getMessage());
        }
    }

    /**
     * Set up breeding interactions on a custom animal entity (from config).
     * IDENTICAL to setupEntityInteractions - copy-pasted to ensure same behavior.
     */
    private void setupCustomAnimalInteractions(Store<EntityStore> store, Ref<EntityStore> entityRef, CustomAnimalConfig customAnimal) {
        String animalName = customAnimal.getModelAssetId();
        if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] setupCustomAnimalInteractions CALLED for: %s", animalName);
        try {
            // Skip players (even if they have animal models) - same check as FeedAnimalInteraction
            if (isPlayerEntity(entityRef)) {
                logVerbose("[CustomAnimal] Skipping player entity with custom animal model: " + animalName);
                return;
            }

            // Skip babies - they can't breed (check for known baby variant patterns)
            String actualModelId = getEntityModelAssetId(store, entityRef);
            if (actualModelId != null && AnimalType.isBabyVariant(actualModelId)) {
                logVerbose("[CustomAnimal] Skipping baby animal: " + actualModelId);
                return;
            }

            Object interactableType = EcsReflectionUtil.getInteractableComponentType();
            Object interactionsType = EcsReflectionUtil.getInteractionsComponentType();
            if (interactionsType == null) {
                getLogger().atWarning().log("[CustomAnimal] %s: interactionsType is NULL, aborting", animalName);
                return;
            }

            // Ensure entity has Interactable component (required for hints to display in solo mode)
            if (interactableType != null) {
                try {
                    java.lang.reflect.Method ensureMethod = store.getClass().getMethod(
                            "ensureAndGetComponent", Ref.class, ComponentType.class);
                    ensureMethod.invoke(store, entityRef, interactableType);
                } catch (Exception e) {
                    // Silent - component may already exist
                }
            }

            // Check if entity already has Interactions component (real NPCs have this)
            // Use getComponent instead of ensureAndGetComponent to avoid adding to non-NPCs
            java.lang.reflect.Method getCompMethod = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    getCompMethod = m;
                    break;
                }
            }
            if (getCompMethod == null) return;

            Object interactions = getCompMethod.invoke(store, entityRef, interactionsType);
            if (interactions == null) {
                // Entity doesn't have Interactions component - not a real NPC, skip
                logVerbose("[CustomAnimal] Skipping non-NPC entity (no Interactions component): " + animalName);
                return;
            }

            String feedInteractionId = "Root_FeedAnimal";

            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            Object useType = null;
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            java.lang.reflect.Method getIntId = interactions.getClass().getMethod(
                    "getInteractionId", interactionTypeClass);
            String currentUse = (String) getIntId.invoke(interactions, useType);

            // Get current hint BEFORE overwriting (for restoration later)
            java.lang.reflect.Method getHint = interactions.getClass().getMethod("getInteractionHint");
            String currentHint = (String) getHint.invoke(interactions);
            if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] %s: currentUse='%s', currentHint='%s', feedInteractionId='%s'",
                animalName, currentUse, currentHint, feedInteractionId);

            if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                // Store original interaction ID AND hint for fallback
                storeOriginalState(entityRef, currentUse, currentHint, null);
                if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] %s: SETTING interaction to %s (was: %s, hint was: %s)",
                    animalName, feedInteractionId, currentUse, currentHint);

                java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                        "setInteractionId", interactionTypeClass, String.class);

                // FIX: Clear the *UseNPC interaction first (set to null like built-in animals)
                // Built-in animals have null as original interaction, custom NPCs have *UseNPC
                // The NPC system may intercept *UseNPC before our override takes effect
                if (currentUse != null && currentUse.startsWith("*")) {
                    setIntId.invoke(interactions, useType, null);
                    if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] %s: cleared special interaction '%s' to null", animalName, currentUse);
                }

                // Now set our interaction
                setIntId.invoke(interactions, useType, feedInteractionId);
            }

            // ALWAYS set hint - custom animals use standard feed hint (not mountable)
            java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                    "setInteractionHint", String.class);
            setHint.invoke(interactions, "animalbreeding.interactionHints.legacyFeed");
            if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] %s: setup complete", animalName);

        } catch (Exception e) {
            getLogger().atSevere().log("[CustomAnimal] %s: setup error: %s", animalName, e.getMessage());
        }
    }

    /**
     * Update an animal's interaction state based on whether feeding makes sense.
     * Called periodically to switch between "feed mode" and "original mode".
     *
     * - FEED MODE: Animal can be fed (not in love, not on cooldown)
     *   Shows "Press F to Feed" or "Press F to Feed / Mount"
     *
     * - ORIGINAL MODE: Feeding doesn't make sense (in love or on cooldown)
     *   Shows original interaction (e.g., "Press F to Mount" for horses)
     *
     * @param entityRef The entity reference
     * @param animalType The animal type
     * @param data The breeding data for this animal
     */
    @SuppressWarnings("unchecked")
    private void updateAnimalInteractionState(Ref<EntityStore> entityRef, AnimalType animalType, BreedingData data) {
        if (!USE_ENTITY_BASED_INTERACTIONS) {
            return; // Only applies to entity-based interactions
        }

        // Skip if entity ref is stale (entity despawned)
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }

        try {
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) return;

            // Determine if we should show feed interaction
            boolean shouldShowFeed = true;
            if (data != null) {
                if (data.isInLove()) {
                    shouldShowFeed = false;
                }
                // Check cooldown - if recently bred, don't show feed
                long cooldown = configManager.getBreedingCooldown(animalType);
                if (data.getCooldownRemaining(cooldown) > 0) {
                    shouldShowFeed = false;
                }
            }

            // Get interactions component
            Object interactionsType = EcsReflectionUtil.getInteractionsComponentType();
            if (interactionsType == null) return;

            java.lang.reflect.Method getCompMethod = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    getCompMethod = m;
                    break;
                }
            }
            if (getCompMethod == null) return;

            Object interactions = getCompMethod.invoke(store, entityRef, interactionsType);
            if (interactions == null) return;

            // Get InteractionType.Use enum value
            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            Object useType = null;
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    useType = enumConst;
                    break;
                }
            }

            java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                    "setInteractionId", interactionTypeClass, String.class);
            java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                    "setInteractionHint", String.class);

            String entityKey = EcsReflectionUtil.getStableEntityKey(entityRef);

            if (shouldShowFeed) {
                // FEED MODE - show feed interaction
                setIntId.invoke(interactions, useType, "Root_FeedAnimal");
                String hintKey = animalType.isMountable()
                        ? "animalbreeding.interactionHints.legacyFeedOrMount"
                        : "animalbreeding.interactionHints.legacyFeed";
                setHint.invoke(interactions, hintKey);
            } else {
                // ORIGINAL MODE - restore original interaction if we have it saved
                OriginalInteractionState original = entityKey != null ? originalStates.get(entityKey) : null;
                if (original != null) {
                    // Restore original interaction ID (even if null - that's the correct original state)
                    // For horses, original Use interaction is null which allows mounting to work
                    setIntId.invoke(interactions, useType, original.getInteractionId());
                    if (original.hasHint()) {
                        setHint.invoke(interactions, original.getHint());
                    } else {
                        // Clear hint if original had none
                        setHint.invoke(interactions, (String) null);
                    }
                    logVerbose(String.format("[StateUpdate] %s: restored original interaction=%s, hint=%s",
                        animalType, original.getInteractionId(), original.getHint()));
                }
                // If we don't have saved state, keep showing feed interaction
            }

        } catch (Exception e) {
            // Entity may have despawned - ignore silently
            logVerbose(String.format("[StateUpdate] Error updating %s: %s", animalType, e.getMessage()));
        }
    }

    /**
     * Update interaction states for all tracked animals.
     * Called periodically from the tick loop.
     */
    private void updateTrackedAnimalStates() {
        if (!USE_ENTITY_BASED_INTERACTIONS) {
            return;
        }

        for (BreedingData data : breedingManager.getAllBreedingData()) {
            Object refObj = data.getEntityRef();
            AnimalType animalType = data.getAnimalType();

            if (refObj == null || animalType == null) continue;

            try {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> entityRef = (Ref<EntityStore>) refObj;

                // Skip and clean up stale refs (entity despawned)
                if (!entityRef.isValid()) {
                    data.setEntityRef(null);
                    continue;
                }

                updateAnimalInteractionState(entityRef, animalType, data);
            } catch (Exception e) {
                // Entity despawned or ref invalid - clean up and continue
                data.setEntityRef(null);
            }
        }
    }

    /**
     * Set up Ability2 hint on an entity (for item-based feeding).
     * This only sets up the hint display - the actual interaction is handled by the item's Ability2.
     * Shows "Press [Ability2 key] to Feed" when player looks at the animal.
     */
    private void setupAbility2HintOnly(Store<EntityStore> store, Ref<EntityStore> entityRef, String hintKey) {
        try {
            Object interactableType = EcsReflectionUtil.getInteractableComponentType();
            Object interactionsType = EcsReflectionUtil.getInteractionsComponentType();

            if (interactableType == null || interactionsType == null) {
                return;
            }

            java.lang.reflect.Method ensureMethod = store.getClass().getMethod(
                    "ensureAndGetComponent", Ref.class, ComponentType.class);

            // Ensure entity has Interactable component (enables hint display)
            try {
                ensureMethod.invoke(store, entityRef, interactableType);
            } catch (Exception e) {
                // Silent - may already have component
            }

            // Check if entity already has Interactions component (real NPCs have this)
            // Use getComponent instead of ensureAndGetComponent to avoid adding to non-NPCs
            java.lang.reflect.Method getCompMethod = null;
            for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    getCompMethod = m;
                    break;
                }
            }
            if (getCompMethod == null) return;

            Object interactions = getCompMethod.invoke(store, entityRef, interactionsType);
            if (interactions == null) {
                // Entity doesn't have Interactions component - not a real NPC, skip
                logVerbose("[SetupInteraction] Skipping non-NPC entity (no Interactions component)");
                return;
            }

            // Get Ability2 enum value
            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            Object ability2Type = null;
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                if (enumConst.toString().equals("Ability2")) {
                    ability2Type = enumConst;
                    break;
                }
            }

            if (ability2Type == null) {
                logVerbose("Could not find Ability2 InteractionType");
                return;
            }

            // Set interaction for Ability2 (don't touch Use - it breaks other interactions)
            java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                    "setInteractionId", interactionTypeClass, String.class);
            setIntId.invoke(interactions, ability2Type, "Root_FeedAnimal");

            // Set the hint (API only supports simple string, no per-type hints)
            java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                    "setInteractionHint", String.class);
            setHint.invoke(interactions, hintKey);
            logVerbose("Set up Ability2 hint: " + hintKey);

        } catch (Exception e) {
            logVerbose("setupAbility2HintOnly error: " + e.getMessage());
        }
    }

    /**
     * Store the original interaction ID for a custom animal entity (for fallback).
     */
    private void storeOriginalInteractionIdForCustom(Ref<EntityStore> entityRef, String originalId, CustomAnimalConfig customAnimal) {
        // Use the same storage mechanism as regular animals
        // Store whatever the original interaction was so we can fall back to it
        String key = EcsReflectionUtil.getStableEntityKey(entityRef);
        if (key == null)
            return;

        if (originalId != null && !originalId.isEmpty()) {
            originalStates.put(key, new OriginalInteractionState(originalId, null));
        }
    }

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

                    UUID entityId = getEntityUUID(entity);

                    // Check if this is a tamed animal - don't delete, mark for respawn
                    if (tamingManager != null && tamingManager.isTamed(entityId)) {
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

                    // Clean up originalStates map to prevent memory leak
                    try {
                        Object ref = entity.getReference();
                        if (ref != null) {
                            String key = EcsReflectionUtil.getStableEntityKey(ref);
                            if (key != null) {
                                originalStates.remove(key);
                            }
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

    // ===========================================
    // TAMING: RESPAWN SYSTEM
    // ===========================================

    /**
     * Check for despawned tamed animals near players and respawn them.
     * Called every 5 seconds by the tick scheduler.
     */
    private void checkAndRespawnTamedAnimals() {
        if (tamingManager == null) return;

        World world = Universe.get().getDefaultWorld();
        if (world == null) return;

        // Must run on world thread to access entity components
        world.execute(() -> {
            double respawnRadius = 64.0; // Configurable in future

            try {
                // Get all players
                for (Player player : world.getPlayers()) {
                    try {
                        Vector3d playerPos = player.getTransformComponent().getPosition();
                        if (playerPos == null) continue;

                        // Find despawned tamed animals near this player
                        java.util.List<TamedAnimalData> toRespawn = tamingManager.getDespawnedAnimalsInRegion(
                                playerPos.getX(), playerPos.getZ(), respawnRadius);

                        for (TamedAnimalData tamedData : toRespawn) {
                            respawnTamedAnimal(world, tamedData);
                        }
                    } catch (Exception e) {
                        // Silent - skip this player
                    }
                }
            } catch (Exception e) {
                // Silent
            }
        });
    }

    /**
     * Respawn a tamed animal at its saved position.
     */
    private void respawnTamedAnimal(World world, TamedAnimalData tamedData) {
        if (tamedData == null || !tamedData.isDespawned()) return;

        AnimalType animalType = tamedData.getAnimalType();
        if (animalType == null) return;

        Vector3d spawnPos = new Vector3d(
                tamedData.getLastX(),
                tamedData.getLastY() + 0.5, // Slightly above ground
                tamedData.getLastZ()
        );

        final UUID oldUuid = tamedData.getAnimalUuid();
        final AnimalType finalAnimalType = animalType;
        final TamedAnimalData finalTamedData = tamedData;

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Determine role ID based on growth stage
                String roleId;
                if (tamedData.getGrowthStage() == GrowthStage.ADULT || tamedData.getGrowthStage() == null) {
                    roleId = finalAnimalType.getAdultNpcRoleId();
                } else {
                    roleId = finalAnimalType.hasBabyVariant() ?
                            finalAnimalType.getBabyNpcRoleId() :
                            finalAnimalType.getAdultNpcRoleId();
                }

                NPCPlugin npcPlugin = NPCPlugin.get();
                int roleIndex = npcPlugin.getIndex(roleId);
                if (roleIndex < 0) {
                    logWarning("Could not find role for respawn: " + roleId);
                    return;
                }

                Vector3f rotation = new Vector3f(0, finalTamedData.getLastRotation(), 0);

                // Spawn the entity
                for (java.lang.reflect.Method m : NPCPlugin.class.getMethods()) {
                    if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                        Class<?> triConsumerClass = m.getParameterTypes()[5];
                        Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                triConsumerClass.getClassLoader(),
                                new Class<?>[] { triConsumerClass },
                                (proxy, method, args) -> null
                        );

                        Object result = m.invoke(npcPlugin, store, roleIndex, spawnPos, rotation, null, noOpCallback);

                        if (result != null) {
                            java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                            @SuppressWarnings("unchecked")
                            Ref<EntityStore> entityRef = (Ref<EntityStore>) getFirst.invoke(result);

                            if (entityRef != null) {
                                // Get new UUID
                                UUID newUuid = null;
                                try {
                                    UUIDComponent uuidComp = store.getComponent(entityRef, EcsReflectionUtil.UUID_TYPE);
                                    if (uuidComp != null) {
                                        newUuid = uuidComp.getUuid();
                                    }
                                } catch (Exception e) {
                                    newUuid = UUID.randomUUID();
                                }

                                if (newUuid == null) {
                                    newUuid = UUID.randomUUID();
                                }

                                // Update taming manager with new UUID and ref
                                tamingManager.markRespawned(oldUuid, newUuid, entityRef);

                                // Restore breeding data
                                BreedingData bData = breedingManager.getOrCreateData(newUuid, finalAnimalType);
                                finalTamedData.applyToBreedingData(bData);
                                bData.setTamed(true, finalTamedData.getOwnerUuid());
                                bData.setCustomName(finalTamedData.getCustomName());
                                bData.setEntityRef(entityRef);

                                // Note: Interaction will be set up by the periodic scan
                                // This avoids duplicating complex reflection code

                                logVerbose("Respawned tamed animal: " + finalTamedData.getCustomName() +
                                        " (" + finalAnimalType + ")");
                            }
                        }
                        break;
                    }
                }

            } catch (Exception e) {
                logWarning("Failed to respawn tamed animal: " + e.getMessage());
            }
        });
    }

    /**
     * Automatically set up interactions on all farm animals in all worlds.
     * Public so it can be called from command classes.
     */
    public void autoSetupNearbyAnimals() {
        if (verboseLogging) getLogger().atInfo().log("[AutoScan] autoSetupNearbyAnimals CALLED");
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                getLogger().atWarning().log("[AutoScan] world is NULL, aborting");
                return;
            }

            // Collect player UUIDs to exclude from animal detection
            final Set<UUID> playerUuids = new HashSet<>();
            for (Player p : world.getPlayers()) {
                UUID pUuid = getEntityUUID(p);
                if (pUuid != null) {
                    playerUuids.add(pUuid);
                }
            }

            // Find all farm animals (including babies)
            if (verboseLogging) getLogger().atInfo().log("[AutoScan] Starting animal scan (customAnimals registered: %d)",
                configManager.getCustomAnimals().size());
            AnimalFinder.findAnimals(world, false, animals -> {
                try {
                    if (verboseLogging) getLogger().atInfo().log("[AutoScan] Found %d animals total", animals.size());
                    if (animals.isEmpty())
                        return;

                    int processedCount = 0;
                    int skippedNull = 0;
                    int skippedDisabled = 0;
                    int skippedBaby = 0;

                    // Log the registered custom animals for debugging
                    if (verboseLogging) getLogger().atInfo().log("[AutoScan] Registered custom animals: %s",
                        String.join(", ", configManager.getCustomAnimals().keySet()));

                    for (AnimalFinder.FoundAnimal animal : animals) {
                        Object entityRef = animal.getEntityRef();
                        AnimalType animalType = animal.getAnimalType();
                        String modelId = animal.getModelAssetId();

                        // Check if this modelId matches any registered custom animal
                        if (configManager.isCustomAnimal(modelId)) {
                            if (verboseLogging) getLogger().atInfo().log("[AutoScan] Processing potential custom animal: '%s' (animalType=%s)", modelId, animalType);
                        }

                        // Skip if this is a player entity (prevents attaching interactions to players with animal models)
                        if (entityRef instanceof Ref) {
                            @SuppressWarnings("unchecked")
                            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                            // Skip UUID check if ref is stale - entity despawned, just proceed
                            if (!ref.isValid()) {
                                logVerbose("[AnimalScan] Skipping stale entity ref for " + animal.getModelAssetId());
                                continue;
                            }
                            try {
                                Store<EntityStore> refStore = ref.getStore();
                                if (refStore != null) {
                                    UUIDComponent uuidComp = refStore.getComponent(ref, EcsReflectionUtil.UUID_TYPE);
                                    if (uuidComp != null && uuidComp.getUuid() != null) {
                                        if (playerUuids.contains(uuidComp.getUuid())) {
                                            logVerbose("Skipping player entity with animal model: " + animal.getModelAssetId());
                                            continue;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // UUID check failed - this happens when UUIDComponent doesn't exist
                                // This is OK: the check is only to filter players, not to validate animals
                                // Don't skip any animals - proceed with interaction setup
                                if (configManager.isCustomAnimal(modelId)) {
                                    logVerbose("[CustomAnimal] " + modelId + " has no UUID component (expected for custom NPCs), proceeding");
                                } else {
                                    logVerbose("[AnimalScan] UUID check failed for " + animal.getModelAssetId() + " (proceeding anyway): " + e.getMessage());
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
                                if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] Found match for '%s' (enabled=%s)", modelId, customAnimal.isEnabled());
                            } else if (configManager.getCustomAnimals().size() > 0) {
                                // Only log if there are custom animals registered - ALWAYS LOG THIS
                                if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] No match for '%s' (registered: %s)",
                                    modelId, String.join(", ", configManager.getCustomAnimals().keySet()));
                            }
                        } else {
                            // Log if a potential custom animal is being detected as built-in
                            if (configManager.isCustomAnimal(modelId)) {
                                getLogger().atWarning().log("[CustomAnimal] '%s' matched as built-in %s instead of custom!", modelId, animalType);
                            }
                        }

                        // Skip if not a recognized farm animal OR custom animal
                        if (animalType == null && customAnimal == null) {
                            skippedNull++;
                            continue;
                        }

                        // Skip if breeding is disabled
                        if (animalType != null && !configManager.isAnimalEnabled(animalType)) {
                            skippedDisabled++;
                            logVerbose("Skipping disabled animal: " + animalType);
                            continue;
                        }
                        if (customAnimal != null && !customAnimal.isEnabled()) {
                            skippedDisabled++;
                            logVerbose("Skipping disabled custom animal: " + animal.getModelAssetId());
                            continue;
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
                                        logVerbose("Setting up interactions for adult: " + animal.getModelAssetId() + " (type: "
                                                + animalType + ")");
                                        setupEntityInteractions(refStore, ref, animalType);
                                    } else if (customAnimal != null) {
                                        if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] ABOUT TO CALL setupCustomAnimalInteractions for: %s", animal.getModelAssetId());
                                        setupCustomAnimalInteractions(refStore, ref, customAnimal);
                                    }
                                } else if (SHOW_ABILITY2_HINTS_ON_ENTITIES) {
                                    // Item-based with hints: Show Ability2 hint on animals
                                    String hintKey = (animalType != null && animalType.isMountable())
                                            ? "animalbreeding.interactionHints.feed"
                                            : "animalbreeding.interactionHints.feed";
                                    setupAbility2HintOnly(refStore, ref, hintKey);
                                }
                                processedCount++;
                            } else {
                                getLogger().atWarning().log("[CustomAnimal] refStore is NULL for: %s", animal.getModelAssetId());
                            }
                        } else {
                            if (customAnimal != null) {
                                if (verboseLogging) getLogger().atInfo().log("[CustomAnimal] Skipping baby custom animal: %s", animal.getModelAssetId());
                            }
                            skippedBaby++;
                        }
                    }
                    logVerbose("Animal scan complete: processed=" + processedCount +
                            ", skippedNull=" + skippedNull +
                            ", skippedDisabled=" + skippedDisabled +
                            ", skippedBaby=" + skippedBaby);
                } catch (Exception e) {
                    // Log errors from animal processing
                    logWarning("autoSetupNearbyAnimals callback error: " + e.getClass().getSimpleName() + ": "
                            + e.getMessage());
                    if (verboseLogging && e.getCause() != null) {
                        logWarning("  Caused by: " + e.getCause().getMessage());
                    }
                }
            });

        } catch (Exception e) {
            // Log errors from initial setup
            logWarning("autoSetupNearbyAnimals setup error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Register the player interaction event handler for breeding.
     */
    private void registerInteractionHandler() {
        try {
            getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        } catch (Exception e) {
        }
        try {
            getEventRegistry().register(PlayerMouseButtonEvent.class, this::onMouseButton);
        } catch (Exception e) {
        }
        try {
            getEventRegistry().register(UseBlockEvent.Pre.class, this::onUseBlockPre);
        } catch (Exception e) {
        }
        try {
            getEventRegistry().register(UseBlockEvent.Post.class, this::onUseBlockPost);
        } catch (Exception e) {
        }
        try {
            getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);
        } catch (Exception e) {
        }
    }

    private void onUseBlockPre(UseBlockEvent.Pre event) {
        useBlockPreCount++;
    }

    private void onUseBlockPost(UseBlockEvent.Post event) {
        useBlockPostCount++;
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        playerReadyCount++;
    }

    private void onMouseButton(PlayerMouseButtonEvent event) {
        mouseClickCount++;
        try {
            handleMouseClick(event);
        } catch (Exception e) {
            // Silent
        }
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        // Set up interactions immediately when player interacts with an animal
        try {
            Entity targetEntity = event.getTargetEntity();
            if (targetEntity == null)
                return;

            // Skip if target is a Player (prevents attaching animal interactions to players with animal models)
            if (targetEntity instanceof Player) {
                return;
            }

            String entityName = getEntityModelId(targetEntity);
            AnimalType animalType = AnimalType.fromEntityTypeId(entityName);
            if (animalType == null)
                return;

            // Check if enabled
            if (!configManager.isAnimalEnabled(animalType))
                return;

            // Set up interaction for this animal
            Object entityRef = getEntityRef(targetEntity);
            if (entityRef != null && entityRef instanceof Ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                World world = targetEntity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    setupEntityInteractions(store, ref, animalType);
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Handle mouse clicks for breeding and taming.
     */
    private void handleMouseClick(PlayerMouseButtonEvent event) {
        Player player = event.getPlayer();

        // Only handle right-click
        if (event.getMouseButton().mouseButtonType != MouseButtonType.Right) {
            return;
        }

        // Check if interacting with an entity
        Entity targetEntity = event.getTargetEntity();
        if (targetEntity == null) {
            return;
        }

        // Skip if target is a Player (prevents treating players with animal models as animals)
        if (targetEntity instanceof Player) {
            return;
        }

        // Debug log
        if (verboseLogging) getLogger().atInfo().log("[TamingDebug] handleMouseClick triggered on entity");

        // Get held item early for taming check
        Item heldItem = event.getItemInHand();
        String itemId = heldItem != null ? heldItem.getId() : null;

        // === TAMING LOGIC (works on any animal) ===
        if (tamingManager != null && player != null) {
            String playerName = player.getDisplayName();
            UUID playerUuid = getPlayerUuidFromEntity(player);

            if (verboseLogging) getLogger().atInfo().log("[TamingDebug] playerName=%s, itemId=%s", playerName, itemId);

            // Check for pending name tag
            boolean hasPendingNameTag = (playerUuid != null && tamingManager.hasPendingNameTag(playerUuid)) ||
                    (playerName != null && tamingManager.hasPendingNameTagByName(playerName));
            boolean isNameTag = isNameTagItem(itemId);

            if (verboseLogging) getLogger().atInfo().log("[TamingDebug] hasPendingNameTag=%s, isNameTag=%s", hasPendingNameTag, isNameTag);

            if (hasPendingNameTag && isNameTag) {
                // Get pending name
                String pendingName = null;
                if (playerUuid != null) {
                    pendingName = tamingManager.consumePendingNameTag(playerUuid);
                }
                if (pendingName == null && playerName != null) {
                    pendingName = tamingManager.consumePendingNameTagByName(playerName);
                } else if (playerName != null) {
                    tamingManager.consumePendingNameTagByName(playerName);
                }

                if (pendingName != null) {
                    // Get animal UUID and type
                    UUID animalUuid = getEntityUUID(targetEntity);
                    String entityName = getEntityModelId(targetEntity);
                    AnimalType animalTypeForTaming = AnimalType.fromEntityTypeId(entityName);

                    if (animalUuid != null && playerUuid != null) {
                        // Check if already tamed by someone else
                        TamedAnimalData existingTamed = tamingManager.getTamedData(animalUuid);
                        if (existingTamed != null && !existingTamed.isOwnedBy(playerUuid)) {
                            player.sendMessage(Message.raw("This animal belongs to someone else!").color("#FF5555"));
                            return;
                        }

                        // Tame the animal
                        TamedAnimalData tamedData = tamingManager.tameAnimal(animalUuid, playerUuid, pendingName, animalTypeForTaming);
                        if (tamedData != null) {
                            // Update breeding data if applicable
                            if (animalTypeForTaming != null) {
                                BreedingData bData = breedingManager.getOrCreateData(animalUuid, animalTypeForTaming);
                                bData.setTamed(true, playerUuid);
                                bData.setCustomName(pendingName);
                            }

                            player.sendMessage(Message.raw(pendingName + " is now yours!").color("#55FF55"));
                            spawnHeartParticlesAtEntity(targetEntity);
                            if (verboseLogging) getLogger().atInfo().log("Tamed animal: %s", pendingName);
                        }
                    }
                }
                return; // Don't proceed with normal feeding
            }

            // Check for pending untame
            boolean hasPendingUntame = (playerUuid != null && tamingManager.hasPendingUntame(playerUuid)) ||
                    (playerName != null && tamingManager.hasPendingUntameByName(playerName));
            if (hasPendingUntame) {
                UUID animalUuid = getEntityUUID(targetEntity);
                if (animalUuid != null && playerUuid != null && tamingManager.untameAnimal(animalUuid, playerUuid)) {
                    if (playerUuid != null) tamingManager.consumePendingUntame(playerUuid);
                    if (playerName != null) tamingManager.consumePendingUntameByName(playerName);
                    player.sendMessage(Message.raw("Animal released!").color("#55FF55"));

                    // Clear taming from breeding data
                    String entityName = getEntityModelId(targetEntity);
                    AnimalType animalTypeForUntame = AnimalType.fromEntityTypeId(entityName);
                    if (animalTypeForUntame != null) {
                        BreedingData bData = breedingManager.getData(animalUuid);
                        if (bData != null) {
                            bData.setTamed(false, null);
                        }
                    }
                } else {
                    if (playerUuid != null) tamingManager.consumePendingUntame(playerUuid);
                    if (playerName != null) tamingManager.consumePendingUntameByName(playerName);
                    TamedAnimalData tamedData = tamingManager.getTamedData(getEntityUUID(targetEntity));
                    if (tamedData != null && playerUuid != null && !tamedData.isOwnedBy(playerUuid)) {
                        player.sendMessage(Message.raw("This animal belongs to someone else!").color("#FF5555"));
                    } else {
                        player.sendMessage(Message.raw("This animal is not tamed.").color("#FFAA00"));
                    }
                }
                return;
            }
        }
        // === END TAMING LOGIC ===

        // Get entity model ID to determine type (via ECS ModelComponent)
        String entityName = getEntityModelId(targetEntity);

        // Try to identify animal type from entity name
        AnimalType animalType = AnimalType.fromEntityTypeId(entityName);
        if (animalType == null) {
            return; // Not a breedable animal
        }

        // Ensure interaction is set up for this animal (in case periodic scan hasn't
        // run yet)
        try {
            Object entityRef = getEntityRef(targetEntity);
            if (entityRef != null && entityRef instanceof Ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                World world = targetEntity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    setupEntityInteractions(store, ref, animalType);
                }
            }
        } catch (Exception e) {
            // Silent - interaction setup is best-effort
        }

        // Check held item (already got it earlier for taming)
        if (heldItem == null) {
            return;
        }

        // Get UUID for this entity (via ECS UUIDComponent)
        UUID entityId = getEntityUUID(targetEntity);

        // Try to feed the animal
        BreedingManager.FeedResult result = breedingManager.tryFeed(entityId, animalType, itemId);

        // Store the entity ref for position tracking (needed for distance-based
        // breeding)
        if (result == BreedingManager.FeedResult.SUCCESS || result == BreedingManager.FeedResult.ALREADY_IN_LOVE) {
            Object entityRef = getEntityRef(targetEntity);
            if (entityRef != null) {
                BreedingData data = breedingManager.getData(entityId);
                if (data != null && data.getEntityRef() == null) {
                    data.setEntityRef(entityRef);
                }
            }
        }

        // Send chat feedback to player
        logVerbose("Feed result for " + animalType.getId() + ": " + result);

        if (player != null) {
            switch (result) {
                case SUCCESS:
                    player.sendMessage(Message
                            .raw("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) + " is now in love!"));
                    // Note: Sound and item consumption handled by FeedAnimal interaction
                    spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case ALREADY_IN_LOVE:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is already in love!"));
                    spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case WRONG_FOOD:
                    java.util.List<String> validFoods = configManager.getBreedingFoods(animalType);
                    String foodList = String.join(", ", validFoods);
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] Wrong food! " + capitalize(animalType.getId())
                            + " needs: " + foodList));
                    break;
                case DISABLED:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] Breeding is disabled for " + animalType.getId() + "s"));
                    break;
                case NOT_ADULT:
                    player.sendMessage(
                            Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is too young to breed"));
                    break;
                case ON_COOLDOWN:
                    BreedingData data = breedingManager.getData(entityId);
                    if (data != null) {
                        long remaining = data.getCooldownRemaining(configManager.getBreedingCooldown(animalType));
                        player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId()
                                + " needs to rest (" + (remaining / 1000) + "s)"));
                    } else {
                        player.sendMessage(
                                Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " needs to rest"));
                    }
                    break;
            }
        }
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Consume 1 item from the player's held item stack.
     */
    public void consumeHeldItem(Player player) {
        try {
            // Get player's inventory
            Inventory inventory = ((LivingEntity) player).getInventory();
            if (inventory == null)
                return;

            // Get the active hotbar slot
            byte activeSlot = inventory.getActiveHotbarSlot();

            // Remove 1 item from the active hotbar slot
            inventory.getHotbar().removeItemStackFromSlot((short) activeSlot, 1);

            // Mark inventory as changed and sync to client
            inventory.markChanged();
            player.sendInventory();
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Play the feeding sound at the target entity's location.
     */
    public void playFeedingSound(Entity targetEntity) {
        try {
            int soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
            if (soundId < 0)
                return;

            Vector3d pos = getEntityPosition(targetEntity);
            World world = targetEntity.getWorld();
            if (world == null)
                return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null)
                return;

            SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), store);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Try to find another animal in love to breed with.
     * For now, this uses our tracked animals - in future, should scan nearby
     * entities.
     */
    private void tryFindMate(UUID animalId, AnimalType type, Player player) {
        BreedingData currentData = breedingManager.getData(animalId);
        if (currentData == null || !currentData.isInLove()) {
            return;
        }

        // Look for another animal of same type that's in love
        for (BreedingData data : breedingManager.getAllBreedingData()) {
            if (data.getAnimalId().equals(animalId)) {
                continue; // Skip self
            }
            if (data.getAnimalType() != type) {
                continue; // Different type
            }
            if (!data.isInLove()) {
                continue; // Not in love
            }

            // Found a mate! Start breeding
            boolean success = breedingManager.tryBreed(animalId, data.getAnimalId(), type);
            if (success && player != null) {
                long gestationTime = configManager.getGestationPeriod(type);
                player.sendMessage(Message.raw("[Lait:AnimalBreeding] Two " + type.getId() + "s are breeding!"));
                player.sendMessage(
                        Message.raw("[Lait:AnimalBreeding] Baby arrives in " + (gestationTime / 1000) + " seconds"));
            }
            break;
        }
    }

    /**
     * Try to find another animal in love and breed INSTANTLY (spawn baby
     * immediately).
     */
    private void tryFindMateInstant(UUID animalId, AnimalType type, Entity targetEntity, Player player) {
        BreedingData currentData = breedingManager.getData(animalId);
        if (currentData == null || !currentData.isInLove()) {
            return;
        }

        // Look for another animal of same type that's in love
        for (BreedingData data : breedingManager.getAllBreedingData()) {
            if (data.getAnimalId().equals(animalId)) {
                continue; // Skip self
            }
            if (data.getAnimalType() != type) {
                continue; // Different type
            }
            if (!data.isInLove()) {
                continue; // Not in love
            }
            if (data.isPregnant()) {
                continue; // Already pregnant
            }

            // Found a mate! INSTANT BREEDING
            currentData.completeBreeding();
            data.completeBreeding();

            // Get spawn position from the target entity
            Vector3d spawnPos = getEntityPosition(targetEntity);
            if (spawnPos == null) {
                spawnPos = new Vector3d(0, 65, 0);
            }

            // Spawn baby
            spawnBabyAnimal(type, spawnPos);

            if (player != null) {
                player.sendMessage(Message.raw("[Lait:AnimalBreeding] Two " + type.getId() + "s have bred!"));
                player.sendMessage(Message.raw("[Lait:AnimalBreeding] A baby " + type.getId() + " has been born!"));
            }

            break;
        }
    }

    /**
     * Spawn heart particles at an entity's position.
     */
    private void spawnHeartParticlesAtEntity(Entity entity) {
        try {
            Vector3d position = getEntityPosition(entity);
            if (position == null)
                return;

            double x = position.getX();
            double y = position.getY() + 1.5;
            double z = position.getZ();

            Class<?> particleUtilClass = Class.forName("com.hypixel.hytale.server.core.universe.world.ParticleUtil");
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            Object store = world.getEntityStore().getStore();

            for (java.lang.reflect.Method method : particleUtilClass.getMethods()) {
                if (method.getName().equals("spawnParticleEffect") && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == String.class &&
                            params[1].getSimpleName().equals("Vector3d") &&
                            params[2].getSimpleName().equals("ComponentAccessor")) {

                        Vector3d heartsPos = new Vector3d(x, y, z);
                        method.invoke(null, HEARTS_PARTICLE, heartsPos, store);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Spawn heart particles at an entity ref's position (for use on world thread).
     * Used by tickLoveAnimals to show continuous hearts while animal is in love.
     */
    @SuppressWarnings("unchecked")
    private void spawnHeartParticlesAtRef(Store<EntityStore> store, Object entityRef) {
        try {
            if (entityRef == null) {
                getLogger().atWarning().log("[Hearts] entityRef is null");
                return;
            }
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            if (!ref.isValid()) {
                // Expected when entity despawned - not a real error, just skip
                logVerbose("[Hearts] ref is invalid (entity likely despawned)");
                return;
            }

            // Get position using the same method as breeding distance check
            Vector3d position = getPositionOnWorldThread(store, ref);
            if (position == null) {
                // More detailed logging
                Store<EntityStore> refStore = ref.getStore();
                getLogger().atWarning().log("[Hearts] position is null - ref.getStore()=" +
                        (refStore != null ? "valid" : "NULL") +
                        ", ref.isValid()=" + ref.isValid() +
                        ", ref.getIndex()=" + ref.getIndex());
                return;
            }

            double x = position.getX();
            double y = position.getY() + 1.5; // Spawn above the animal
            double z = position.getZ();

            Vector3d heartsPos = new Vector3d(x, y, z);

            // Use ParticleUtil.spawnParticleEffect
            boolean methodFound = false;
            for (java.lang.reflect.Method method : ParticleUtil.class.getMethods()) {
                if (method.getName().equals("spawnParticleEffect") && method.getParameterCount() == 3) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] == String.class &&
                            params[1].getSimpleName().equals("Vector3d") &&
                            params[2].getSimpleName().equals("ComponentAccessor")) {
                        method.invoke(null, HEARTS_PARTICLE, heartsPos, store);
                        methodFound = true;
                        return;
                    }
                }
            }
            if (!methodFound) {
                getLogger().atWarning().log("[Hearts] spawnParticleEffect method not found");
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[Hearts] Error in spawnHeartParticlesAtRef: " + e.getMessage());
        }
    }

    /**
     * Play feeding sound at entity's position.
     */
    private void playFeedingSoundAtEntity(Entity entity) {
        try {
            Vector3d pos = getEntityPosition(entity);
            if (pos == null)
                return;

            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            Object store = world.getEntityStore().getStore();

            // Get sound ID
            Class<?> soundEventClass = Class
                    .forName("com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent");
            Object assetMap = soundEventClass.getMethod("getAssetMap").invoke(null);
            int soundId = (int) assetMap.getClass().getMethod("getIndex", Object.class).invoke(assetMap,
                    "SFX_Consume_Bread");

            if (soundId < 0)
                return;

            // Play 3D sound
            Class<?> soundUtilClass = Class.forName("com.hypixel.hytale.server.core.universe.world.SoundUtil");
            for (java.lang.reflect.Method m : soundUtilClass.getMethods()) {
                if (m.getName().equals("playSoundEvent3d") && m.getParameterCount() == 6) {
                    Class<?>[] paramTypes = m.getParameterTypes();
                    if (paramTypes[0] == int.class && paramTypes[1] == double.class) {
                        java.util.function.Predicate<Object> allPlayers = p -> true;
                        m.invoke(null, soundId, pos.getX(), pos.getY(), pos.getZ(), allPlayers, store);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Get entity position from Entity object.
     */
    private Vector3d getEntityPosition(Entity entity) {
        try {
            // Try to get position directly from Entity
            java.lang.reflect.Method getPosition = entity.getClass().getMethod("getPosition");
            Object pos = getPosition.invoke(entity);
            if (pos instanceof Vector3d) {
                return (Vector3d) pos;
            }
            // Try to convert Vector3f to Vector3d
            if (pos != null && pos.getClass().getSimpleName().equals("Vector3f")) {
                java.lang.reflect.Method getX = pos.getClass().getMethod("getX");
                java.lang.reflect.Method getY = pos.getClass().getMethod("getY");
                java.lang.reflect.Method getZ = pos.getClass().getMethod("getZ");
                float x = (Float) getX.invoke(pos);
                float y = (Float) getY.invoke(pos);
                float z = (Float) getZ.invoke(pos);
                return new Vector3d(x, y, z);
            }
        } catch (NoSuchMethodException e) {
            // Try alternate method names
            try {
                java.lang.reflect.Method getPos = entity.getClass().getMethod("getPos");
                Object pos = getPos.invoke(entity);
                if (pos instanceof Vector3d) {
                    return (Vector3d) pos;
                }
            } catch (Exception e2) {
                // Continue to fallback
            }
        } catch (Exception e) {
            // Silent
        }

        // Fallback: try to get from Ref/Store if entity has getRef()
        try {
            java.lang.reflect.Method getRef = entity.getClass().getMethod("getRef");
            Object ref = getRef.invoke(entity);
            if (ref != null) {
                java.lang.reflect.Method getStore = ref.getClass().getMethod("getStore");
                Object store = getStore.invoke(ref);

                Class<?> transformClass = Class
                        .forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
                java.lang.reflect.Method getComponentType = transformClass.getMethod("getComponentType");
                Object componentType = getComponentType.invoke(null);

                Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
                Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");
                java.lang.reflect.Method getComponent = store.getClass().getMethod("getComponent", refClass,
                        componentTypeClass);
                Object transform = getComponent.invoke(store, ref, componentType);

                if (transform != null) {
                    java.lang.reflect.Method getPosition = transform.getClass().getMethod("getPosition");
                    return (Vector3d) getPosition.invoke(transform);
                }
            }
        } catch (Exception e) {
            // Silent
        }

        return null;
    }

    /**
     * Get the Ref<EntityStore> from an Entity object.
     */
    private Object getEntityRef(Entity entity) {
        return EcsReflectionUtil.getEntityRef(entity);
    }

    /**
     * Get UUID for an entity using ECS UUIDComponent.
     * Delegates to EcsReflectionUtil.
     */
    private UUID getEntityUUID(Entity entity) {
        return EcsReflectionUtil.getEntityUUID(entity);
    }

    /**
     * Get UUID for a player entity.
     */
    @SuppressWarnings("unchecked")
    public UUID getPlayerUuidFromEntity(Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                World world = player.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, EcsReflectionUtil.UUID_TYPE);
                    if (uuidComp != null && uuidComp.getUuid() != null) {
                        return uuidComp.getUuid();
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Check if an item ID is a Name Tag item.
     */
    private boolean isNameTagItem(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        return lower.contains("nametag") || lower.contains("name_tag");
    }

    /**
     * Get model asset ID for an entity using ECS ModelComponent (avoids deprecated
     * getLegacyDisplayName()).
     * This is used to determine the animal type.
     */
    @SuppressWarnings("unchecked")
    private String getEntityModelId(Entity entity) {
        return EcsReflectionUtil.getEntityModelId(entity);
    }

    /**
     * Tick method to handle animals in love (optimized):
     * - Check if two animals of same type are in love and nearby
     * - If close enough, breed them
     * - If too far apart, wait for player to herd them together
     * - Love expires after LOVE_DURATION (30 seconds)
     */
    private void tickLoveAnimals() {
        // Early exit if nothing tracked
        int trackedCount = breedingManager.getTrackedCount();
        int inLoveTotal = breedingManager.getInLoveCount();

        // Always log status when any animal is in love (for debugging)
        if (inLoveTotal > 0) {
            if (verboseLogging) getLogger().atInfo().log("[TickLove] Running: tracked=" + trackedCount + ", inLove=" + inLoveTotal);
        }

        if (trackedCount == 0)
            return;

        long now = System.currentTimeMillis();

        // Single pass: expire love AND collect eligible animals AND group by type
        // Reuse collections to avoid allocation per tick
        tickLoveByType.values().forEach(java.util.List::clear);
        tickLoveByType.clear();
        tickLoveEntityRefs.clear();
        int inLoveCount = 0;
        int inLoveWithRef = 0;
        int inLoveNoRef = 0;

        for (BreedingData data : breedingManager.getAllBreedingData()) {
            if (data.isInLove()) {
                // Check expiration
                if (now - data.getLoveStartTime() > LOVE_DURATION) {
                    data.resetLove();
                    continue;
                }

                // Collect entity ref for heart particles (all in-love animals)
                if (data.getEntityRef() != null) {
                    tickLoveEntityRefs.add(data.getEntityRef());
                    inLoveWithRef++;
                } else {
                    inLoveNoRef++;
                }

                // Collect if eligible for breeding
                if (!data.isPregnant() && data.getGrowthStage().canBreed()) {
                    tickLoveByType.computeIfAbsent(data.getAnimalType(), k -> new java.util.ArrayList<>()).add(data);
                    inLoveCount++;
                }
            }
        }

        // Also process custom animals in love mode
        breedingManager.tickCustomAnimalLove(); // Expire old love modes
        for (BreedingManager.CustomAnimalLoveData customData : breedingManager.getCustomAnimalsInLove()) {
            if (customData.getEntityRef() != null) {
                tickLoveEntityRefs.add(customData.getEntityRef());
                inLoveWithRef++;
            } else {
                inLoveNoRef++;
            }
        }

        // Debug: Log love status every tick
        if (inLoveWithRef > 0 || inLoveNoRef > 0) {
            if (verboseLogging) getLogger().atInfo().log("[Hearts] Tracked: " + trackedCount +
                    ", InLove w/ref: " + inLoveWithRef +
                    ", InLove no ref: " + inLoveNoRef);
        }

        // Spawn heart particles for all animals in love (runs every 1 second)
        if (!tickLoveEntityRefs.isEmpty()) {
            World world = Universe.get().getDefaultWorld();
            if (world != null) {
                // Copy refs for async execution (list will be cleared on next tick)
                final java.util.List<Object> refsSnapshot = new java.util.ArrayList<>(tickLoveEntityRefs);
                world.execute(() -> {
                    try {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        int spawned = 0;
                        for (Object entityRef : refsSnapshot) {
                            spawnHeartParticlesAtRef(store, entityRef);
                            spawned++;
                        }
                        if (verboseLogging) getLogger().atInfo()
                                .log("[Hearts] Spawned particles for " + spawned + "/" + refsSnapshot.size() + " entities");
                    } catch (Exception e) {
                        getLogger().atWarning().log("[Hearts] Error spawning: " + e.getMessage());
                    }
                });
            }
        }

        // Early exit if less than 2 animals in love
        if (inLoveCount < 2)
            return;

        // Must execute ECS operations on world thread
        World world = Universe.get().getDefaultWorld();
        if (world == null)
            return;

        // For each type with 2+ animals in love, check distance and breed if close
        for (java.util.Map.Entry<AnimalType, java.util.List<BreedingData>> entry : tickLoveByType.entrySet()) {
            java.util.List<BreedingData> animalsOfType = entry.getValue();
            if (animalsOfType.size() < 2)
                continue;

            BreedingData animal1 = animalsOfType.get(0);
            BreedingData animal2 = animalsOfType.get(1);

            if (animal1.getEntityRef() == null || animal2.getEntityRef() == null) {
                continue;
            }

            final BreedingData finalAnimal1 = animal1;
            final BreedingData finalAnimal2 = animal2;
            final AnimalType finalType = entry.getKey();

            // Execute on world thread for ECS access
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Vector3d pos1 = getPositionOnWorldThread(store, finalAnimal1.getEntityRef());
                    Vector3d pos2 = getPositionOnWorldThread(store, finalAnimal2.getEntityRef());

                    if (pos1 == null || pos2 == null)
                        return;

                    double distance = calculateDistance(pos1, pos2);

                    if (distance <= BREEDING_DISTANCE) {
                        finalAnimal1.completeBreeding();
                        finalAnimal2.completeBreeding();
                        // Spawn baby at midpoint between the two parents
                        Vector3d midpoint = new Vector3d(
                                (pos1.getX() + pos2.getX()) / 2.0,
                                (pos1.getY() + pos2.getY()) / 2.0,
                                (pos1.getZ() + pos2.getZ()) / 2.0);
                        spawnBabyAnimal(finalType, midpoint);
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        }

        // Also check custom animal breeding (same logic, different data source)
        checkCustomAnimalBreeding(world);
    }

    /**
     * Check for custom animals in love that are close enough to breed.
     * Similar to built-in animal breeding, but uses CustomAnimalLoveData.
     */
    private void checkCustomAnimalBreeding(World world) {
        // Group custom animals in love by modelAssetId
        java.util.Map<String, java.util.List<BreedingManager.CustomAnimalLoveData>> byType = new java.util.HashMap<>();
        for (BreedingManager.CustomAnimalLoveData data : breedingManager.getCustomAnimalsInLove()) {
            if (data.isInLove() && data.getEntityRef() != null) {
                byType.computeIfAbsent(data.getModelAssetId(), k -> new java.util.ArrayList<>()).add(data);
            }
        }

        // For each type with 2+ animals in love, check distance
        for (java.util.Map.Entry<String, java.util.List<BreedingManager.CustomAnimalLoveData>> entry : byType.entrySet()) {
            java.util.List<BreedingManager.CustomAnimalLoveData> animalsOfType = entry.getValue();
            if (animalsOfType.size() < 2) continue;

            BreedingManager.CustomAnimalLoveData animal1 = animalsOfType.get(0);
            BreedingManager.CustomAnimalLoveData animal2 = animalsOfType.get(1);

            if (animal1.getEntityRef() == null || animal2.getEntityRef() == null) continue;

            final BreedingManager.CustomAnimalLoveData finalAnimal1 = animal1;
            final BreedingManager.CustomAnimalLoveData finalAnimal2 = animal2;
            final String modelAssetId = entry.getKey();

            // Execute on world thread for ECS access
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Vector3d pos1 = getPositionOnWorldThread(store, finalAnimal1.getEntityRef());
                    Vector3d pos2 = getPositionOnWorldThread(store, finalAnimal2.getEntityRef());

                    if (pos1 == null || pos2 == null) return;

                    double distance = calculateDistance(pos1, pos2);

                    if (distance <= BREEDING_DISTANCE) {
                        if (verboseLogging) getLogger().atInfo().log("[CustomBreed] Breeding %s at distance %.1f", modelAssetId, distance);

                        finalAnimal1.completeBreeding();
                        finalAnimal2.completeBreeding();

                        // Spawn baby at midpoint between the two parents
                        Vector3d midpoint = new Vector3d(
                            (pos1.getX() + pos2.getX()) / 2.0,
                            (pos1.getY() + pos2.getY()) / 2.0,
                            (pos1.getZ() + pos2.getZ()) / 2.0);

                        // Get custom animal config for baby spawning
                        CustomAnimalConfig customConfig = configManager.getCustomAnimal(modelAssetId);
                        spawnCustomAnimalBaby(modelAssetId, customConfig, midpoint);
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        }
    }

    /**
     * Spawn a baby custom animal at the given position.
     * If babyNpcRoleId is set, spawn using that role at full scale.
     * Otherwise, use scaling fallback: spawn adult NPC at 40% scale.
     */
    private void spawnCustomAnimalBaby(String modelAssetId, CustomAnimalConfig customConfig, Vector3d position) {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            final String finalModelAssetId = modelAssetId;
            final CustomAnimalConfig finalConfig = customConfig;
            final Vector3d spawnPos = position;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    // Use reflection to access NPCPlugin
                    Class<?> npcPluginClass = Class.forName("com.hypixel.hytale.server.npc.NPCPlugin");
                    java.lang.reflect.Method getInstance = npcPluginClass.getMethod("get");
                    Object npcPlugin = getInstance.invoke(null);

                    String usedRoleName = null;
                    boolean usingBabyRole = false;
                    boolean roleExists = false;

                    // 1. First, check if we have a dedicated baby NPC role
                    if (finalConfig != null && finalConfig.getBabyNpcRoleId() != null) {
                        java.lang.reflect.Method hasRoleName = npcPluginClass.getMethod("hasRoleName", String.class);
                        roleExists = (boolean) hasRoleName.invoke(npcPlugin, finalConfig.getBabyNpcRoleId());
                        if (roleExists) {
                            usedRoleName = finalConfig.getBabyNpcRoleId();
                            usingBabyRole = true;
                            logVerbose("Using dedicated baby NPC role: " + usedRoleName);
                        }
                    }

                    // 2. If no baby role, use adult role with scaling fallback
                    if (!roleExists) {
                        String adultRole = finalConfig != null ? finalConfig.getAdultNpcRoleId() : null;
                        if (adultRole == null) adultRole = finalModelAssetId;

                        java.lang.reflect.Method hasRoleName = npcPluginClass.getMethod("hasRoleName", String.class);
                        roleExists = (boolean) hasRoleName.invoke(npcPlugin, adultRole);
                        if (roleExists) {
                            usedRoleName = adultRole;
                            logVerbose("Using adult NPC role with scaling: " + usedRoleName);
                        }
                    }

                    if (!roleExists || usedRoleName == null) {
                        getLogger().atWarning().log("[CustomBreed] No valid NPC role found for: " + finalModelAssetId);
                        return;
                    }

                    // Find spawnNPC method via reflection
                    java.lang.reflect.Method spawnNPC = null;
                    for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                        if (m.getName().equals("spawnNPC")) {
                            spawnNPC = m;
                            break;
                        }
                    }

                    if (spawnNPC == null) {
                        getLogger().atWarning().log("[CustomBreed] Could not find spawnNPC method");
                        return;
                    }

                    // Spawn the entity
                    Vector3f rotation = new Vector3f(0, 0, 0);
                    Object result = spawnNPC.invoke(npcPlugin, usedRoleName, spawnPos, rotation, null);

                    if (result == null) {
                        getLogger().atWarning().log("[CustomBreed] Failed to spawn baby: " + usedRoleName);
                        return;
                    }

                    // Get the entity reference from the result
                    @SuppressWarnings("unchecked")
                    Ref<EntityStore> babyRef = null;
                    try {
                        java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                        babyRef = (Ref<EntityStore>) getFirst.invoke(result);
                    } catch (Exception e) {
                        logVerbose("Could not get entity ref from spawn result: " + e.getMessage());
                    }

                    // Apply scaling if not using baby role (40% size)
                    if (!usingBabyRole && babyRef != null) {
                        float babyScale = 0.4f;
                        try {
                            ModelComponent modelComp = store.getComponent(babyRef, ModelComponent.getComponentType());
                            if (modelComp != null) {
                                java.lang.reflect.Method setScale = modelComp.getClass().getMethod("setScale", float.class);
                                setScale.invoke(modelComp, babyScale);
                                logVerbose("Applied baby scale " + babyScale + " to custom animal");
                            }
                        } catch (Exception e) {
                            logVerbose("Could not apply scale: " + e.getMessage());
                        }
                    }

                    // NOTE: Babies don't get feed interactions - they'll get them when they grow up

                    if (verboseLogging) getLogger().atInfo().log("[CustomBreed] Spawned baby %s at (%.1f, %.1f, %.1f)",
                        finalModelAssetId, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());

                } catch (Exception e) {
                    getLogger().atWarning().log("[CustomBreed] Error spawning baby: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            getLogger().atWarning().log("[CustomBreed] Error in spawnCustomAnimalBaby: " + e.getMessage());
        }
    }

    /**
     * Get position on world thread using full reflection for robustness.
     */
    @SuppressWarnings("unchecked")
    private Vector3d getPositionOnWorldThread(Store<EntityStore> store, Object entityRef) {
        try {
            if (entityRef == null)
                return null;

            // Get the store from the ref
            java.lang.reflect.Method getStoreMethod = entityRef.getClass().getMethod("getStore");
            Object refStore = getStoreMethod.invoke(entityRef);
            if (refStore == null)
                refStore = store;

            // Use full reflection to get TransformComponent
            Class<?> transformClass = Class.forName(
                    "com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
            java.lang.reflect.Method getComponentType = transformClass.getMethod("getComponentType");
            Object componentType = getComponentType.invoke(null);

            if (componentType == null) {
                getLogger().atWarning().log("[Hearts] TransformComponent.getComponentType() returned null");
                return null;
            }

            // Use reflection to call store.getComponent(ref, componentType)
            Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
            Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");
            java.lang.reflect.Method getComponent = refStore.getClass().getMethod("getComponent", refClass,
                    componentTypeClass);
            Object transform = getComponent.invoke(refStore, entityRef, componentType);

            if (transform == null) {
                return null;
            }

            // Get position via getPosition() method
            java.lang.reflect.Method getPosition = transform.getClass().getMethod("getPosition");
            Object pos = getPosition.invoke(transform);

            if (pos instanceof Vector3d) {
                return (Vector3d) pos;
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[Hearts] getPositionOnWorldThread error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get position from BreedingData's entityRef.
     */
    @SuppressWarnings("unchecked")
    private Vector3d getPositionFromBreedingData(BreedingData data) {
        Object entityRef = data.getEntityRef();
        if (entityRef == null || !(entityRef instanceof Ref))
            return null;

        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return null;

            Store<EntityStore> store = world.getEntityStore().getStore();
            TransformComponent transform = store.getComponent((Ref<EntityStore>) entityRef, EcsReflectionUtil.TRANSFORM_TYPE);

            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Silent
        }

        return null;
    }

    /**
     * Calculate distance between two positions.
     */
    private double calculateDistance(Vector3d pos1, Vector3d pos2) {
        double dx = pos2.getX() - pos1.getX();
        double dy = pos2.getY() - pos1.getY();
        double dz = pos2.getZ() - pos1.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Perform instant breeding between two animals.
     */
    private void performInstantBreeding(BreedingData animal1, BreedingData animal2, AnimalType type,
            Vector3d spawnPos) {
        animal1.completeBreeding();
        animal2.completeBreeding();
        spawnBabyAnimal(type, spawnPos);
    }

    /**
     * Spawn a baby animal of the given type at the position.
     * For animals WITH baby variants: spawns baby NPC
     * For animals WITHOUT baby variants: spawns adult NPC at small scale (0.4)
     */
    private void spawnBabyAnimal(AnimalType animalType, Vector3d position) {
        try {
            boolean hasBabyVariant = animalType.hasBabyVariant();
            // For baby variants, use baby role; for others, use adult role
            String roleId = hasBabyVariant ? animalType.getBabyNpcRoleId() : animalType.getAdultNpcRoleId();
            float initialScale = hasBabyVariant ? 1.0f : animalType.getScaleForStage(GrowthStage.BABY);

            logVerbose("Attempting to spawn " + (hasBabyVariant ? "baby" : "scaled adult") + ": " + roleId +
                    (hasBabyVariant ? "" : " at scale " + initialScale));

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot spawn baby - world is null");
                return;
            }

            final Vector3d spawnPos = position;
            final AnimalType finalAnimalType = animalType;
            final String finalRoleId = roleId;
            final boolean finalHasBabyVariant = hasBabyVariant;
            final float finalInitialScale = initialScale;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Class<?> npcPluginClass = Class.forName("com.hypixel.hytale.server.npc.NPCPlugin");
                    java.lang.reflect.Method getInstance = npcPluginClass.getMethod("get");
                    Object npcPlugin = getInstance.invoke(null);

                    java.lang.reflect.Method hasRoleName = npcPluginClass.getMethod("hasRoleName", String.class);
                    boolean roleExists = (boolean) hasRoleName.invoke(npcPlugin, finalRoleId);

                    if (!roleExists) {
                        logWarning("NPC role not found: " + finalRoleId);
                        return;
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);

                    // Create scaled model if needed (for creatures without baby variants)
                    Object scaledModel = null;
                    if (!finalHasBabyVariant) {
                        try {
                            Class<?> modelAssetClass = Class
                                    .forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                            Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                            Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap,
                                    finalAnimalType.getModelAssetId());

                            if (modelAsset != null) {
                                Class<?> modelClass = Class
                                        .forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                                java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel",
                                        modelAssetClass, float.class);
                                scaledModel = createScaledModel.invoke(null, modelAsset, finalInitialScale);
                            }
                        } catch (Exception e) {
                            logWarning("Failed to create scaled model: " + e.getMessage());
                        }
                    }

                    java.lang.reflect.Method spawnNPC = null;
                    for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                        if (m.getName().equals("spawnNPC")) {
                            spawnNPC = m;
                            break;
                        }
                    }

                    Object result = null;
                    if (spawnNPC != null) {
                        try {
                            result = spawnNPC.invoke(npcPlugin, store, finalRoleId, null, spawnPos, rotation);
                        } catch (Exception e1) {
                            try {
                                result = spawnNPC.invoke(npcPlugin, store, finalRoleId, "", spawnPos, rotation);
                            } catch (Exception e2) {
                                // Silent
                            }
                        }
                    }

                    if (result == null) {
                        java.lang.reflect.Method getIndex = npcPluginClass.getMethod("getIndex", String.class);
                        int roleIndex = (int) getIndex.invoke(npcPlugin, finalRoleId);

                        if (roleIndex >= 0) {
                            try {
                                java.lang.reflect.Method validateRole = npcPluginClass
                                        .getMethod("validateSpawnableRole", String.class);
                                validateRole.invoke(npcPlugin, finalRoleId);
                            } catch (Exception e) {
                            }

                            try {
                                java.lang.reflect.Method prepareRole = npcPluginClass
                                        .getMethod("prepareRoleBuilderInfo", int.class);
                                prepareRole.invoke(npcPlugin, roleIndex);
                            } catch (Exception e) {
                            }

                            for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                                if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                                    Class<?>[] paramTypes = m.getParameterTypes();
                                    Class<?> triConsumerClass = paramTypes[5];
                                    Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                            triConsumerClass.getClassLoader(),
                                            new Class<?>[] { triConsumerClass },
                                            (proxy, method, args) -> null);

                                    try {
                                        // Pass scaled model for creatures without baby variants
                                        result = m.invoke(npcPlugin, store, roleIndex, spawnPos, rotation, scaledModel,
                                                noOpCallback);
                                    } catch (Exception e) {
                                        // Silent
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    if (result != null) {
                        String logMessage = finalHasBabyVariant
                                ? "Baby " + finalAnimalType.getId() + " born"
                                : "Young " + finalAnimalType.getId() + " born (scale "
                                        + String.format("%.1f", finalInitialScale) + ")";
                        if (verboseLogging) getLogger().atInfo().log("[Lait:AnimalBreeding] " + logMessage + " at " +
                                String.format("%.0f, %.0f, %.0f", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));

                        Object entityRef = null;
                        try {
                            java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                            entityRef = getFirst.invoke(result);
                        } catch (Exception e) {
                            entityRef = result;
                        }

                        if (entityRef != null) {
                            UUID babyId = UUID.randomUUID();
                            breedingManager.registerBaby(babyId, finalAnimalType, entityRef);

                            // For creatures without baby variants, always apply initial scale after spawn
                            // (spawnNPC doesn't accept a model parameter, so we must scale afterwards)
                            if (!finalHasBabyVariant) {
                                try {
                                    Class<?> modelCompClass = Class.forName(
                                            "com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
                                    java.lang.reflect.Method getModelType = modelCompClass
                                            .getMethod("getComponentType");
                                    Object modelType = getModelType.invoke(null);

                                    java.lang.reflect.Method getComponent = null;
                                    for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                                        if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                                            Class<?>[] params = m.getParameterTypes();
                                            if (params[0].isAssignableFrom(entityRef.getClass())) {
                                                getComponent = m;
                                                break;
                                            }
                                        }
                                    }

                                    if (getComponent != null) {
                                        Object modelComp = getComponent.invoke(store, entityRef, modelType);
                                        if (modelComp != null && EcsReflectionUtil.isModelFieldInitialized() && EcsReflectionUtil.getCachedModelField() != null) {
                                            // Use cached Field for performance
                                            Object currentModel = EcsReflectionUtil.getCachedModelField().get(modelComp);

                                            if (currentModel != null) {
                                                java.lang.reflect.Field assetIdField = currentModel.getClass()
                                                        .getDeclaredField("modelAssetId");
                                                assetIdField.setAccessible(true);
                                                String modelAssetId = (String) assetIdField.get(currentModel);

                                                Class<?> modelAssetClass = Class.forName(
                                                        "com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                                                Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                                                Object modelAsset = assetMap.getClass()
                                                        .getMethod("getAsset", Object.class)
                                                        .invoke(assetMap, modelAssetId);

                                                if (modelAsset != null) {
                                                    Class<?> modelClass = Class.forName(
                                                            "com.hypixel.hytale.server.core.asset.type.model.config.Model");
                                                    java.lang.reflect.Method createScaledModel = modelClass.getMethod(
                                                            "createScaledModel", modelAssetClass, float.class);
                                                    Object newModel = createScaledModel.invoke(null, modelAsset,
                                                            finalInitialScale);

                                                    // Try to convert to ModelReference if needed
                                                    Object modelToSet = newModel;
                                                    try {
                                                        java.lang.reflect.Method toReference = newModel.getClass()
                                                                .getMethod("toReference");
                                                        Object modelRef = toReference.invoke(newModel);
                                                        if (modelRef != null) {
                                                            modelToSet = modelRef;
                                                        }
                                                    } catch (NoSuchMethodException e) {
                                                        // No toReference method, use the model directly
                                                    }

                                                    EcsReflectionUtil.getCachedModelField().set(modelComp, modelToSet);

                                                    // Try to trigger ECS sync via setComponent
                                                    try {
                                                        java.lang.reflect.Method setComponent = null;
                                                        for (java.lang.reflect.Method m : store.getClass()
                                                                .getMethods()) {
                                                            if (m.getName().equals("setComponent")
                                                                    && m.getParameterCount() == 3) {
                                                                Class<?>[] params = m.getParameterTypes();
                                                                if (params[0].isAssignableFrom(entityRef.getClass())) {
                                                                    setComponent = m;
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                        if (setComponent != null) {
                                                            setComponent.invoke(store, entityRef, modelType, modelComp);
                                                        }
                                                    } catch (Exception syncEx) {
                                                        // Silent - sync method may not exist
                                                    }

                                                    // Try CommandBuffer for proper sync
                                                    try {
                                                        Object commandBuffer = world.getClass()
                                                                .getMethod("getCommandBuffer").invoke(world);
                                                        if (commandBuffer != null) {
                                                            for (java.lang.reflect.Method m : commandBuffer.getClass()
                                                                    .getMethods()) {
                                                                if (m.getName().equals("setComponent")
                                                                        && m.getParameterCount() == 3) {
                                                                    Class<?>[] params = m.getParameterTypes();
                                                                    if (params[0]
                                                                            .isAssignableFrom(entityRef.getClass())) {
                                                                        m.invoke(commandBuffer, entityRef, modelType,
                                                                                modelComp);
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch (Exception cbEx) {
                                                        // Silent
                                                    }

                                                    logVerbose("Applied initial scale " + finalInitialScale + " to "
                                                            + finalAnimalType.getId());
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception scaleEx) {
                                    logWarning("Failed to apply initial scale: " + scaleEx.getMessage());
                                    scaleEx.printStackTrace();
                                }
                            }
                        }
                    } else {
                        logWarning("Failed to spawn " + (finalHasBabyVariant ? "baby" : "young") + " "
                                + finalAnimalType.getId() + " - spawn returned null");
                    }

                } catch (Exception e) {
                    logError("Error spawning baby: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logError("Error in spawnBabyAnimal: " + e.getMessage());
        }
    }

    /**
     * Update an entity's model scale (for creatures without baby variants).
     * 
     * @param animalId   The animal's UUID
     * @param animalType The type of animal
     * @param scale      The target scale (0.4 for baby, 0.7 for juvenile, 1.0 for
     *                   adult)
     */
    private void updateEntityScale(UUID animalId, AnimalType animalType, float scale) {
        try {
            logVerbose("Updating scale for " + animalType.getId() + " to " + scale);

            BreedingData data = breedingManager.getData(animalId);
            if (data == null) {
                logWarning("Cannot update scale - no breeding data for animal");
                return;
            }

            Object entityRef = data.getEntityRef();
            if (entityRef == null) {
                logWarning("Cannot update scale - no entity ref for animal");
                return;
            }

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot update scale - world is null");
                return;
            }

            final Object finalEntityRef = entityRef;
            final float targetScale = scale;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    // Get ModelComponent
                    Class<?> modelCompClass = Class
                            .forName("com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
                    java.lang.reflect.Method getModelType = modelCompClass.getMethod("getComponentType");
                    Object modelType = getModelType.invoke(null);

                    java.lang.reflect.Method getComponent = null;
                    for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                        if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                            Class<?>[] params = m.getParameterTypes();
                            if (params[0].isAssignableFrom(finalEntityRef.getClass())) {
                                getComponent = m;
                                break;
                            }
                        }
                    }

                    if (getComponent == null)
                        return;

                    // Try to get model component - may throw if entity ref is stale
                    Object modelComp = null;
                    try {
                        modelComp = getComponent.invoke(store, finalEntityRef, modelType);
                    } catch (Exception refEx) {
                        Throwable cause = refEx;
                        if (refEx instanceof java.lang.reflect.InvocationTargetException) {
                            cause = ((java.lang.reflect.InvocationTargetException) refEx).getTargetException();
                        }
                        if (cause instanceof IllegalStateException &&
                                cause.getMessage() != null &&
                                cause.getMessage().contains("Invalid entity")) {
                            logVerbose("Entity ref is stale - removing tracking data");
                            breedingManager.removeData(animalId);
                            return;
                        }
                        throw refEx;
                    }

                    if (modelComp == null) {
                        logVerbose("Entity has no ModelComponent - removing stale data");
                        breedingManager.removeData(animalId);
                        return;
                    }

                    // Use cached Field for performance (avoid getDeclaredField per call)
                    if (!EcsReflectionUtil.isModelFieldInitialized() || EcsReflectionUtil.getCachedModelField() == null) {
                        logWarning("Model field cache not initialized");
                        return;
                    }
                    Object currentModel = EcsReflectionUtil.getCachedModelField().get(modelComp);

                    if (currentModel == null) {
                        logWarning("Entity has no model - cannot scale");
                        return;
                    }

                    // Get the modelAssetId from current model
                    java.lang.reflect.Field assetIdField = currentModel.getClass().getDeclaredField("modelAssetId");
                    assetIdField.setAccessible(true);
                    String modelAssetId = (String) assetIdField.get(currentModel);

                    // Get the ModelAsset
                    Class<?> modelAssetClass = Class
                            .forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                    Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                    Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap,
                            modelAssetId);

                    if (modelAsset == null) {
                        logWarning("ModelAsset not found: " + modelAssetId);
                        return;
                    }

                    // Create new scaled model
                    Class<?> modelClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                    java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel",
                            modelAssetClass, float.class);
                    Object newModel = createScaledModel.invoke(null, modelAsset, targetScale);

                    // Try to convert to ModelReference if needed
                    Object modelToSet = newModel;
                    try {
                        java.lang.reflect.Method toReference = newModel.getClass().getMethod("toReference");
                        Object modelRef = toReference.invoke(newModel);
                        if (modelRef != null) {
                            modelToSet = modelRef;
                            logVerbose("Converted Model to ModelReference for setting");
                        }
                    } catch (NoSuchMethodException e) {
                        // No toReference method, use the model directly
                        logVerbose("No toReference method, using Model directly");
                    }

                    // Set the new model on the ModelComponent using cached field
                    EcsReflectionUtil.getCachedModelField().set(modelComp, modelToSet);

                    // Log what we set
                    logVerbose("Set model field to: " + modelToSet.toString());

                    // Try to trigger ECS sync by using setComponent
                    try {
                        java.lang.reflect.Method setComponent = null;
                        for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                            if (m.getName().equals("setComponent") && m.getParameterCount() == 3) {
                                Class<?>[] params = m.getParameterTypes();
                                if (params[0].isAssignableFrom(finalEntityRef.getClass())) {
                                    setComponent = m;
                                    break;
                                }
                            }
                        }
                        if (setComponent != null) {
                            setComponent.invoke(store, finalEntityRef, modelType, modelComp);
                            logVerbose("Set component via store.setComponent for scale sync");
                        }
                    } catch (Exception syncEx) {
                        logVerbose("Could not use setComponent for sync: " + syncEx.getMessage());
                    }

                    // Also try marking the entity as dirty/changed if possible
                    try {
                        java.lang.reflect.Method markDirty = modelComp.getClass().getMethod("markDirty");
                        markDirty.invoke(modelComp);
                        logVerbose("Marked ModelComponent as dirty");
                    } catch (NoSuchMethodException nsme) {
                        // Method doesn't exist, try alternatives
                        try {
                            java.lang.reflect.Method setChanged = modelComp.getClass().getMethod("setChanged",
                                    boolean.class);
                            setChanged.invoke(modelComp, true);
                            logVerbose("Set ModelComponent as changed");
                        } catch (NoSuchMethodException nsme2) {
                            // No dirty/changed method available
                        }
                    }

                    // Try using CommandBuffer for proper ECS sync
                    try {
                        Object commandBuffer = world.getClass().getMethod("getCommandBuffer").invoke(world);
                        if (commandBuffer != null) {
                            // Look for setComponent method on CommandBuffer
                            for (java.lang.reflect.Method m : commandBuffer.getClass().getMethods()) {
                                if (m.getName().equals("setComponent") && m.getParameterCount() == 3) {
                                    Class<?>[] params = m.getParameterTypes();
                                    if (params[0].isAssignableFrom(finalEntityRef.getClass())) {
                                        m.invoke(commandBuffer, finalEntityRef, modelType, modelComp);
                                        logVerbose("Queued component update via CommandBuffer");
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception cbEx) {
                        logVerbose("CommandBuffer approach failed: " + cbEx.getMessage());
                    }

                    if (verboseLogging) getLogger().atInfo().log("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) +
                            " grew to scale " + String.format("%.1f", targetScale));

                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null)
                            cause = e;
                    }
                    String errorMsg = cause.getMessage();
                    if (errorMsg == null) {
                        errorMsg = cause.getClass().getSimpleName() + " (no message)";
                    }
                    logError("Error updating entity scale: " + errorMsg);
                    cause.printStackTrace();
                }
            });

        } catch (Exception e) {
            logError("Error in updateEntityScale: " + e.getMessage());
        }
    }

    /**
     * Transform a baby animal into an adult by removing the baby and spawning an
     * adult NPC.
     * Used for animals WITH baby variants (livestock).
     */
    private void transformBabyToAdult(UUID animalId, AnimalType animalType) {
        try {
            logVerbose("Transforming baby " + animalType.getId() + " to adult");

            BreedingData data = breedingManager.getData(animalId);
            if (data == null) {
                logWarning("Cannot transform - no breeding data for animal");
                return;
            }

            Object entityRef = data.getEntityRef();
            if (entityRef == null) {
                logWarning("Cannot transform - no entity ref for animal");
                return;
            }

            World world = Universe.get().getDefaultWorld();
            if (world == null) {
                logWarning("Cannot transform - world is null");
                return;
            }

            String adultRoleId = animalType.getModelAssetId();
            final Object finalEntityRef = entityRef;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Class<?> transformCompClass = Class
                            .forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
                    java.lang.reflect.Method getTransformType = transformCompClass.getMethod("getComponentType");
                    Object transformType = getTransformType.invoke(null);

                    java.lang.reflect.Method getComponent = null;
                    for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                        if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                            Class<?>[] params = m.getParameterTypes();
                            if (params[0].isAssignableFrom(finalEntityRef.getClass())) {
                                getComponent = m;
                                break;
                            }
                        }
                    }

                    if (getComponent == null)
                        return;

                    // Try to get transform component - may throw if entity ref is stale
                    Object transformComp = null;
                    try {
                        transformComp = getComponent.invoke(store, finalEntityRef, transformType);
                    } catch (Exception refEx) {
                        // Entity reference is invalid (entity was despawned)
                        Throwable cause = refEx;
                        if (refEx instanceof java.lang.reflect.InvocationTargetException) {
                            cause = ((java.lang.reflect.InvocationTargetException) refEx).getTargetException();
                        }
                        if (cause instanceof IllegalStateException &&
                                cause.getMessage() != null &&
                                cause.getMessage().contains("Invalid entity")) {
                            logVerbose("Baby entity ref is stale - removing tracking data");
                            breedingManager.removeData(animalId);
                            return;
                        }
                        throw refEx; // Re-throw if it's a different error
                    }

                    if (transformComp == null) {
                        // Entity no longer exists - remove stale tracking data
                        logVerbose("Baby entity no longer exists - removing stale data");
                        breedingManager.removeData(animalId);
                        return;
                    }

                    java.lang.reflect.Method getPosition = transformComp.getClass().getMethod("getPosition");
                    Vector3d babyPosition = (Vector3d) getPosition.invoke(transformComp);

                    // Check if position is valid (entity might have been removed)
                    if (babyPosition == null) {
                        logVerbose("Baby entity no longer has valid position - removing stale data");
                        breedingManager.removeData(animalId);
                        return;
                    }

                    // Remove the baby entity
                    try {
                        Class<?> removeReasonClass = Class.forName("com.hypixel.hytale.component.RemoveReason");
                        Object despawnReason = null;
                        for (Object constant : removeReasonClass.getEnumConstants()) {
                            if (constant.toString().contains("DESPAWN") || constant.toString().contains("REMOVE")) {
                                despawnReason = constant;
                                break;
                            }
                        }
                        if (despawnReason == null) {
                            despawnReason = removeReasonClass.getEnumConstants()[0];
                        }

                        java.lang.reflect.Method removeEntity = null;
                        for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                            if (m.getName().equals("removeEntity") && m.getParameterCount() == 2) {
                                Class<?>[] params = m.getParameterTypes();
                                if (params[0].isAssignableFrom(finalEntityRef.getClass())) {
                                    removeEntity = m;
                                    break;
                                }
                            }
                        }

                        if (removeEntity != null) {
                            removeEntity.invoke(store, finalEntityRef, despawnReason);
                        }
                    } catch (Exception e) {
                        // Silent
                    }

                    // Spawn adult NPC at the same position
                    Class<?> npcPluginClass = Class.forName("com.hypixel.hytale.server.npc.NPCPlugin");
                    java.lang.reflect.Method getInstance = npcPluginClass.getMethod("get");
                    Object npcPlugin = getInstance.invoke(null);

                    java.lang.reflect.Method getIndex = npcPluginClass.getMethod("getIndex", String.class);
                    int roleIndex = (int) getIndex.invoke(npcPlugin, adultRoleId);

                    if (roleIndex < 0) {
                        logWarning("Adult NPC role not found: " + adultRoleId);
                        return;
                    }

                    Vector3f rotation = new Vector3f(0, 0, 0);
                    boolean spawned = false;
                    for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                        if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                            Class<?>[] paramTypes = m.getParameterTypes();
                            Class<?> triConsumerClass = paramTypes[5];
                            Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                    triConsumerClass.getClassLoader(),
                                    new Class<?>[] { triConsumerClass },
                                    (proxy, method, args) -> null);

                            Object result = m.invoke(npcPlugin, store, roleIndex, babyPosition, rotation, null,
                                    noOpCallback);
                            if (result != null) {
                                getLogger().atInfo()
                                        .log("[Lait:AnimalBreeding] " + capitalize(animalType.getId())
                                                + " grew into an adult at " +
                                                String.format("%.0f, %.0f, %.0f", babyPosition.getX(),
                                                        babyPosition.getY(), babyPosition.getZ()));
                                spawned = true;
                            }
                            break;
                        }
                    }

                    if (!spawned) {
                        logWarning("Failed to spawn adult " + animalType.getId());
                    }

                    breedingManager.removeData(animalId);

                } catch (Exception e) {
                    // Unwrap InvocationTargetException to get real cause
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null)
                            cause = e;
                    }
                    String errorMsg = cause.getMessage();
                    if (errorMsg == null) {
                        errorMsg = cause.getClass().getSimpleName() + " (no message)";
                    }
                    logError("Error transforming to adult: " + errorMsg);
                    // Print stack trace for debugging
                    cause.printStackTrace();
                    // Clean up stale data to prevent repeated errors
                    breedingManager.removeData(animalId);
                }
            });

        } catch (Exception e) {
            logError("Error in transformBabyToAdult: " + e.getMessage());
        }
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

        // Save tamed animal data before shutdown
        if (persistenceManager != null && tamingManager != null) {
            getLogger().atInfo().log("[Taming] Saving tamed animals on shutdown...");
            persistenceManager.stopAutoSave();
            persistenceManager.forceSave(tamingManager.getAllTamedAnimals());
        }

        // Clear breeding data
        if (breedingManager != null) {
            breedingManager.clearAll();
        }

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

}
