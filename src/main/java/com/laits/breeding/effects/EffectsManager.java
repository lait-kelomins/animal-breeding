package com.laits.breeding.effects;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.util.EntityUtil;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Manages particle and sound effects for the breeding plugin.
 * Provides methods for spawning heart particles and playing feeding sounds.
 */
public class EffectsManager {

    // Particle effect constants
    private static final String HEARTS_PARTICLE = "BreedingHearts";
    private static final String FEEDING_SOUND = "SFX_Consume_Bread";

    // Height offset for particles above entities
    private static final double PARTICLE_HEIGHT_OFFSET = 1.5;

    // Logging
    private boolean verboseLogging = false;
    private Consumer<String> logger;
    private Consumer<String> warningLogger;

    // Position retriever for world thread operations
    private BiConsumer<Store<EntityStore>, Object> positionRetriever;

    public EffectsManager() {
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
     * Play the feeding sound at the target entity's location.
     *
     * @param targetEntity The entity to play the sound at
     */
    public void playFeedingSound(Entity targetEntity) {
        try {
            int soundId = SoundEvent.getAssetMap().getIndex(FEEDING_SOUND);
            if (soundId < 0)
                return;

            Vector3d pos = EntityUtil.getEntityPosition(targetEntity);
            if (pos == null)
                return;

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
     * Play feeding sound at an entity's position (alternate implementation).
     *
     * @param entity The entity to play the sound at
     */
    public void playFeedingSoundAtEntity(Entity entity) {
        try {
            Vector3d pos = EntityUtil.getEntityPosition(entity);
            if (pos == null)
                return;

            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            Store<EntityStore> store = world.getEntityStore().getStore();

            int soundId = SoundEvent.getAssetMap().getIndex(FEEDING_SOUND);
            if (soundId < 0)
                return;

            SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), store);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Spawn heart particles at an entity's position.
     *
     * @param entity The entity to spawn particles above
     */
    public void spawnHeartParticlesAtEntity(Entity entity) {
        try {
            Vector3d position = EntityUtil.getEntityPosition(entity);
            if (position == null)
                return;

            double x = position.getX();
            double y = position.getY() + PARTICLE_HEIGHT_OFFSET;
            double z = position.getZ();

            World world = Universe.get().getDefaultWorld();
            if (world == null)
                return;

            Store<EntityStore> store = world.getEntityStore().getStore();
            Vector3d heartsPos = new Vector3d(x, y, z);
            ParticleUtil.spawnParticleEffect(HEARTS_PARTICLE, heartsPos, store);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Spawn heart particles at an entity ref's position.
     * Used for spawning particles on the world thread when you have an entity
     * reference.
     *
     * @param store     The entity store
     * @param entityRef The entity reference (Ref<EntityStore>)
     */
    @SuppressWarnings("unchecked")
    public void spawnHeartParticlesAtRef(Store<EntityStore> store, Object entityRef) {
        try {
            if (entityRef == null) {
                if (warningLogger != null) {
                    warningLogger.accept("[Hearts] entityRef is null");
                }
                return;
            }

            Ref<EntityStore> ref = (Ref<EntityStore>) entityRef;
            if (!ref.isValid()) {
                // Expected when entity despawned - not a real error
                if (verboseLogging && logger != null) {
                    logger.accept("[Hearts] ref is invalid (entity likely despawned)");
                }
                return;
            }

            // Get position from transform component
            Vector3d position = getPositionFromRef(store, ref);
            if (position == null) {
                if (warningLogger != null) {
                    Store<EntityStore> refStore = ref.getStore();
                    warningLogger.accept("[Hearts] position is null - ref.getStore()=" +
                            (refStore != null ? "valid" : "NULL") +
                            ", ref.isValid()=" + ref.isValid() +
                            ", ref.getIndex()=" + ref.getIndex());
                }
                return;
            }

            double x = position.getX();
            double y = position.getY() + PARTICLE_HEIGHT_OFFSET;
            double z = position.getZ();

            Vector3d heartsPos = new Vector3d(x, y, z);
            ParticleUtil.spawnParticleEffect(HEARTS_PARTICLE, heartsPos, store);
        } catch (Exception e) {
            if (warningLogger != null) {
                warningLogger.accept("[Hearts] Error in spawnHeartParticlesAtRef: " + e.getMessage());
            }
        }
    }

    /**
     * Spawn heart particles at a specific position.
     *
     * @param position The world position
     * @param store    The entity store
     */
    public void spawnHeartParticlesAtPosition(Vector3d position, Store<EntityStore> store) {
        try {
            if (position == null || store == null)
                return;

            Vector3d heartsPos = new Vector3d(
                    position.getX(),
                    position.getY() + PARTICLE_HEIGHT_OFFSET,
                    position.getZ());
            ParticleUtil.spawnParticleEffect(HEARTS_PARTICLE, heartsPos, store);
        } catch (Exception e) {
            // Silent
        }
    }

    /**
     * Get position from an entity reference using reflection for robustness.
     */
    private Vector3d getPositionFromRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        try {
            if (ref == null)
                return null;

            if (store == null) {
                Store<EntityStore> refStore = ref.getStore();
                store = refStore;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

            if (transform == null)
                return null;

            Vector3d pos = transform.getPosition();

            return pos;
        } catch (Exception e) {
            if (warningLogger != null) {
                warningLogger.accept("[Hearts] getPositionFromRef error: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get the hearts particle effect name.
     */
    public static String getHeartsParticleName() {
        return HEARTS_PARTICLE;
    }

    /**
     * Get the feeding sound name.
     */
    public static String getFeedingSoundName() {
        return FEEDING_SOUND;
    }
}
