package com.animaltaming.hytale;

import com.animaltaming.system.SystemContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Iterator;

/**
 * Hytale API implementation of SystemContext.
 * Bridges plugin logic with actual Hytale game systems.
 *
 * API patterns verified from hytale-api-reference:
 * - Entity.getReference() -> Ref<EntityStore> (Entity.java:264-266)
 * - ref.getStore().getComponent(ref, Type.getComponentType()) (Ref.java:32-35)
 * - MovementStates.crouching is a FIELD (PlayerRef.java:94-100)
 * - LivingEntity.getInventory() (LivingEntity.java:121-123)
 * - Inventory.getItemInHand() (Inventory.java:636-638)
 * - ItemStack.getItemId() (ItemStack.java:107-109)
 */
public class HytaleSystemContext implements SystemContext {

    private final HytaleEntityAdapter entityAdapter;
    private World currentWorld;
    private long currentTick = 0;

    // Registry for tameable animals by species
    private final Map<String, Set<Long>> tameablesBySpecies = new HashMap<>();
    private final Map<Long, String> entitySpecies = new HashMap<>();

    // Pending interactions for this tick
    private final List<InteractionEvent> pendingInteractions = new ArrayList<>();

    public HytaleSystemContext(HytaleEntityAdapter entityAdapter) {
        this.entityAdapter = Objects.requireNonNull(entityAdapter, "entityAdapter is required");
    }

    public void setWorld(World world) {
        this.currentWorld = world;
    }

    public World getWorld() {
        return currentWorld;
    }

    public HytaleEntityAdapter getEntityAdapter() {
        return entityAdapter;
    }

    /**
     * Called each tick to update the current tick counter.
     */
    public void tick() {
        currentTick++;
        pendingInteractions.clear();
    }

    // ==================== TIME ====================

    @Override
    public long getCurrentTick() {
        return currentTick;
    }

    @Override
    public int getTickRate() {
        return 30; // Hytale runs at 30 TPS
    }

    // ==================== ENTITY EXISTENCE ====================

    @Override
    public boolean entityExists(long entityId) {
        return entityAdapter.getEntity(entityId)
                .map(entity -> {
                    Ref<EntityStore> ref = entity.getReference();
                    return ref != null && ref.isValid();
                })
                .orElse(false);
    }

    // ==================== POSITION ====================

    @Override
    public double getEntityX(long entityId) {
        return entityAdapter.getEntity(entityId)
                .map(entity -> {
                    TransformComponent tc = entity.getTransformComponent();
                    if (tc == null) return 0.0;
                    Vector3d pos = tc.getPosition();
                    return pos != null ? pos.getX() : 0.0;
                })
                .orElse(0.0);
    }

    @Override
    public double getEntityY(long entityId) {
        return entityAdapter.getEntity(entityId)
                .map(entity -> {
                    TransformComponent tc = entity.getTransformComponent();
                    if (tc == null) return 0.0;
                    Vector3d pos = tc.getPosition();
                    return pos != null ? pos.getY() : 0.0;
                })
                .orElse(0.0);
    }

    @Override
    public double getEntityZ(long entityId) {
        return entityAdapter.getEntity(entityId)
                .map(entity -> {
                    TransformComponent tc = entity.getTransformComponent();
                    if (tc == null) return 0.0;
                    Vector3d pos = tc.getPosition();
                    return pos != null ? pos.getZ() : 0.0;
                })
                .orElse(0.0);
    }

    // ==================== PLAYER QUERIES ====================

    @Override
    public List<PlayerInfo> getAllPlayers() {
        List<PlayerInfo> players = new ArrayList<>();
        for (Map.Entry<Long, Entity> entry : entityAdapter.getAllMappings()) {
            Entity entity = entry.getValue();
            if (entity instanceof Player player) {
                players.add(new PlayerInfo(
                        entry.getKey(),
                        player.getUuid(),
                        player.getDisplayName()
                ));
            }
        }
        return players;
    }

