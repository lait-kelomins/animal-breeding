package com.animaltaming.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple thread-safe publish-subscribe event bus.
 * Supports typed event handlers with priority ordering.
 */
public class EventBus {

    private final Map<Class<?>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType the event class to subscribe to
     * @param handler the handler to call when event is published
     * @param <T> the event type
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribe(eventType, handler, 0);
    }

    /**
     * Subscribe to events with a priority.
     * Lower priority values run first.
     *
     * @param eventType the event class to subscribe to
     * @param handler the handler to call when event is published
     * @param priority handler priority (lower runs first)
     * @param <T> the event type
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler, int priority) {
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(handler, "handler is required");

        List<HandlerEntry<?>> list = handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(new HandlerEntry<>(handler, priority));

        // Sort by priority (lower first)
        list.sort(Comparator.comparingInt(e -> e.priority));
    }

    /**
     * Unsubscribe a handler from an event type.
     *
     * @param eventType the event class
     * @param handler the handler to remove
     * @param <T> the event type
     */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<HandlerEntry<?>> list = handlers.get(eventType);
        if (list != null) {
            list.removeIf(entry -> entry.handler.equals(handler));
        }
    }

    /**
     * Publish an event to all registered handlers.
     * Handlers are called in priority order.
     * Handler exceptions are caught and logged, not propagated.
     *
     * @param event the event to publish
     * @param <T> the event type
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        Objects.requireNonNull(event, "event is required");

        List<HandlerEntry<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }

        for (HandlerEntry<?> entry : list) {
            try {
                ((Consumer<T>) entry.handler).accept(event);
            } catch (RuntimeException e) {
                // Log but don't propagate - one handler failure shouldn't stop others
                System.err.println("[EventBus] Handler exception for " + event.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Check if there are any handlers for an event type.
     *
     * @param eventType the event class
     * @return true if handlers exist
     */
    public boolean hasHandlers(Class<?> eventType) {
        List<HandlerEntry<?>> list = handlers.get(eventType);
        return list != null && !list.isEmpty();
    }

    /**
     * Get the number of handlers for an event type.
     *
     * @param eventType the event class
     * @return handler count
     */
    public int getHandlerCount(Class<?> eventType) {
        List<HandlerEntry<?>> list = handlers.get(eventType);
        return list == null ? 0 : list.size();
    }

    /**
     * Clear all handlers.
     */
    public void clear() {
        handlers.clear();
    }

    /**
     * Clear handlers for a specific event type.
     *
     * @param eventType the event class
     */
    public void clear(Class<?> eventType) {
        handlers.remove(eventType);
    }

    private record HandlerEntry<T>(Consumer<T> handler, int priority) {}
}
