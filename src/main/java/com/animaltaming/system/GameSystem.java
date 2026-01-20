package com.animaltaming.system;

/**
 * Base interface for game systems.
 * Systems process game logic each tick.
 */
public interface GameSystem {

    /**
     * Update the system for one tick.
     *
     * @param context the system context providing world access
     * @param deltaTime time since last tick in seconds
     */
    void update(SystemContext context, float deltaTime);

    /**
     * Get the system's priority.
     * Lower values run first.
     *
     * @return priority value (default 0)
     */
    default int priority() {
        return 0;
    }

    /**
     * Get the system's name for logging.
     *
     * @return system name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Check if the system is enabled.
     *
     * @return true if enabled (default true)
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Called when the system is registered.
     */
    default void onRegister() {
        // Override if needed
    }

    /**
     * Called when the system is unregistered.
     */
    default void onUnregister() {
        // Override if needed
    }
}
