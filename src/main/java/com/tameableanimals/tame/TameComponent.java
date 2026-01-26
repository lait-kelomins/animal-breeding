package com.tameableanimals.tame;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class TameComponent implements Component<EntityStore> {
    public static final BuilderCodec<TameComponent> CODEC = BuilderCodec.builder(TameComponent.class, TameComponent::new)
            .append(new KeyedCodec<>("IsTamed", Codec.BOOLEAN), (data, value) -> data.isTamed = value, data -> data.isTamed)
            .add()
            .append(new KeyedCodec<>("TamerUUID", Codec.UUID_BINARY), (data, value) -> data.tamerUUID = value, data -> data.tamerUUID)
            .add()
            .append(new KeyedCodec<>("TamerName", Codec.STRING), (data, value) -> data.tamerName = value, data -> data.tamerName)
            .add()
            .append(new KeyedCodec<>("HytameId", Codec.UUID_BINARY), (data, value) -> data.hytameId = value, data -> data.hytameId)
            .add()
            .build();

    public static ComponentType<EntityStore, TameComponent> getComponentType() {
        return LaitsBreedingPlugin.getInstance().getTameComponentType();
    }

    private Boolean isTamed = false;
    private UUID tamerUUID = null;
    private String tamerName = null;
    private UUID hytameId = null;  // Stable ID linking ECS to tamed_animals.json

    public boolean isTamed() {
        return Boolean.TRUE.equals(isTamed);
    }

    public UUID getTamerUUID() {
        return tamerUUID;
    }

    public String getTamerName() {
        return tamerName;
    }

    public UUID getHytameId() {
        return hytameId;
    }

    public void setHytameId(UUID hytameId) {
        this.hytameId = hytameId;
    }

    public void setTamed(@Nonnull UUID player, @Nonnull String playerName) {
        this.isTamed = true;
        this.tamerUUID = player;
        this.tamerName = playerName;
        // Generate hytameId on first tame if not already set
        if (this.hytameId == null) {
            this.hytameId = UUID.randomUUID();
        }
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        TameComponent component = new TameComponent();
        component.isTamed = this.isTamed;
        component.tamerUUID = this.tamerUUID;
        component.tamerName = this.tamerName;
        component.hytameId = this.hytameId;
        return component;
    }
}
