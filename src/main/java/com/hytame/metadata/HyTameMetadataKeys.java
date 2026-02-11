package com.hytame.metadata;

/**
 * Metadata key constants stored on capture crate ItemStacks.
 * When a tamed animal is captured, these keys are written to the item.
 * When the animal is released, these keys are read and applied to the spawned entity.
 */
public final class HyTameMetadataKeys {
    private HyTameMetadataKeys() {}

    /** Stable HyTame ID (UUID) - links to TamedAnimalData across respawns */
    public static final String HYTAME_ID = "HyTame.Id";

    /** Owner player UUID */
    public static final String OWNER_UUID = "HyTame.OwnerUuid";

    /** Owner player display name */
    public static final String OWNER_NAME = "HyTame.OwnerName";

    /** Custom name assigned by the player */
    public static final String CUSTOM_NAME = "HyTame.Name";

    /** Animal type string (e.g., "COW", "SHEEP") */
    public static final String ANIMAL_TYPE = "HyTame.Type";

    /** Whether the animal was tamed (always true when metadata is present) */
    public static final String TAMED = "HyTame.Tamed";
}
