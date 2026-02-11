package com.hytame.coop;

import com.hytame.models.AnimalType;
import com.hytame.models.TamedAnimalData;
import com.hytame.tame.HyTameComponent;

import java.util.UUID;

/**
 * Carries HyTame taming data alongside CapturedNPCMetadata instances.
 * Used by CoopCodecExtender's companion WeakHashMap to persist taming
 * data through chunk save/load via the CODEC extension.
 */
public class HyTameCoopData {

    private UUID hytameId;
    private UUID ownerUuid;
    private String ownerName;
    private String customName;
    private String animalType;
    private boolean tamed;

    public HyTameCoopData() {}

    public HyTameCoopData(UUID hytameId, UUID ownerUuid, String ownerName,
                          String customName, String animalType, boolean tamed) {
        this.hytameId = hytameId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.customName = customName;
        this.animalType = animalType;
        this.tamed = tamed;
    }

    /**
     * Build from ECS component + TamingManager data.
     */
    public static HyTameCoopData from(HyTameComponent comp, TamedAnimalData tamedData) {
        HyTameCoopData data = new HyTameCoopData();
        data.tamed = comp.isTamed();
        data.hytameId = comp.getHytameId();
        data.ownerUuid = comp.getTamerUUID();
        data.ownerName = comp.getTamerName();
        if (tamedData != null) {
            data.customName = tamedData.getCustomName();
            AnimalType type = tamedData.getAnimalType();
            data.animalType = type != null ? type.name() : null;
        }
        return data;
    }

    // --- String-based setters for CODEC field lambdas ---

    public void setHytameIdStr(String value) {
        this.hytameId = value != null ? UUID.fromString(value) : null;
    }

    public String getHytameIdStr() {
        return hytameId != null ? hytameId.toString() : null;
    }

    public void setOwnerUuidStr(String value) {
        this.ownerUuid = value != null ? UUID.fromString(value) : null;
    }

    public String getOwnerUuidStr() {
        return ownerUuid != null ? ownerUuid.toString() : null;
    }

    // --- Standard getters/setters ---

    public UUID getHytameId() { return hytameId; }
    public void setHytameId(UUID hytameId) { this.hytameId = hytameId; }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public String getAnimalType() { return animalType; }
    public void setAnimalType(String animalType) { this.animalType = animalType; }

    public boolean isTamed() { return tamed; }
    public void setTamed(boolean tamed) { this.tamed = tamed; }

    @Override
    public String toString() {
        return "HyTameCoopData{hytameId=" + hytameId
                + ", owner=" + ownerName
                + ", name=" + customName
                + ", type=" + animalType
                + ", tamed=" + tamed + "}";
    }
}
