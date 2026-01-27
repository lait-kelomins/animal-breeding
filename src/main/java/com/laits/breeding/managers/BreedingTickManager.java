package com.laits.breeding.managers;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.BreedingData;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.EcsReflectionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages the breeding tick logic including:
 * - Love mode expiration
 * - Heart particle spawning
 * - Breeding distance checks
 * - Custom animal breeding
 */
public class BreedingTickManager {

    // Constants
    private static final long LOVE_DURATION = 30000; // 30 seconds in milliseconds
    private static final double BREEDING_DISTANCE = 5.0; // Blocks

    // Dependencies
    private final BreedingManager breedingManager;
    private final ConfigManager configManager;

    // Reusable collections to avoid allocation per tick
    private final Map<AnimalType, List<BreedingData>> tickLoveByType = new HashMap<>();
    private final List<Object> tickLoveEntityRefs = new ArrayList<>();

    // Callbacks for spawning (injected from plugin)
    private BiConsumer<AnimalType, BreedingData[]> onBreedingComplete;
    private BiConsumer<String, BreedingManager.CustomAnimalLoveData[]> onCustomBreedingComplete;
    private Consumer<Object> heartParticleSpawner;
    private BiConsumer<Vector3d, Store<EntityStore>> heartParticlePositionSpawner;

    // Logging
    private boolean verboseLogging = false;
    private Consumer<String> logger;
    private Consumer<String> warningLogger;

    public BreedingTickManager(BreedingManager breedingManager, ConfigManager configManager) {
        this.breedingManager = breedingManager;
        this.configManager = configManager;
    }

    /**
     * Set the callback for when two animals should breed.
     * The callback receives (AnimalType, [animal1Data, animal2Data])
     */
    public void setOnBreedingComplete(BiConsumer<AnimalType, BreedingData[]> callback) {
        this.onBreedingComplete = callback;
    }

    /**
     * Set the callback for when two custom animals should breed.
     * The callback receives (modelAssetId, [animal1Data, animal2Data])
     */
    public void setOnCustomBreedingComplete(BiConsumer<String, BreedingManager.CustomAnimalLoveData[]> callback) {
        this.onCustomBreedingComplete = callback;
    }

    /**
     * Set the callback for spawning heart particles at an entity ref.
     */
    public void setHeartParticleSpawner(Consumer<Object> spawner) {
        this.heartParticleSpawner = spawner;
    }

    /**
     * Set the callback for spawning heart particles at a position.
     * This is more reliable as it uses the position directly without ref lookup.
     */
    public void setHeartParticlePositionSpawner(BiConsumer<Vector3d, Store<EntityStore>> spawner) {
        this.heartParticlePositionSpawner = spawner;
    }

    /**
     * Set verbose logging mode.
     */
    public void setVerboseLogging(boolean verbose) {
        this.verboseLogging = verbose;
    }

    /**
     * Set the logger for info messages.
     */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    /**
     * Set the logger for warning messages.
     */
    public void setWarningLogger(Consumer<String> warningLogger) {
        this.warningLogger = warningLogger;
    }

