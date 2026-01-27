package com.laits.breeding.managers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.GrowthStage;
import com.laits.breeding.models.TamedAnimalData;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.NameplateUtil;
import com.tameableanimals.tame.HyTameComponent;

import it.unimi.dsi.fastutil.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages respawning of tamed animals.
 * Handles:
 * - Updating tamed animal positions
 * - Checking for despawned tamed animals
 * - Respawning tamed animals when players are nearby
 */
public class RespawnManager {

    // Dependencies (injected)
    private TamingManager tamingManager;
    private BreedingManager breedingManager;
    private Supplier<ComponentType<EntityStore, HyTameComponent>> hyTameTypeSupplier;
    private Function<Ref<EntityStore>, Vector3d> positionGetter;

    // Logging
    private boolean verboseLogging = false;
    private Consumer<String> logger;
    private Consumer<String> warningLogger;

    // Respawn radius in blocks
    private static final double RESPAWN_RADIUS = 64.0;

    public RespawnManager() {
    }

    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================

    public void setTamingManager(TamingManager tamingManager) {
        this.tamingManager = tamingManager;
    }

    public void setBreedingManager(BreedingManager breedingManager) {
        this.breedingManager = breedingManager;
    }

    public void setHyTameTypeSupplier(Supplier<ComponentType<EntityStore, HyTameComponent>> supplier) {
        this.hyTameTypeSupplier = supplier;
    }

    public void setPositionGetter(Function<Ref<EntityStore>, Vector3d> getter) {
        this.positionGetter = getter;
    }

