package com.tameableanimals.config;

import java.util.HashSet;
import java.util.Set;

public class TameableAnimalsConfig {

    private boolean debugChatMessages = true;
    private Set<String> tameableAnimalGroups = new HashSet<>();

    public TameableAnimalsConfig() {
        tameableAnimalGroups.add("PreyBig");
        tameableAnimalGroups.add("PreySmall");
        tameableAnimalGroups.add("Livestock");
        tameableAnimalGroups.add("Critters");
    }

    public Set<String> getTameableAnimalGroups() { return tameableAnimalGroups; }
    public void setTameableAnimalGroups(Set<String> tameableAnimalGroups) { this.tameableAnimalGroups = tameableAnimalGroups; }

    public boolean getDebugChatMessages() { return debugChatMessages; }
    public void setDebugChatMessages(boolean debugChatMessages) { this.debugChatMessages = debugChatMessages; }

    // Allows for malformed configs, only overwriting the supplied fields
    public void loadTameConfig(TameableAnimalsConfig other) {
        if (other == null) return;
        if (other.getTameableAnimalGroups() != null) setTameableAnimalGroups(other.getTameableAnimalGroups());
        setDebugChatMessages(other.getDebugChatMessages());
    }
}