    @Override
    public List<Long> getPlayersInRadius(double x, double y, double z, double radius) {
        double radiusSquared = radius * radius;
        List<Long> result = new ArrayList<>();

        for (Map.Entry<Long, Entity> entry : entityAdapter.getAllMappings()) {
            Entity entity = entry.getValue();
            if (!(entity instanceof Player)) continue;

            TransformComponent tc = entity.getTransformComponent();
            if (tc == null) continue;

            Vector3d pos = tc.getPosition();
            if (pos == null) continue;

            double dx = pos.getX() - x;
            double dy = pos.getY() - y;
            double dz = pos.getZ() - z;
            double distSquared = dx * dx + dy * dy + dz * dz;

            if (distSquared <= radiusSquared) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Check if a player is sneaking.
     * Pattern verified from PlayerRef.java:94-100
     */
    @Override
    public boolean isPlayerSneaking(long playerId) {
        return entityAdapter.getEntity(playerId)
                .filter(e -> e instanceof Player)
                .map(entity -> {
                    // Pattern from PlayerRef.java:94-100
                    Ref<EntityStore> ref = entity.getReference();
                    if (ref == null || !ref.isValid()) {
                        return false;
                    }

                    Store<EntityStore> store = ref.getStore();
                    MovementStatesComponent msc = store.getComponent(
                            ref,
                            MovementStatesComponent.getComponentType()
                    );

                    if (msc == null) {
                        return false;
                    }

                    MovementStates states = msc.getMovementStates();
                    // crouching is a FIELD, not a method
                    return states != null && states.crouching;
                })
                .orElse(false);
    }

    /**
     * Get the item ID held by a player.
     * Pattern verified from:
     * - LivingEntity.java:121-123 (getInventory)
     * - Inventory.java:636-638 (getItemInHand)
     * - ItemStack.java:107-109 (getItemId)
     */
    @Override
    public Optional<String> getHeldItemId(long playerId) {
        return entityAdapter.getEntity(playerId)
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .map(LivingEntity::getInventory)
                .map(Inventory::getItemInHand)
                .filter(itemStack -> itemStack != null && !itemStack.isEmpty())
                .map(ItemStack::getItemId);
    }

    /**
     * Consume one item from a player's hand.
     *
     * ItemStack is immutable in Hytale - we create a new stack with reduced
     * quantity and update the inventory slot directly.
     */
    @Override
    public boolean consumeHeldItem(long playerId) {
        return entityAdapter.getEntity(playerId)
                .filter(e -> e instanceof LivingEntity)
                .map(e -> (LivingEntity) e)
                .map(entity -> {
                    Inventory inventory = entity.getInventory();
                    ItemStack held = inventory.getItemInHand();
                    if (held == null || held.isEmpty()) {
                        return false;
                    }

                    // ItemStack is immutable - create new with reduced quantity
                    // withQuantity() returns null if quantity becomes 0
                    ItemStack reduced = held.withQuantity(held.getQuantity() - 1);

                    // Get the hotbar and active slot to update
                    ItemContainer hotbar = inventory.getHotbar();
                    byte activeSlot = inventory.getActiveHotbarSlot();

                    if (activeSlot >= 0) {
                        // Set the reduced stack (or null if consumed completely)
                        hotbar.setItemStackForSlot(activeSlot, reduced);
                    }
                    return true;
                })
                .orElse(false);
    }

    // ==================== MOUNT QUERIES ====================

    /**
     * Get entities riding an entity.
     * Pattern verified from MountedByComponent.java
     */
    @Override
    public List<Long> getRiders(long entityId) {
        return entityAdapter.getEntity(entityId)
                .map(entity -> {
                    Ref<EntityStore> ref = entity.getReference();
                    if (ref == null || !ref.isValid()) {
                        return List.<Long>of();
                    }

                    Store<EntityStore> store = ref.getStore();
                    MountedByComponent mbc = store.getComponent(
                            ref,
                            MountedByComponent.getComponentType()
                    );

                    if (mbc == null) {
                        return List.<Long>of();
                    }

                    List<Ref<EntityStore>> passengers = mbc.getPassengers();
                    return passengers.stream()
                            .filter(Ref::isValid)
                            .map(entityAdapter::findEntityIdByRef)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();
                })
                .orElse(List.of());
    }

    // ==================== TAMEABLE ANIMALS ====================

    /**
     * Register a tameable animal with its species.
     */
    public void registerTameableAnimal(long entityId, String speciesId) {
        tameablesBySpecies
                .computeIfAbsent(speciesId, k -> new HashSet<>())
                .add(entityId);
        entitySpecies.put(entityId, speciesId);
    }

    /**
     * Unregister a tameable animal.
     */
    public void unregisterTameableAnimal(long entityId) {
        String speciesId = entitySpecies.remove(entityId);
        if (speciesId != null) {
            Set<Long> species = tameablesBySpecies.get(speciesId);
            if (species != null) {
                species.remove(entityId);
                // Clean up empty species sets
                if (species.isEmpty()) {
                    tameablesBySpecies.remove(speciesId);
                }
            }
        }
    }

    /**
     * Clean up stale tameable animal entries where the entity no longer exists.
     * @return number of entries removed
     */
    public int cleanupStaleTameableAnimals() {
        int removed = 0;
        Iterator<Map.Entry<Long, String>> it = entitySpecies.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, String> entry = it.next();
            long entityId = entry.getKey();
            if (!entityExists(entityId)) {
                String speciesId = entry.getValue();
                it.remove();
                removed++;

                Set<Long> species = tameablesBySpecies.get(speciesId);
                if (species != null) {
                    species.remove(entityId);
                    if (species.isEmpty()) {
                        tameablesBySpecies.remove(speciesId);
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Clear all tameable animal registrations.
     */
    public void clearTameableAnimals() {
        tameablesBySpecies.clear();
        entitySpecies.clear();
    }

    @Override
    public List<TameableAnimalInfo> getTameableAnimals() {
        List<TameableAnimalInfo> result = new ArrayList<>();
        for (Map.Entry<Long, String> entry : entitySpecies.entrySet()) {
            long entityId = entry.getKey();
            String speciesId = entry.getValue();

            entityAdapter.getEntity(entityId).ifPresent(entity -> {
                result.add(new TameableAnimalInfo(
                        entityId,
                        entity.getUuid(),
                        speciesId
                ));
            });
        }
        return result;
    }

    @Override
    public Optional<Long> getEntityIdForAnimal(UUID animalId) {
        return entityAdapter.getEntityIdByUuid(animalId);
    }

    // ==================== INTERACTIONS ====================

    /**
     * Queue an interaction event for processing this tick.
     */
    public void queueInteraction(long playerEntityId, long targetEntityId, String type) {
        pendingInteractions.add(new InteractionEvent(playerEntityId, targetEntityId, type));
    }

    @Override
    public List<InteractionEvent> getPendingInteractions() {
        return List.copyOf(pendingInteractions);
    }

    // ==================== ACTIONS ====================

    /**
     * Teleport an entity to a new position.
     * Uses TransformComponent.setPosition() to move the entity.
     */
    @Override
    public void teleport(long entityId, double x, double y, double z) {
        entityAdapter.getEntity(entityId).ifPresent(entity -> {
            TransformComponent tc = entity.getTransformComponent();
            if (tc != null) {
                tc.setPosition(new Vector3d(x, y, z));
            }
        });
    }

    /**
     * Move an entity toward a target position at a given speed.
     *
     * Uses direct position updates via TransformComponent. This provides smooth
     * movement but does not account for pathfinding around obstacles.
     * When proper Hytale navigation APIs (PathFollower, etc.) are available,
     * this should be updated to use them for natural AI movement.
     *
     * @param entityId the entity to move
     * @param targetX target X coordinate
     * @param targetY target Y coordinate
     * @param targetZ target Z coordinate
     * @param speed movement speed (blocks per second)
     */
    @Override
    public void moveEntityToward(long entityId, double targetX, double targetY, double targetZ, double speed) {
        entityAdapter.getEntity(entityId).ifPresent(entity -> {
            TransformComponent tc = entity.getTransformComponent();
            if (tc == null) return;

            Vector3d pos = tc.getPosition();
            if (pos == null) return;

            double dx = targetX - pos.getX();
            double dy = targetY - pos.getY();
            double dz = targetZ - pos.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Already at target
            if (distance < 0.1) return;

            // Calculate movement for this tick
            // Speed is blocks per second, tick rate is 30 TPS
            double moveDistance = speed / getTickRate();
            if (moveDistance > distance) {
                moveDistance = distance; // Don't overshoot
            }

            double factor = moveDistance / distance;
            double newX = pos.getX() + dx * factor;
            double newY = pos.getY() + dy * factor;
            double newZ = pos.getZ() + dz * factor;

            tc.setPosition(new Vector3d(newX, newY, newZ));
        });
    }

    /**
     * Spawn particles at a location.
     *
     * NOTE: Hytale's particle system is asset-based (particle system IDs defined in
     * GameAssets/Assets/Server/VFX/ParticleSystems/). The documented plugin API does
     * not expose a direct method for spawning particles programmatically.
     *
     * Options for proper implementation:
     * 1. Send SpawnParticleSystem packet directly to nearby players
     * 2. Use NPC ActionSpawnParticles component if entity-attached
     * 3. Define custom particle systems in assets and trigger via interactions
     *
     * For now, this logs the request. Visual feedback will require additional Hytale
     * API research or custom asset integration.
     */
    @Override
    public void spawnParticle(double x, double y, double z, String particleType) {
        // Particle API not exposed to plugins - would need packet-level access
        // Particle types used: "heart", "portal", "note", "smoke"
        System.out.println("[HyTame] Particle '" + particleType + "' at (" +
                String.format("%.1f, %.1f, %.1f", x, y, z) + ")");
    }

    /**
     * Play a sound at a location.
     *
     * NOTE: Hytale's sound system is asset-based (sound event IDs defined in
     * GameAssets/Assets/Server/Audio/SoundEvents/). The documented plugin API does
     * not expose a direct method for playing sounds programmatically.
     *
     * Options for proper implementation:
     * 1. Send PlaySoundEvent3D packet directly to nearby players
     * 2. Use NPC ActionPlaySound component if entity-attached
     * 3. Define custom sound events in assets and trigger via interactions
     *
     * For now, this logs the request. Audio feedback will require additional Hytale
     * API research or custom asset integration.
     */
    @Override
    public void playSound(double x, double y, double z, String soundType) {
        // Sound API not exposed to plugins - would need packet-level access
        // Sound types used: "purr", etc.
        System.out.println("[HyTame] Sound '" + soundType + "' at (" +
                String.format("%.1f, %.1f, %.1f", x, y, z) + ")");
    }

    /**
     * Send a message to a player.
     * Uses Player.sendMessage(Message.text()) pattern from Hytale events API.
     */
    @Override
    public void sendMessage(long playerId, String message) {
        entityAdapter.getPlayer(playerId).ifPresent(player -> {
            player.sendMessage(Message.raw(message));
        });
    }
}
