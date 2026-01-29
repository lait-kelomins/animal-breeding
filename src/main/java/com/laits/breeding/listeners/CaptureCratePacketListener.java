package com.laits.breeding.listeners;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.laits.breeding.LaitsBreedingPlugin;
import com.laits.breeding.util.EntityUtil;
import com.laits.breeding.util.EcsReflectionUtil;
import com.laits.breeding.util.NetworkIdCache;
import com.laits.breeding.util.TameHelper;
import com.laits.breeding.managers.TamingManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for capture crate interaction packets to detect when animals
 * are captured or released.
 *
 * Pattern from packet analysis:
 * - CAPTURE: SyncInteractionChains with entityId != -1, rootInteraction=3707, itemInHandId=Tool_Capture_Crate
 * - RELEASE: SyncInteractionChains with entityId == -1, has blockPosition, rootInteraction=3707, itemInHandId=Tool_Capture_Crate
 */
public class CaptureCratePacketListener {

    private static final int SYNC_INTERACTION_CHAINS_PACKET_ID = 290;
    private static final String CAPTURE_CRATE_ITEM_ID = "Tool_Capture_Crate";
    private static final int CAPTURE_CRATE_ROOT_INTERACTION = 3707;

    // Track pending captures: entityId -> (playerUUID, slotIndex)
    // Used to match EntityRemoveEvent with the capturing player
    private static final Map<Integer, PendingCapture> pendingCaptures = new ConcurrentHashMap<>();

    // Track pending releases: positionKey -> playerUUID
    private static final Map<String, PendingRelease> pendingReleases = new ConcurrentHashMap<>();

    private static final long PENDING_TTL_MS = 2000; // 2 seconds TTL

    private final HytaleLogger logger;
    private boolean registered = false;