    /**
     * Main tick method to handle animals in love.
     * Should be called every second.
     */
    public void tick() {
        // Early exit if nothing tracked
        int trackedCount = breedingManager.getTrackedCount();
        int inLoveTotal = breedingManager.getInLoveCount();

        if (inLoveTotal > 0 && verboseLogging && logger != null) {
            logger.accept("[TickLove] Running: tracked=" + trackedCount + ", inLove=" + inLoveTotal);
        }

        if (trackedCount == 0)
            return;

        long now = System.currentTimeMillis();

        // Clear reusable collections
        tickLoveByType.values().forEach(List::clear);
        tickLoveByType.clear();
        tickLoveEntityRefs.clear();

        int inLoveCount = 0;
        List<BreedingData> animalsInLove = new ArrayList<>();

        // Single pass: expire love AND collect eligible animals AND group by type
        for (BreedingData data : breedingManager.getAllBreedingData()) {
            if (data.isInLove()) {
                // Check expiration
                if (now - data.getLoveStartTime() > LOVE_DURATION) {
                    data.resetLove();
                    continue;
                }

                // Collect for heart particles (will use UUID lookup)
                animalsInLove.add(data);

                // Collect if eligible for breeding
                if (!data.isPregnant() && data.getGrowthStage().canBreed()) {
                    tickLoveByType.computeIfAbsent(data.getAnimalType(), k -> new ArrayList<>()).add(data);
                    inLoveCount++;
                }
            }
        }

        // Also process custom animals in love mode
        breedingManager.tickCustomAnimalLove();
        for (BreedingManager.CustomAnimalLoveData customData : breedingManager.getCustomAnimalsInLove()) {
            if (customData.getEntityRef() != null) {
                tickLoveEntityRefs.add(customData.getEntityRef());
            }
        }

        // Debug logging
        if (!animalsInLove.isEmpty() && logger != null) {
            logger.accept("[Hearts] Animals in love: " + animalsInLove.size());
        }

        // Spawn heart particles using UUID lookup for fresh positions
        if (!animalsInLove.isEmpty() && heartParticlePositionSpawner != null) {
            World world = Universe.get().getDefaultWorld();
            if (world != null) {
                world.execute(() -> {
                    try {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        for (BreedingData data : animalsInLove) {
                            // Use UUID lookup to get fresh position
                            Vector3d pos = findEntityPositionByUuid(store, data);
                            if (pos != null) {
                                // Spawn particles directly at the position
                                heartParticlePositionSpawner.accept(pos, store);
                            }
                        }
                    } catch (Exception e) {
                        // Silent
                    }
                });
            }
        }

        // Also spawn for custom animals (they use the old ref approach for now)
        if (!tickLoveEntityRefs.isEmpty() && heartParticleSpawner != null) {
            for (Object entityRef : tickLoveEntityRefs) {
                heartParticleSpawner.accept(entityRef);
            }
        }

        // Early exit if less than 2 animals in love
        if (inLoveCount < 2)
            return;

        // Check breeding for each type with 2+ animals in love
        World world = Universe.get().getDefaultWorld();
        if (world == null)
            return;

        for (Map.Entry<AnimalType, List<BreedingData>> entry : tickLoveByType.entrySet()) {
            List<BreedingData> animalsOfType = entry.getValue();
            if (animalsOfType.size() < 2)
                continue;

            BreedingData animal1 = animalsOfType.get(0);
            BreedingData animal2 = animalsOfType.get(1);

            if (animal1.getEntityRef() == null || animal2.getEntityRef() == null) {
                continue;
            }

            checkBreedingDistance(world, entry.getKey(), animal1, animal2);
        }

        // Also check custom animal breeding
        checkCustomAnimalBreeding(world);
    }

