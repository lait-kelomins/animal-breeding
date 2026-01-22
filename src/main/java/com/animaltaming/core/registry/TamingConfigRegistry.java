package com.animaltaming.core.registry;

import com.animaltaming.api.model.TamingConfig;

import java.util.*;

/**
 * Registry for species taming configurations.
 * Provides O(1) lookup by speciesId.
 * Thread-safe for reads after initial loading.
 */
public class TamingConfigRegistry {

    private final Map<String, TamingConfig> configs = new HashMap<>();

    /**
     * Register a species configuration.
     * Throws if a config with the same speciesId already exists.
     *
     * @param config the configuration to register
     * @throws IllegalStateException if speciesId is already registered
     */
    public void register(TamingConfig config) {
        Objects.requireNonNull(config, "config is required");

        if (configs.containsKey(config.speciesId())) {
            throw new IllegalStateException("Species already registered: " + config.speciesId());
        }

        configs.put(config.speciesId(), config);
    }

    /**
     * Register multiple configurations.
     *
     * @param configList list of configurations to register
     */
    public void registerAll(Collection<TamingConfig> configList) {
        for (TamingConfig config : configList) {
            register(config);
        }
    }

    /**
     * Get a configuration by species ID.
     *
     * @param speciesId the species identifier
     * @return the configuration, or empty if not found
     */
    public Optional<TamingConfig> get(String speciesId) {
        return Optional.ofNullable(configs.get(speciesId));
    }

    /**
     * Check if a species is registered.
     *
     * @param speciesId the species identifier
     * @return true if registered
     */
    public boolean contains(String speciesId) {
        return configs.containsKey(speciesId);
    }

    /**
     * Get all registered species IDs.
     *
     * @return unmodifiable set of species IDs
     */
    public Set<String> getAllSpeciesIds() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    /**
     * Get all registered configurations.
     *
     * @return unmodifiable collection of configs
     */
    public Collection<TamingConfig> getAllConfigs() {
        return Collections.unmodifiableCollection(configs.values());
    }

    /**
     * Get the number of registered species.
     *
     * @return count
     */
    public int size() {
        return configs.size();
    }

    /**
     * Clear all registrations.
     */
    public void clear() {
        configs.clear();
    }
}
