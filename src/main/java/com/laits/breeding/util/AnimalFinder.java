package com.laits.breeding.util;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.models.AnimalType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility for finding farm animals in the world using the ECS system.
 *
 * Entity types are identified via ModelComponent.model.modelAssetId.
 * Entity references are obtained via ArchetypeChunk.getReferenceTo(index).
 */
public class AnimalFinder {

    // Component type uses centralized cache from EcsReflectionUtil
    private static final ComponentType<EntityStore, ModelComponent> MODEL_COMPONENT_TYPE = EcsReflectionUtil.MODEL_TYPE;

    /**
     * Result of finding an animal in the world.
     */
    public static class FoundAnimal {
        private final Ref<EntityStore> entityRef;
        private final String modelAssetId;
        private final AnimalType animalType;
        private final boolean isBaby;

        public FoundAnimal(Ref<EntityStore> entityRef, String modelAssetId) {
            this.entityRef = entityRef;
            this.modelAssetId = modelAssetId;
            this.animalType = AnimalType.fromModelAssetId(modelAssetId);
            this.isBaby = AnimalType.isBabyVariant(modelAssetId);
        }

        public Ref<EntityStore> getEntityRef() { return entityRef; }
        public String getModelAssetId() { return modelAssetId; }
        public AnimalType getAnimalType() { return animalType; }
        public boolean isBaby() { return isBaby; }
        public boolean isFarmAnimal() { return animalType != null; }

        @Override
        public String toString() {
            return modelAssetId + (isBaby ? " (baby)" : "") +
                   (animalType != null ? " [" + animalType + "]" : "");
        }
    }

    /**
     * Find all farm animals in the given world.
     * Must be called from WorldThread or will schedule on it.
     *
     * @param world The World object from player.getWorld()
     * @param callback Called with results when complete
     */
    public static void findFarmAnimals(World world, Consumer<List<FoundAnimal>> callback) {
        findAnimals(world, true, callback);
    }

    /**
     * Find all animals (farm or all) in the given world.
     *
     * @param world The World object
     * @param farmOnly If true, only return farm animals
     * @param callback Called with results when complete
     */
    public static void findAnimals(World world, boolean farmOnly, Consumer<List<FoundAnimal>> callback) {
        if (world == null) {
            callback.accept(new ArrayList<>());
            return;
        }

        EntityStore entityStore = world.getEntityStore();
        if (entityStore == null) {
            callback.accept(new ArrayList<>());
            return;
        }

        Store<EntityStore> store = entityStore.getStore();
        if (store == null) {
            callback.accept(new ArrayList<>());
            return;
        }

        final List<FoundAnimal> results = new ArrayList<>();

        // Execute on WorldThread
        world.execute(() -> {
            try {
                scanEntities(store, farmOnly, results);
            } catch (Exception e) {
                // Log error silently
            }
            callback.accept(results);
        });
    }

    /**
     * Synchronously scan entities (must be on WorldThread).
     */
    private static void scanEntities(Store<EntityStore> store, boolean farmOnly, List<FoundAnimal> results) {
        store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
            processChunk(chunk, farmOnly, results);
        });
    }

    /**
     * Process a single ArchetypeChunk to find animals.
     */
    private static void processChunk(ArchetypeChunk<EntityStore> chunk, boolean farmOnly, List<FoundAnimal> results) {
        int chunkSize = chunk.size();
        if (chunkSize == 0) return;

        // Process each entity in chunk
        for (int i = 0; i < chunkSize; i++) {
            try {
                ModelComponent modelComp = chunk.getComponent(i, MODEL_COMPONENT_TYPE);
                if (modelComp == null) continue;

                // Extract modelAssetId from the model field
                String assetId = extractModelAssetId(modelComp);
                if (assetId == null) continue;

                // Check if it's a farm animal (if filtering)
                if (farmOnly) {
                    AnimalType type = AnimalType.fromModelAssetId(assetId);
                    if (type == null) continue;
                }

                // Get entity reference
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef != null) {
                    results.add(new FoundAnimal(entityRef, assetId));
                }

            } catch (Exception e) {
                // Skip this entity
            }
        }
    }

    /**
     * Extract modelAssetId from ModelComponent using cached reflection on the model field.
     * The model field contains modelAssetId which identifies the entity type.
     */
    private static String extractModelAssetId(ModelComponent modelComp) {
        try {
            Model model = modelComp.getModel();
            if (model == null) return null;

            return model.getModelAssetId();
        } catch (Exception e) {
            return null;
        }
    }
}
