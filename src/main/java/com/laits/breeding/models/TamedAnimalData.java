package com.laits.breeding.models;

import java.util.UUID;

/**
 * Data for a tamed animal that persists across sessions.
 * This data is saved to disk and survives server restarts.
 */
public class TamedAnimalData {

    // Core identity
    private UUID animalUuid;           // Current entity UUID (changes on respawn)
    private UUID ownerUuid;            // Player who tamed this animal
    private String customName;         // Player-assigned name
    private AnimalType animalType;     // Type for respawning

    // Location data (for respawn)
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastRotation;        // Y rotation for facing direction
    private String worldId;            // World identifier (for multi-world support)

    // Breeding data to preserve across respawns
    private long lastBreedTime;
    private long birthTime;
    private GrowthStage growthStage;

    // Taming metadata
    private long tamedTime;
    private boolean isDespawned;       // True if awaiting respawn (chunk unload, etc.)
    private boolean isDead;            // True if animal died (for future revive mechanic)
    private long deathTime;            // When animal died
    private long despawnTime;          // When entity was marked despawned (for cleanup)
    private boolean allowInteraction;  // Whether other players can interact

    // Transient (not saved) - runtime entity reference
    private transient Object entityRef;

    /**
     * Default constructor for Gson deserialization.
     */
    public TamedAnimalData() {
        this.allowInteraction = true;
        this.growthStage = GrowthStage.ADULT;
    }

    /**
     * Create tamed animal data when an animal is first tamed.
     */
    public TamedAnimalData(UUID animalUuid, UUID ownerUuid, String customName, AnimalType animalType) {
        this.animalUuid = animalUuid;
        this.ownerUuid = ownerUuid;
        this.customName = customName;
        this.animalType = animalType;
        this.tamedTime = System.currentTimeMillis();
        this.isDespawned = false;
        this.allowInteraction = true;
        this.growthStage = GrowthStage.ADULT;
        this.worldId = "default";
    }

    // === Core Identity ===

    public UUID getAnimalUuid() {
        return animalUuid;
    }

    public void setAnimalUuid(UUID animalUuid) {
        this.animalUuid = animalUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public AnimalType getAnimalType() {
        return animalType;
    }

    public void setAnimalType(AnimalType animalType) {
        this.animalType = animalType;
    }

    // === Location ===

    public double getLastX() {
        return lastX;
    }

    public double getLastY() {
        return lastY;
    }

    public double getLastZ() {
        return lastZ;
    }

    public void setLastPosition(double x, double y, double z) {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
    }

    public float getLastRotation() {
        return lastRotation;
    }

    public void setLastRotation(float rotation) {
        this.lastRotation = rotation;
    }

    public String getWorldId() {
        return worldId;
    }

    public void setWorldId(String worldId) {
        this.worldId = worldId;
    }

    // === Breeding Data ===

    public long getLastBreedTime() {
        return lastBreedTime;
    }

    public void setLastBreedTime(long lastBreedTime) {
        this.lastBreedTime = lastBreedTime;
    }

    public long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(long birthTime) {
        this.birthTime = birthTime;
    }

    public GrowthStage getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(GrowthStage growthStage) {
        this.growthStage = growthStage;
    }

    // === Taming Metadata ===

    public long getTamedTime() {
        return tamedTime;
    }

    public void setTamedTime(long tamedTime) {
        this.tamedTime = tamedTime;
    }

    public boolean isDespawned() {
        return isDespawned;
    }

    public void setDespawned(boolean despawned) {
        isDespawned = despawned;
        if (despawned) {
            this.despawnTime = System.currentTimeMillis();
        }
    }

    public boolean isDead() {
        return isDead;
    }

    public void setDead(boolean dead) {
        isDead = dead;
        if (dead) {
            this.deathTime = System.currentTimeMillis();
        }
    }

    public long getDeathTime() {
        return deathTime;
    }

    public long getDespawnTime() {
        return despawnTime;
    }

    public boolean isAllowInteraction() {
        return allowInteraction;
    }

    public void setAllowInteraction(boolean allowInteraction) {
        this.allowInteraction = allowInteraction;
    }

    // === Entity Reference (Transient) ===

    public Object getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(Object entityRef) {
        this.entityRef = entityRef;
    }

    // === Utility Methods ===

    /**
     * Check if this animal is owned by the given player.
     */
    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    /**
     * Check if the given player can interact with this animal.
     * Owner can always interact, others only if allowInteraction is true.
     */
    public boolean canInteract(UUID playerUuid) {
        if (isOwnedBy(playerUuid)) {
            return true;
        }
        return allowInteraction;
    }

    /**
     * Copy breeding state from BreedingData for persistence.
     */
    public void copyFromBreedingData(BreedingData data) {
        if (data != null) {
            this.lastBreedTime = data.getLastBreedTime();
            this.birthTime = data.getBirthTime();
            this.growthStage = data.getGrowthStage();
        }
    }

    /**
     * Apply saved breeding state to BreedingData after respawn.
     */
    public void applyToBreedingData(BreedingData data) {
        if (data != null) {
            data.setLastBreedTime(this.lastBreedTime);
            data.setGrowthStage(this.growthStage);
        }
    }

    @Override
    public String toString() {
        return "TamedAnimalData{" +
                "name='" + customName + '\'' +
                ", type=" + animalType +
                ", owner=" + ownerUuid +
                ", despawned=" + isDespawned +
                '}';
    }
}
