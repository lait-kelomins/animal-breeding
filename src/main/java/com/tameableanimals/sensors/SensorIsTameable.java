package com.tameableanimals.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.models.AnimalType;
import com.laits.breeding.models.CustomAnimalConfig;
import com.laits.breeding.util.ConfigManager;
import com.laits.breeding.util.EcsReflectionUtil;
import com.tameableanimals.tame.HyTameComponent;

import javax.annotation.Nonnull;

public class SensorIsTameable extends SensorBase {
    protected final boolean value;

    public SensorIsTameable(@Nonnull BuilderSensorIsTameable builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.value = builder.getValue(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt,
            @Nonnull Store<EntityStore> store) {
        ModelComponent modelComponent = store.getComponent(ref, EcsReflectionUtil.MODEL_TYPE);
        String modelAssetId = modelComponent.getModel().getModelAssetId();

        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        ConfigManager configManager = plugin.getConfigManager();

        CustomAnimalConfig customAnimal = null;

        AnimalType animalType = AnimalType.fromModelAssetId(modelAssetId);

        // Debug logging (only when debug mode enabled)
        if (configManager.isDebugMode()) {
            boolean breedingEnabled = animalType != null && configManager.isBreedingEnabled(animalType);
            boolean tamingEnabled = animalType != null && configManager.isTamingEnabled(animalType);
            plugin.getLogger().atInfo().log("[IsTameable] modelAssetId=%s, animalType=%s, breedingEnabled=%s, tamingEnabled=%s",
                      modelAssetId, animalType, breedingEnabled, tamingEnabled);
        }

        if (animalType != null && (configManager.isBreedingEnabled(animalType)
                || configManager.isTamingEnabled(animalType))) {
            return super.matches(ref, role, dt, store);
        }

        if (animalType == null) {
            customAnimal = configManager.getCustomAnimal(modelAssetId);
        }
        else
        {
            return false;
        }

        if (customAnimal != null && (configManager.isCustomAnimalBreedingEnabled(customAnimal.getModelAssetId())
                || configManager.isCustomAnimalTamingEnabled(customAnimal.getModelAssetId())))
            return super.matches(ref, role, dt, store);

        return false;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