    // ========================================================================
    // LOGGING CONFIGURATION
    // ========================================================================

    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public void setWarningLogger(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    private void logVerbose(String message) {
        if (verboseLogging && logger != null) {
            logger.accept(message);
        }
    }

    private void logWarning(String message) {
        if (warningLogger != null) {
            warningLogger.accept(message);
        }
    }

    // ========================================================================
    // POSITION UPDATES
    // ========================================================================

    /**
     * Update positions for all tamed animals.
     * Called periodically to keep saved positions current.
     * Groups animals by world for multi-world support.
     */
    public void updateTamedAnimalPositions() {
        if (tamingManager == null)
            return;

        // Group animals by worldId
        Map<String, java.util.List<TamedAnimalData>> animalsByWorld = new HashMap<>();
        for (TamedAnimalData data : tamingManager.getAllTamedAnimals()) {
            if (data == null || data.isDespawned())
                continue;
            if (data.getEntityRef() == null)
                continue;

            String worldId = data.getWorldId();
            if (worldId == null) worldId = "default";
            animalsByWorld.computeIfAbsent(worldId, k -> new java.util.ArrayList<>()).add(data);
        }

        final int[] totalUpdated = {0};

        // Process each world's animals on that world's thread
        for (Map.Entry<String, java.util.List<TamedAnimalData>> entry : animalsByWorld.entrySet()) {
            String worldId = entry.getKey();
            java.util.List<TamedAnimalData> animals = entry.getValue();

            World world = "default".equals(worldId) ?
                Universe.get().getDefaultWorld() :
                Universe.get().getWorld(worldId);
            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }
            if (world == null) continue;

            final java.util.List<TamedAnimalData> finalAnimals = animals;
            world.execute(() -> {
                try {
                    int updated = 0;
                    for (TamedAnimalData data : finalAnimals) {
                        Object refObj = data.getEntityRef();
                        if (refObj == null) continue;

                        @SuppressWarnings("unchecked")
                        Ref<EntityStore> entityRef = (Ref<EntityStore>) refObj;

                        try {
                            Vector3d pos = positionGetter != null ? positionGetter.apply(entityRef) : null;
                            if (pos != null) {
                                // Update position if it changed significantly (> 0.5 blocks)
                                double dx = pos.getX() - data.getLastX();
                                double dy = pos.getY() - data.getLastY();
                                double dz = pos.getZ() - data.getLastZ();
                                double distSq = dx * dx + dy * dy + dz * dz;

                                if (distSq > 0.25) { // > 0.5 block movement
                                    data.setLastPosition(pos.getX(), pos.getY(), pos.getZ());
                                    updated++;
                                }
                            }
                        } catch (Exception e) {
                            // Entity may have despawned - mark it
                            data.setDespawned(true);
                            data.setEntityRef(null);
                        }
                    }

                    if (updated > 0) {
                        synchronized (totalUpdated) {
                            totalUpdated[0] += updated;
                        }
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        }

        // Save after all worlds processed (with small delay to allow world.execute to complete)
        if (!animalsByWorld.isEmpty()) {
            // Schedule save slightly later
            World defaultWorld = Universe.get().getDefaultWorld();
            if (defaultWorld != null) {
                defaultWorld.execute(() -> {
                    if (totalUpdated[0] > 0) {
                        tamingManager.saveImmediately();
                        logVerbose("Updated positions for " + totalUpdated[0] + " tamed animals across all worlds");
                    }
                });
            }
        }
    }

    // ========================================================================
    // RESPAWN CHECKING
    // ========================================================================

    /**
     * Check for despawned tamed animals near players and respawn them.
     * Called every 5 seconds by the tick scheduler.
     *
     * Logic for each JSON entry:
     * - If isDead: do nothing (dead animals don't respawn)
     * - If entity exists (valid ref): track it, ensure not marked despawned
     * - If entity doesn't exist: mark as despawned for potential respawn
     *
     * Then for each player: respawn nearby despawned animals.
     */
    public void checkAndRespawnTamedAnimals() {
        if (tamingManager == null) {
            logVerbose("[RespawnCheck] tamingManager is null");
            return;
        }

        Collection<TamedAnimalData> allAnimals = tamingManager.getAllTamedAnimals();
        logVerbose("[RespawnCheck] Tamed animals count: " + allAnimals.size());

        if (allAnimals.isEmpty()) {
            return;
        }

        // Group animals by worldId for multi-world support
        Map<String, java.util.List<TamedAnimalData>> animalsByWorld = new HashMap<>();
        for (TamedAnimalData data : allAnimals) {
            String worldId = data.getWorldId();
            if (worldId == null || worldId.isEmpty()) {
                worldId = "default";
            }
            animalsByWorld.computeIfAbsent(worldId, k -> new java.util.ArrayList<>()).add(data);
        }

        // Process each world that has tamed animals
        for (Map.Entry<String, java.util.List<TamedAnimalData>> entry : animalsByWorld.entrySet()) {
            String worldName = entry.getKey();
            java.util.List<TamedAnimalData> worldAnimals = entry.getValue();

            World world = "default".equals(worldName) ? Universe.get().getDefaultWorld() : Universe.get().getWorld(worldName);
            if (world == null) {
                logVerbose("[RespawnCheck] World not found: " + worldName + ", trying default");
                world = Universe.get().getDefaultWorld();
            }
            if (world == null) {
                logVerbose("[RespawnCheck] No world available for: " + worldName);
                continue;
            }

            final World finalWorld = world;
            final java.util.List<TamedAnimalData> finalWorldAnimals = worldAnimals;
            final String finalWorldName = worldName;

            // Must run on world thread to access entity components
            finalWorld.execute(() -> {
                try {
                    Store<EntityStore> store = finalWorld.getEntityStore().getStore();

                    // Phase 0: Scan all entities for HyTameComponent and build hytameId -> entityRef map
                    Map<UUID, Ref<EntityStore>> entitiesByHytameId = new HashMap<>();
                    ComponentType<EntityStore, HyTameComponent> hyTameType = hyTameTypeSupplier != null
                            ? hyTameTypeSupplier.get()
                            : null;

                    if (hyTameType != null) {
                        store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                            int chunkSize = chunk.size();
                            for (int i = 0; i < chunkSize; i++) {
                                try {
                                    HyTameComponent hyTameComp = chunk.getComponent(i, hyTameType);
                                    if (hyTameComp != null && hyTameComp.isTamed() && hyTameComp.getHytameId() != null) {
                                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                                        if (ref != null && ref.isValid()) {
                                            NPCEntity npc = store.getComponent(ref, EcsReflectionUtil.NPC_TYPE);
                                            var despawn = store.getComponent(ref, EcsReflectionUtil.DESPAWN_TYPE);
                                            if (npc != null && !npc.isDespawning() && despawn == null) {
                                                entitiesByHytameId.put(hyTameComp.getHytameId(), ref);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Skip invalid entries
                                }
                            }
                        });
                        logVerbose("[RespawnCheck] World " + finalWorldName + ": Found " + entitiesByHytameId.size() + " entities with HyTameComponent");
                    }

                    // Phase 1: Update entity status for animals in this world
                    for (TamedAnimalData tamedData : finalWorldAnimals) {
                        if (tamedData.isDead()) {
                            continue;
                        }

                        boolean entityExists = false;
                        Ref<EntityStore> tamedRef = tamedData.getEntityRef();
                        UUID hytameId = tamedData.getHytameId();

                        // First check: stored entityRef
                        if (tamedRef != null && tamedRef.isValid()) {
                            try {
                                NPCEntity npcEntity = store.getComponent(tamedRef, EcsReflectionUtil.NPC_TYPE);
                                var despawnComp = store.getComponent(tamedRef, EcsReflectionUtil.DESPAWN_TYPE);

                                if (npcEntity != null && !npcEntity.isDespawning() && despawnComp == null) {
                                    entityExists = true;
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                // Entity ref became stale between validity check and access - treat as not existing
                            }
                        }

                        // Second check: scan by HytameId
                        if (!entityExists && hytameId != null) {
                            Ref<EntityStore> foundRef = entitiesByHytameId.get(hytameId);
                            if (foundRef != null && foundRef.isValid()) {
                                try {
                                    tamedData.setEntityRef(foundRef);
                                    entityExists = true;
                                    logVerbose("[RespawnCheck] Found entity by HytameId: " + tamedData.getCustomName());

                                    var uuidComp = store.getComponent(foundRef, EcsReflectionUtil.UUID_TYPE);
                                    if (uuidComp != null) {
                                        UUID newUuid = uuidComp.getUuid();
                                        if (newUuid != null && !newUuid.equals(tamedData.getAnimalUuid())) {
                                            tamingManager.markRespawned(tamedData.getAnimalUuid(), newUuid, foundRef);
                                        }
                                    }
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    // Entity ref became stale - treat as not existing
                                    entityExists = false;
                                }
                            }
                        }

                        if (entityExists) {
                            if (tamedData.isDespawned()) {
                                tamedData.setDespawned(false);
                                logVerbose("[RespawnCheck] Entity found for: " + tamedData.getCustomName()
                                        + ", unmarking despawned");
                            }
                        } else {
                            if (!tamedData.isDespawned()) {
                                double x = tamedData.getLastX();
                                double y = tamedData.getLastY();
                                double z = tamedData.getLastZ();
                                tamingManager.onTamedAnimalDespawn(tamedData.getAnimalUuid(), x, y, z);
                                logVerbose("[RespawnCheck] Marking despawned: " + tamedData.getCustomName());
                            }
                        }
                    }

                    // Phase 2: Respawn despawned animals near players in this world
                    for (Player player : finalWorld.getPlayers()) {
                        try {
                            Vector3d playerPos = player.getTransformComponent().getPosition();
                            if (playerPos == null)
                                continue;

                            // Only get despawned animals that belong to this world
                            java.util.List<TamedAnimalData> toRespawn = tamingManager.getDespawnedAnimalsInRegion(
                                    playerPos.getX(), playerPos.getZ(), RESPAWN_RADIUS);

                            for (TamedAnimalData tamedData : toRespawn) {
                                // Filter by world
                                String animalWorld = tamedData.getWorldId();
                                if (animalWorld == null || animalWorld.isEmpty()) animalWorld = "default";
                                if (!finalWorldName.equals(animalWorld)) continue;

                                if (!tamedData.isDead()) {
                                    respawnTamedAnimal(finalWorld, store, tamedData);
                                }
                            }
                        } catch (Exception e) {
                            logVerbose("[RespawnCheck] Error processing player: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logWarning("[RespawnCheck] Exception in world.execute: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    // ========================================================================
    // RESPAWN IMPLEMENTATION
    // ========================================================================

    /**
     * Respawn a tamed animal at its saved position.
     */
    private void respawnTamedAnimal(World world, Store<EntityStore> store, TamedAnimalData tamedData) {
        if (tamedData == null || !tamedData.isDespawned())
            return;

        AnimalType animalType = tamedData.getAnimalType();
        if (animalType == null)
            return;

        Vector3d spawnPos = new Vector3d(
                tamedData.getLastX(),
                tamedData.getLastY() + 0.5,
                tamedData.getLastZ());

        final UUID oldUuid = tamedData.getAnimalUuid();
        final AnimalType finalAnimalType = animalType;
        final TamedAnimalData finalTamedData = tamedData;

        try {
            // Determine role ID based on growth stage
            String roleId;
            if (tamedData.getGrowthStage() == GrowthStage.ADULT || tamedData.getGrowthStage() == null) {
                roleId = finalAnimalType.getAdultNpcRoleId();
            } else {
                roleId = finalAnimalType.hasBabyVariant() ? finalAnimalType.getBabyNpcRoleId()
                        : finalAnimalType.getAdultNpcRoleId();
            }

            NPCPlugin npcPlugin = NPCPlugin.get();
            int roleIndex = npcPlugin.getIndex(roleId);
            if (roleIndex < 0) {
                logWarning("Could not find role for respawn: " + roleId);
                return;
            }

            Vector3f rotation = new Vector3f(0, finalTamedData.getLastRotation(), 0);

            Pair<Ref<EntityStore>, NPCEntity> newNpc = NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos,
                    rotation, null, null);

            if (newNpc != null) {
                Ref<EntityStore> entityRef = newNpc.first();

                if (entityRef != null) {
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

                    // Set HyTameComponent on the respawned entity
                    UUID ownerUuid = finalTamedData.getOwnerUuid();
                    String ownerName = finalTamedData.getOwnerName();
                    UUID hytameId = finalTamedData.getHytameId();
                    if (ownerUuid != null && hyTameTypeSupplier != null) {
                        String effectiveOwnerName = (ownerName != null) ? ownerName : "Unknown";

                        ComponentType<EntityStore, HyTameComponent> hyTameType = hyTameTypeSupplier.get();
                        if (hyTameType != null) {
                            HyTameComponent hyTameComp = store.ensureAndGetComponent(entityRef, hyTameType);
                            if (hyTameComp != null) {
                                hyTameComp.setTamed(ownerUuid, effectiveOwnerName);
                                if (hytameId != null) {
                                    hyTameComp.setHytameId(hytameId);
                                }
                                logVerbose("Set HyTameComponent on respawned entity: owner=" + effectiveOwnerName
                                        + ", hytameId=" + hytameId);
                            }
                        }
                    }

                    // Restore breeding data
                    BreedingData bData = breedingManager.getOrCreateData(newUuid, finalAnimalType);
                    finalTamedData.applyToBreedingData(bData);
                    bData.setTamed(true, finalTamedData.getOwnerUuid());
                    bData.setCustomName(finalTamedData.getCustomName());
                    bData.setEntityRef(entityRef);

                    // Restore nameplate
                    String customName = finalTamedData.getCustomName();
                    if (customName != null && !customName.isEmpty()
                            && !customName.equalsIgnoreCase(NameplateUtil.UNDEFINED_NAME)) {
                        NameplateUtil.setEntityNameplate(entityRef, customName);
                    }

                    logVerbose("Respawned tamed animal: " + finalTamedData.getCustomName() +
                            " (" + finalAnimalType + ")");
                }
            }

        } catch (Exception e) {
            logWarning("Failed to respawn tamed animal: " + e.getMessage());
        }
    }

    /**
     * Get the respawn radius constant.
     */
    public static double getRespawnRadius() {
        return RESPAWN_RADIUS;
    }
}
