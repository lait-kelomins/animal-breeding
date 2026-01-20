package com.laits.breeding.listeners;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems.StatModifyingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.laits.breeding.LaitsBreedingPlugin;

import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

public class LaitDamageDisabler extends DamageEventSystem implements StatModifyingSystem {
    @Nonnull
    private static final Query QUERY = EntityStatMap.getComponentType();
    @Nonnull
    private static final Set DEPENDENCIES;
    // $FF: synthetic field
    static final boolean $assertionsDisabled = !DamageSystems.class.desiredAssertionStatus();

    public Query getQuery() {
        return QUERY;
    }

    @Nonnull
    public Set getDependencies() {
        return DEPENDENCIES;
    }

    public void handle(int index, @Nonnull ArchetypeChunk archetypeChunk, @Nonnull Store store,
            @Nonnull CommandBuffer commandBuffer, @Nonnull Damage damage) {
        EntityStatMap entityStatMapComponent = (EntityStatMap) archetypeChunk.getComponent(index, EntityStatMap.getComponentType());
        if (!$assertionsDisabled && entityStatMapComponent == null) {
            throw new AssertionError();
        } else {
            
            Archetype archetype = archetypeChunk.getArchetype();
            boolean dead = archetype.contains(DeathComponent.getComponentType());

            if (dead) {
                // Disable all damage by setting damage amount to zero
                damage.setCancelled(true);
            }
        }
    }

    static {
        DEPENDENCIES = Set.of(new SystemGroupDependency(Order.AFTER, DamageModule.get().getInspectDamageGroup()), new SystemDependency(Order.BEFORE, DeathSystems.ClearHealth.class));
    }
    

    private void log(String message) {
        
            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atInfo()
                        .log("[Lait:AnimalBreeding] " + message);
            }
    }
}