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

    // Getter for tick scheduler (used by commands)
    ScheduledExecutorService getTickScheduler() {
        return tickScheduler;
    }

    // Cached ECS component types for performance
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE = TransformComponent
            .getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE = UUIDComponent.getComponentType();

    // Cached reflection Field for ModelComponent.model (avoid per-call getDeclaredField)
    private static java.lang.reflect.Field cachedModelField = null;
    private static boolean modelFieldInitialized = false;

    static {
        try {
            cachedModelField = ModelComponent.class.getDeclaredField("model");
            cachedModelField.setAccessible(true);
            modelFieldInitialized = true;
        } catch (Exception e) {
            modelFieldInitialized = false;
        }
    }

    // These are initialized at runtime since their getComponentType() may not be
    // public
    private static Object INTERACTIONS_COMP_TYPE = null;
    private static Object INTERACTABLE_COMP_TYPE = null;

    private static Object getInteractionsComponentType() {
        if (INTERACTIONS_COMP_TYPE == null) {
            try {
                INTERACTIONS_COMP_TYPE = Interactions.class.getMethod("getComponentType").invoke(null);
            } catch (Exception e) {
                System.out.println("[LaitsBreeding] ERROR: Failed to get Interactions component type: " + e.getMessage());
            }
        }
        return INTERACTIONS_COMP_TYPE;
    }

    private static Object getInteractableComponentType() {
        if (INTERACTABLE_COMP_TYPE == null) {
            try {
                INTERACTABLE_COMP_TYPE = Interactable.class.getMethod("getComponentType").invoke(null);
            } catch (Exception e) {
                System.out.println("[LaitsBreeding] ERROR: Failed to get Interactable component type: " + e.getMessage());
            }
        }
        return INTERACTABLE_COMP_TYPE;
    }

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
     * Get a stable key for an entity ref using UUIDComponent.
     * Falls back to index if UUID not available.
     */
    private static String getStableEntityKey(Object entityRef) {
        if (!(entityRef instanceof Ref))
            return null;
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
                if (uuidComp != null && uuidComp.getUuid() != null) {
                    return uuidComp.getUuid().toString();
                }
            }
        } catch (Exception e) {
            // Fall through to index-based key
        }
        // Fallback to ref index if UUID not available
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            Integer index = ref.getIndex();
            if (index != null) {
                return "idx:" + index;
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

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
     * COPIED FROM FeedAnimalInteraction.getUuidFromRef() - known working implementation.
     */
    private UUID getUuidFromRef(Ref<EntityStore> ref) {
        try {
            Store<EntityStore> store = ref.getStore();
            if (store != null) {
                UUIDComponent uuidComp = store.getComponent(ref, UUID_TYPE);
                if (uuidComp != null) {
                    UUID uuid = uuidComp.getUuid();
                    if (uuid != null) {
                        return uuid;
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to fallback
        }

        // Fallback: use ref index if available (stable within session)
        try {
            Integer index = ref.getIndex();
            if (index != null) {
                return UUID.nameUUIDFromBytes(("entity_ref_" + index).getBytes());
            }
        } catch (Exception e) {
            // Silent
        }
        return UUID.nameUUIDFromBytes(ref.toString().getBytes());
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
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, UUID_TYPE);
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
        String key = getStableEntityKey(entityRef);
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
        String key = getStableEntityKey(entityRef);
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
        String key = getStableEntityKey(entityRef);
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
        getCommandRegistry().registerCommand(new BreedingHelpCommand());      // Use /breed help
        getCommandRegistry().registerCommand(new BreedingStatusCommand());    // Use /breed status
        getCommandRegistry().registerCommand(new BreedingConfigCommand());    // Use /breed config
        getCommandRegistry().registerCommand(new BreedingGrowthCommand());    // Use /breed growth
        getCommandRegistry().registerCommand(new NameTagCommand());           // Use /breed tame
        getCommandRegistry().registerCommand(new TamingInfoCommand());        // Use /breed info
        getCommandRegistry().registerCommand(new TamingSettingsCommand());    // Use /breed settings
        getCommandRegistry().registerCommand(new UntameCommand());            // Use /breed untame
        getCommandRegistry().registerCommand(new CustomAnimalCommand());      // Use /breed custom

        // Dev/debug commands (no unified equivalent)
        getCommandRegistry().registerCommand(new BreedingLogsCommand());
        getCommandRegistry().registerCommand(new BreedingDevCommand());
        getCommandRegistry().registerCommand(new BreedingHintCommand());
        getCommandRegistry().registerCommand(new BreedingScanCommand());
        getCommandRegistry().registerCommand(new BreedingCachesCommand());
        getCommandRegistry().registerCommand(new NoClipCommand());
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
    private void attachInteractionsToAnimals() {
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
                                        ? "server.interactionHints.feed"
                                        : "server.interactionHints.feed";
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
        try {
            ModelComponent modelComp = store.getComponent(entityRef, MODEL_TYPE);
            if (modelComp == null)
                return null;

            // Use cached Field for performance (avoid getDeclaredField per call)
            if (!modelFieldInitialized || cachedModelField == null)
                return null;

            Object model = cachedModelField.get(modelComp);
            if (model == null)
                return null;

            // Extract modelAssetId from model.toString()
            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0)
                return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start)
                return null;
            return modelStr.substring(start, end);

        } catch (Exception e) {
            return null;
        }
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
            Object interactableType = getInteractableComponentType();
            Object interactionsType = getInteractionsComponentType();
            if (interactionsType == null) {
                getLogger().atWarning().log("[SetupInteraction] interactionsType is NULL for %s", animalType);
                return;
            }

            // Check if entity already has Interactions component (real NPCs have this)
            // Use getComponent instead of ensureAndGetComponent to avoid adding to non-NPCs
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
                    ? "server.interactionHints.legacyFeedOrMount"
                    : "server.interactionHints.legacyFeed";
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

            Object interactableType = getInteractableComponentType();
            Object interactionsType = getInteractionsComponentType();
            if (interactionsType == null) {
                getLogger().atWarning().log("[CustomAnimal] %s: interactionsType is NULL, aborting", animalName);
                return;
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
            setHint.invoke(interactions, "server.interactionHints.legacyFeed");
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
            Object interactionsType = getInteractionsComponentType();
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

            String entityKey = getStableEntityKey(entityRef);

            if (shouldShowFeed) {
                // FEED MODE - show feed interaction
                setIntId.invoke(interactions, useType, "Root_FeedAnimal");
                String hintKey = animalType.isMountable()
                        ? "server.interactionHints.legacyFeedOrMount"
                        : "server.interactionHints.legacyFeed";
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
                updateAnimalInteractionState(entityRef, animalType, data);
            } catch (Exception e) {
                // Entity despawned or ref invalid - will be cleaned up by stale check
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
            Object interactableType = getInteractableComponentType();
            Object interactionsType = getInteractionsComponentType();

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
        String key = getStableEntityKey(entityRef);
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
                            String key = getStableEntityKey(ref);
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
                                    UUIDComponent uuidComp = store.getComponent(entityRef, UUID_TYPE);
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
     * Package-private so it can be called from the BreedingScanCommand.
     */
    void autoSetupNearbyAnimals() {
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
                            try {
                                Store<EntityStore> refStore = ref.getStore();
                                if (refStore != null) {
                                    UUIDComponent uuidComp = refStore.getComponent(ref, UUID_TYPE);
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
                                            ? "server.interactionHints.feed"
                                            : "server.interactionHints.feed";
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
        try {
            // Try Entity.getRef() method
            java.lang.reflect.Method getRef = entity.getClass().getMethod("getRef");
            return getRef.invoke(entity);
        } catch (NoSuchMethodException e) {
            // Try alternative methods
            try {
                // Some entities might have getEntityRef()
                java.lang.reflect.Method getEntityRef = entity.getClass().getMethod("getEntityRef");
                return getEntityRef.invoke(entity);
            } catch (Exception e2) {
                // Silent - not all entities have ref access
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    /**
     * Get UUID for an entity using ECS UUIDComponent (avoids deprecated getUuid()).
     * Falls back to generating a consistent UUID from entity ref.
     */
    @SuppressWarnings("unchecked")
    private UUID getEntityUUID(Entity entity) {
        try {
            // Try to get from UUIDComponent via ECS
            Object entityRef = getEntityRef(entity);
            if (entityRef != null && entityRef instanceof Ref) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, UUID_TYPE);
                    if (uuidComp != null) {
                        UUID uuid = uuidComp.getUuid();
                        if (uuid != null) {
                            return uuid;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to alternative
        }

        // Fallback: generate consistent UUID from entity ref string
        Object entityRef = getEntityRef(entity);
        if (entityRef != null) {
            return UUID.nameUUIDFromBytes(("entity_" + entityRef.toString()).getBytes());
        }

        // Last resort: use object identity
        return UUID.nameUUIDFromBytes(("entity_" + System.identityHashCode(entity)).getBytes());
    }

    /**
     * Get UUID for a player entity.
     */
    @SuppressWarnings("unchecked")
    private UUID getPlayerUuidFromEntity(Player player) {
        try {
            Object entityRef = player.getReference();
            if (entityRef != null && entityRef instanceof Ref) {
                World world = player.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    UUIDComponent uuidComp = store.getComponent((Ref<EntityStore>) entityRef, UUID_TYPE);
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
        try {
            Object entityRef = getEntityRef(entity);
            if (entityRef != null && entityRef instanceof Ref) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    ModelComponent modelComp = store.getComponent((Ref<EntityStore>) entityRef, MODEL_TYPE);
                    if (modelComp != null) {
                        // Use cached Field for performance (avoid getDeclaredField per call)
                        if (!modelFieldInitialized || cachedModelField == null)
                            return entity.toString();

                        Object model = cachedModelField.get(modelComp);

                        if (model != null) {
                            // Parse modelAssetId from toString to avoid additional reflection
                            String modelStr = model.toString();
                            int start = modelStr.indexOf("modelAssetId='");
                            if (start >= 0) {
                                start += 14;
                                int end = modelStr.indexOf("'", start);
                                if (end > start) {
                                    return modelStr.substring(start, end);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silent - fall through to alternative
        }

        // Fallback: use entity toString which may contain type info
        return entity.toString();
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
        java.util.Map<AnimalType, java.util.List<BreedingData>> byType = new java.util.HashMap<>();
        java.util.List<Object> inLoveEntityRefs = new java.util.ArrayList<>();
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
                    inLoveEntityRefs.add(data.getEntityRef());
                    inLoveWithRef++;
                } else {
                    inLoveNoRef++;
                }

                // Collect if eligible for breeding
                if (!data.isPregnant() && data.getGrowthStage().canBreed()) {
                    byType.computeIfAbsent(data.getAnimalType(), k -> new java.util.ArrayList<>()).add(data);
                    inLoveCount++;
                }
            }
        }

        // Also process custom animals in love mode
        breedingManager.tickCustomAnimalLove(); // Expire old love modes
        for (BreedingManager.CustomAnimalLoveData customData : breedingManager.getCustomAnimalsInLove()) {
            if (customData.getEntityRef() != null) {
                inLoveEntityRefs.add(customData.getEntityRef());
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
        if (!inLoveEntityRefs.isEmpty()) {
            World world = Universe.get().getDefaultWorld();
            if (world != null) {
                final int refCount = inLoveEntityRefs.size();
                world.execute(() -> {
                    try {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        int spawned = 0;
                        for (Object entityRef : inLoveEntityRefs) {
                            spawnHeartParticlesAtRef(store, entityRef);
                            spawned++;
                        }
                        if (verboseLogging) getLogger().atInfo()
                                .log("[Hearts] Spawned particles for " + spawned + "/" + refCount + " entities");
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
        for (java.util.Map.Entry<AnimalType, java.util.List<BreedingData>> entry : byType.entrySet()) {
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
            TransformComponent transform = store.getComponent((Ref<EntityStore>) entityRef, TRANSFORM_TYPE);

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
                                        if (modelComp != null && modelFieldInitialized && cachedModelField != null) {
                                            // Use cached Field for performance
                                            Object currentModel = cachedModelField.get(modelComp);

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

                                                    cachedModelField.set(modelComp, modelToSet);

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
                    if (!modelFieldInitialized || cachedModelField == null) {
                        logWarning("Model field cache not initialized");
                        return;
                    }
                    Object currentModel = cachedModelField.get(modelComp);

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
                    cachedModelField.set(modelComp, modelToSet);

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

    // ===========================================
    // COMMANDS
    // ===========================================

    // ===========================================
    // UNIFIED /breed COMMAND
    // ===========================================

    /**
     * Unified command for all breeding functionality.
     * Usage: /breed [subcommand]
     * Subcommands: help, status, config, growth, tame, untame, info, settings, custom
     */
    public static class BreedCommand extends AbstractCommand {
        public BreedCommand() {
            super("breed", "Main command for Lait's Animal Breeding");
            addSubCommand(new BreedHelpSubCommand());
            addSubCommand(new BreedStatusSubCommand());
            addSubCommand(new BreedConfigSubCommand());
            addSubCommand(new BreedGrowthSubCommand());
            addSubCommand(new BreedTameSubCommand());
            addSubCommand(new BreedUntameSubCommand());
            addSubCommand(new BreedInfoSubCommand());
            addSubCommand(new BreedSettingsSubCommand());
            addSubCommand(new BreedCustomSubCommand());
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            // Default action: show help
            showHelp(ctx);
            return CompletableFuture.completedFuture(null);
        }

        private static void showHelp(CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Lait's Animal Breeding ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                    .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Commands:").color("#FFAA00"));
            ctx.sendMessage(Message.raw("/breed help").color("#FFFFFF")
                    .insert(Message.raw(" - Show this help").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed status").color("#FFFFFF")
                    .insert(Message.raw(" - View tracked animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed config ...").color("#FFFFFF")
                    .insert(Message.raw(" - Configuration commands").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed growth").color("#FFFFFF")
                    .insert(Message.raw(" - Toggle baby growth").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed tame <name>").color("#FFFFFF")
                    .insert(Message.raw(" - Prepare to tame an animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed untame").color("#FFFFFF")
                    .insert(Message.raw(" - Release a tamed animal").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed info").color("#FFFFFF")
                    .insert(Message.raw(" - Show taming info").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed settings").color("#FFFFFF")
                    .insert(Message.raw(" - Taming settings").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("/breed custom ...").color("#FFFFFF")
                    .insert(Message.raw(" - Manage custom animals").color("#AAAAAA")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Feed animals their favorite food to breed!").color("#55FF55"));
        }

        // --- Subcommand: help ---
        public static class BreedHelpSubCommand extends AbstractCommand {
            public BreedHelpSubCommand() {
                super("help", "Show help information");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                showHelp(ctx);
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: status ---
        public static class BreedStatusSubCommand extends AbstractCommand {
            public BreedStatusSubCommand() {
                super("status", "View tracked animals and breeding stats");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                BreedingManager breeding = plugin.getBreedingManager();
                TamingManager taming = plugin.getTamingManager();

                ctx.sendMessage(Message.raw("=== Breeding Status ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Animals tracked: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(breeding.getTrackedCount())).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("In love mode: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(breeding.getInLoveCount())).color("#FF69B4")));
                ctx.sendMessage(Message.raw("Pregnant: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(breeding.getPregnantCount())).color("#FFFF55")));

                if (taming != null) {
                    ctx.sendMessage(Message.raw("Tamed animals: ").color("#AAAAAA")
                            .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#55FF55")));
                }

                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: config (delegates to BreedingConfigCommand subcommands) ---
        public static class BreedConfigSubCommand extends AbstractCommand {
            public BreedConfigSubCommand() {
                super("config", "Configuration commands");
                // Add all config subcommands
                addSubCommand(new BreedingConfigCommand.ReloadSubCommand());
                addSubCommand(new BreedingConfigCommand.SaveSubCommand());
                addSubCommand(new BreedingConfigCommand.ListSubCommand());
                addSubCommand(new BreedingConfigCommand.InfoSubCommand());
                addSubCommand(new BreedingConfigCommand.EnableSubCommand());
                addSubCommand(new BreedingConfigCommand.DisableSubCommand());
                addSubCommand(new BreedingConfigCommand.SetSubCommand());
                addSubCommand(new BreedingConfigCommand.AddFoodSubCommand());
                addSubCommand(new BreedingConfigCommand.RemoveFoodSubCommand());
                addSubCommand(new BreedingConfigCommand.PresetSubCommand());
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                // Show config summary
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Config not loaded!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }
                ctx.sendMessage(Message.raw("=== Breeding Config ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                        .insert(Message.raw(plugin.getConfigManager().getActivePreset()).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Type ").color("#AAAAAA")
                        .insert(Message.raw("/breed config").color("#FFFFFF"))
                        .insert(Message.raw(" and press TAB for subcommands").color("#AAAAAA")));
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: growth ---
        public static class BreedGrowthSubCommand extends AbstractCommand {
            public BreedGrowthSubCommand() {
                super("growth", "Toggle baby animal growth");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                ConfigManager config = plugin.getConfigManager();
                boolean current = config.isGrowthEnabled();
                config.setGrowthEnabled(!current);

                if (config.isGrowthEnabled()) {
                    ctx.sendMessage(Message.raw("Baby growth: ").color("#AAAAAA")
                            .insert(Message.raw("ENABLED").color("#55FF55")));
                    ctx.sendMessage(Message.raw("Babies will grow into adults over time.").color("#AAAAAA"));
                } else {
                    ctx.sendMessage(Message.raw("Baby growth: ").color("#AAAAAA")
                            .insert(Message.raw("DISABLED").color("#FF5555")));
                    ctx.sendMessage(Message.raw("Babies will stay babies forever!").color("#AAAAAA"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: tame ---
        public static class BreedTameSubCommand extends AbstractCommand {
            private final RequiredArg<String> nameArg;

            public BreedTameSubCommand() {
                super("tame", "Prepare to tame and name an animal");
                nameArg = withRequiredArg("name", "Name for the animal", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null || plugin.tamingManager == null) {
                    ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String name = ctx.get(nameArg);
                if (name.length() > 32) {
                    ctx.sendMessage(Message.raw("Name too long (max 32 characters)").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                if (!ctx.isPlayer()) {
                    ctx.sendMessage(Message.raw("This command can only be used by players").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                Player player = (Player) ctx.sender();
                if (player == null) {
                    ctx.sendMessage(Message.raw("Could not identify player").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Schedule UUID lookup on world thread
                World world = Universe.get().getDefaultWorld();
                if (world != null) {
                    final String pendingName = name;
                    final Player finalPlayer = player;
                    world.execute(() -> {
                        try {
                            UUID playerUuid = plugin.getPlayerUuidFromEntity(finalPlayer);
                            if (playerUuid != null) {
                                plugin.tamingManager.setPendingNameTag(playerUuid, pendingName);
                            }
                        } catch (Exception e) {
                            // Silent
                        }
                    });
                }

                // Also store by name as fallback
                String playerName = player.getDisplayName();
                if (playerName != null) {
                    plugin.tamingManager.setPendingNameTagByName(playerName, name);
                }

                ctx.sendMessage(Message.raw("Name tag ready: ").color("#AAAAAA")
                        .insert(Message.raw(name).color("#55FF55")));
                ctx.sendMessage(Message.raw("Press F on an animal to tame it.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: untame ---
        public static class BreedUntameSubCommand extends AbstractCommand {
            public BreedUntameSubCommand() {
                super("untame", "Release a tamed animal");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null || plugin.tamingManager == null) {
                    ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                if (!ctx.isPlayer()) {
                    ctx.sendMessage(Message.raw("This command can only be used by players").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                Player player = (Player) ctx.sender();
                String playerName = player != null ? player.getDisplayName() : null;
                if (playerName == null) {
                    ctx.sendMessage(Message.raw("Could not identify player").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.tamingManager.setPendingUntameByName(playerName);
                ctx.sendMessage(Message.raw("Right-click a tamed animal to release it.").color("#FFAA00"));
                ctx.sendMessage(Message.raw("(You can only untame animals you own.)").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: info ---
        public static class BreedInfoSubCommand extends AbstractCommand {
            public BreedInfoSubCommand() {
                super("info", "Show taming information");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null || plugin.tamingManager == null) {
                    ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                TamingManager taming = plugin.tamingManager;
                ctx.sendMessage(Message.raw("=== Taming Status ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Total tamed: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Awaiting respawn: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(taming.getDespawnedCount())).color("#FFFF55")));
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: settings ---
        public static class BreedSettingsSubCommand extends AbstractCommand {
            public BreedSettingsSubCommand() {
                super("settings", "Taming settings");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ctx.sendMessage(Message.raw("This command is not yet available.").color("#FFFF55"));
                ctx.sendMessage(Message.raw("By default, others CAN interact with your tamed animals.").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        // --- Subcommand: custom (delegates to CustomAnimalCommand subcommands) ---
        public static class BreedCustomSubCommand extends AbstractCommand {
            public BreedCustomSubCommand() {
                super("custom", "Manage custom animals from other mods");
                addSubCommand(new CustomAnimalCommand.CustomAnimalAddCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalRemoveCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalListCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalInfoCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalEnableCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalDisableCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalAddFoodCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalRemoveFoodCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalScanCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalSetRoleCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalSetBabyCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalSetGrowthCommand());
                addSubCommand(new CustomAnimalCommand.CustomAnimalSetCooldownCommand());
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ctx.sendMessage(Message.raw("=== Custom Animal Commands ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("/breed custom scan").color("#FFFFFF")
                        .insert(Message.raw(" - Find creature names in world").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom add <model> <food>").color("#FFFFFF")
                        .insert(Message.raw(" - Add custom animal").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom remove <model>").color("#FFFFFF")
                        .insert(Message.raw(" - Remove custom animal").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom list").color("#FFFFFF")
                        .insert(Message.raw(" - List added custom animals").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom info <model>").color("#FFFFFF")
                        .insert(Message.raw(" - Show details").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom setrole <model> <role>").color("#FFFFFF")
                        .insert(Message.raw(" - Set NPC role for spawning").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom setgrowth <model> <min>").color("#FFFFFF")
                        .insert(Message.raw(" - Set growth time").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breed custom setcooldown <model> <min>").color("#FFFFFF")
                        .insert(Message.raw(" - Set breeding cooldown").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("Run ").color("#AAAAAA")
                        .insert(Message.raw("/breed custom scan").color("#FFFF55"))
                        .insert(Message.raw(" first to find creature names!").color("#AAAAAA")));
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    // ===========================================
    // LEGACY COMMANDS (with deprecation warnings)
    // ===========================================

    // ===========================================
    // TAMING COMMANDS
    // ===========================================

    /**
     * Set a pending name tag for taming an animal.
     * Usage: /nametag <name>
     * Then right-click an animal while holding a Name Tag item to apply.
     */
    public static class NameTagCommand extends AbstractCommand {
        private final RequiredArg<String> nameArg;

        public NameTagCommand() {
            super("nametag", "[Deprecated] Name and tame an animal - Use /breed tame instead");
            nameArg = withRequiredArg("name", "Name for the animal", ArgTypes.STRING);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed tame <name> instead").color("#FFAA00"));

            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null || plugin.tamingManager == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            String name = ctx.get(nameArg);

            // Validate name length
            if (name.length() > 32) {
                ctx.sendMessage(Message.raw("Name too long (max 32 characters)").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Get player from context
            if (!ctx.isPlayer()) {
                ctx.sendMessage(Message.raw("This command can only be used by players").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            Player player = (Player) ctx.sender();
            if (player == null) {
                ctx.sendMessage(Message.raw("Could not identify player").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Schedule UUID lookup on world thread and store pending name tag
            World world = Universe.get().getDefaultWorld();
            if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] /nametag command: world=%s, player=%s", world, player.getDisplayName());

            if (world != null) {
                final String pendingName = name;
                final Player finalPlayer = player;
                if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] Scheduling world.execute for UUID lookup");
                world.execute(() -> {
                    if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] Inside world.execute callback");
                    try {
                        UUID playerUuid = plugin.getPlayerUuidFromEntity(finalPlayer);
                        if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] getPlayerUuidFromEntity returned: %s", playerUuid);
                        if (playerUuid != null) {
                            plugin.tamingManager.setPendingNameTag(playerUuid, pendingName);
                            if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] SUCCESS: Set pending name tag for UUID %s: %s", playerUuid, pendingName);
                        } else {
                            plugin.getLogger().atWarning().log("[Taming] FAILED: playerUuid is null");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().atWarning().log("[Taming] Error: %s", e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else {
                plugin.getLogger().atWarning().log("[Taming] World is null!");
            }

            // Also store by name as fallback
            String playerName = player.getDisplayName();
            if (LaitsBreedingPlugin.isVerboseLogging()) plugin.getLogger().atInfo().log("[Taming] Also storing by name: %s", playerName);
            if (playerName != null) {
                plugin.tamingManager.setPendingNameTagByName(playerName, name);
            }

            ctx.sendMessage(Message.raw("Name tag ready: ").color("#AAAAAA")
                    .insert(Message.raw(name).color("#55FF55")));
            ctx.sendMessage(Message.raw("Press F on an animal to tame it.").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Show taming info and list tamed animals.
     * Usage: /taminginfo
     */
    public static class TamingInfoCommand extends AbstractCommand {

        public TamingInfoCommand() {
            super("taminginfo", "[Deprecated] Show taming information - Use /breed info instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed info instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));

            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null || plugin.tamingManager == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            TamingManager taming = plugin.tamingManager;

            ctx.sendMessage(Message.raw("=== Taming Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Total tamed: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getTamedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Awaiting respawn: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(taming.getDespawnedCount())).color("#FFFF55")));

            // Note: Player-specific info requires world thread access
            // For now just show global stats
            ctx.sendMessage(Message.raw("Use /breedstatus for detailed info").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle whether other players can interact with your tamed animals.
     * Usage: /tamingsettings
     * NOTE: Currently not functional due to thread safety constraints.
     */
    public static class TamingSettingsCommand extends AbstractCommand {

        public TamingSettingsCommand() {
            super("tamingsettings", "[Deprecated] Taming settings - Use /breed settings instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed settings instead").color("#FFAA00"));

            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null || plugin.tamingManager == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // TODO: This command needs world thread access to work properly
            // For now, animals default to allowing interaction
            ctx.sendMessage(Message.raw("This command is not yet available.").color("#FFFF55"));
            ctx.sendMessage(Message.raw("By default, others CAN interact with your tamed animals.").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Release a tamed animal.
     * Usage: /untame
     * Then right-click a tamed animal to release it.
     */
    public static class UntameCommand extends AbstractCommand {

        public UntameCommand() {
            super("untame", "[Deprecated] Release a tamed animal - Use /breed untame instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed untame instead").color("#FFAA00"));

            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null || plugin.tamingManager == null) {
                ctx.sendMessage(Message.raw("Taming system not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Get player name from context (thread-safe approach)
            String playerName = getPlayerNameFromContext(ctx);
            if (playerName == null) {
                ctx.sendMessage(Message.raw("Could not identify player").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            plugin.tamingManager.setPendingUntameByName(playerName);

            ctx.sendMessage(Message.raw("Right-click a tamed animal to release it.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("(You can only untame animals you own.)").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }

        private String getPlayerNameFromContext(CommandContext ctx) {
            try {
                if (ctx.isPlayer()) {
                    Player player = (Player) ctx.sender();
                    return player != null ? player.getDisplayName() : null;
                }
            } catch (Exception e) {
                // Silent
            }
            return null;
        }
    }

    // ===========================================
    // BREEDING COMMANDS
    // ===========================================

    public static class BreedingHelpCommand extends AbstractCommand {

        public BreedingHelpCommand() {
            super("laitsbreeding", "[Deprecated] Show Animal Breeding help - Use /breed instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("=== Lait's Animal Breeding ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                    .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Feed animals their favorite food to put them in love mode.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Two animals in love will breed and produce a baby!").color("#AAAAAA"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Breeding Foods:").color("#FFFF55"));
            ctx.sendMessage(
                    Message.raw("  Cow - ").color("#AAAAAA").insert(Message.raw("Cauliflower").color("#FFFFFF")));
            ctx.sendMessage(
                    Message.raw("  Pig - ").color("#AAAAAA").insert(Message.raw("Brown Mushroom").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Chicken - ").color("#AAAAAA").insert(Message.raw("Corn").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Sheep - ").color("#AAAAAA").insert(Message.raw("Lettuce").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Goat - ").color("#AAAAAA").insert(Message.raw("Apple").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Horse - ").color("#AAAAAA").insert(Message.raw("Carrot").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Camel - ").color("#AAAAAA").insert(Message.raw("Wheat").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Ram - ").color("#AAAAAA").insert(Message.raw("Apple").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Turkey - ").color("#AAAAAA").insert(Message.raw("Corn").color("#FFFFFF")));
            ctx.sendMessage(
                    Message.raw("  Boar - ").color("#AAAAAA").insert(Message.raw("Red Mushroom").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Rabbit - ").color("#AAAAAA").insert(Message.raw("Carrot").color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Commands:").color("#FFFF55"));
            ctx.sendMessage(Message.raw("  /laitsbreeding ").color("#FFFFFF")
                    .insert(Message.raw("- This help").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  /breedstatus ").color("#FFFFFF")
                    .insert(Message.raw("- View breeding stats").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  /breedconfig ").color("#FFFFFF")
                    .insert(Message.raw("- Configure breeding").color("#AAAAAA")));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Show breeding system status.
     * Usage: /breedstatus
     */
    public static class BreedingStatusCommand extends AbstractCommand {

        public BreedingStatusCommand() {
            super("breedstatus", "[Deprecated] Show breeding system status - Use /breed status instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed status instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Error: Plugin not initialized").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            BreedingManager breeding = plugin.getBreedingManager();
            GrowthManager growth = plugin.getGrowthManager();
            ConfigManager config = plugin.getConfigManager();

            ctx.sendMessage(Message.raw("=== Breeding Status ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Tracked animals: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getTrackedCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Pregnant: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getPregnantCount())).color("#FFFFFF")));
            ctx.sendMessage(Message.raw("In love: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(breeding.getInLoveCount())).color("#FF55FF")));
            ctx.sendMessage(Message.raw(""));

            Map<GrowthStage, Integer> stageCounts = growth.getGrowthStageCounts();
            ctx.sendMessage(Message.raw("Growth stages:").color("#FFFF55"));
            ctx.sendMessage(Message.raw("  Babies: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.BABY))).color("#55FFFF")));
            ctx.sendMessage(Message.raw("  Juveniles: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.JUVENILE))).color("#55FFFF")));
            ctx.sendMessage(Message.raw("  Adults: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(stageCounts.get(GrowthStage.ADULT))).color("#55FF55")));

            // Spawn detector statistics
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Spawn Detection:").color("#FFFF55"));
            int detectedCount = NewAnimalSpawnDetector.getDetectedCount();
            long lastDetection = NewAnimalSpawnDetector.getLastDetectionTime();
            String lastAnimal = NewAnimalSpawnDetector.getLastDetectedAnimal();

            ctx.sendMessage(Message.raw("  Detected spawns: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(detectedCount)).color("#FFFFFF")));
            if (lastDetection > 0) {
                long secondsAgo = (System.currentTimeMillis() - lastDetection) / 1000;
                ctx.sendMessage(Message.raw("  Last detection: ").color("#AAAAAA")
                        .insert(Message.raw(lastAnimal + " (" + secondsAgo + "s ago)").color("#55FFFF")));
            } else {
                ctx.sendMessage(Message.raw("  Last detection: ").color("#AAAAAA")
                        .insert(Message.raw("none").color("#777777")));
            }

            if (breeding.getTrackedCount() == 0) {
                ctx.sendMessage(Message.raw(""));
                ctx.sendMessage(Message.raw("No animals tracked yet. Feed some animals!").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle verbose logging.
     * Usage: /breedlogs
     */
    public static class BreedingLogsCommand extends AbstractCommand {

        public BreedingLogsCommand() {
            super("breedlogs", "Toggle breeding plugin verbose logs");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean newState = !LaitsBreedingPlugin.isVerboseLogging();
            LaitsBreedingPlugin.setVerboseLogging(newState);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo()
                        .log("[Lait:AnimalBreeding] Verbose logging " + (newState ? "enabled" : "disabled"));
            }

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Verbose logging ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));
            if (newState) {
                ctx.sendMessage(Message.raw("Debug information will now appear in server logs.").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    public static class NoClipCommand extends AbstractCommand {
        // Track which players have noclip enabled
        private static final java.util.Set<String> noclipPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public NoClipCommand() {
            super("noclip", "Toggle noclip (invulnerable + fly camera)");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            Player player = (Player) ctx.sender();
            String playerName = player.getDisplayName();

            // Toggle state
            boolean enabling = !noclipPlayers.contains(playerName);

            if (enabling) {
                noclipPlayers.add(playerName);
            } else {
                noclipPlayers.remove(playerName);
            }

            // Send fly camera packet
            try {
            } catch (Exception e) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin != null) {
                    plugin.getLogger().atWarning().log("Fly camera packet error: " + e.getMessage());
                }
            }

            // Run ECS operations on world thread
            // World world = Universe.get().getDefaultWorld();
            // if (world != null) {
            //     world.execute(() -> {
            //         try {
            //             Store store = ctx.senderAsPlayerRef().getStore();
            //             if (enabling) {
            //                 // Add Invulnerable = player can't take damage
            //                 store.ensureComponent(player.getReference(), Invulnerable.getComponentType());
            //             } else {
            //                 // Remove Invulnerable = player takes damage normally
            //                 store.tryRemoveComponent(player.getReference(), Invulnerable.getComponentType());
            //             }
            //         } catch (Exception e) {
            //             LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            //             if (plugin != null) {
            //                 plugin.getLogger().atWarning().log("Noclip toggle error: " + e.getMessage());
            //             }
            //         }
            //     });
            // }

            // if (enabling) {
            //     ctx.sendMessage(Message.raw("Noclip ENABLED").color("#55FF55")
            //         .insert(Message.raw(" (invulnerable + fly camera)").color("#AAAAAA")));
            // } else {
            //     ctx.sendMessage(Message.raw("Noclip DISABLED").color("#FF5555"));
            // }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle baby growth on/off.
     * Usage: /breedgrowth
     */
    public static class BreedingGrowthCommand extends AbstractCommand {

        public BreedingGrowthCommand() {
            super("breedgrowth", "[Deprecated] Toggle baby animal growth - Use /breed growth instead");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed growth instead").color("#FFAA00"));

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null || plugin.configManager == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            boolean newState = !plugin.configManager.isGrowthEnabled();
            plugin.configManager.setGrowthEnabled(newState);

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Baby growth ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));

            if (!newState) {
                ctx.sendMessage(Message.raw("Babies will not grow into adults until re-enabled.").color("#AAAAAA"));
            }

            // Save config to persist the setting
            plugin.configManager.saveToFile();

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Manually trigger animal scan for debugging.
     * Usage: /breedscan
     */
    public static class BreedingScanCommand extends AbstractCommand {

        public BreedingScanCommand() {
            super("breedscan", "Manually trigger animal scan for breeding interactions");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            // Enable verbose logging for this scan
            boolean wasVerbose = LaitsBreedingPlugin.isVerboseLogging();
            LaitsBreedingPlugin.setVerboseLogging(true);

            ctx.sendMessage(Message.raw("Starting manual animal scan...").color("#FFFF55"));
            ctx.sendMessage(Message.raw("Check server logs for details (verbose logging enabled)").color("#AAAAAA"));

            try {
                plugin.autoSetupNearbyAnimals();
                ctx.sendMessage(Message.raw("Scan triggered successfully").color("#55FF55"));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Scan error: " + e.getMessage()).color("#FF5555"));
            }

            // Restore verbose logging state after a delay
            if (!wasVerbose) {
                plugin.getTickScheduler().schedule(() -> {
                    LaitsBreedingPlugin.setVerboseLogging(false);
                }, 5, TimeUnit.SECONDS);
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Toggle development debug mode - broadcasts debug messages to all players
     * in-game.
     * Usage: /breeddev
     */
    public static class BreedingDevCommand extends AbstractCommand {

        public BreedingDevCommand() {
            super("breeddev", "Toggle in-game chat logging (shows all debug messages in chat)");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean newState = !LaitsBreedingPlugin.isDevMode();
            LaitsBreedingPlugin.setDevMode(newState);

            // Also enable/disable verbose logging to match
            LaitsBreedingPlugin.setVerboseLogging(newState);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo()
                        .log("[Lait:AnimalBreeding] Chat logging " + (newState ? "enabled" : "disabled"));
            }

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Chat logging ").color("#AAAAAA")
                    .insert(Message.raw(statusText).color(statusColor)));
            if (newState) {
                ctx.sendMessage(Message.raw("All debug messages will now appear in chat.").color("#FFAA00"));
                ctx.sendMessage(Message.raw("Use /breeddev again to disable.").color("#AAAAAA"));
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    // ===========================================
    // HINT FORMAT TESTING
    // ===========================================

    // Different hint formats to test - will cycle through these
    // Found: game uses localization keys like "server.interactionHints.xxx"
    // Language file has: interactionHints.generic = Press [{key}] to interact
    // Note: Localization keys only work when loaded from JSON assets, not runtime
    // For runtime, we need to use resolved text or a special format
    private static final String[] HINT_FORMATS = {
            "Feed", // 0: Simple text (current default)
            "Press [F] to Feed", // 1: Literal with [F]
            "Press [Use] to Feed", // 2: With interaction type name
            "[F] Feed", // 3: Key prefix
            "§ePress §f[F]§e to Feed", // 4: With color codes
            "server.interactionHints.generic", // 5: Localization key (may not work)
            "Press [{key}] to Feed", // 6: Raw format placeholder
            "@server.interactionHints.generic", // 7: Try @ prefix
    };

    private static int currentHintFormatIndex = 0;

    public static String getCurrentHintFormat() {
        return HINT_FORMATS[currentHintFormatIndex];
    }

    public static int cycleHintFormat() {
        currentHintFormatIndex = (currentHintFormatIndex + 1) % HINT_FORMATS.length;
        return currentHintFormatIndex;
    }

    /**
     * Test hint format command.
     * Usage: /breedhint
     */
    public static class BreedingHintCommand extends AbstractCommand {

        public BreedingHintCommand() {
            super("breedhint", "Cycle through hint format options");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            int newIndex = cycleHintFormat();
            String newFormat = getCurrentHintFormat();

            ctx.sendMessage(Message.raw("Hint Test - Format #" + newIndex + ": ").color("#FFFF55")
                    .insert(Message.raw("\"" + newFormat + "\"").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Look at an animal to see the new hint format.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Run ").color("#AAAAAA")
                    .insert(Message.raw("/breedhint").color("#FFFFFF"))
                    .insert(Message.raw(" again to try the next format.").color("#AAAAAA")));

            // Force re-setup of interactions with new hint
            LaitsBreedingPlugin plugin = getInstance();
            if (plugin != null) {
                plugin.attachInteractionsToAnimals();
            }

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Debug command to show cache sizes for memory leak monitoring.
     * Usage: /breedcaches
     */
    public static class BreedingCachesCommand extends AbstractCommand {

        public BreedingCachesCommand() {
            super("breedcaches", "Show cache sizes for debugging memory leaks");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not available").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sendMessage(Message.raw("=== Cache Status ===").color("#FF9900"));

            // Spawn detector cache
            if (plugin.spawnDetector != null) {
                int spawnCacheSize = plugin.spawnDetector.getProcessedCacheSize();
                ctx.sendMessage(Message.raw("  processedEntities: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(spawnCacheSize)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  processedEntities: ").color("#AAAAAA")
                        .insert(Message.raw("N/A (detector not running)").color("#FF5555")));
            }

            // Original interactions cache
            int interactionsCacheSize = getOriginalInteractionsCacheSize();
            ctx.sendMessage(Message.raw("  originalStates: ").color("#AAAAAA")
                    .insert(Message.raw(String.valueOf(interactionsCacheSize)).color("#FFFFFF")));

            // Breeding data cache
            if (plugin.breedingManager != null) {
                int breedingCacheSize = plugin.breedingManager.getTrackedCount();
                ctx.sendMessage(Message.raw("  breedingDataMap: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(breedingCacheSize)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  breedingDataMap: ").color("#AAAAAA")
                        .insert(Message.raw("N/A").color("#FF5555")));
            }

            // Taming manager caches
            if (plugin.tamingManager != null) {
                int tamedCount = plugin.tamingManager.getTamedCount();
                int pendingCount = plugin.tamingManager.getPendingCount();
                ctx.sendMessage(Message.raw("  tamedAnimals: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(tamedCount)).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("  pendingEntries: ").color("#AAAAAA")
                        .insert(Message.raw(String.valueOf(pendingCount)).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("  tamedAnimals: ").color("#AAAAAA")
                        .insert(Message.raw("N/A").color("#FF5555")));
            }

            ctx.sendMessage(Message.raw("Caches are cleaned periodically (every 5-10 min).").color("#AAAAAA"));

            return CompletableFuture.completedFuture(null);
        }
    }

    // ===========================================
    // CONFIG COMMAND
    // ===========================================

    /**
     * Manage breeding configuration at runtime.
     * Usage:
     * /breedconfig - Show current config summary
     * /breedconfig list [category] - List animals (optionally by category)
     * /breedconfig info <animal> - Show detailed info for an animal
     * /breedconfig preset list - List available presets
     * /breedconfig preset apply <name> - Apply a preset
     * /breedconfig reload - Reload from file
     * /breedconfig save - Save current config
     * /breedconfig enable <animal|category|ALL> - Enable breeding
     * /breedconfig disable <animal|category|ALL> - Disable breeding
     * /breedconfig set <animal> food <item> - Set primary breeding food
     * /breedconfig set <animal> growth <min> - Set growth time
     * /breedconfig set <animal> cooldown <min> - Set breed cooldown
     * /breedconfig addfood <animal> <item> - Add breeding food
     * /breedconfig removefood <animal> <item> - Remove breeding food
     */
    /**
     * Main /breedconfig command with proper sub-commands.
     * Uses the Hytale command API with typed arguments and tab completion.
     */
    public static class BreedingConfigCommand extends AbstractCommand {

        public BreedingConfigCommand() {
            super("breedconfig", "[Deprecated] Manage breeding configuration - Use /breed config instead");

            // Register all sub-commands
            addSubCommand(new ReloadSubCommand());
            addSubCommand(new SaveSubCommand());
            addSubCommand(new ListSubCommand());
            addSubCommand(new InfoSubCommand());
            addSubCommand(new EnableSubCommand());
            addSubCommand(new DisableSubCommand());
            addSubCommand(new SetSubCommand());
            addSubCommand(new AddFoodSubCommand());
            addSubCommand(new RemoveFoodSubCommand());
            addSubCommand(new PresetSubCommand());
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed config instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));

            // Called when /breedconfig is run with no sub-command - show summary
            LaitsBreedingPlugin plugin = getInstance();
            if (plugin == null) {
                ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                return CompletableFuture.completedFuture(null);
            }

            ConfigManager config = plugin.getConfigManager();
            showConfigSummary(ctx, config);
            return CompletableFuture.completedFuture(null);
        }

        // ==================== Helper Methods ====================

        private static ConfigManager getConfig() {
            LaitsBreedingPlugin plugin = getInstance();
            return plugin != null ? plugin.getConfigManager() : null;
        }

        private static void showConfigSummary(CommandContext ctx, ConfigManager config) {
            // Test different color formats to find what works
            ctx.sendMessage(Message.raw("=== Breeding Config ===").color("#FF9900")); // Hex color
            ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                    .insert(Message.raw(config.getActivePreset()).color("#FFFFFF")));

            // Count by category
            java.util.Map<AnimalType.Category, int[]> counts = new java.util.EnumMap<>(AnimalType.Category.class);
            for (AnimalType.Category cat : AnimalType.Category.values()) {
                counts.put(cat, new int[] { 0, 0 }); // [enabled, total]
            }
            for (AnimalType type : AnimalType.values()) {
                int[] c = counts.get(type.getCategory());
                c[1]++;
                if (config.isAnimalEnabled(type))
                    c[0]++;
            }

            ctx.sendMessage(Message.raw("Categories:").color("#AAAAAA"));
            for (AnimalType.Category cat : AnimalType.Category.values()) {
                int[] c = counts.get(cat);
                String hexColor = c[0] > 0 ? "#55FF55" : "#AAAAAA"; // Green if enabled, gray if not
                ctx.sendMessage(Message.raw("  ")
                        .insert(Message.raw(cat.name()).color(hexColor))
                        .insert(Message.raw(": " + c[0] + "/" + c[1] + " enabled").color("#AAAAAA")));
            }

            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Type ").color("#AAAAAA")
                    .insert(Message.raw("/breedconfig").color("#FFFFFF"))
                    .insert(Message.raw(" and press TAB for commands").color("#AAAAAA")));
        }

        // ==================== Sub-Commands ====================

        /** /breedconfig reload */
        public static class ReloadSubCommand extends AbstractCommand {
            public ReloadSubCommand() {
                super("reload", "Reload configuration from file");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }
                config.loadFromFile(getInstance().getDataDirectory().resolve("config.json"));
                ctx.sendMessage(Message.raw("Config reloaded from file.").color("#55FF55"));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig save */
        public static class SaveSubCommand extends AbstractCommand {
            public SaveSubCommand() {
                super("save", "Save current configuration to file");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }
                config.saveToFile();
                ctx.sendMessage(Message.raw("Config saved to file.").color("#55FF55"));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig list [category] */
        public static class ListSubCommand extends AbstractCommand {
            private final OptionalArg<AnimalType.Category> categoryArg;

            public ListSubCommand() {
                super("list", "List all animals or filter by category");
                categoryArg = withOptionalArg("category", "Filter by category (LIVESTOCK, MAMMAL, etc.)",
                        ArgTypes.forEnum("category", AnimalType.Category.class));
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                AnimalType.Category filterCat = ctx.get(categoryArg);

                String title = "=== Animal Config" + (filterCat != null ? " (" + filterCat + ")" : "") + " ===";
                ctx.sendMessage(Message.raw(title).color("#FF9900"));

                AnimalType.Category currentCat = null;
                for (AnimalType type : AnimalType.values()) {
                    if (filterCat != null && type.getCategory() != filterCat)
                        continue;

                    // Print category header
                    if (currentCat != type.getCategory()) {
                        currentCat = type.getCategory();
                        ctx.sendMessage(Message.raw("--- " + currentCat.name() + " ---").color("#FFFF55"));
                    }

                    ConfigManager.AnimalConfig ac = config.getAnimalConfig(type);
                    boolean enabled = config.isAnimalEnabled(type);
                    int foodCount = ac != null ? ac.breedingFoods.size() : 1;
                    double growth = ac != null ? ac.growthTimeMinutes : 30.0;
                    boolean hasBaby = type.hasBabyVariant();

                    // Build message with proper colors
                    Message line = Message.raw(enabled ? "+ " : "- ").color(enabled ? "#55FF55" : "#FF5555")
                            .insert(Message.raw(String.format("%-15s ", type.name())).color("#FFFFFF"))
                            .insert(Message.raw("foods=").color("#AAAAAA"))
                            .insert(Message.raw(String.valueOf(foodCount)).color("#FFFF55"))
                            .insert(Message.raw(" growth=").color("#AAAAAA"))
                            .insert(Message.raw(String.format("%.0fm", growth)).color("#FFFF55"));
                    if (!hasBaby) {
                        line = line.insert(Message.raw(" (no baby)").color("#555555"));
                    }
                    ctx.sendMessage(line);
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig info <animal> */
        public static class InfoSubCommand extends AbstractCommand {
            private final RequiredArg<String> animalArg;

            public InfoSubCommand() {
                super("info", "Show detailed information for an animal");
                animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String animalId = ctx.get(animalArg);
                ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);

                if (lookup == null) {
                    ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                            .insert(Message.raw(animalId).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                if (lookup.isBuiltIn()) {
                    // Built-in animal info
                    AnimalType type = lookup.getBuiltInType();
                    ConfigManager.AnimalConfig ac = config.getAnimalConfig(type);

                    ctx.sendMessage(Message.raw("=== " + type.name() + " ===").color("#FF9900"));
                    ctx.sendMessage(Message.raw("Category: ").color("#AAAAAA")
                            .insert(Message.raw(type.getCategory().name()).color("#FFFFFF")));
                    boolean enabled = config.isAnimalEnabled(type);
                    ctx.sendMessage(Message.raw("Enabled: ").color("#AAAAAA")
                            .insert(Message.raw(enabled ? "Yes" : "No").color(enabled ? "#55FF55" : "#FF5555")));
                    boolean hasBaby = type.hasBabyVariant();
                    Message babyMsg = Message.raw("Has Baby: ").color("#AAAAAA")
                            .insert(Message.raw(hasBaby ? "Yes" : "No").color(hasBaby ? "#55FF55" : "#FF5555"));
                    if (hasBaby) {
                        babyMsg = babyMsg.insert(Message.raw(" (" + type.getBabyNpcRoleId() + ")").color("#AAAAAA"));
                    }
                    ctx.sendMessage(babyMsg);
                    ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                            .insert(Message.raw((ac != null ? ac.growthTimeMinutes : 30.0) + " min").color("#FFFF55")));
                    ctx.sendMessage(Message.raw("Cooldown: ").color("#AAAAAA")
                            .insert(Message.raw((ac != null ? ac.breedCooldownMinutes : 5.0) + " min").color("#FFFF55")));
                    ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));

                    java.util.List<String> foods = config.getBreedingFoods(type);
                    for (int i = 0; i < foods.size(); i++) {
                        String food = foods.get(i);
                        boolean isPrimary = i == 0;
                        ctx.sendMessage(Message.raw(isPrimary ? "* " : "  ").color(isPrimary ? "#55FF55" : "#AAAAAA")
                                .insert(Message.raw(food).color("#FFFFFF")));
                    }
                } else {
                    // Custom animal info
                    CustomAnimalConfig custom = lookup.getCustomConfig();

                    ctx.sendMessage(Message.raw("=== " + custom.getDisplayName() + " (Custom) ===").color("#FF9900"));
                    ctx.sendMessage(Message.raw("Model ID: ").color("#AAAAAA")
                            .insert(Message.raw(custom.getModelAssetId()).color("#FFFFFF")));
                    boolean enabled = custom.isEnabled();
                    ctx.sendMessage(Message.raw("Enabled: ").color("#AAAAAA")
                            .insert(Message.raw(enabled ? "Yes" : "No").color(enabled ? "#55FF55" : "#FF5555")));
                    ctx.sendMessage(Message.raw("NPC Role: ").color("#AAAAAA")
                            .insert(Message.raw(custom.getAdultNpcRoleId()).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                            .insert(Message.raw(custom.getGrowthTimeMinutes() + " min").color("#FFFF55")));
                    ctx.sendMessage(Message.raw("Cooldown: ").color("#AAAAAA")
                            .insert(Message.raw(custom.getBreedCooldownMinutes() + " min").color("#FFFF55")));
                    ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));

                    java.util.List<String> foods = custom.getBreedingFoods();
                    for (int i = 0; i < foods.size(); i++) {
                        String food = foods.get(i);
                        boolean isPrimary = i == 0;
                        ctx.sendMessage(Message.raw(isPrimary ? "* " : "  ").color(isPrimary ? "#55FF55" : "#AAAAAA")
                                .insert(Message.raw(food).color("#FFFFFF")));
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig enable <target> */
        public static class EnableSubCommand extends AbstractCommand {
            private final RequiredArg<String> targetArg;

            public EnableSubCommand() {
                super("enable", "Enable breeding for animal, category, or ALL");
                targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String target = ctx.get(targetArg).toUpperCase();
                handleToggle(ctx, config, target, true);
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig disable <target> */
        public static class DisableSubCommand extends AbstractCommand {
            private final RequiredArg<String> targetArg;

            public DisableSubCommand() {
                super("disable", "Disable breeding for animal, category, or ALL");
                targetArg = withRequiredArg("target", "Animal name, category (LIVESTOCK, MAMMAL, etc.), or ALL",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String target = ctx.get(targetArg).toUpperCase();
                handleToggle(ctx, config, target, false);
                return CompletableFuture.completedFuture(null);
            }
        }

        /** Shared toggle logic for enable/disable */
        private static void handleToggle(CommandContext ctx, ConfigManager config, String target, boolean enable) {
            String statusColor = enable ? "#55FF55" : "#FF5555";
            String statusWord = enable ? "Enabled" : "Disabled";

            // Check if it's ALL
            if (target.equalsIgnoreCase("ALL")) {
                for (AnimalType type : AnimalType.values()) {
                    config.setAnimalEnabled(type, enable);
                }
                // Also enable/disable all custom animals
                for (String customId : config.getCustomAnimals().keySet()) {
                    config.setCustomAnimalEnabled(customId, enable);
                }
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                        .insert(Message.raw(" breeding for ALL animals (including custom).").color("#AAAAAA")));
                return;
            }

            // Check if it's a category
            try {
                AnimalType.Category cat = AnimalType.Category.valueOf(target.toUpperCase());
                int count = 0;
                for (AnimalType type : AnimalType.values()) {
                    if (type.getCategory() == cat) {
                        config.setAnimalEnabled(type, enable);
                        count++;
                    }
                }
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                        .insert(Message.raw(" breeding for " + count + " " + cat.name() + " animals.")
                                .color("#AAAAAA")));
                return;
            } catch (IllegalArgumentException ignored) {
            }

            // Use unified lookup for individual animal (built-in or custom)
            ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(target);
            if (lookup != null) {
                config.setAnyAnimalEnabled(target, enable);
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                        .insert(Message.raw(" breeding for ").color("#AAAAAA"))
                        .insert(Message.raw(lookup.getDisplayName()).color("#FFFFFF")));
            } else {
                ctx.sendMessage(Message.raw("Unknown animal or category: ").color("#FF5555")
                        .insert(Message.raw(target).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Animals: COW, PIG, CHICKEN, or custom animal names").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("Categories: ").color("#AAAAAA")
                        .insert(Message.raw(java.util.Arrays.toString(AnimalType.Category.values())).color("#FFFFFF")));
            }
        }

        /** /breedconfig set <animal> <property> <value> */
        public static class SetSubCommand extends AbstractCommand {
            private final RequiredArg<String> animalArg;
            private final RequiredArg<String> propertyArg;
            private final RequiredArg<String> valueArg;

            public SetSubCommand() {
                super("set", "Set animal property (food, growth, cooldown)");
                animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                        ArgTypes.STRING);
                propertyArg = withRequiredArg("property", "Property: food, growth, cooldown",
                        ArgTypes.STRING);
                valueArg = withRequiredArg("value", "New value",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String animalId = ctx.get(animalArg);
                String property = ctx.get(propertyArg).toLowerCase();
                String value = ctx.get(valueArg);

                // Use unified lookup - works for both built-in and custom animals
                ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
                if (lookup == null) {
                    ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                            .insert(Message.raw(animalId).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                String displayName = lookup.getDisplayName();

                switch (property) {
                    case "food":
                        // Resolve food shortcut
                        String resolvedFood = resolveFoodShortcut(value);
                        config.setAnyAnimalFood(animalId, resolvedFood);
                        ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                .insert(Message.raw(displayName).color("#FFFFFF"))
                                .insert(Message.raw(" primary food to: ").color("#55FF55"))
                                .insert(Message.raw(resolvedFood).color("#FFFFFF")));
                        ctx.sendMessage(Message.raw("(This replaces all foods. Use ").color("#AAAAAA")
                                .insert(Message.raw("/breed config addfood").color("#FFFFFF"))
                                .insert(Message.raw(" to add more.)").color("#AAAAAA")));
                        break;

                    case "growth":
                        try {
                            double minutes = Double.parseDouble(value);
                            config.setAnyAnimalGrowthTime(animalId, minutes);
                            ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                    .insert(Message.raw(displayName).color("#FFFFFF"))
                                    .insert(Message.raw(" growth time to: ").color("#55FF55"))
                                    .insert(Message.raw(minutes + " min").color("#FFFF55")));
                        } catch (NumberFormatException e) {
                            ctx.sendMessage(Message.raw("Invalid number: ").color("#FF5555")
                                    .insert(Message.raw(value).color("#FFFFFF")));
                        }
                        break;

                    case "cooldown":
                        try {
                            double minutes = Double.parseDouble(value);
                            config.setAnyAnimalCooldown(animalId, minutes);
                            ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                    .insert(Message.raw(displayName).color("#FFFFFF"))
                                    .insert(Message.raw(" cooldown to: ").color("#55FF55"))
                                    .insert(Message.raw(minutes + " min").color("#FFFF55")));
                        } catch (NumberFormatException e) {
                            ctx.sendMessage(Message.raw("Invalid number: ").color("#FF5555")
                                    .insert(Message.raw(value).color("#FFFFFF")));
                        }
                        break;

                    default:
                        ctx.sendMessage(Message.raw("Unknown property: ").color("#FF5555")
                                .insert(Message.raw(property).color("#FFFFFF")));
                        ctx.sendMessage(Message.raw("Valid: food, growth, cooldown").color("#AAAAAA"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig addfood <animal> <item> */
        public static class AddFoodSubCommand extends AbstractCommand {
            private final RequiredArg<String> animalArg;
            private final RequiredArg<String> foodArg;

            public AddFoodSubCommand() {
                super("addfood", "Add a breeding food to an animal");
                animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                        ArgTypes.STRING);
                foodArg = withRequiredArg("food", "Item ID or shortcut (e.g., Carrot, Wheat, Apple)",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String animalId = ctx.get(animalArg);
                String foodInput = ctx.get(foodArg);

                // Use unified lookup
                ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
                if (lookup == null) {
                    ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                            .insert(Message.raw(animalId).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }
                if (foodInput == null || foodInput.isEmpty()) {
                    ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Resolve food shortcut
                String food = resolveFoodShortcut(foodInput);

                config.addAnyAnimalFood(animalId, food);
                ctx.sendMessage(Message.raw("Added ").color("#55FF55")
                        .insert(Message.raw(food).color("#FFFFFF"))
                        .insert(Message.raw(" to " + lookup.getDisplayName() + " breeding foods.").color("#55FF55")));
                ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                        .insert(Message.raw(String.join(", ", config.getAnyAnimalFoods(animalId))).color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig removefood <animal> <item> */
        public static class RemoveFoodSubCommand extends AbstractCommand {
            private final RequiredArg<String> animalArg;
            private final RequiredArg<String> foodArg;

            public RemoveFoodSubCommand() {
                super("removefood", "Remove a breeding food from an animal");
                animalArg = withRequiredArg("animal", "Animal name (built-in or custom)",
                        ArgTypes.STRING);
                foodArg = withRequiredArg("food", "Item ID or shortcut to remove",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String animalId = ctx.get(animalArg);
                String foodInput = ctx.get(foodArg);

                // Use unified lookup
                ConfigManager.AnimalLookupResult lookup = config.lookupAnimal(animalId);
                if (lookup == null) {
                    ctx.sendMessage(Message.raw("Unknown animal: ").color("#FF5555")
                            .insert(Message.raw(animalId).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use /breed custom add to register custom animals").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }
                if (foodInput == null || foodInput.isEmpty()) {
                    ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Resolve food shortcut
                String food = resolveFoodShortcut(foodInput);

                java.util.List<String> foods = config.getAnyAnimalFoods(animalId);
                if (foods.size() <= 1) {
                    ctx.sendMessage(Message.raw("Cannot remove last food. Use ").color("#FF5555")
                            .insert(Message.raw("/breed config set food").color("#FFFFFF"))
                            .insert(Message.raw(" to replace instead.").color("#FF5555")));
                    return CompletableFuture.completedFuture(null);
                }

                config.removeAnyAnimalFood(animalId, food);
                ctx.sendMessage(Message.raw("Removed ").color("#55FF55")
                        .insert(Message.raw(food).color("#FFFFFF"))
                        .insert(Message.raw(" from " + lookup.getDisplayName() + " breeding foods.").color("#55FF55")));
                ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                        .insert(Message.raw(String.join(", ", config.getAnyAnimalFoods(animalId))).color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig preset - with list, apply, save, and restore sub-commands */
        public static class PresetSubCommand extends AbstractCommand {
            public PresetSubCommand() {
                super("preset", "Manage configuration presets");
                addSubCommand(new PresetListSubCommand());
                addSubCommand(new PresetApplySubCommand());
                addSubCommand(new PresetSaveSubCommand());
                addSubCommand(new PresetRestoreSubCommand());
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                // Show preset help when /breedconfig preset is called alone
                ctx.sendMessage(Message.raw("=== Preset Commands ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("/breedconfig preset list ").color("#FFFFFF")
                        .insert(Message.raw("- Show available presets").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breedconfig preset apply <name> ").color("#FFFFFF")
                        .insert(Message.raw("- Apply a preset").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breedconfig preset save <name> ").color("#FFFFFF")
                        .insert(Message.raw("- Save current config as preset").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("/breedconfig preset restore <name> ").color("#FFFFFF")
                        .insert(Message.raw("- Reset built-in preset to defaults").color("#AAAAAA")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig preset list */
        public static class PresetListSubCommand extends AbstractCommand {
            public PresetListSubCommand() {
                super("list", "Show available presets");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                ctx.sendMessage(Message.raw("=== Available Presets ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Current: ").color("#AAAAAA")
                        .insert(Message.raw(config.getActivePreset()).color("#FFFFFF")));
                for (String preset : config.getAvailablePresets()) {
                    boolean isCurrent = preset.equals(config.getActivePreset());
                    boolean isBuiltin = config.isBuiltinPreset(preset);
                    String desc;
                    switch (preset) {
                        case "default":
                            desc = "Original values, livestock only";
                            break;
                        case "default_extended":
                            desc = "Default timings + multiple foods";
                            break;
                        case "lait_curated":
                            desc = "Balanced timings, multiple foods";
                            break;
                        case "zoo":
                            desc = "Real animals (no mythic/vermin/boss)";
                            break;
                        case "all":
                            desc = "All 119 animals enabled";
                            break;
                        default:
                            desc = "(custom)";
                            break;
                    }
                    String marker = isCurrent ? "* " : (isBuiltin ? "  " : "  ");
                    Message line = Message.raw(marker).color(isCurrent ? "#55FF55" : "#AAAAAA")
                            .insert(Message.raw(preset).color("#FFFFFF"))
                            .insert(Message.raw(" - " + desc).color("#555555"));
                    ctx.sendMessage(line);
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig preset apply <name> */
        public static class PresetApplySubCommand extends AbstractCommand {
            private final RequiredArg<String> presetArg;

            public PresetApplySubCommand() {
                super("apply", "Apply a configuration preset");
                presetArg = withRequiredArg("preset", "Preset name (default, lait_curated)",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String presetName = ctx.get(presetArg).toLowerCase();
                if (config.applyPreset(presetName)) {
                    ctx.sendMessage(Message.raw("Applied preset: ").color("#55FF55")
                            .insert(Message.raw(presetName).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                            .insert(Message.raw("/breedconfig save").color("#FFFFFF"))
                            .insert(Message.raw(" to persist changes.").color("#AAAAAA")));
                } else {
                    ctx.sendMessage(Message.raw("Unknown preset: ").color("#FF5555")
                            .insert(Message.raw(presetName).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Available: ").color("#AAAAAA")
                            .insert(Message.raw(String.join(", ", config.getAvailablePresets())).color("#FFFFFF")));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig preset save <name> */
        public static class PresetSaveSubCommand extends AbstractCommand {
            private final RequiredArg<String> presetArg;

            public PresetSaveSubCommand() {
                super("save", "Save current configuration as a preset");
                presetArg = withRequiredArg("name", "Name for the new preset",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String presetName = ctx.get(presetArg).toLowerCase().replaceAll("[^a-z0-9_-]", "_");
                if (config.saveAsPreset(presetName)) {
                    ctx.sendMessage(Message.raw("Saved preset: ").color("#55FF55")
                            .insert(Message.raw(presetName).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("File: ").color("#AAAAAA")
                            .insert(Message.raw("mods/presets/" + presetName + ".json").color("#FFFFFF")));
                } else {
                    ctx.sendMessage(Message.raw("Failed to save preset!").color("#FF5555"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /**
         * /breedconfig preset restore <name> - Reset a built-in preset to default
         * values
         */
        public static class PresetRestoreSubCommand extends AbstractCommand {
            private final RequiredArg<String> presetArg;

            public PresetRestoreSubCommand() {
                super("restore", "Reset a built-in preset to its default values");
                presetArg = withRequiredArg("preset", "Preset name (default, lait_curated, zoo, all)",
                        ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String presetName = ctx.get(presetArg).toLowerCase();

                if (!config.isBuiltinPreset(presetName)) {
                    ctx.sendMessage(Message.raw("Can only restore built-in presets: ").color("#FF5555")
                            .insert(Message.raw("default, default_extended, lait_curated, zoo, all").color("#FFFFFF")));
                    return CompletableFuture.completedFuture(null);
                }

                if (config.restorePreset(presetName)) {
                    ctx.sendMessage(Message.raw("Restored preset to defaults: ").color("#55FF55")
                            .insert(Message.raw(presetName).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("All customizations have been reset.").color("#AAAAAA"));
                } else {
                    ctx.sendMessage(Message.raw("Failed to restore preset!").color("#FF5555"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        // ==================== Food Shortcut Resolver ====================

        /**
         * Food shortcut mappings for easier command usage.
         * Maps short names like "Carrot" to full item IDs like
         * "Plant_Crop_Carrot_Item".
         */
        private static final java.util.Map<String, String> FOOD_SHORTCUTS = new java.util.HashMap<>();
        static {
            // Crops
            FOOD_SHORTCUTS.put("carrot", "Plant_Crop_Carrot_Item");
            FOOD_SHORTCUTS.put("wheat", "Plant_Crop_Wheat_Item");
            FOOD_SHORTCUTS.put("corn", "Plant_Crop_Corn_Item");
            FOOD_SHORTCUTS.put("potato", "Plant_Crop_Potato_Item");
            FOOD_SHORTCUTS.put("lettuce", "Plant_Crop_Lettuce_Item");
            FOOD_SHORTCUTS.put("cauliflower", "Plant_Crop_Cauliflower_Item");
            FOOD_SHORTCUTS.put("rice", "Plant_Crop_Rice_Item");

            // Mushrooms
            FOOD_SHORTCUTS.put("mushroom_brown", "Plant_Crop_Mushroom_Cap_Brown");
            FOOD_SHORTCUTS.put("mushroom_red", "Plant_Crop_Mushroom_Cap_Red");
            FOOD_SHORTCUTS.put("brown_mushroom", "Plant_Crop_Mushroom_Cap_Brown");
            FOOD_SHORTCUTS.put("red_mushroom", "Plant_Crop_Mushroom_Cap_Red");

            // Fruits
            FOOD_SHORTCUTS.put("apple", "Plant_Fruit_Apple");
            FOOD_SHORTCUTS.put("berries", "Plant_Fruit_Berries_Red");
            FOOD_SHORTCUTS.put("red_berries", "Plant_Fruit_Berries_Red");
            FOOD_SHORTCUTS.put("cactus_flower", "Plant_Cactus_Flower");

            // Meat (raw and cooked)
            FOOD_SHORTCUTS.put("wildmeat", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("wildmeat_raw", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("wildmeat_cooked", "Food_Wildmeat_Cooked");
            FOOD_SHORTCUTS.put("cooked_wildmeat", "Food_Wildmeat_Cooked");
            FOOD_SHORTCUTS.put("meat", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("meat_raw", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("raw_meat", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("meat_cooked", "Food_Wildmeat_Cooked");
            FOOD_SHORTCUTS.put("cooked_meat", "Food_Wildmeat_Cooked");
            FOOD_SHORTCUTS.put("beef", "Food_Beef_Raw");
            FOOD_SHORTCUTS.put("beef_raw", "Food_Beef_Raw");
            FOOD_SHORTCUTS.put("pork", "Food_Pork_Raw");
            FOOD_SHORTCUTS.put("pork_raw", "Food_Pork_Raw");
            FOOD_SHORTCUTS.put("chicken_meat", "Food_Chicken_Raw");
            FOOD_SHORTCUTS.put("chicken_raw", "Food_Chicken_Raw");

            // Fish
            FOOD_SHORTCUTS.put("fish", "Food_Fish_Raw");
            FOOD_SHORTCUTS.put("fish_raw", "Food_Fish_Raw");
            FOOD_SHORTCUTS.put("raw_fish", "Food_Fish_Raw");
            FOOD_SHORTCUTS.put("fish_grilled", "Food_Fish_Grilled");
            FOOD_SHORTCUTS.put("grilled_fish", "Food_Fish_Grilled");
            FOOD_SHORTCUTS.put("fish_cooked", "Food_Fish_Grilled");
            FOOD_SHORTCUTS.put("cooked_fish", "Food_Fish_Grilled");
        }

        /**
         * Resolve a food shortcut to its full item ID.
         * If the input doesn't match any shortcut, returns the input unchanged.
         */
        static String resolveFoodShortcut(String input) {
            if (input == null)
                return null;
            String resolved = FOOD_SHORTCUTS.get(input.toLowerCase());
            return resolved != null ? resolved : input;
        }
    }

    // ==================== Custom Animal Command ====================

    /**
     * /customanimal - Manage custom animals from other mods.
     * Usage:
     *   /customanimal add <modelAssetId> <food1> [food2] [food3] - Add a custom animal
     *   /customanimal remove <modelAssetId> - Remove a custom animal
     *   /customanimal list - List all custom animals
     *   /customanimal info <modelAssetId> - Show info about a custom animal
     *   /customanimal enable <modelAssetId> - Enable a custom animal
     *   /customanimal disable <modelAssetId> - Disable a custom animal
     *   /customanimal addfood <modelAssetId> <food> - Add a breeding food
     *   /customanimal removefood <modelAssetId> <food> - Remove a breeding food
     */
    public static class CustomAnimalCommand extends AbstractCommand {
        public CustomAnimalCommand() {
            super("customanimal", "[Deprecated] Manage custom animals - Use /breed custom instead");
            addSubCommand(new CustomAnimalAddCommand());
            addSubCommand(new CustomAnimalRemoveCommand());
            addSubCommand(new CustomAnimalListCommand());
            addSubCommand(new CustomAnimalInfoCommand());
            addSubCommand(new CustomAnimalEnableCommand());
            addSubCommand(new CustomAnimalDisableCommand());
            addSubCommand(new CustomAnimalAddFoodCommand());
            addSubCommand(new CustomAnimalRemoveFoodCommand());
            addSubCommand(new CustomAnimalScanCommand());
            addSubCommand(new CustomAnimalSetRoleCommand());
            addSubCommand(new CustomAnimalSetBabyCommand());
            addSubCommand(new CustomAnimalSetGrowthCommand());
            addSubCommand(new CustomAnimalSetCooldownCommand());
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("[Deprecated] Use /breed custom instead").color("#FFAA00"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("=== Custom Animal Commands ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("/customanimal add <model> <food> ").color("#AAAAAA")
                    .insert(Message.raw("- Add custom animal").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("/customanimal remove <model> ").color("#AAAAAA")
                    .insert(Message.raw("- Remove custom animal").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("/customanimal list ").color("#AAAAAA")
                    .insert(Message.raw("- List all custom animals").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("/customanimal info <model> ").color("#AAAAAA")
                    .insert(Message.raw("- Show custom animal info").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("/customanimal enable/disable <model> ").color("#AAAAAA")
                    .insert(Message.raw("- Toggle enabled").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("/customanimal addfood/removefood <model> <food> ").color("#AAAAAA")
                    .insert(Message.raw("- Modify foods").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("Use /breedconfig save after changes to persist!").color("#FFAA00"));
            return CompletableFuture.completedFuture(null);
        }

        /** /customanimal add <npcRole> <food1> [food2] [food3] */
        public static class CustomAnimalAddCommand extends AbstractCommand {
            private final RequiredArg<String> roleArg;
            private final RequiredArg<String> food1Arg;
            private final OptionalArg<String> food2Arg;
            private final OptionalArg<String> food3Arg;

            public CustomAnimalAddCommand() {
                super("add", "Add a custom animal by NPC role");
                roleArg = withRequiredArg("npcRole", "NPC role name (validates and auto-discovers model)", ArgTypes.STRING);
                food1Arg = withRequiredArg("food1", "Primary breeding food item ID", ArgTypes.STRING);
                food2Arg = withOptionalArg("food2", "Optional second food", ArgTypes.STRING);
                food3Arg = withOptionalArg("food3", "Optional third food", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String roleName = ctx.get(roleArg);
                String food1 = BreedingConfigCommand.resolveFoodShortcut(ctx.get(food1Arg));

                java.util.List<String> foods = new java.util.ArrayList<>();
                foods.add(food1);

                String food2 = ctx.get(food2Arg);
                if (food2 != null && !food2.isEmpty()) {
                    foods.add(BreedingConfigCommand.resolveFoodShortcut(food2));
                }
                String food3 = ctx.get(food3Arg);
                if (food3 != null && !food3.isEmpty()) {
                    foods.add(BreedingConfigCommand.resolveFoodShortcut(food3));
                }

                // 1. Validate the NPC role exists
                NPCPlugin npcPlugin = NPCPlugin.get();
                int roleIndex = npcPlugin.getIndex(roleName);
                if (roleIndex < 0) {
                    ctx.sendMessage(Message.raw("NPC role not found: " + roleName).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Make sure this is a valid NPC role name.").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("Use /breed custom scan to find creatures nearby.").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                // 2. Discover model by spawning temp entity
                ctx.sendMessage(Message.raw("Discovering model for role: " + roleName + "...").color("#AAAAAA"));

                String modelAssetId = discoverModelFromRole(plugin, roleName, roleIndex);
                if (modelAssetId == null) {
                    ctx.sendMessage(Message.raw("Could not determine model for role: " + roleName).color("#FF5555"));
                    ctx.sendMessage(Message.raw("The role exists but model discovery failed.").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                // 3. Check if model already registered
                if (plugin.getConfigManager().isCustomAnimal(modelAssetId)) {
                    ctx.sendMessage(Message.raw("Model '" + modelAssetId + "' already registered!").color("#FFAA00"));
                    ctx.sendMessage(Message.raw("Use /breed custom remove " + modelAssetId + " first.").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                // 4. Store both model and role
                plugin.getConfigManager().addCustomAnimal(modelAssetId, foods);
                plugin.getConfigManager().setCustomAnimalNpcRole(modelAssetId, roleName);

                ctx.sendMessage(Message.raw("Added custom animal!").color("#55FF55"));
                ctx.sendMessage(Message.raw("  NPC Role: ").color("#AAAAAA")
                        .insert(Message.raw(roleName).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("  Model: ").color("#AAAAAA")
                        .insert(Message.raw(modelAssetId).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("  Foods: ").color("#AAAAAA")
                        .insert(Message.raw(String.join(", ", foods)).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Scanning world for creatures...").color("#AAAAAA"));

                // Trigger rescan to set up interactions
                plugin.autoSetupNearbyAnimals();

                ctx.sendMessage(Message.raw("Interactions set up! Feed the creature to breed.").color("#55FF55"));
                ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                        .insert(Message.raw("/breedconfig save").color("#FFFFFF"))
                        .insert(Message.raw(" to persist changes.").color("#AAAAAA")));
                ctx.sendMessage(Message.raw("To set a baby role: ").color("#AAAAAA")
                        .insert(Message.raw("/breed custom setbaby " + modelAssetId + " <babyRole>").color("#FFFF55")));

                return CompletableFuture.completedFuture(null);
            }

            /**
             * Discover the model asset ID by spawning a temp entity and reading its ModelComponent.
             */
            private String discoverModelFromRole(LaitsBreedingPlugin plugin, String roleName, int roleIndex) {
                try {
                    World world = Universe.get().getDefaultWorld();

                    // If getDefaultWorld fails, try to get the world from plugin's stored entities
                    if (world == null) {
                        if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] getDefaultWorld returned null, trying alternative methods...");

                        // Try getting world via reflection on Universe
                        try {
                            java.lang.reflect.Method getWorlds = Universe.class.getMethod("getWorlds");
                            @SuppressWarnings("unchecked")
                            java.util.Collection<World> worlds = (java.util.Collection<World>) getWorlds.invoke(Universe.get());
                            if (worlds != null && !worlds.isEmpty()) {
                                world = worlds.iterator().next();
                                if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Got world from getWorlds()");
                            }
                        } catch (Exception e) {
                            if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] getWorlds() not available: %s", e.getMessage());
                        }
                    }

                    if (world == null) {
                        plugin.getLogger().atWarning().log("No world available for model discovery");
                        return null;
                    }

                    // Make effectively final for lambda
                    final World finalWorld = world;

                    // Use a CompletableFuture to get result from world thread
                    java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();

                    if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Starting discovery for role: %s (index: %d)", roleName, roleIndex);

                    finalWorld.execute(() -> {
                        try {
                            Store<EntityStore> store = finalWorld.getEntityStore().getStore();
                            NPCPlugin npcPlugin = NPCPlugin.get();

                            // Spawn at high Y location (above world) - negative Y may not work
                            Vector3d tempPos = new Vector3d(0, 500, 0);
                            Vector3f rotation = new Vector3f(0, 0, 0);

                            if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Spawning temp entity at %s", tempPos);

                            // Use reflection for spawnEntity
                            boolean foundMethod = false;
                            for (java.lang.reflect.Method m : NPCPlugin.class.getMethods()) {
                                if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                                    foundMethod = true;
                                    Class<?> triConsumerClass = m.getParameterTypes()[5];
                                    Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                        triConsumerClass.getClassLoader(),
                                        new Class<?>[] { triConsumerClass },
                                        (proxy, method, args) -> null
                                    );

                                    Object result = m.invoke(npcPlugin, store, roleIndex, tempPos, rotation, null, noOpCallback);

                                    if (result != null) {
                                        if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Spawn succeeded, extracting model...");

                                        // Result is Pair<Ref<EntityStore>, NPCEntity> - fastutil uses left()/right()
                                        // Try multiple method names for compatibility
                                        Object entityRef = null;
                                        Object npcEntity = null;

                                        // Try left()/right() first (fastutil ObjectObjectImmutablePair)
                                        try {
                                            java.lang.reflect.Method leftMethod = result.getClass().getMethod("left");
                                            entityRef = leftMethod.invoke(result);
                                            java.lang.reflect.Method rightMethod = result.getClass().getMethod("right");
                                            npcEntity = rightMethod.invoke(result);
                                        } catch (NoSuchMethodException e1) {
                                            // Try first()/second()
                                            try {
                                                java.lang.reflect.Method firstMethod = result.getClass().getMethod("first");
                                                entityRef = firstMethod.invoke(result);
                                                java.lang.reflect.Method secondMethod = result.getClass().getMethod("second");
                                                npcEntity = secondMethod.invoke(result);
                                            } catch (NoSuchMethodException e2) {
                                                // Try getFirst()/getSecond()
                                                java.lang.reflect.Method getFirst = result.getClass().getMethod("getFirst");
                                                entityRef = getFirst.invoke(result);
                                                java.lang.reflect.Method getSecond = result.getClass().getMethod("getSecond");
                                                npcEntity = getSecond.invoke(result);
                                            }
                                        }

                                        if (entityRef != null) {
                                            @SuppressWarnings("unchecked")
                                            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                                            String modelId = extractModelFromRef(plugin, store, ref);

                                            if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Extracted model: %s", modelId);

                                            // Despawn the temp entity - try multiple method names
                                            if (npcEntity != null) {
                                                boolean despawned = false;
                                                String[] despawnMethods = {"despawn", "remove", "kill", "delete", "destroy"};
                                                for (String methodName : despawnMethods) {
                                                    try {
                                                        java.lang.reflect.Method despawnMethod = npcEntity.getClass().getMethod(methodName);
                                                        despawnMethod.invoke(npcEntity);
                                                        despawned = true;
                                                        break;
                                                    } catch (NoSuchMethodException ignored) {}
                                                }
                                                if (!despawned) {
                                                    // Entity at y=500 will likely despawn naturally
                                                    if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Note: temp entity at y=500 will timeout");
                                                }
                                            }

                                            future.complete(modelId);
                                            return;
                                        } else {
                                            plugin.getLogger().atWarning().log("[ModelDiscovery] entityRef is null");
                                        }
                                    } else {
                                        plugin.getLogger().atWarning().log("[ModelDiscovery] spawnEntity returned null");
                                    }
                                    break;
                                }
                            }
                            if (!foundMethod) {
                                plugin.getLogger().atWarning().log("[ModelDiscovery] Could not find spawnEntity method");
                            }
                            future.complete(null);
                        } catch (Exception e) {
                            plugin.getLogger().atWarning().log("[ModelDiscovery] Error: %s", e.getMessage());
                            e.printStackTrace();
                            future.complete(null);
                        }
                    });

                    // Wait for result with timeout
                    return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("Model discovery failed for %s: %s", roleName, e.getMessage());
                    return null;
                }
            }

            /**
             * Extract model asset ID from entity reference using ModelComponent.
             */
            private String extractModelFromRef(LaitsBreedingPlugin plugin, Store<EntityStore> store, Ref<EntityStore> ref) {
                try {
                    ComponentType<EntityStore, ModelComponent> modelType = ModelComponent.getComponentType();
                    ModelComponent modelComp = store.getComponent(ref, modelType);
                    if (modelComp == null) {
                        plugin.getLogger().atWarning().log("[ModelDiscovery] ModelComponent is null");
                        return null;
                    }

                    java.lang.reflect.Field modelField = ModelComponent.class.getDeclaredField("model");
                    modelField.setAccessible(true);
                    Object model = modelField.get(modelComp);
                    if (model == null) {
                        plugin.getLogger().atWarning().log("[ModelDiscovery] model field is null");
                        return null;
                    }

                    String modelStr = model.toString();
                    if (verboseLogging) plugin.getLogger().atInfo().log("[ModelDiscovery] Model toString: %s", modelStr);

                    // Try modelAssetId='...' format
                    int start = modelStr.indexOf("modelAssetId='");
                    if (start >= 0) {
                        start += 14;
                        int end = modelStr.indexOf("'", start);
                        if (end > start) {
                            return modelStr.substring(start, end);
                        }
                    }

                    // Try modelAssetId=... format (without quotes)
                    start = modelStr.indexOf("modelAssetId=");
                    if (start >= 0) {
                        start += 13;
                        int end = modelStr.indexOf(",", start);
                        if (end < 0) end = modelStr.indexOf(")", start);
                        if (end < 0) end = modelStr.indexOf("}", start);
                        if (end > start) {
                            return modelStr.substring(start, end).trim();
                        }
                    }

                    plugin.getLogger().atWarning().log("[ModelDiscovery] Could not parse modelAssetId from: %s", modelStr);
                    return null;
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[ModelDiscovery] extractModelFromRef error: %s", e.getMessage());
                    return null;
                }
            }
        }

        /** /customanimal remove <modelAssetId> */
        public static class CustomAnimalRemoveCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;

            public CustomAnimalRemoveCommand() {
                super("remove", "Remove a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID to remove", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                if (plugin.getConfigManager().removeCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Removed custom animal: ").color("#55FF55")
                            .insert(Message.raw(modelId).color("#FFFFFF")));
                    ctx.sendMessage(Message.raw("Use /breedconfig save to persist changes!").color("#FFAA00"));
                } else {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal list */
        public static class CustomAnimalListCommand extends AbstractCommand {
            public CustomAnimalListCommand() {
                super("list", "List all custom animals");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                java.util.Map<String, CustomAnimalConfig> customs = plugin.getConfigManager().getCustomAnimals();
                if (customs.isEmpty()) {
                    ctx.sendMessage(Message.raw("No custom animals defined.").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("Use /customanimal add <model> <food> to add one!").color("#FFAA00"));
                    return CompletableFuture.completedFuture(null);
                }

                ctx.sendMessage(Message.raw("=== Custom Animals (" + customs.size() + ") ===").color("#FF9900"));
                for (CustomAnimalConfig custom : customs.values()) {
                    String status = custom.isEnabled() ? "[ON]" : "[OFF]";
                    String statusColor = custom.isEnabled() ? "#55FF55" : "#FF5555";
                    ctx.sendMessage(Message.raw(status).color(statusColor)
                            .insert(Message.raw(" " + custom.getModelAssetId()).color("#FFFFFF"))
                            .insert(Message.raw(" - " + custom.getBreedingFoods().size() + " foods").color("#AAAAAA")));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal info <modelAssetId> */
        public static class CustomAnimalInfoCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;

            public CustomAnimalInfoCommand() {
                super("info", "Show info about a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                CustomAnimalConfig custom = plugin.getConfigManager().getCustomAnimal(modelId);
                if (custom == null) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                ctx.sendMessage(Message.raw("=== " + custom.getDisplayName() + " ===").color("#FF9900"));
                ctx.sendMessage(Message.raw("Model ID: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getModelAssetId()).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Enabled: ").color("#AAAAAA")
                        .insert(Message.raw(custom.isEnabled() ? "Yes" : "No").color(custom.isEnabled() ? "#55FF55" : "#FF5555")));
                ctx.sendMessage(Message.raw("Mountable: ").color("#AAAAAA")
                        .insert(Message.raw(custom.isMountable() ? "Yes" : "No").color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Growth Time: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getGrowthTimeMinutes() + " min").color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Breed Cooldown: ").color("#AAAAAA")
                        .insert(Message.raw(custom.getBreedCooldownMinutes() + " min").color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Breeding Foods:").color("#AAAAAA"));
                for (String food : custom.getBreedingFoods()) {
                    ctx.sendMessage(Message.raw("  - " + food).color("#FFFFFF"));
                }
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal enable <modelAssetId> */
        public static class CustomAnimalEnableCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;

            public CustomAnimalEnableCommand() {
                super("enable", "Enable a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalEnabled(modelId, true);
                ctx.sendMessage(Message.raw("Enabled custom animal: ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF")));
                plugin.autoSetupNearbyAnimals();
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal disable <modelAssetId> */
        public static class CustomAnimalDisableCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;

            public CustomAnimalDisableCommand() {
                super("disable", "Disable a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalEnabled(modelId, false);
                ctx.sendMessage(Message.raw("Disabled custom animal: ").color("#FF5555")
                        .insert(Message.raw(modelId).color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal addfood <modelAssetId> <food> */
        public static class CustomAnimalAddFoodCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<String> foodArg;

            public CustomAnimalAddFoodCommand() {
                super("addfood", "Add a breeding food to a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
                foodArg = withRequiredArg("food", "Food item ID to add", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
                plugin.getConfigManager().addCustomAnimalFood(modelId, food);
                ctx.sendMessage(Message.raw("Added food ").color("#55FF55")
                        .insert(Message.raw(food).color("#FFFFFF"))
                        .insert(Message.raw(" to " + modelId).color("#AAAAAA")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal removefood <modelAssetId> <food> */
        public static class CustomAnimalRemoveFoodCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<String> foodArg;

            public CustomAnimalRemoveFoodCommand() {
                super("removefood", "Remove a breeding food from a custom animal");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
                foodArg = withRequiredArg("food", "Food item ID to remove", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String food = BreedingConfigCommand.resolveFoodShortcut(ctx.get(foodArg));
                plugin.getConfigManager().removeCustomAnimalFood(modelId, food);
                ctx.sendMessage(Message.raw("Removed food ").color("#FF5555")
                        .insert(Message.raw(food).color("#FFFFFF"))
                        .insert(Message.raw(" from " + modelId).color("#AAAAAA")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /**
         * Scan the world for all entities and show their modelAssetIds.
         * Helps users find the exact name to use for custom animals.
         */
        public static class CustomAnimalScanCommand extends AbstractCommand {
            public CustomAnimalScanCommand() {
                super("scan", "Scan world for all creature modelAssetIds");
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = getInstance();
                if (plugin == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                ctx.sendMessage(Message.raw("Scanning world for creatures...").color("#FFFF55"));

                World world = Universe.get().getDefaultWorld();
                if (world == null) {
                    ctx.sendMessage(Message.raw("No world available!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Find ALL entities with ModelComponent
                AnimalFinder.findAnimals(world, false, animals -> {
                    if (animals.isEmpty()) {
                        ctx.sendMessage(Message.raw("No creatures found in the world.").color("#AAAAAA"));
                        return;
                    }

                    // Group by modelAssetId and count
                    java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
                    for (AnimalFinder.FoundAnimal animal : animals) {
                        String id = animal.getModelAssetId();
                        counts.merge(id, 1, Integer::sum);
                    }

                    ctx.sendMessage(Message.raw("=== Detected Creatures (" + counts.size() + " types) ===").color("#FF9900"));

                    // Show built-in animals first
                    ctx.sendMessage(Message.raw("Built-in animals:").color("#55FF55"));
                    int builtInCount = 0;
                    for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                        AnimalType type = AnimalType.fromModelAssetId(entry.getKey());
                        if (type != null) {
                            ctx.sendMessage(Message.raw("  " + entry.getKey()).color("#AAAAAA")
                                    .insert(Message.raw(" x" + entry.getValue()).color("#FFFFFF"))
                                    .insert(Message.raw(" [" + type + "]").color("#55FF55")));
                            builtInCount++;
                        }
                    }
                    if (builtInCount == 0) {
                        ctx.sendMessage(Message.raw("  (none found)").color("#AAAAAA"));
                    }

                    // Show other creatures (potential custom animals)
                    ctx.sendMessage(Message.raw("Other creatures (can add as custom):").color("#FFAA00"));
                    int otherCount = 0;
                    for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                        AnimalType type = AnimalType.fromModelAssetId(entry.getKey());
                        if (type == null) {
                            // Check if already added as custom
                            boolean isCustom = plugin.getConfigManager().isCustomAnimal(entry.getKey());
                            String status = isCustom ? " [ADDED]" : "";
                            String statusColor = isCustom ? "#55FF55" : "#FFAA00";
                            ctx.sendMessage(Message.raw("  " + entry.getKey()).color("#FFFFFF")
                                    .insert(Message.raw(" x" + entry.getValue()).color("#AAAAAA"))
                                    .insert(Message.raw(status).color(statusColor)));
                            otherCount++;
                        }
                    }
                    if (otherCount == 0) {
                        ctx.sendMessage(Message.raw("  (none found)").color("#AAAAAA"));
                    }

                    ctx.sendMessage(Message.raw("Use ").color("#AAAAAA")
                            .insert(Message.raw("/breed custom add <name> <food>").color("#FFFFFF"))
                            .insert(Message.raw(" to add a creature").color("#AAAAAA")));

                    // Show registered custom animals for comparison
                    java.util.Map<String, CustomAnimalConfig> customAnimals = plugin.getConfigManager().getCustomAnimals();
                    if (!customAnimals.isEmpty()) {
                        ctx.sendMessage(Message.raw(""));
                        ctx.sendMessage(Message.raw("Registered custom animals:").color("#55FFFF"));
                        for (String registeredName : customAnimals.keySet()) {
                            boolean foundInWorld = counts.containsKey(registeredName);
                            String foundStatus = foundInWorld ? " [IN WORLD]" : " [NOT FOUND]";
                            String foundColor = foundInWorld ? "#55FF55" : "#FF5555";
                            ctx.sendMessage(Message.raw("  " + registeredName).color("#FFFFFF")
                                    .insert(Message.raw(foundStatus).color(foundColor)));
                        }

                        // Trigger interaction setup for any custom animals found in world
                        ctx.sendMessage(Message.raw("Setting up interactions for custom animals...").color("#AAAAAA"));
                        plugin.autoSetupNearbyAnimals();
                    }
                });

                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal setrole <modelAssetId> <roleId> - Set the NPC role for spawning */
        public static class CustomAnimalSetRoleCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<String> roleArg;

            public CustomAnimalSetRoleCommand() {
                super("setrole", "Set the NPC role ID for spawning babies");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
                roleArg = withRequiredArg("roleId", "NPC role ID", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                String roleId = ctx.get(roleArg);

                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Use /breed custom add first").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalNpcRole(modelId, roleId);
                ctx.sendMessage(Message.raw("Set NPC role for ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF"))
                        .insert(Message.raw(" to ").color("#55FF55"))
                        .insert(Message.raw(roleId).color("#FFAA00")));
                ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal setbaby <modelAssetId> <babyRoleId> - Set the baby NPC role */
        public static class CustomAnimalSetBabyCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<String> babyRoleArg;

            public CustomAnimalSetBabyCommand() {
                super("setbaby", "Set the NPC role for spawning babies");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID of the adult", ArgTypes.STRING);
                babyRoleArg = withRequiredArg("babyRoleId", "NPC role ID for baby spawning", ArgTypes.STRING);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                String babyRoleId = ctx.get(babyRoleArg);

                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Use /breed custom add first").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                // Validate the baby role exists
                NPCPlugin npcPlugin = NPCPlugin.get();
                int roleIndex = npcPlugin.getIndex(babyRoleId);
                if (roleIndex < 0) {
                    ctx.sendMessage(Message.raw("Baby NPC role not found: " + babyRoleId).color("#FF5555"));
                    ctx.sendMessage(Message.raw("Make sure this is a valid NPC role name.").color("#AAAAAA"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalBabyRole(modelId, babyRoleId);
                ctx.sendMessage(Message.raw("Set baby NPC role for ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF"))
                        .insert(Message.raw(" to ").color("#55FF55"))
                        .insert(Message.raw(babyRoleId).color("#FFAA00")));
                ctx.sendMessage(Message.raw("Babies will now spawn using this role instead of scaling.").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal setgrowth <modelAssetId> <minutes> - Set the growth time */
        public static class CustomAnimalSetGrowthCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<Double> timeArg;

            public CustomAnimalSetGrowthCommand() {
                super("setgrowth", "Set growth time in minutes");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
                timeArg = withRequiredArg("minutes", "Growth time in minutes", ArgTypes.DOUBLE);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                double minutes = ctx.get(timeArg);

                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                if (minutes <= 0) {
                    ctx.sendMessage(Message.raw("Growth time must be positive").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalGrowthTime(modelId, minutes);
                ctx.sendMessage(Message.raw("Set growth time for ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF"))
                        .insert(Message.raw(" to ").color("#55FF55"))
                        .insert(Message.raw(minutes + " min").color("#FFAA00")));
                ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /customanimal setcooldown <modelAssetId> <minutes> - Set the breeding cooldown */
        public static class CustomAnimalSetCooldownCommand extends AbstractCommand {
            private final RequiredArg<String> modelArg;
            private final RequiredArg<Double> timeArg;

            public CustomAnimalSetCooldownCommand() {
                super("setcooldown", "Set breeding cooldown in minutes");
                modelArg = withRequiredArg("modelAssetId", "Model asset ID", ArgTypes.STRING);
                timeArg = withRequiredArg("minutes", "Cooldown in minutes", ArgTypes.DOUBLE);
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                if (plugin == null || plugin.getConfigManager() == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                String modelId = ctx.get(modelArg);
                double minutes = ctx.get(timeArg);

                if (!plugin.getConfigManager().isCustomAnimal(modelId)) {
                    ctx.sendMessage(Message.raw("Custom animal not found: " + modelId).color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                if (minutes < 0) {
                    ctx.sendMessage(Message.raw("Cooldown must be non-negative").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                plugin.getConfigManager().setCustomAnimalCooldown(modelId, minutes);
                ctx.sendMessage(Message.raw("Set cooldown for ").color("#55FF55")
                        .insert(Message.raw(modelId).color("#FFFFFF"))
                        .insert(Message.raw(" to ").color("#55FF55"))
                        .insert(Message.raw(minutes + " min").color("#FFAA00")));
                ctx.sendMessage(Message.raw("Use /breed config save to persist").color("#AAAAAA"));
                return CompletableFuture.completedFuture(null);
            }
        }
    }
}
