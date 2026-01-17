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
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.AddReason;

import com.laits.breeding.managers.BreedingManager;
import com.laits.breeding.managers.GrowthManager;
import com.laits.breeding.listeners.UseBlockHandler;
import com.laits.breeding.interactions.FeedAnimalInteraction;
import com.laits.breeding.models.AnimalType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.AnimalFinder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class for Lait's Animal Breeding.
 */
public class LaitsBreedingPlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private static LaitsBreedingPlugin instance;

    // Heart particle ID for breeding love effect
    private static final String HEARTS_PARTICLE = "NPC/Emotions/Spawners/Hearts";

    // Breeding distance - animals must be within this range to breed
    private static final double BREEDING_DISTANCE = 5.0;
    // How long animals stay in love (milliseconds)
    private static final long LOVE_DURATION = 30000; // 30 seconds

    private ConfigManager configManager;
    private BreedingManager breedingManager;
    private GrowthManager growthManager;
    private ScheduledExecutorService tickScheduler;

    // Cached reflection objects for performance
    private Class<?> cachedTransformClass;
    private Object cachedTransformComponentType;
    private java.lang.reflect.Method cachedGetPosition;
    private boolean reflectionCacheInitialized = false;

    // Event counters for diagnostics
    private static int playerReadyCount = 0;
    private static int mouseClickCount = 0;
    private static int useBlockPreCount = 0;
    private static int useBlockPostCount = 0;
    public static int getPlayerReadyCount() { return playerReadyCount; }
    public static int getMouseClickCount() { return mouseClickCount; }
    public static int getUseBlockPreCount() { return useBlockPreCount; }
    public static int getUseBlockPostCount() { return useBlockPostCount; }

    // Cached reflection objects for entity interaction setup (initialized in start())
    private Object cachedInteractionsCompType;
    private Object cachedInteractableCompType;
    private Object cachedInteractionTypeUse;
    private Class<?> cachedRefClass;
    private Class<?> cachedComponentTypeClass;
    private boolean interactionCacheInitialized = false;

    // Verbose logging toggle (controlled by /breedlogs command)
    private static boolean verboseLogging = false;
    public static boolean isVerboseLogging() { return verboseLogging; }
    public static void setVerboseLogging(boolean enabled) { verboseLogging = enabled; }

    // Development debug mode - broadcasts to all players in-game (controlled by /breeddev command)
    // Set to false for production builds
    private static boolean devMode = false;
    public static boolean isDevMode() { return devMode; }
    public static void setDevMode(boolean enabled) { devMode = enabled; }

    // Store original interaction IDs before we override them (for fallback, e.g., horse mounting)
    // Key is entity index (stable across different Ref instances)
    private static final Map<Integer, String> originalInteractions = new ConcurrentHashMap<>();

    /**
     * Get the original interaction ID for an entity (before we set Root_FeedAnimal).
     * Used by FeedAnimalInteraction to fall back to default behavior (e.g., mounting).
     */
    public static String getOriginalInteractionId(Object entityRef) {
        try {
            java.lang.reflect.Method getIndex = entityRef.getClass().getMethod("getIndex");
            Integer index = (Integer) getIndex.invoke(entityRef);
            return originalInteractions.get(index);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Store the original interaction ID for an entity.
     */
    private static void storeOriginalInteractionId(Object entityRef, String interactionId) {
        try {
            java.lang.reflect.Method getIndex = entityRef.getClass().getMethod("getIndex");
            Integer index = (Integer) getIndex.invoke(entityRef);
            if (index != null) {
                originalInteractions.put(index, interactionId);
            }
        } catch (Exception e) {
            // Silent
        }
    }

    /** Log verbose/debug message (only when verbose logging is enabled) */
    private void logVerbose(String message) {
        if (verboseLogging) {
            getLogger().atInfo().log("[Lait:AnimalBreeding] " + message);
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
        if (!devMode) return;

        try {
            // Also log to server console
            getLogger().atInfo().log("[DEV] " + message);

            // Broadcast to all online players
            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

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

            if (forEachMethod == null) return;

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
                                Object ref = chunk.getClass().getMethod("getReferenceTo", int.class).invoke(chunk, i);
                                if (ref != null) {
                                    // Check if this is a player by trying to call sendMessage
                                    try {
                                        java.lang.reflect.Method sendMsg = ref.getClass().getMethod("sendMessage", Message.class);
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
                }
            );

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
        // Initialize config manager and load from file
        configManager = new ConfigManager();
        configManager.setLogger(msg -> getLogger().atInfo().log(msg));

        // Load config from plugin's data directory (created automatically by the server)
        java.nio.file.Path configPath = getDataDirectory().resolve("config.json");
        configManager.loadFromFile(configPath);

        breedingManager = new BreedingManager(configManager);
        growthManager = new GrowthManager(configManager, breedingManager);

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
        // Per docs: "Setup Phase - Register commands, events, and initialize resources here"
        registerInteractionHandler();

        // Register our custom FeedAnimalInteraction type with the codec
        try {
            getCodecRegistry(Interaction.CODEC)
                .register("FeedAnimal", FeedAnimalInteraction.class, FeedAnimalInteraction.CODEC);
        } catch (Exception e) {
            logWarning("FeedAnimalInteraction codec registration skipped (may already exist): " + e.getMessage());
        }

        // Register ECS system for block interactions
        try {
            getEntityStoreRegistry().registerSystem(new UseBlockHandler());
        } catch (Exception e) {
            // Silent
        }

        // Register commands
        getCommandRegistry().registerCommand(new BreedingHelpCommand());
        getCommandRegistry().registerCommand(new BreedingStatusCommand());
        getCommandRegistry().registerCommand(new BreedingLogsCommand());
        getCommandRegistry().registerCommand(new BreedingDevCommand());
        getCommandRegistry().registerCommand(new BreedingHintCommand());
        getCommandRegistry().registerCommand(new BreedingConfigCommand());
    }

    @Override
    protected void start() {
        // Configure RootInteraction chain (after assets are loaded)
        try {
            Class<?> rootIntClass = Class.forName("com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction");
            java.lang.reflect.Method preloadMethod = rootIntClass.getMethod("getRootInteractionOrUnknown", String.class);
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
        tickScheduler.scheduleAtFixedRate(() -> {
            try {
                breedingManager.tickPregnancies();
                growthManager.tickGrowth();
                tickLoveAnimals();
            } catch (Exception e) {
                // Silent - tick errors are non-critical
            }
        }, 1, 1, TimeUnit.SECONDS);

        // Attach Root_FeedAnimal interaction to all breedable animals
        attachInteractionsToAnimals();

        // Register entity removal listener to clean up breeding data when animals die
        registerEntityRemovalListener();

        getLogger().atInfo().log("[Lait:AnimalBreeding] Plugin started! Commands: /laitsbreeding, /breedstatus");
    }

    /**
     * Initialize cached reflection objects for frequently used classes/methods.
     * This avoids repeated Class.forName() and getMethod() calls in tick loops.
     */
    private void initReflectionCache() {
        try {
            cachedTransformClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
            java.lang.reflect.Method getComponentType = cachedTransformClass.getMethod("getComponentType");
            cachedTransformComponentType = getComponentType.invoke(null);
            cachedGetPosition = cachedTransformClass.getMethod("getPosition");
            reflectionCacheInitialized = true;
        } catch (Exception e) {
            reflectionCacheInitialized = false;
            logWarning("Reflection cache init failed (distance breeding may not work): " + e.getMessage());
        }

        // Initialize interaction setup cache
        try {
            Class<?> interactionsClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.interaction.Interactions");
            cachedInteractionsCompType = interactionsClass.getMethod("getComponentType").invoke(null);

            Class<?> interactableClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.entity.component.Interactable");
            cachedInteractableCompType = interactableClass.getMethod("getComponentType").invoke(null);

            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                if (enumConst.toString().equals("Use")) {
                    cachedInteractionTypeUse = enumConst;
                    break;
                }
            }

            cachedRefClass = Class.forName("com.hypixel.hytale.component.Ref");
            cachedComponentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");

            interactionCacheInitialized = true;
            logVerbose("Interaction cache initialized successfully");
        } catch (Exception e) {
            interactionCacheInitialized = false;
            logWarning("Interaction cache init failed: " + e.getMessage());
        }
    }

    /**
     * Attaching interactions to animals via periodic scanning.
     * Note: Event-based detection (PrefabPlaceEntityEvent, LoadedNPCEvent) was tested
     * but these events don't fire for natural animal spawns in Hytale.
     */
    private void attachInteractionsToAnimals() {
        // Scan when a player connects (entities spawn when chunks load around players)
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            tickScheduler.schedule(() -> {
                try {
                    autoSetupNearbyAnimals();
                } catch (Exception e) {
                    // Silent
                }
            }, 3, TimeUnit.SECONDS);
        });

        // Primary: Periodic scan every 30 seconds for animals
        tickScheduler.scheduleAtFixedRate(() -> {
            try {
                autoSetupNearbyAnimals();
            } catch (Exception e) {
                // Silent
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Set up interactions for a single entity if it's a breedable animal.
     * Must be called from the world thread.
     *
     * @param world The world containing the entity
     * @param entityRef The entity reference (Ref<EntityStore>)
     */
    private void setupSingleEntity(World world, Object entityRef) {
        if (!interactionCacheInitialized) return;

        try {
            Object entityStore = world.getClass().getMethod("getEntityStore").invoke(world);
            Object store = entityStore.getClass().getMethod("getStore").invoke(entityStore);

            // Get the model asset ID to identify the animal type
            String modelAssetId = getEntityModelAssetId(store, entityRef);
            if (modelAssetId == null) return;

            // Check if it's a farm animal
            AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);
            if (animalType == null) return; // Not a recognized animal

            logVerbose("Setting up animal: " + modelAssetId + " (" + animalType + ")");

            // Skip if breeding is disabled for this animal type
            if (!configManager.isAnimalEnabled(animalType)) return;

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

        } catch (Exception e) {
            // Check for stale ref error
            Throwable cause = e;
            if (e instanceof java.lang.reflect.InvocationTargetException) {
                cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
            }
            if (cause instanceof IllegalStateException &&
                cause.getMessage() != null &&
                cause.getMessage().contains("Invalid entity")) {
                // Entity was despawned - ignore
                return;
            }
            logVerbose("setupSingleEntity error: " + e.getMessage());
        }
    }

    /**
     * Get the model asset ID from an entity reference.
     */
    private String getEntityModelAssetId(Object store, Object entityRef) {
        try {
            Class<?> modelCompClass = Class.forName(
                "com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
            Object modelCompType = modelCompClass.getMethod("getComponentType").invoke(null);

            java.lang.reflect.Method getCompMethod = store.getClass().getMethod(
                "getComponent", cachedRefClass, cachedComponentTypeClass);
            Object modelComp = getCompMethod.invoke(store, entityRef, modelCompType);
            if (modelComp == null) return null;

            java.lang.reflect.Field modelField = modelCompClass.getDeclaredField("model");
            modelField.setAccessible(true);
            Object model = modelField.get(modelComp);
            if (model == null) return null;

            // Extract modelAssetId from model.toString()
            String modelStr = model.toString();
            int start = modelStr.indexOf("modelAssetId='");
            if (start < 0) return null;
            start += 14;
            int end = modelStr.indexOf("'", start);
            if (end <= start) return null;
            return modelStr.substring(start, end);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Set up breeding interactions on a single entity.
     */
    private void setupEntityInteractions(Object store, Object entityRef, AnimalType animalType) {
        try {
            java.lang.reflect.Method ensureMethod = store.getClass().getMethod(
                "ensureAndGetComponent", cachedRefClass, cachedComponentTypeClass);

            // Ensure entity has Interactable component (required for hints to show)
            try {
                ensureMethod.invoke(store, entityRef, cachedInteractableCompType);
            } catch (Exception e) {
                // Silently continue - component may already exist
            }

            Object interactions = ensureMethod.invoke(store, entityRef, cachedInteractionsCompType);
            if (interactions == null) return;

            String feedInteractionId = "Root_FeedAnimal";

            java.lang.reflect.Method getIntId = interactions.getClass().getMethod(
                "getInteractionId",
                Class.forName("com.hypixel.hytale.protocol.InteractionType"));

            String currentUse = (String) getIntId.invoke(interactions, cachedInteractionTypeUse);

            if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                // Save original interaction ID for fallback (e.g., horse mounting)
                if (currentUse != null && !currentUse.isEmpty()) {
                    storeOriginalInteractionId(entityRef, currentUse);
                    logVerbose("Saved original interaction: " + currentUse);
                }

                java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                    "setInteractionId",
                    Class.forName("com.hypixel.hytale.protocol.InteractionType"),
                    String.class);

                setIntId.invoke(interactions, cachedInteractionTypeUse, feedInteractionId);
            }

            // Set the interaction hint
            java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                "setInteractionHint", String.class);
            setHint.invoke(interactions, "server.interactionHints.feed");
            logVerbose("Interaction setup complete for " + animalType);

        } catch (Exception e) {
            logVerbose("setupEntityInteractions error: " + e.getMessage());
        }
    }

    /**
     * Register listener to clean up breeding data when entities are removed (death, despawn, etc.)
     */
    private void registerEntityRemovalListener() {
        try {
            getEventRegistry().registerGlobal(EntityRemoveEvent.class, event -> {
                try {
                    Entity entity = event.getEntity();
                    if (entity == null) return;

                    UUID entityId = getEntityUUID(entity);

                    BreedingData data = breedingManager.getData(entityId);
                    if (data != null) {
                        breedingManager.removeData(entityId);
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
     */
    private void autoSetupNearbyAnimals() {
        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            // Get the Interactions component type
            Class<?> interactionsClass = Class.forName("com.hypixel.hytale.server.core.modules.interaction.Interactions");
            java.lang.reflect.Method getCompType = interactionsClass.getMethod("getComponentType");
            Object interactionsCompType = getCompType.invoke(null);

            // Get InteractionType enum values
            Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
            Object useType = null;
            Object secondaryType = null;
            for (Object enumConst : interactionTypeClass.getEnumConstants()) {
                String name = enumConst.toString();
                if (name.equals("Use")) useType = enumConst;
                if (name.equals("Secondary")) secondaryType = enumConst;
            }

            final Object compType = interactionsCompType;
            final Object intTypeUse = useType;
            final Object intTypeSecondary = secondaryType;

            // Find all farm animals (including babies)
            AnimalFinder.findAnimals(world, false, animals -> {
                try {
                    if (animals.isEmpty()) return;

                    Object entityStore = world.getClass().getMethod("getEntityStore").invoke(world);
                    Object store = entityStore.getClass().getMethod("getStore").invoke(entityStore);

                    java.lang.reflect.Method ensureMethod = store.getClass().getMethod("ensureAndGetComponent",
                        Class.forName("com.hypixel.hytale.component.Ref"),
                        Class.forName("com.hypixel.hytale.component.ComponentType"));

                    String feedInteractionId = "Root_FeedAnimal";

                    for (AnimalFinder.FoundAnimal animal : animals) {
                        Object entityRef = animal.getEntityRef();
                        AnimalType animalType = animal.getAnimalType();

                        // Skip if not a recognized farm animal
                        if (animalType == null) continue;

                        // Skip if breeding is disabled for this animal type
                        if (!configManager.isAnimalEnabled(animalType)) continue;

                        // Check if this is a baby that needs growth tracking
                        if (animal.isBaby()) {
                            UUID babyId = UUID.nameUUIDFromBytes(entityRef.toString().getBytes());
                            if (breedingManager.getData(babyId) == null) {
                                breedingManager.registerBaby(babyId, animalType, entityRef);
                            }
                        }

                        // Set up interactions for adults (babies can't breed)
                        if (!animal.isBaby()) {
                            // Ensure entity has Interactable component (required for hints to show)
                            try {
                                Class<?> interactableClass = Class.forName(
                                    "com.hypixel.hytale.server.core.modules.entity.component.Interactable");
                                Object interactableType = interactableClass.getMethod("getComponentType").invoke(null);
                                ensureMethod.invoke(store, entityRef, interactableType);
                            } catch (Exception e) {
                                // Silently continue - Interactable component may already exist
                            }

                            Object interactions = ensureMethod.invoke(store, entityRef, compType);

                            if (interactions != null) {
                                java.lang.reflect.Method getIntId = interactions.getClass().getMethod(
                                    "getInteractionId",
                                    Class.forName("com.hypixel.hytale.protocol.InteractionType"));

                                String currentUse = (String) getIntId.invoke(interactions, intTypeUse);

                                if (currentUse == null || !currentUse.equals(feedInteractionId)) {
                                    // Save original interaction ID for fallback (e.g., horse mounting)
                                    if (currentUse != null && !currentUse.isEmpty()) {
                                        storeOriginalInteractionId(entityRef, currentUse);
                                        logVerbose("Saved original interaction for entity: " + currentUse);
                                    }

                                    java.lang.reflect.Method setIntId = interactions.getClass().getMethod(
                                        "setInteractionId",
                                        Class.forName("com.hypixel.hytale.protocol.InteractionType"),
                                        String.class);

                                    setIntId.invoke(interactions, intTypeUse, feedInteractionId);
                                }

                                // Set the interaction hint
                                java.lang.reflect.Method setHint = interactions.getClass().getMethod(
                                    "setInteractionHint", String.class);
                                // Use our custom localization key (defined in Server/Languages/en-US/server.lang)
                                setHint.invoke(interactions, "server.interactionHints.feed");
                                logVerbose("Set hint: server.interactionHints.feed");
                            }
                        }
                    }
                } catch (Exception e) {
                    // Silent
                }
            });

        } catch (Exception e) {
            // Silent - this runs frequently
        }
    }

    /**
     * Register the player interaction event handler for breeding.
     */
    private void registerInteractionHandler() {
        try { getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady); } catch (Exception e) { }
        try { getEventRegistry().register(PlayerMouseButtonEvent.class, this::onMouseButton); } catch (Exception e) { }
        try { getEventRegistry().register(UseBlockEvent.Pre.class, this::onUseBlockPre); } catch (Exception e) { }
        try { getEventRegistry().register(UseBlockEvent.Post.class, this::onUseBlockPost); } catch (Exception e) { }
        try { getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract); } catch (Exception e) { }
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
        // Handled by handleMouseClick - this is a fallback
    }

    /**
     * Handle mouse clicks for breeding.
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

        // Get entity model ID to determine type (via ECS ModelComponent)
        String entityName = getEntityModelId(targetEntity);

        // Try to identify animal type from entity name
        AnimalType animalType = AnimalType.fromEntityTypeId(entityName);
        if (animalType == null) {
            return; // Not a breedable animal
        }

        // Get held item
        Item heldItem = event.getItemInHand();
        if (heldItem == null) {
            return;
        }

        String itemId = heldItem.getId();

        // Get UUID for this entity (via ECS UUIDComponent)
        UUID entityId = getEntityUUID(targetEntity);

        // Try to feed the animal
        BreedingManager.FeedResult result = breedingManager.tryFeed(entityId, animalType, itemId);

        // Store the entity ref for position tracking (needed for distance-based breeding)
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
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) + " is now in love!"));
                    // Note: Sound and item consumption handled by FeedAnimal interaction
                    spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case ALREADY_IN_LOVE:
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is already in love!"));
                    spawnHeartParticlesAtEntity(targetEntity);
                    break;
                case WRONG_FOOD:
                    java.util.List<String> validFoods = configManager.getBreedingFoods(animalType);
                    String foodList = String.join(", ", validFoods);
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] Wrong food! " + capitalize(animalType.getId()) + " needs: " + foodList));
                    break;
                case DISABLED:
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] Breeding is disabled for " + animalType.getId() + "s"));
                    break;
                case NOT_ADULT:
                    player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " is too young to breed"));
                    break;
                case ON_COOLDOWN:
                    BreedingData data = breedingManager.getData(entityId);
                    if (data != null) {
                        long remaining = data.getCooldownRemaining(configManager.getBreedingCooldown(animalType));
                        player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " needs to rest (" + (remaining / 1000) + "s)"));
                    } else {
                        player.sendMessage(Message.raw("[Lait:AnimalBreeding] This " + animalType.getId() + " needs to rest"));
                    }
                    break;
            }
        }
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Consume 1 item from the player's held item stack.
     */
    public void consumeHeldItem(Player player) {
        try {
            // Get player's inventory
            Inventory inventory = ((LivingEntity) player).getInventory();
            if (inventory == null) return;

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
            if (soundId < 0) return;

            Vector3d pos = getEntityPosition(targetEntity);
            World world = targetEntity.getWorld();
            if (world == null) return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            if (store == null) return;

            SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), store);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Try to find another animal in love to breed with.
     * For now, this uses our tracked animals - in future, should scan nearby entities.
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
                player.sendMessage(Message.raw("[Lait:AnimalBreeding] Baby arrives in " + (gestationTime / 1000) + " seconds"));
            }
            break;
        }
    }

    /**
     * Try to find another animal in love and breed INSTANTLY (spawn baby immediately).
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
            if (position == null) return;

            double x = position.getX();
            double y = position.getY() + 1.5;
            double z = position.getZ();

            Class<?> particleUtilClass = Class.forName("com.hypixel.hytale.server.core.universe.world.ParticleUtil");
            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

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
     * Play feeding sound at entity's position.
     */
    private void playFeedingSoundAtEntity(Entity entity) {
        try {
            Vector3d pos = getEntityPosition(entity);
            if (pos == null) return;

            World world = Universe.get().getDefaultWorld();
            if (world == null) return;

            Object store = world.getEntityStore().getStore();

            // Get sound ID
            Class<?> soundEventClass = Class.forName("com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent");
            Object assetMap = soundEventClass.getMethod("getAssetMap").invoke(null);
            int soundId = (int) assetMap.getClass().getMethod("getIndex", Object.class).invoke(assetMap, "SFX_Consume_Bread");

            if (soundId < 0) return;

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

                Class<?> transformClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
                java.lang.reflect.Method getComponentType = transformClass.getMethod("getComponentType");
                Object componentType = getComponentType.invoke(null);

                Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
                Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");
                java.lang.reflect.Method getComponent = store.getClass().getMethod("getComponent", refClass, componentTypeClass);
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
    private UUID getEntityUUID(Entity entity) {
        try {
            // Try to get from UUIDComponent via ECS
            Object entityRef = getEntityRef(entity);
            if (entityRef != null) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Class<?> uuidCompClass = Class.forName("com.hypixel.hytale.server.core.entity.UUIDComponent");
                    java.lang.reflect.Method getComponentType = uuidCompClass.getMethod("getComponentType");
                    Object componentType = getComponentType.invoke(null);

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
                        Object uuidComp = getComponent.invoke(store, entityRef, componentType);
                        if (uuidComp != null) {
                            java.lang.reflect.Method getUuid = uuidComp.getClass().getMethod("getUuid");
                            Object uuid = getUuid.invoke(uuidComp);
                            if (uuid instanceof UUID) {
                                return (UUID) uuid;
                            }
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
     * Get model asset ID for an entity using ECS ModelComponent (avoids deprecated getLegacyDisplayName()).
     * This is used to determine the animal type.
     */
    private String getEntityModelId(Entity entity) {
        try {
            Object entityRef = getEntityRef(entity);
            if (entityRef != null) {
                World world = entity.getWorld();
                if (world != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Class<?> modelCompClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
                    java.lang.reflect.Method getComponentType = modelCompClass.getMethod("getComponentType");
                    Object componentType = getComponentType.invoke(null);

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
                        Object modelComp = getComponent.invoke(store, entityRef, componentType);
                        if (modelComp != null) {
                            java.lang.reflect.Field modelField = modelCompClass.getDeclaredField("model");
                            modelField.setAccessible(true);
                            Object model = modelField.get(modelComp);

                            if (model != null) {
                                java.lang.reflect.Field assetIdField = model.getClass().getDeclaredField("modelAssetId");
                                assetIdField.setAccessible(true);
                                return (String) assetIdField.get(model);
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
        if (breedingManager.getTrackedCount() == 0) return;

        long now = System.currentTimeMillis();

        // Single pass: expire love AND collect eligible animals AND group by type
        java.util.Map<AnimalType, java.util.List<BreedingData>> byType = new java.util.HashMap<>();
        int inLoveCount = 0;

        for (BreedingData data : breedingManager.getAllBreedingData()) {
            if (data.isInLove()) {
                // Check expiration
                if (now - data.getLoveStartTime() > LOVE_DURATION) {
                    data.resetLove();
                    continue;
                }
                // Collect if eligible for breeding
                if (!data.isPregnant() && data.getGrowthStage().canBreed()) {
                    byType.computeIfAbsent(data.getAnimalType(), k -> new java.util.ArrayList<>()).add(data);
                    inLoveCount++;
                }
            }
        }

        // Early exit if less than 2 animals in love
        if (inLoveCount < 2) return;

        // Must execute ECS operations on world thread
        World world = Universe.get().getDefaultWorld();
        if (world == null) return;

        // For each type with 2+ animals in love, check distance and breed if close
        for (java.util.Map.Entry<AnimalType, java.util.List<BreedingData>> entry : byType.entrySet()) {
            java.util.List<BreedingData> animalsOfType = entry.getValue();
            if (animalsOfType.size() < 2) continue;

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

                    if (pos1 == null || pos2 == null) return;

                    double distance = calculateDistance(pos1, pos2);

                    if (distance <= BREEDING_DISTANCE) {
                        finalAnimal1.completeBreeding();
                        finalAnimal2.completeBreeding();
                        spawnBabyAnimal(finalType, pos1);
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        }
    }

    /**
     * Get position on world thread using cached reflection objects.
     */
    private Vector3d getPositionOnWorldThread(Store<EntityStore> store, Object entityRef) {
        if (!reflectionCacheInitialized) return null;
        try {
            // Find compatible getComponent method dynamically
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
            if (getComponent == null) return null;

            Object transform = getComponent.invoke(store, entityRef, cachedTransformComponentType);
            if (transform != null) {
                return (Vector3d) cachedGetPosition.invoke(transform);
            }
        } catch (Exception e) {
            // Entity removed or invalid
        }
        return null;
    }

    /**
     * Get position from BreedingData's entityRef.
     */
    private Vector3d getPositionFromBreedingData(BreedingData data) {
        Object entityRef = data.getEntityRef();
        if (entityRef == null) return null;

        try {
            World world = Universe.get().getDefaultWorld();
            if (world == null) return null;

            Store<EntityStore> store = world.getEntityStore().getStore();

            Class<?> transformClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
            java.lang.reflect.Method getComponentType = transformClass.getMethod("getComponentType");
            Object componentType = getComponentType.invoke(null);

            Class<?> refClass = Class.forName("com.hypixel.hytale.component.Ref");
            Class<?> componentTypeClass = Class.forName("com.hypixel.hytale.component.ComponentType");

            java.lang.reflect.Method getComponent = store.getClass().getMethod("getComponent", refClass, componentTypeClass);
            Object transform = getComponent.invoke(store, entityRef, componentType);

            if (transform != null) {
                java.lang.reflect.Method getPosition = transform.getClass().getMethod("getPosition");
                return (Vector3d) getPosition.invoke(transform);
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
    private void performInstantBreeding(BreedingData animal1, BreedingData animal2, AnimalType type, Vector3d spawnPos) {
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
                            Class<?> modelAssetClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                            Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                            Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap, finalAnimalType.getModelAssetId());

                            if (modelAsset != null) {
                                Class<?> modelClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                                java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel", modelAssetClass, float.class);
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
                                java.lang.reflect.Method validateRole = npcPluginClass.getMethod("validateSpawnableRole", String.class);
                                validateRole.invoke(npcPlugin, finalRoleId);
                            } catch (Exception e) { }

                            try {
                                java.lang.reflect.Method prepareRole = npcPluginClass.getMethod("prepareRoleBuilderInfo", int.class);
                                prepareRole.invoke(npcPlugin, roleIndex);
                            } catch (Exception e) { }

                            for (java.lang.reflect.Method m : npcPluginClass.getMethods()) {
                                if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
                                    Class<?>[] paramTypes = m.getParameterTypes();
                                    Class<?> triConsumerClass = paramTypes[5];
                                    Object noOpCallback = java.lang.reflect.Proxy.newProxyInstance(
                                        triConsumerClass.getClassLoader(),
                                        new Class<?>[] { triConsumerClass },
                                        (proxy, method, args) -> null
                                    );

                                    try {
                                        // Pass scaled model for creatures without baby variants
                                        result = m.invoke(npcPlugin, store, roleIndex, spawnPos, rotation, scaledModel, noOpCallback);
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
                            : "Young " + finalAnimalType.getId() + " born (scale " + String.format("%.1f", finalInitialScale) + ")";
                        getLogger().atInfo().log("[Lait:AnimalBreeding] " + logMessage + " at " +
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
                                    Class<?> modelCompClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
                                    java.lang.reflect.Method getModelType = modelCompClass.getMethod("getComponentType");
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
                                        if (modelComp != null) {
                                            java.lang.reflect.Field modelField = modelCompClass.getDeclaredField("model");
                                            modelField.setAccessible(true);
                                            Object currentModel = modelField.get(modelComp);

                                            if (currentModel != null) {
                                                java.lang.reflect.Field assetIdField = currentModel.getClass().getDeclaredField("modelAssetId");
                                                assetIdField.setAccessible(true);
                                                String modelAssetId = (String) assetIdField.get(currentModel);

                                                Class<?> modelAssetClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                                                Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                                                Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap, modelAssetId);

                                                if (modelAsset != null) {
                                                    Class<?> modelClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                                                    java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel", modelAssetClass, float.class);
                                                    Object newModel = createScaledModel.invoke(null, modelAsset, finalInitialScale);

                                                    // Try to convert to ModelReference if needed
                                                    Object modelToSet = newModel;
                                                    try {
                                                        java.lang.reflect.Method toReference = newModel.getClass().getMethod("toReference");
                                                        Object modelRef = toReference.invoke(newModel);
                                                        if (modelRef != null) {
                                                            modelToSet = modelRef;
                                                        }
                                                    } catch (NoSuchMethodException e) {
                                                        // No toReference method, use the model directly
                                                    }

                                                    modelField.set(modelComp, modelToSet);

                                                    // Try to trigger ECS sync via setComponent
                                                    try {
                                                        java.lang.reflect.Method setComponent = null;
                                                        for (java.lang.reflect.Method m : store.getClass().getMethods()) {
                                                            if (m.getName().equals("setComponent") && m.getParameterCount() == 3) {
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
                                                        Object commandBuffer = world.getClass().getMethod("getCommandBuffer").invoke(world);
                                                        if (commandBuffer != null) {
                                                            for (java.lang.reflect.Method m : commandBuffer.getClass().getMethods()) {
                                                                if (m.getName().equals("setComponent") && m.getParameterCount() == 3) {
                                                                    Class<?>[] params = m.getParameterTypes();
                                                                    if (params[0].isAssignableFrom(entityRef.getClass())) {
                                                                        m.invoke(commandBuffer, entityRef, modelType, modelComp);
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } catch (Exception cbEx) {
                                                        // Silent
                                                    }

                                                    logVerbose("Applied initial scale " + finalInitialScale + " to " + finalAnimalType.getId());
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
                        logWarning("Failed to spawn " + (finalHasBabyVariant ? "baby" : "young") + " " + finalAnimalType.getId() + " - spawn returned null");
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
     * @param animalId The animal's UUID
     * @param animalType The type of animal
     * @param scale The target scale (0.4 for baby, 0.7 for juvenile, 1.0 for adult)
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
                    Class<?> modelCompClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
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

                    if (getComponent == null) return;

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

                    // Get current model from ModelComponent
                    java.lang.reflect.Field modelField = modelCompClass.getDeclaredField("model");
                    modelField.setAccessible(true);
                    Object currentModel = modelField.get(modelComp);

                    if (currentModel == null) {
                        logWarning("Entity has no model - cannot scale");
                        return;
                    }

                    // Get the modelAssetId from current model
                    java.lang.reflect.Field assetIdField = currentModel.getClass().getDeclaredField("modelAssetId");
                    assetIdField.setAccessible(true);
                    String modelAssetId = (String) assetIdField.get(currentModel);

                    // Get the ModelAsset
                    Class<?> modelAssetClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
                    Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
                    Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap, modelAssetId);

                    if (modelAsset == null) {
                        logWarning("ModelAsset not found: " + modelAssetId);
                        return;
                    }

                    // Create new scaled model
                    Class<?> modelClass = Class.forName("com.hypixel.hytale.server.core.asset.type.model.config.Model");
                    java.lang.reflect.Method createScaledModel = modelClass.getMethod("createScaledModel", modelAssetClass, float.class);
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

                    // Set the new model on the ModelComponent
                    modelField.set(modelComp, modelToSet);

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
                            java.lang.reflect.Method setChanged = modelComp.getClass().getMethod("setChanged", boolean.class);
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

                    getLogger().atInfo().log("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) +
                        " grew to scale " + String.format("%.1f", targetScale));

                } catch (Exception e) {
                    Throwable cause = e;
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (cause == null) cause = e;
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
     * Transform a baby animal into an adult by removing the baby and spawning an adult NPC.
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

                    Class<?> transformCompClass = Class.forName("com.hypixel.hytale.server.core.modules.entity.component.TransformComponent");
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

                    if (getComponent == null) return;

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
                                (proxy, method, args) -> null
                            );

                            Object result = m.invoke(npcPlugin, store, roleIndex, babyPosition, rotation, null, noOpCallback);
                            if (result != null) {
                                getLogger().atInfo().log("[Lait:AnimalBreeding] " + capitalize(animalType.getId()) + " grew into an adult at " +
                                    String.format("%.0f, %.0f, %.0f", babyPosition.getX(), babyPosition.getY(), babyPosition.getZ()));
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
                        if (cause == null) cause = e;
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
            tickScheduler.shutdown();
            try {
                if (!tickScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    tickScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                tickScheduler.shutdownNow();
            }
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

    public GrowthManager getGrowthManager() {
        return growthManager;
    }

    // ===========================================
    // COMMANDS
    // ===========================================

    /**
     * Help command.
     * Usage: /laitsbreeding
     */
    public static class BreedingHelpCommand extends AbstractCommand {

        public BreedingHelpCommand() {
            super("laitsbreeding", "Show Animal Breeding help");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            ctx.sendMessage(Message.raw("=== Lait's Animal Breeding ===").color("#FF9900"));
            ctx.sendMessage(Message.raw("Version: ").color("#AAAAAA")
                .insert(Message.raw(LaitsBreedingPlugin.VERSION).color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Feed animals their favorite food to put them in love mode.").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("Two animals in love will breed and produce a baby!").color("#AAAAAA"));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Breeding Foods:").color("#FFFF55"));
            ctx.sendMessage(Message.raw("  Cow - ").color("#AAAAAA").insert(Message.raw("Cauliflower").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Pig - ").color("#AAAAAA").insert(Message.raw("Brown Mushroom").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Chicken - ").color("#AAAAAA").insert(Message.raw("Corn").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Sheep - ").color("#AAAAAA").insert(Message.raw("Lettuce").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Goat - ").color("#AAAAAA").insert(Message.raw("Apple").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Horse - ").color("#AAAAAA").insert(Message.raw("Carrot").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Camel - ").color("#AAAAAA").insert(Message.raw("Wheat").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Ram - ").color("#AAAAAA").insert(Message.raw("Apple").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Turkey - ").color("#AAAAAA").insert(Message.raw("Corn").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Boar - ").color("#AAAAAA").insert(Message.raw("Red Mushroom").color("#FFFFFF")));
            ctx.sendMessage(Message.raw("  Rabbit - ").color("#AAAAAA").insert(Message.raw("Carrot").color("#FFFFFF")));
            ctx.sendMessage(Message.raw(""));
            ctx.sendMessage(Message.raw("Commands:").color("#FFFF55"));
            ctx.sendMessage(Message.raw("  /laitsbreeding ").color("#FFFFFF").insert(Message.raw("- This help").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  /breedstatus ").color("#FFFFFF").insert(Message.raw("- View breeding stats").color("#AAAAAA")));
            ctx.sendMessage(Message.raw("  /breedconfig ").color("#FFFFFF").insert(Message.raw("- Configure breeding").color("#AAAAAA")));

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Show breeding system status.
     * Usage: /breedstatus
     */
    public static class BreedingStatusCommand extends AbstractCommand {

        public BreedingStatusCommand() {
            super("breedstatus", "Show breeding system status");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
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
                plugin.getLogger().atInfo().log("[Lait:AnimalBreeding] Verbose logging " + (newState ? "enabled" : "disabled"));
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

    /**
     * Toggle development debug mode - broadcasts debug messages to all players in-game.
     * Usage: /breeddev
     */
    public static class BreedingDevCommand extends AbstractCommand {

        public BreedingDevCommand() {
            super("breeddev", "Toggle in-game development debug messages");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            boolean newState = !LaitsBreedingPlugin.isDevMode();
            LaitsBreedingPlugin.setDevMode(newState);

            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo().log("[Lait:AnimalBreeding] Dev mode " + (newState ? "enabled" : "disabled"));
            }

            String statusColor = newState ? "#55FF55" : "#FF5555";
            String statusText = newState ? "ENABLED" : "DISABLED";
            ctx.sendMessage(Message.raw("Dev mode ").color("#AAAAAA")
                .insert(Message.raw(statusText).color(statusColor)));
            if (newState) {
                ctx.sendMessage(Message.raw("Debug messages will appear in-game for all players.").color("#FFAA00"));
                ctx.sendMessage(Message.raw("(Also logged to server console with [DEV] prefix)").color("#AAAAAA"));
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
        "Feed",                                    // 0: Simple text (current default)
        "Press [F] to Feed",                       // 1: Literal with [F]
        "Press [Use] to Feed",                     // 2: With interaction type name
        "[F] Feed",                                // 3: Key prefix
        "§ePress §f[F]§e to Feed",                 // 4: With color codes
        "server.interactionHints.generic",         // 5: Localization key (may not work)
        "Press [{key}] to Feed",                   // 6: Raw format placeholder
        "@server.interactionHints.generic",        // 7: Try @ prefix
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

    // ===========================================
    // CONFIG COMMAND
    // ===========================================

    /**
     * Manage breeding configuration at runtime.
     * Usage:
     *   /breedconfig                            - Show current config summary
     *   /breedconfig list [category]            - List animals (optionally by category)
     *   /breedconfig info <animal>              - Show detailed info for an animal
     *   /breedconfig preset list                - List available presets
     *   /breedconfig preset apply <name>        - Apply a preset
     *   /breedconfig reload                     - Reload from file
     *   /breedconfig save                       - Save current config
     *   /breedconfig enable <animal|category|ALL> - Enable breeding
     *   /breedconfig disable <animal|category|ALL> - Disable breeding
     *   /breedconfig set <animal> food <item>   - Set primary breeding food
     *   /breedconfig set <animal> growth <min>  - Set growth time
     *   /breedconfig set <animal> cooldown <min> - Set breed cooldown
     *   /breedconfig addfood <animal> <item>    - Add breeding food
     *   /breedconfig removefood <animal> <item> - Remove breeding food
     */
    /**
     * Main /breedconfig command with proper sub-commands.
     * Uses the Hytale command API with typed arguments and tab completion.
     */
    public static class BreedingConfigCommand extends AbstractCommand {

        public BreedingConfigCommand() {
            super("breedconfig", "Manage breeding plugin configuration");

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
            ctx.sendMessage(Message.raw("=== Breeding Config ===").color("#FF9900"));  // Hex color
            ctx.sendMessage(Message.raw("Active Preset: ").color("#AAAAAA")
                .insert(Message.raw(config.getActivePreset()).color("#FFFFFF")));

            // Count by category
            java.util.Map<AnimalType.Category, int[]> counts = new java.util.EnumMap<>(AnimalType.Category.class);
            for (AnimalType.Category cat : AnimalType.Category.values()) {
                counts.put(cat, new int[]{0, 0}); // [enabled, total]
            }
            for (AnimalType type : AnimalType.values()) {
                int[] c = counts.get(type.getCategory());
                c[1]++;
                if (config.isAnimalEnabled(type)) c[0]++;
            }

            ctx.sendMessage(Message.raw("Categories:").color("#AAAAAA"));
            for (AnimalType.Category cat : AnimalType.Category.values()) {
                int[] c = counts.get(cat);
                String hexColor = c[0] > 0 ? "#55FF55" : "#AAAAAA";  // Green if enabled, gray if not
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
                    if (filterCat != null && type.getCategory() != filterCat) continue;

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
            private final RequiredArg<AnimalType> animalArg;

            public InfoSubCommand() {
                super("info", "Show detailed information for an animal");
                animalArg = withRequiredArg("animal", "Animal type",
                    ArgTypes.forEnum("animal", AnimalType.class));
            }

            @Override
            protected CompletableFuture<Void> execute(CommandContext ctx) {
                ConfigManager config = getConfig();
                if (config == null) {
                    ctx.sendMessage(Message.raw("Plugin not initialized!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                AnimalType type = ctx.get(animalArg);
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
            if (target.equals("ALL")) {
                for (AnimalType type : AnimalType.values()) {
                    config.setAnimalEnabled(type, enable);
                }
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for ALL animals.").color("#AAAAAA")));
                return;
            }

            // Check if it's a category
            try {
                AnimalType.Category cat = AnimalType.Category.valueOf(target);
                int count = 0;
                for (AnimalType type : AnimalType.values()) {
                    if (type.getCategory() == cat) {
                        config.setAnimalEnabled(type, enable);
                        count++;
                    }
                }
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for " + count + " " + cat.name() + " animals.").color("#AAAAAA")));
                return;
            } catch (IllegalArgumentException ignored) {}

            // Try as individual animal
            try {
                AnimalType type = AnimalType.valueOf(target);
                config.setAnimalEnabled(type, enable);
                ctx.sendMessage(Message.raw(statusWord).color(statusColor)
                    .insert(Message.raw(" breeding for ").color("#AAAAAA"))
                    .insert(Message.raw(type.name()).color("#FFFFFF")));
            } catch (IllegalArgumentException e) {
                ctx.sendMessage(Message.raw("Unknown animal or category: ").color("#FF5555")
                    .insert(Message.raw(target).color("#FFFFFF")));
                ctx.sendMessage(Message.raw("Animals: COW, PIG, CHICKEN, WOLF, etc.").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("Categories: ").color("#AAAAAA")
                    .insert(Message.raw(java.util.Arrays.toString(AnimalType.Category.values())).color("#FFFFFF")));
            }
        }

        /** /breedconfig set <animal> <property> <value> */
        public static class SetSubCommand extends AbstractCommand {
            private final RequiredArg<AnimalType> animalArg;
            private final RequiredArg<String> propertyArg;
            private final RequiredArg<String> valueArg;

            public SetSubCommand() {
                super("set", "Set animal property (food, growth, cooldown)");
                animalArg = withRequiredArg("animal", "Animal type",
                    ArgTypes.forEnum("animal", AnimalType.class));
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

                AnimalType type = ctx.get(animalArg);
                String property = ctx.get(propertyArg).toLowerCase();
                String value = ctx.get(valueArg);

                switch (property) {
                    case "food":
                        // Resolve food shortcut
                        String resolvedFood = resolveFoodShortcut(value);
                        config.setBreedingFood(type, resolvedFood);
                        ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                            .insert(Message.raw(type.name()).color("#FFFFFF"))
                            .insert(Message.raw(" primary food to: ").color("#55FF55"))
                            .insert(Message.raw(resolvedFood).color("#FFFFFF")));
                        ctx.sendMessage(Message.raw("(This replaces all foods. Use ").color("#AAAAAA")
                            .insert(Message.raw("/breedconfig addfood").color("#FFFFFF"))
                            .insert(Message.raw(" to add more.)").color("#AAAAAA")));
                        break;

                    case "growth":
                        try {
                            double minutes = Double.parseDouble(value);
                            config.setGrowthTime(type, minutes);
                            ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                .insert(Message.raw(type.name()).color("#FFFFFF"))
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
                            config.setBreedingCooldown(type, minutes);
                            ctx.sendMessage(Message.raw("Set ").color("#55FF55")
                                .insert(Message.raw(type.name()).color("#FFFFFF"))
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
            private final RequiredArg<AnimalType> animalArg;
            private final RequiredArg<String> foodArg;

            public AddFoodSubCommand() {
                super("addfood", "Add a breeding food to an animal");
                animalArg = withRequiredArg("animal", "Animal type",
                    ArgTypes.forEnum("animal", AnimalType.class));
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

                AnimalType type = ctx.get(animalArg);
                String foodInput = ctx.get(foodArg);

                // Null checks for arguments
                if (type == null) {
                    ctx.sendMessage(Message.raw("Invalid animal type!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }
                if (foodInput == null || foodInput.isEmpty()) {
                    ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Resolve food shortcut
                String food = resolveFoodShortcut(foodInput);

                config.addBreedingFood(type, food);
                ctx.sendMessage(Message.raw("Added ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" to " + type.name() + " breeding foods.").color("#55FF55")));
                ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                    .insert(Message.raw(String.join(", ", config.getBreedingFoods(type))).color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig removefood <animal> <item> */
        public static class RemoveFoodSubCommand extends AbstractCommand {
            private final RequiredArg<AnimalType> animalArg;
            private final RequiredArg<String> foodArg;

            public RemoveFoodSubCommand() {
                super("removefood", "Remove a breeding food from an animal");
                animalArg = withRequiredArg("animal", "Animal type",
                    ArgTypes.forEnum("animal", AnimalType.class));
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

                AnimalType type = ctx.get(animalArg);
                String foodInput = ctx.get(foodArg);

                // Null checks for arguments
                if (type == null) {
                    ctx.sendMessage(Message.raw("Invalid animal type!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }
                if (foodInput == null || foodInput.isEmpty()) {
                    ctx.sendMessage(Message.raw("Food item is required!").color("#FF5555"));
                    return CompletableFuture.completedFuture(null);
                }

                // Resolve food shortcut
                String food = resolveFoodShortcut(foodInput);

                java.util.List<String> foods = config.getBreedingFoods(type);
                if (foods.size() <= 1) {
                    ctx.sendMessage(Message.raw("Cannot remove last food. Use ").color("#FF5555")
                        .insert(Message.raw("/breedconfig set food").color("#FFFFFF"))
                        .insert(Message.raw(" to replace instead.").color("#FF5555")));
                    return CompletableFuture.completedFuture(null);
                }

                config.removeBreedingFood(type, food);
                ctx.sendMessage(Message.raw("Removed ").color("#55FF55")
                    .insert(Message.raw(food).color("#FFFFFF"))
                    .insert(Message.raw(" from " + type.name() + " breeding foods.").color("#55FF55")));
                ctx.sendMessage(Message.raw("Foods: ").color("#AAAAAA")
                    .insert(Message.raw(String.join(", ", config.getBreedingFoods(type))).color("#FFFFFF")));
                return CompletableFuture.completedFuture(null);
            }
        }

        /** /breedconfig preset - with list, apply, and save sub-commands */
        public static class PresetSubCommand extends AbstractCommand {
            public PresetSubCommand() {
                super("preset", "Manage configuration presets");
                addSubCommand(new PresetListSubCommand());
                addSubCommand(new PresetApplySubCommand());
                addSubCommand(new PresetSaveSubCommand());
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
                    String desc = preset.equals("default") ? "Streamlined, livestock only"
                        : preset.equals("lait_curated") ? "Organic experience, multiple foods"
                        : preset.equals("zoo") ? "All real animals, no mythic creatures"
                        : "(custom)";
                    Message line = Message.raw(isCurrent ? "* " : "  ").color(isCurrent ? "#55FF55" : "#AAAAAA")
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

        // ==================== Food Shortcut Resolver ====================

        /**
         * Food shortcut mappings for easier command usage.
         * Maps short names like "Carrot" to full item IDs like "Plant_Crop_Carrot_Item".
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
            FOOD_SHORTCUTS.put("cactus_fruit", "Plant_Crop_Cactus_Fruit");

            // Meat (raw)
            FOOD_SHORTCUTS.put("wildmeat", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("wildmeat_raw", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("meat", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("meat_raw", "Food_Wildmeat_Raw");
            FOOD_SHORTCUTS.put("raw_meat", "Food_Wildmeat_Raw");
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

            // Other
            FOOD_SHORTCUTS.put("insect", "Food_Insect");
            FOOD_SHORTCUTS.put("bug", "Food_Insect");
        }

        /**
         * Resolve a food shortcut to its full item ID.
         * If the input doesn't match any shortcut, returns the input unchanged.
         */
        private static String resolveFoodShortcut(String input) {
            if (input == null) return null;
            String resolved = FOOD_SHORTCUTS.get(input.toLowerCase());
            return resolved != null ? resolved : input;
        }
    }
}
