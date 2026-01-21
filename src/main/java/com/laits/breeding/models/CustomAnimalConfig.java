package com.laits.breeding.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a custom animal defined in config.json.
 * Allows modded creatures to be supported without code changes.
 *
 * Custom animals use scaling for babies (spawn small adult, scale up)
 * unless babyNpcRoleId is specified.
 */
public class CustomAnimalConfig {

    private final String modelAssetId;
    private final String displayName;
    private final List<String> breedingFoods;
    private final double growthTimeMinutes;
    private final double breedCooldownMinutes;
    private final String babyNpcRoleId;      // Optional: NPC role for spawning babies
    private final String adultNpcRoleId;     // Optional: NPC role for spawning adults (defaults to modelAssetId)
    private final boolean mountable;
    private final boolean enabled;

    public CustomAnimalConfig(
            String modelAssetId,
            String displayName,
            List<String> breedingFoods,
            double growthTimeMinutes,
            double breedCooldownMinutes,
            String babyNpcRoleId,
            String adultNpcRoleId,
            boolean mountable,
            boolean enabled
    ) {
        this.modelAssetId = modelAssetId;
        this.displayName = displayName != null ? displayName : modelAssetId;
        this.breedingFoods = breedingFoods != null ? new ArrayList<>(breedingFoods) : new ArrayList<>();
        this.growthTimeMinutes = growthTimeMinutes > 0 ? growthTimeMinutes : 30.0;
        this.breedCooldownMinutes = breedCooldownMinutes > 0 ? breedCooldownMinutes : 5.0;
        this.babyNpcRoleId = babyNpcRoleId;
        this.adultNpcRoleId = adultNpcRoleId != null ? adultNpcRoleId : modelAssetId;
        this.mountable = mountable;
        this.enabled = enabled;
    }

    public String getModelAssetId() {
        return modelAssetId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return modelAssetId.toLowerCase();
    }

    public List<String> getBreedingFoods() {
        return new ArrayList<>(breedingFoods);
    }

    public double getGrowthTimeMinutes() {
        return growthTimeMinutes;
    }

    public double getBreedCooldownMinutes() {
        return breedCooldownMinutes;
    }

    public String getBabyNpcRoleId() {
        return babyNpcRoleId;
    }

    public String getAdultNpcRoleId() {
        return adultNpcRoleId;
    }

    public boolean hasBabyVariant() {
        return babyNpcRoleId != null && !babyNpcRoleId.isEmpty();
    }

    public boolean isMountable() {
        return mountable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if the given item ID is valid breeding food for this animal.
     */
    public boolean isBreedingFood(String itemId) {
        if (itemId == null || breedingFoods.isEmpty()) {
            return false;
        }
        for (String food : breedingFoods) {
            if (food.equalsIgnoreCase(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the scale for a baby of this custom animal.
     * Only used for creatures WITHOUT baby NPC variants.
     */
    public float getBabyScale() {
        return 0.4f;  // 40% size for babies
    }

    @Override
    public String toString() {
        return "CustomAnimal{" + modelAssetId + ", foods=" + breedingFoods.size() + "}";
    }
}
