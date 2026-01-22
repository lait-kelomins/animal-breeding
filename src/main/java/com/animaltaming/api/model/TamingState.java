package com.animaltaming.api.model;

/**
 * States in the taming process.
 */
public enum TamingState {
    /**
     * Animal is wild and will flee from players.
     */
    WILD,

    /**
     * Player is sneaking nearby, calming timer is running.
     */
    CALMING,

    /**
     * Animal is calmed and can be fed or mounted.
     */
    CALMED,

    /**
     * Trust is being built through feeding.
     */
    BONDING_FEED,

    /**
     * Trust is being built through mounting (rideable animals).
     */
    BONDING_MOUNT,

    /**
     * Animal is fully tamed and has an owner.
     */
    TAMED
}
