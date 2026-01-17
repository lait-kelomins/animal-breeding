package com.laits.breeding.listeners;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ECS-based event handler for block interactions.
 */
public class UseBlockHandler extends EntityEventSystem<EntityStore, UseBlockEvent> {

    private static int eventCount = 0;
    private static String lastPlayer = "none";
    private static long lastEventTime = 0;

    public UseBlockHandler() {
        super(UseBlockEvent.class);
    }

    public static int getEventCount() { return eventCount; }
    public static String getLastPlayer() { return lastPlayer; }
    public static long getLastEventTime() { return lastEventTime; }

    @Override
    public void handle(
            int entityIndex,
            @NotNull ArchetypeChunk<EntityStore> chunk,
            @NotNull Store<EntityStore> store,
            @NotNull CommandBuffer<EntityStore> buffer,
            @NotNull UseBlockEvent event
    ) {
        eventCount++;
        lastEventTime = System.currentTimeMillis();

        try {
            PlayerRef playerRef = chunk.getComponent(entityIndex, PlayerRef.getComponentType());
            if (playerRef != null) {
                lastPlayer = playerRef.getUsername();
            }
        } catch (Exception e) {
            // Silent
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
