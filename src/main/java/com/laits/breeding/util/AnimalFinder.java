package com.laits.breeding.util;

import com.laits.breeding.models.AnimalType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Utility for finding farm animals in the world using the ECS system.
 *
 * Entity types are identified via ModelComponent.model.modelAssetId.
 * Entity references are obtained via ArchetypeChunk.getReferenceTo(index).
 */
public class AnimalFinder {

    /**
     * Result of finding an animal in the world.
     */
    public static class FoundAnimal {
        private final Object entityRef;       // Ref<EntityStore>
        private final String modelAssetId;    // e.g., "Cow", "Pig"
        private final AnimalType animalType;  // Mapped type or null
        private final boolean isBaby;

        public FoundAnimal(Object entityRef, String modelAssetId) {
            this.entityRef = entityRef;
            this.modelAssetId = modelAssetId;
            this.animalType = AnimalType.fromModelAssetId(modelAssetId);
            this.isBaby = AnimalType.isBabyVariant(modelAssetId);
        }

        public Object getEntityRef() { return entityRef; }
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
    public static void findFarmAnimals(Object world, Consumer<List<FoundAnimal>> callback) {
        findAnimals(world, true, callback);
    }

    /**
     * Find all animals (farm or all) in the given world.
     *
     * @param world The World object
     * @param farmOnly If true, only return farm animals
     * @param callback Called with results when complete
     */
    public static void findAnimals(Object world, boolean farmOnly, Consumer<List<FoundAnimal>> callback) {
        try {
            // Get EntityStore and inner Store
            Object entityStore = callMethod(world, "getEntityStore");
            if (entityStore == null) {
                callback.accept(new ArrayList<>());
                return;
            }

            Object innerStore = callMethod(entityStore, "getStore");
            if (innerStore == null) {
                callback.accept(new ArrayList<>());
                return;
            }

            // Find execute method to run on WorldThread
            Method executeMethod = null;
            for (Method m : world.getClass().getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 1) {
                    executeMethod = m;
                    break;
                }
            }

            if (executeMethod == null) {
                callback.accept(new ArrayList<>());
                return;
            }

            final Object store = innerStore;
            final List<FoundAnimal> results = new ArrayList<>();

            Runnable task = () -> {
                try {
                    scanEntities(store, farmOnly, results);
                } catch (Exception e) {
                    // Log error
                }
                callback.accept(results);
            };

            executeMethod.invoke(world, task);

        } catch (Exception e) {
            callback.accept(new ArrayList<>());
        }
    }

    /**
     * Synchronously scan entities (must be on WorldThread).
     */
    private static void scanEntities(Object store, boolean farmOnly, List<FoundAnimal> results) throws Exception {
        // Find forEachChunk method
        Method forEachMethod = null;
        for (Method m : store.getClass().getMethods()) {
            if (m.getName().equals("forEachChunk") && m.getParameterCount() == 1 &&
                m.getParameterTypes()[0].getSimpleName().contains("BiConsumer")) {
                forEachMethod = m;
                break;
            }
        }

        if (forEachMethod == null) return;

        Class<?> consumerClass = forEachMethod.getParameterTypes()[0];

        Object consumer = Proxy.newProxyInstance(
            consumerClass.getClassLoader(),
            new Class<?>[] { consumerClass },
            (proxy, method, args) -> {
                if (method.getName().equals("accept") && args != null && args.length >= 2) {
                    Object archetypeChunk = args[0];
                    processChunk(archetypeChunk, farmOnly, results);
                }
                return null;
            }
        );

        forEachMethod.invoke(store, consumer);
    }

    /**
     * Process a single ArchetypeChunk to find animals.
     */
    private static void processChunk(Object chunk, boolean farmOnly, List<FoundAnimal> results) {
        try {
            // Get chunk size
            int chunkSize = (Integer) callMethod(chunk, "size");
            if (chunkSize == 0) return;

            // Get archetype and componentTypes
            Object archetype = callMethod(chunk, "getArchetype");
            if (archetype == null) return;

            Field ctField = archetype.getClass().getDeclaredField("componentTypes");
            ctField.setAccessible(true);
            Object compTypesArr = ctField.get(archetype);
            if (compTypesArr == null) return;

            // Get ModelComponent type at index 41
            Object modelType = java.lang.reflect.Array.get(compTypesArr, 41);
            if (modelType == null) return; // No model component in this archetype

            // Get getComponent and getReferenceTo methods
            Method getCompMethod = null;
            Method getRefMethod = null;
            for (Method m : chunk.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    getCompMethod = m;
                }
                if (m.getName().equals("getReferenceTo") && m.getParameterCount() == 1) {
                    getRefMethod = m;
                }
            }

            if (getCompMethod == null || getRefMethod == null) return;

            // Process each entity in chunk
            for (int i = 0; i < chunkSize; i++) {
                try {
                    Object modelComp = getCompMethod.invoke(chunk, i, modelType);
                    if (modelComp == null) continue;

                    // Get model field
                    Field modelField = modelComp.getClass().getDeclaredField("model");
                    modelField.setAccessible(true);
                    Object model = modelField.get(modelComp);
                    if (model == null) continue;

                    // Extract modelAssetId
                    String modelStr = model.toString();
                    String assetId = extractModelAssetId(modelStr);
                    if (assetId == null) continue;

                    // Check if it's a farm animal (if filtering)
                    if (farmOnly) {
                        AnimalType type = AnimalType.fromModelAssetId(assetId);
                        if (type == null) continue;
                    }

                    // Get entity reference
                    Object entityRef = getRefMethod.invoke(chunk, i);
                    if (entityRef != null) {
                        results.add(new FoundAnimal(entityRef, assetId));
                    }

                } catch (Exception e) {
                    // Skip this entity
                }
            }

        } catch (Exception e) {
            // Skip this chunk
        }
    }

    /**
     * Extract modelAssetId from model.toString() format:
     * Model{modelAssetId='Duck', scale=1.0, ...}
     */
    private static String extractModelAssetId(String modelStr) {
        int start = modelStr.indexOf("modelAssetId='");
        if (start < 0) return null;
        start += 14;
        int end = modelStr.indexOf("'", start);
        if (end <= start) return null;
        return modelStr.substring(start, end);
    }

    private static Object callMethod(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