    /**
     * Check if two animals are close enough to breed.
     */
    private void checkBreedingDistance(World world, AnimalType animalType,
                                       BreedingData animal1, BreedingData animal2) {
        final BreedingData finalAnimal1 = animal1;
        final BreedingData finalAnimal2 = animal2;
        final AnimalType finalType = animalType;

        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();

                // Use UUID-based lookup which is more reliable than stored refs
                Vector3d pos1 = findEntityPositionByUuid(store, finalAnimal1);
                Vector3d pos2 = findEntityPositionByUuid(store, finalAnimal2);

                if (pos1 == null || pos2 == null) {
                    if (verboseLogging && logger != null) {
                        logger.accept("[Breeding] Position lookup failed - pos1: " + (pos1 != null) + ", pos2: " + (pos2 != null));
                    }
                    return;
                }

                double distance = calculateDistance(pos1, pos2);

                if (distance <= BREEDING_DISTANCE && onBreedingComplete != null) {
                    finalAnimal1.completeBreeding();
                    finalAnimal2.completeBreeding();
                    onBreedingComplete.accept(finalType, new BreedingData[] { finalAnimal1, finalAnimal2 });
                }
            } catch (Exception e) {
                // Silent
            }
        });
    }

    /**
     * Check for custom animals in love that are close enough to breed.
     */
    private void checkCustomAnimalBreeding(World world) {
        // Group custom animals in love by modelAssetId
        Map<String, List<BreedingManager.CustomAnimalLoveData>> byType = new HashMap<>();
        for (BreedingManager.CustomAnimalLoveData data : breedingManager.getCustomAnimalsInLove()) {
            if (data.isInLove() && data.getEntityRef() != null) {
                byType.computeIfAbsent(data.getModelAssetId(), k -> new ArrayList<>()).add(data);
            }
        }

        // For each type with 2+ animals in love, check distance
        for (Map.Entry<String, List<BreedingManager.CustomAnimalLoveData>> entry : byType.entrySet()) {
            List<BreedingManager.CustomAnimalLoveData> animalsOfType = entry.getValue();
            if (animalsOfType.size() < 2)
                continue;

            BreedingManager.CustomAnimalLoveData animal1 = animalsOfType.get(0);
            BreedingManager.CustomAnimalLoveData animal2 = animalsOfType.get(1);

            if (animal1.getEntityRef() == null || animal2.getEntityRef() == null)
                continue;

            final BreedingManager.CustomAnimalLoveData finalAnimal1 = animal1;
            final BreedingManager.CustomAnimalLoveData finalAnimal2 = animal2;
            final String modelAssetId = entry.getKey();

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    Vector3d pos1 = getPositionFromRef(store, finalAnimal1.getEntityRef());
                    Vector3d pos2 = getPositionFromRef(store, finalAnimal2.getEntityRef());

                    if (pos1 == null || pos2 == null)
                        return;

                    double distance = calculateDistance(pos1, pos2);

                    if (distance <= BREEDING_DISTANCE) {
                        if (verboseLogging && logger != null) {
                            logger.accept("[CustomBreed] Breeding " + modelAssetId + " at distance " +
                                    String.format("%.1f", distance));
                        }

                        finalAnimal1.completeBreeding();
                        finalAnimal2.completeBreeding();

                        if (onCustomBreedingComplete != null) {
                            onCustomBreedingComplete.accept(modelAssetId,
                                    new BreedingManager.CustomAnimalLoveData[] { finalAnimal1, finalAnimal2 });
                        }
                    }
                } catch (Exception e) {
                    // Silent
                }
            });
        }
    }

    /**
     * Get position from an entity reference.
     */
    @SuppressWarnings("unchecked")
    private Vector3d getPositionFromRef(Store<EntityStore> store, Object entityRef) {
        if (entityRef == null)
            return null;

        try {
            if (entityRef instanceof Ref) {
                Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
                Store<EntityStore> refStore = ref.getStore();
                if (refStore == null)
                    refStore = store;

                TransformComponent transform = refStore.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    return transform.getPosition();
                }
            }
        } catch (Exception e) {
            // Entity may have despawned
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
     * Find an entity by UUID and get its position.
     * This is more reliable than using stored refs which become stale.
     * Also updates the entityRef in BreedingData for future use.
     *
     * @param store The entity store
     * @param data The breeding data containing the animal UUID
     * @return The entity's position, or null if not found
     */
    @SuppressWarnings("unchecked")
    private Vector3d findEntityPositionByUuid(Store<EntityStore> store, BreedingData data) {
        UUID animalId = data.getAnimalId();
        if (animalId == null || store == null) {
            return null;
        }

        final Ref<EntityStore>[] foundRef = new Ref[1];
        final Vector3d[] foundPos = new Vector3d[1];

        try {
            store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                if (foundRef[0] != null) return; // Already found

                int chunkSize = chunk.size();
                for (int i = 0; i < chunkSize; i++) {
                    try {
                        UUIDComponent uuidComp = chunk.getComponent(i, EcsReflectionUtil.UUID_TYPE);
                        if (uuidComp != null && animalId.equals(uuidComp.getUuid())) {
                            Ref<EntityStore> ref = chunk.getReferenceTo(i);
                            if (ref != null && ref.isValid()) {
                                TransformComponent transform = chunk.getComponent(i, EcsReflectionUtil.TRANSFORM_TYPE);
                                if (transform != null) {
                                    foundRef[0] = ref;
                                    foundPos[0] = transform.getPosition();
                                }
                            }
                            return; // Found the entity
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                    }
                }
            });

            if (foundRef[0] != null && foundPos[0] != null) {
                // Update the stored ref for heart particles
                data.setEntityRef(foundRef[0]);
                return foundPos[0];
            }
        } catch (Exception e) {
            // Scan failed
        }

        return null;
    }

    /**
     * Get the breeding distance constant.
     */
    public static double getBreedingDistance() {
        return BREEDING_DISTANCE;
    }

    /**
     * Get the love duration constant.
     */
    public static long getLoveDuration() {
        return LOVE_DURATION;
    }
}