    public CaptureCratePacketListener(HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Register the packet listener.
     */
    public void register() {
        if (registered) return;

        log("Attempting to register packet listener...");
        try {
            PacketAdapters.registerInbound(this::onInboundPacket);
            registered = true;
            log("Capture crate packet listener registered successfully!");
        } catch (Exception e) {
            log("Failed to register packet listener: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle inbound packets.
     */
    private boolean onInboundPacket(PacketHandler handler, Packet packet) {
        try {
            // Log ALL packets to see if listener is working
            String packetName = packet.getClass().getSimpleName();
            int packetId = packet.getId();

            // Only log interaction-related packets to avoid spam
            if (packetId == SYNC_INTERACTION_CHAINS_PACKET_ID || packetName.contains("Interaction")) {
                log("Received packet: " + packetName + " (id=" + packetId + ")");
            }

            if (packetId != SYNC_INTERACTION_CHAINS_PACKET_ID) {
                return false;
            }

            log("Processing SyncInteractionChains packet...");
            processSyncInteractionChains(handler, packet);
        } catch (Exception e) {
            log("Error in onInboundPacket: " + e.getMessage());
            e.printStackTrace();
        }
        return false; // Allow packet to propagate
    }

    /**
     * Process SyncInteractionChains packet to detect capture/release.
     */
    private void processSyncInteractionChains(PacketHandler handler, Packet packet) {
        try {
            // Get the 'updates' array from the packet
            Object updates = getFieldValue(packet, "updates", Object.class);

            if (updates == null) {
                log("Updates field is null");
                return;
            }

            // Handle array type
            if (updates.getClass().isArray()) {
                Object[] updatesArray = (Object[]) updates;
                log("Found " + updatesArray.length + " updates (array)");

                for (Object update : updatesArray) {
                    processInteractionChainUpdate(handler, update);
                }
            } else if (updates instanceof List) {
                List<?> updatesList = (List<?>) updates;
                log("Found " + updatesList.size() + " updates (list)");

                for (Object update : updatesList) {
                    processInteractionChainUpdate(handler, update);
                }
            } else {
                log("Updates field is unexpected type: " + updates.getClass().getName());
            }
        } catch (Exception e) {
            log("Error in processSyncInteractionChains: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process a single interaction chain update.
     */
    private void processInteractionChainUpdate(PacketHandler handler, Object update) {
        try {
            // Debug: log update class and fields
            log("Update class: " + update.getClass().getSimpleName());

            // Check if this is initial=true (first packet of interaction)
            Boolean initial = getFieldValue(update, "initial", Boolean.class);
            log("  initial=" + initial);
            if (initial == null || !initial) return;

            // Check itemInHandId
            String itemInHandId = getFieldValue(update, "itemInHandId", String.class);
            log("  itemInHandId=" + itemInHandId);
            if (!CAPTURE_CRATE_ITEM_ID.equals(itemInHandId)) return;

            log("  -> Capture crate interaction detected!");

            // Get equipSlot for inventory access
            Integer equipSlot = getFieldValue(update, "equipSlot", Integer.class);

            // Check rootInteraction in interactionData
            Object interactionDataList = getFieldValue(update, "interactionData", Object.class);
            if (interactionDataList instanceof List && !((List<?>) interactionDataList).isEmpty()) {
                Object firstInteraction = ((List<?>) interactionDataList).get(0);
                Integer rootInteraction = getFieldValue(firstInteraction, "rootInteraction", Integer.class);
                if (rootInteraction == null || rootInteraction != CAPTURE_CRATE_ROOT_INTERACTION) {
                    return;
                }
            }

            // Get the chain data
            Object chainData = getFieldValue(update, "data", Object.class);
            if (chainData == null) return;

            // Get entityId from chain data
            Integer entityId = getFieldValue(chainData, "entityId", Integer.class);
            if (entityId == null) return;

            // Get player from handler
            Player player = getPlayerFromHandler(handler);
            if (player == null) {
                log("Could not get player from handler");
                return;
            }

            UUID playerUuid = EntityUtil.getEntityUUID(player);
            if (playerUuid == null) return;

            // Log the item metadata for investigation
            logItemMetadata(player, equipSlot);

            if (entityId != -1) {
                // CAPTURE: Player is capturing an entity
                handleCapture(player, playerUuid, entityId, equipSlot);
            } else {
                // RELEASE: Player is releasing on a block
                Object blockPosition = getFieldValue(chainData, "blockPosition", Object.class);
                if (blockPosition != null) {
                    handleRelease(player, playerUuid, blockPosition, equipSlot);
                }
            }
        } catch (Exception e) {
            log("Error processing interaction chain: " + e.getMessage());
        }
    }

    /**
     * Log item metadata for investigation.
     */
    private void logItemMetadata(Player player, Integer slotIndex) {
        try {
            var inventory = player.getInventory();
            if (inventory == null) {
                log("[ItemMetadata] Inventory is null");
                return;
            }

            // Try to get item at slot
            ItemStack item = null;
            if (slotIndex != null && slotIndex >= 0) {
                try {
                    // Try getHotbarItem if available
                    Method getHotbarItem = inventory.getClass().getMethod("getHotbarItem", int.class);
                    item = (ItemStack) getHotbarItem.invoke(inventory, slotIndex);
                } catch (NoSuchMethodException e) {
                    // Try alternative
                }
            }

            // Fallback to active hotbar item
            if (item == null) {
                item = inventory.getActiveHotbarItem();
            }

            if (item == null) {
                log("[ItemMetadata] Item is null");
                return;
            }

            log("[ItemMetadata] ItemId: " + item.getItemId());
            log("[ItemMetadata] Quantity: " + item.getQuantity());

            // Try to get metadata field
            try {
                Field metadataField = findField(item.getClass(), "metadata");
                if (metadataField != null) {
                    metadataField.setAccessible(true);
                    Object metadata = metadataField.get(item);
                    log("[ItemMetadata] metadata field: " + metadata);
                } else {
                    log("[ItemMetadata] No 'metadata' field found");
                }
            } catch (Exception e) {
                log("[ItemMetadata] Error getting metadata field: " + e.getMessage());
            }

            // Try getMetadata method
            try {
                Method getMetadata = item.getClass().getMethod("getMetadata");
                Object metadata = getMetadata.invoke(item);
                log("[ItemMetadata] getMetadata(): " + metadata);
            } catch (NoSuchMethodException e) {
                log("[ItemMetadata] No getMetadata() method");
            } catch (Exception e) {
                log("[ItemMetadata] Error calling getMetadata(): " + e.getMessage());
            }

            // List all fields for investigation
            log("[ItemMetadata] All fields in ItemStack:");
            for (Field f : item.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(item);
                    String valStr = (val != null) ? val.toString() : "null";
                    if (valStr.length() > 100) valStr = valStr.substring(0, 100) + "...";
                    log("[ItemMetadata]   " + f.getName() + " (" + f.getType().getSimpleName() + "): " + valStr);
                } catch (Exception e) {
                    log("[ItemMetadata]   " + f.getName() + ": <error>");
                }
            }

        } catch (Exception e) {
            log("[ItemMetadata] Error: " + e.getMessage());
        }
    }

    /**
     * Handle capture detection.
     * Schedules entity UUID lookup on WorldThread immediately, before entity is removed.
     */
    private void handleCapture(Player player, UUID playerUuid, int entityId, Integer slotIndex) {
        log("CAPTURE detected: player=" + playerUuid + " entityId(networkId)=" + entityId + " slot=" + slotIndex);

        // Store pending capture by entityId (network ID) as fallback
        pendingCaptures.put(entityId, new PendingCapture(playerUuid, slotIndex != null ? slotIndex : -1));

        // Schedule immediate lookup on WorldThread - this runs BEFORE entity removal
        try {
            World world = player.getWorld();
            if (world != null) {
                final int targetNetworkId = entityId;
                final UUID capturingPlayer = playerUuid;

                world.execute(() -> {
                    try {
                        log("  [WorldThread] Looking up entity with networkId=" + targetNetworkId);
                        final Ref<EntityStore>[] foundRef = new Ref[1];
                        UUID animalUuid = lookupEntityByNetworkId(world, targetNetworkId, foundRef);

                        if (animalUuid != null && foundRef[0] != null) {
                            log("  [WorldThread] Found entity UUID: " + animalUuid);

                            // Log the entity model to verify we found the right entity
                            try {
                                Store<EntityStore> store = foundRef[0].getStore();
                                if (store != null) {
                                    var modelComp = store.getComponent(foundRef[0], EcsReflectionUtil.MODEL_TYPE);
                                    if (modelComp != null && modelComp.getModel() != null) {
                                        log("  [WorldThread] Entity model: " + modelComp.getModel().getModelAssetId());
                                    }
                                }
                            } catch (Exception e) {
                                log("  [WorldThread] Could not get model: " + e.getMessage());
                            }

                            // Get HyTameComponent directly and log all its fields
                            var hyTameComp = TameHelper.getHyTameComponent(foundRef[0]);
                            if (hyTameComp != null) {
                                log("  [WorldThread] HyTameComponent EXISTS:");
                                log("    - isTamed(): " + hyTameComp.isTamed());
                                log("    - getHytameId(): " + hyTameComp.getHytameId());
                                log("    - getTamerUUID(): " + hyTameComp.getTamerUUID());
                                log("    - getTamerName(): " + hyTameComp.getTamerName());
                                // Note: customName is stored in TamedAnimalData, not HyTameComponent

                                UUID hytameId = hyTameComp.getHytameId();
                                if (hytameId != null && hyTameComp.isTamed()) {
                                    log("  [WorldThread] Entity is tamed! Registering by hytameId=" + hytameId);
                                    // Register by hytameId for more reliable matching
                                    CoopResidentTracker.registerPendingCaptureByHytameId(hytameId, capturingPlayer);
                                } else if (hyTameComp.isTamed()) {
                                    log("  [WorldThread] Entity is tamed but no hytameId, using UUID");
                                    CoopResidentTracker.registerPendingCapture(animalUuid, capturingPlayer);
                                } else {
                                    log("  [WorldThread] HyTameComponent exists but isTamed()=false");
                                }
                            } else {
                                log("  [WorldThread] HyTameComponent is NULL on this entity");
                            }

                            // Also log TamingManager state for comparison
                            LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
                            if (plugin != null) {
                                TamingManager tamingManager = plugin.getTamingManager();
                                if (tamingManager != null) {
                                    log("  [WorldThread] TamingManager has " + tamingManager.getTamedCount() + " total tamed animals");
                                    // Try to find by UUID
                                    boolean managerTamed = tamingManager.isTamed(animalUuid);
                                    log("  [WorldThread] TamingManager.isTamed(entityUUID=" + animalUuid + ") = " + managerTamed);
                                }
                            }
                        } else {
                            log("  [WorldThread] Could not find entity with networkId=" + targetNetworkId);
                        }
                    } catch (Exception e) {
                        log("  [WorldThread] Error: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log("  Failed to schedule WorldThread lookup: " + e.getMessage());
        }

        // Cleanup old entries
        cleanupExpired();
    }

    /**
     * Look up an entity's UUID by its network ID (from packet entityId).
     * Uses cached lookup first (O(1)), falls back to brute force if not in cache.
     * Also populates foundRefOut[0] with the found Ref for further use.
     */
    @SuppressWarnings("unchecked")
    private UUID lookupEntityByNetworkId(World world, int targetNetworkId, Ref<EntityStore>[] foundRefOut) {
        try {
            // First try the cache (O(1) lookup)
            Ref<EntityStore> cachedRef = NetworkIdCache.getByNetworkId(targetNetworkId);
            if (cachedRef != null && cachedRef.isValid()) {
                if (foundRefOut != null) {
                    foundRefOut[0] = cachedRef;
                }
                return EcsReflectionUtil.getUuidFromRef(cachedRef);
            }

            // Fallback to brute force if not in cache
            EntityStore entityStore = world.getEntityStore();
            if (entityStore == null) return null;

            Store<EntityStore> store = entityStore.getStore();
            if (store == null) return null;

            final UUID[] foundUuid = {null};
            final Ref<EntityStore>[] foundRef = new Ref[1];

            store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> buffer) -> {
                if (foundUuid[0] != null) return;

                int chunkSize = chunk.size();
                for (int i = 0; i < chunkSize; i++) {
                    try {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;

                        // Get NetworkId component and compare
                        var networkIdComp = store.getComponent(ref, EcsReflectionUtil.NETWORK_ID_TYPE);
                        if (networkIdComp != null) {
                            int networkId = networkIdComp.getId();
                            if (networkId == targetNetworkId) {
                                foundUuid[0] = EcsReflectionUtil.getUuidFromRef(ref);
                                foundRef[0] = ref;
                                return;
                            }
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }
            });

            // Copy found ref to output parameter
            if (foundRefOut != null && foundRef[0] != null) {
                foundRefOut[0] = foundRef[0];
            }

            return foundUuid[0];
        } catch (Exception e) {
            log("  lookupEntityByNetworkId error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handle release detection.
     */
    private void handleRelease(Player player, UUID playerUuid, Object blockPosition, Integer slotIndex) {
        try {
            Integer x = getFieldValue(blockPosition, "x", Integer.class);
            Integer y = getFieldValue(blockPosition, "y", Integer.class);
            Integer z = getFieldValue(blockPosition, "z", Integer.class);

            if (x == null || y == null || z == null) return;

            log("RELEASE detected: player=" + playerUuid + " pos=" + x + "," + y + "," + z + " slot=" + slotIndex);

            // Store pending release for matching when animal spawns
            String posKey = x + "," + y + "," + z;
            pendingReleases.put(posKey, new PendingRelease(playerUuid, slotIndex != null ? slotIndex : -1));

            // Also store nearby positions (animal might spawn slightly offset)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    String nearbyKey = (x + dx) + "," + y + "," + (z + dz);
                    pendingReleases.put(nearbyKey, new PendingRelease(playerUuid, slotIndex != null ? slotIndex : -1));
                }
            }

            // Cleanup old entries
            cleanupExpired();
        } catch (Exception e) {
            log("Error handling release: " + e.getMessage());
        }
    }

    /**
     * Check if there's a pending capture for an entity ID.
     * Called from EntityRemoveEvent handler.
     */
    public static PendingCapture consumePendingCapture(int entityId) {
        PendingCapture pending = pendingCaptures.remove(entityId);
        if (pending != null && !pending.isExpired()) {
            return pending;
        }
        return null;
    }

    /**
     * Check if there's a pending release near a position.
     * Called by NewAnimalSpawnDetector when a new animal spawns.
     */
    public static PendingRelease consumePendingRelease(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    String key = (bx + dx) + "," + (by + dy) + "," + (bz + dz);
                    PendingRelease pending = pendingReleases.remove(key);
                    if (pending != null && !pending.isExpired()) {
                        logStatic("Consumed pending release at " + key);
                        return pending;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get player from packet handler.
     */
    private Player getPlayerFromHandler(PacketHandler handler) {
        try {
            log("Handler class: " + handler.getClass().getName());

            // List all fields on handler
            log("Handler fields:");
            for (Field f : handler.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(handler);
                    String typeName = f.getType().getSimpleName();
                    String valStr = (val != null) ? val.getClass().getSimpleName() : "null";
                    log("  " + f.getName() + " (" + typeName + "): " + valStr);
                } catch (Exception e) {
                    log("  " + f.getName() + ": <error>");
                }
            }

            // List all methods on handler
            log("Handler methods:");
            for (var m : handler.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getName().startsWith("get")) {
                    log("  " + m.getName() + "() -> " + m.getReturnType().getSimpleName());
                }
            }

            // Try common field names
            for (String fieldName : new String[]{"playerComponent", "player", "connection", "owner", "session", "client"}) {
                Field field = findField(handler.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    Object value = field.get(handler);
                    log("Found field '" + fieldName + "': " + (value != null ? value.getClass().getName() : "null"));
                    if (value instanceof Player) {
                        return (Player) value;
                    }
                    if (value != null) {
                        // Try to find player in nested object
                        Field playerField = findField(value.getClass(), "player");
                        if (playerField != null) {
                            playerField.setAccessible(true);
                            Object playerValue = playerField.get(value);
                            if (playerValue instanceof Player) {
                                return (Player) playerValue;
                            }
                        }
                    }
                }
            }

            // Try getPlayer() method
            try {
                var method = handler.getClass().getMethod("getPlayer");
                Object result = method.invoke(handler);
                if (result instanceof Player) {
                    return (Player) result;
                }
            } catch (NoSuchMethodException e) {
                // Continue
            }
        } catch (Exception e) {
            log("Error getting player: " + e.getMessage());
        }
        return null;
    }

    private Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> type) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (type.isInstance(value)) {
                    return (T) value;
                }
                if (value != null) {
                    if (type == Integer.class && value instanceof Number) {
                        return (T) Integer.valueOf(((Number) value).intValue());
                    }
                    if (type == Boolean.class && value instanceof Boolean) {
                        return (T) value;
                    }
                }
            }
        } catch (Exception e) {
            // Silent
        }
        return null;
    }

    private static void cleanupExpired() {
        pendingCaptures.entrySet().removeIf(entry -> entry.getValue().isExpired());
        pendingReleases.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void log(String message) {
        if (logger != null && LaitsBreedingPlugin.isVerboseLogging()) {
            logger.atInfo().log("[CaptureCrate] " + message);
        }
    }

    private static void logStatic(String message) {
        LaitsBreedingPlugin plugin = LaitsBreedingPlugin.getInstance();
        if (plugin != null && LaitsBreedingPlugin.isVerboseLogging()) {
            plugin.getLogger().atInfo().log("[CaptureCrate] " + message);
        }
    }

    /**
     * Pending capture info.
     */
    public static class PendingCapture {
        public final UUID playerUuid;
        public final int slotIndex;
        public final long timestamp;

        PendingCapture(UUID playerUuid, int slotIndex) {
            this.playerUuid = playerUuid;
            this.slotIndex = slotIndex;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > PENDING_TTL_MS;
        }
    }

    /**
     * Pending release info.
     */
    public static class PendingRelease {
        public final UUID playerUuid;
        public final int slotIndex;
        public final long timestamp;

        PendingRelease(UUID playerUuid, int slotIndex) {
            this.playerUuid = playerUuid;
            this.slotIndex = slotIndex;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > PENDING_TTL_MS;
        }
    }
}
