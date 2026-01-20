package com.animaltaming.hytale;

import com.animaltaming.AnimalTamingPlugin;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.World;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Hytale mod entry point that wires the Animal Taming Plugin
 * to the real Hytale game systems.
 *
 * Usage from Hytale mod loader:
 * 1. Create instance: new HytaleModEntryPoint(pluginFolder)
 * 2. Initialize with world: entryPoint.initialize(world)
 * 3. Call onTick() each server tick
 * 4. Register entities as they spawn: entryPoint.onEntitySpawn(entity)
 * 5. Unregister on despawn: entryPoint.onEntityDespawn(entity)
 *
 * Note: The exact integration depends on Hytale's mod loading API.
 * This class provides the bridge between Hytale events and plugin logic.
 */
public class HytaleModEntryPoint {

    private final Path pluginFolder;
    private final HytaleEntityAdapter entityAdapter;
    private final HytaleSystemContext systemContext;
    private final AnimalTamingPlugin plugin;

    private boolean initialized = false;
    private long lastTickTime = System.nanoTime();

    /**
     * Create the mod entry point.
     *
     * @param pluginFolder path to the plugin's data folder
     */
    public HytaleModEntryPoint(Path pluginFolder) {
        this.pluginFolder = Objects.requireNonNull(pluginFolder, "pluginFolder required");

        // Create the adapter and context
        this.entityAdapter = new HytaleEntityAdapter();
        this.systemContext = new HytaleSystemContext(entityAdapter);

        // Create the plugin with constructor injection
        this.plugin = new AnimalTamingPlugin(pluginFolder);
    }

    /**
     * Initialize the mod with the active world.
     * Call this when the world is ready.
     *
     * @param world the Hytale world
     */
    public void initialize(World world) {
        Objects.requireNonNull(world, "world required");

        systemContext.setWorld(world);
        plugin.onEnable();
        initialized = true;

        System.out.println("[AnimalTaming] Mod initialized with world");
    }

    /**
     * Called each server tick.
     * Converts tick timing to deltaTime and updates the plugin.
     */
    public void onTick() {
        if (!initialized) {
            return;
        }

        // Calculate delta time
        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastTickTime) / 1_000_000_000.0f;
        lastTickTime = currentTime;

        // Update the system context's internal tick counter
        systemContext.tick();

        // Update the plugin
        plugin.onTick(systemContext, deltaTime);
    }

    /**
     * Called when an entity spawns in the world.
     * Registers the entity for tracking.
     *
     * @param entity the spawned entity
     */
    public void onEntitySpawn(Entity entity) {
        if (entity == null) {
            return;
        }

        long entityId = entityAdapter.registerEntity(entity);

        // Check if this is a tameable animal and register it
        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
            String speciesId = getSpeciesId(entity);
            if (speciesId != null && plugin.getConfigRegistry().contains(speciesId)) {
                systemContext.registerTameableAnimal(entityId, speciesId);
            }
        }
    }

    /**
     * Called when an entity despawns from the world.
     * Unregisters the entity from tracking.
     *
     * @param entity the despawned entity
     */
    public void onEntityDespawn(Entity entity) {
        if (entity == null) {
            return;
        }

        entityAdapter.getEntityId(entity).ifPresent(entityId -> {
            systemContext.unregisterTameableAnimal(entityId);
            entityAdapter.unregisterEntity(entity);
        });
    }

    /**
     * Called when a player interacts with an entity.
     * Queues the interaction for processing.
     *
     * @param player the player entity
     * @param target the target entity
     * @param interactionType the type of interaction (e.g., "right_click")
     */
    public void onPlayerInteract(Entity player, Entity target, String interactionType) {
        if (player == null || target == null) {
            return;
        }

        entityAdapter.getEntityId(player).ifPresent(playerId ->
            entityAdapter.getEntityId(target).ifPresent(targetId ->
                systemContext.queueInteraction(playerId, targetId, interactionType)
            )
        );
    }

    /**
     * Shutdown the mod.
     * Call this when the server is stopping.
     */
    public void shutdown() {
        if (initialized) {
            plugin.onDisable();
            entityAdapter.clear();
            initialized = false;
            System.out.println("[AnimalTaming] Mod shutdown complete");
        }
    }

    /**
     * Get the species ID for an entity.
     * Returns the entity type identifier (e.g., "hytale:horse", "hytale:wolf").
     *
     * @param entity the entity
     * @return the species ID or null if not determinable
     */
    private String getSpeciesId(Entity entity) {
        if (entity == null) {
            return null;
        }
        // Use EntityModule to get the identifier for this entity's class
        return EntityModule.get().getIdentifier(entity.getClass());
    }

    // ==================== ACCESSORS ====================

    /**
     * Get the plugin instance.
     */
    public AnimalTamingPlugin getPlugin() {
        return plugin;
    }

    /**
     * Get the entity adapter.
     */
    public HytaleEntityAdapter getEntityAdapter() {
        return entityAdapter;
    }

    /**
     * Get the system context.
     */
    public HytaleSystemContext getSystemContext() {
        return systemContext;
    }

    /**
     * Check if the mod is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
}
