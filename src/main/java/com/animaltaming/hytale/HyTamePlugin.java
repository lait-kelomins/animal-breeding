package com.animaltaming.hytale;

import com.animaltaming.AnimalTamingPlugin;
import com.animaltaming.api.model.TamingConfig;
import com.animaltaming.api.model.TamingProgress;
import com.animaltaming.api.model.TamingState;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * HyTame Plugin - Hytale integration layer.
 *
 * This is a thin JavaPlugin wrapper that translates Hytale events
 * into method calls on HytaleModEntryPoint. All game logic remains
 * in the framework-agnostic classes.
 *
 * Responsibilities:
 * - Register event listeners (EntityRemoveEvent, AddWorldEvent, PlayerInteractEvent)
 * - Schedule tick updates via TaskRegistry
 * - Initialize HytaleModEntryPoint when world is ready
 * - Delegate all events to HytaleModEntryPoint
 *
 * Entity Spawn Detection:
 * Hytale does not provide a general EntitySpawnEvent. Tameable animals are registered
 * in two ways:
 * 1. On first player interaction - when a player interacts with an unregistered entity,
 *    it's automatically registered if it's a tameable species
 * 2. Manual registration - call entryPoint.onEntitySpawn(entity) from external systems
 *
 * For proactive entity detection, consider implementing a RefSystem<EntityStore> that
 * listens for onEntityAdded events via the ECS system.
 */
public class HyTamePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private HytaleModEntryPoint entryPoint;
    private ScheduledFuture<?> tickTask;

    /**
     * Constructor required by Hytale plugin system.
     */
    public HyTamePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("HyTame: Setting up event handlers...");

        // World lifecycle - initialize entryPoint when world is ready
        // Events are keyed by world name (String), so use registerGlobal() for all worlds
        getEventRegistry().registerGlobal(AddWorldEvent.class, this::onWorldAdd);

        // Entity lifecycle - cleanup on despawn/death
        getEventRegistry().registerGlobal(EntityRemoveEvent.class, this::onEntityRemove);

        // Player interactions - feeding, petting, mode toggle
        getEventRegistry().registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);

        LOGGER.atInfo().log("HyTame: Event handlers registered");
    }

    @Override
    protected void start() {
        LOGGER.atInfo().log("HyTame: Starting plugin...");

        // Create entry point (not yet initialized - needs world)
        entryPoint = new HytaleModEntryPoint(getDataDirectory());

        // Register tick task - runs every server tick (~30 TPS = 33ms per tick)
        // This drives the entire taming system: calming, feeding, behavior, etc.
        tickTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
            () -> {
                if (entryPoint != null && entryPoint.isInitialized()) {
                    entryPoint.onTick();
                }
            },
            0,              // initial delay
            33,             // period in milliseconds (~30 TPS)
            TimeUnit.MILLISECONDS
        );
        // Note: TaskRegistry.registerTask expects ScheduledFuture<Void>, but scheduleAtFixedRate
        // returns ScheduledFuture<?>. We keep the reference for cancellation in shutdown().

        LOGGER.atInfo().log("HyTame: Plugin started, waiting for world initialization...");
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("HyTame: Shutting down...");

        // Cancel the tick task
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }

        if (entryPoint != null) {
            entryPoint.shutdown();
            entryPoint = null;
        }

        LOGGER.atInfo().log("HyTame: Shutdown complete");
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Called when a world is added to the server.
     * Initializes the entry point with the first available world.
     */
    private void onWorldAdd(AddWorldEvent event) {
        if (entryPoint != null && !entryPoint.isInitialized()) {
            entryPoint.initialize(event.getWorld());
            LOGGER.atInfo().log("HyTame: Initialized with world");
        }
    }

    /**
     * Called when an entity is removed (death, despawn, etc.).
     * Delegates to entry point for cleanup.
     */
    private void onEntityRemove(EntityRemoveEvent event) {
        if (entryPoint != null && entryPoint.isInitialized()) {
            Entity entity = event.getEntity();
            if (entity != null) {
                entryPoint.onEntityDespawn(entity);
            }
        }
    }

    /**
     * Called when a player interacts with something.
     * Delegates to entry point for taming interactions.
     *
     * Also handles lazy entity registration - if an interacted entity
     * hasn't been registered yet, it's registered on first interaction.
     *
     * IMPORTANT: This method cancels the event when a valid feeding interaction
     * is detected to prevent the player from eating the food themselves.
     */
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (entryPoint == null || !entryPoint.isInitialized()) {
            return;
        }

        Entity player = event.getPlayer();
        Entity target = event.getTargetEntity();

        // Only process entity interactions (not block interactions)
        if (player == null || target == null) {
            return;
        }

        // Lazy registration: register entity on first interaction
        // This compensates for the lack of EntitySpawnEvent
        entryPoint.onEntitySpawn(target);

        // Get entity IDs for further checks
        HytaleEntityAdapter adapter = entryPoint.getEntityAdapter();
        Optional<Long> playerIdOpt = adapter.getEntityId(player);
        Optional<Long> targetIdOpt = adapter.getEntityId(target);

        if (playerIdOpt.isEmpty() || targetIdOpt.isEmpty()) {
            return;
        }

        long playerId = playerIdOpt.get();
        long targetId = targetIdOpt.get();

        // Check if this is a feeding interaction that should be intercepted
        // Cancel the event to prevent player from eating the food themselves
        if (shouldInterceptForFeeding(playerId, targetId)) {
            event.setCancelled(true);
            LOGGER.atInfo().log("HyTame: Intercepted feeding interaction for entity %d", targetId);
        }

        String actionType = event.getActionType() != null
                ? event.getActionType().name().toLowerCase()
                : "interact";
        entryPoint.onPlayerInteract(player, target, actionType);
    }

    /**
     * Check if this interaction should be intercepted as a feeding attempt.
     * Returns true if:
     * 1. Target has taming progress in CALMED or BONDING_FEED state
     * 2. Player is holding food accepted by that species
     *
     * When this returns true, the event should be cancelled to prevent
     * the player from eating the food themselves.
     */
    private boolean shouldInterceptForFeeding(long playerId, long targetId) {
        AnimalTamingPlugin plugin = entryPoint.getPlugin();
        HytaleSystemContext context = entryPoint.getSystemContext();

        // Check taming progress via TamingService
        Optional<TamingProgress> progressOpt = plugin.getTamingService()
                .getTamingProgress(targetId);

        if (progressOpt.isEmpty()) {
            return false;
        }

        TamingProgress progress = progressOpt.get();
        TamingState state = progress.state();

        // Only intercept in CALMED or BONDING_FEED states
        if (state != TamingState.CALMED && state != TamingState.BONDING_FEED) {
            return false;
        }

        // Check if player is holding accepted food
        Optional<String> heldItem = context.getHeldItemId(playerId);
        if (heldItem.isEmpty()) {
            return false;
        }

        // Check if the held item is accepted food for this species
        Optional<TamingConfig> configOpt = plugin.getConfigRegistry()
                .get(progress.speciesId());
        if (configOpt.isEmpty()) {
            return false;
        }

        return configOpt.get().acceptsFood(heldItem.get());
    }

    // ==================== ACCESSORS ====================

    /**
     * Get the entry point instance.
     * May be null before start() or after shutdown().
     */
    public HytaleModEntryPoint getEntryPoint() {
        return entryPoint;
    }
}
