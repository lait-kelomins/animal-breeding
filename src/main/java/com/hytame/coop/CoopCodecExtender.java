package com.hytame.coop;

import com.hypixel.hytale.builtin.adventure.farming.states.CoopBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.builder.BuilderField;
import com.hypixel.hytale.server.npc.metadata.CapturedNPCMetadata;
import com.hytame.HyTamePlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Extends CapturedNPCMetadata.CODEC to persist HyTame taming data
 * alongside vanilla coop metadata via a companion WeakHashMap.
 *
 * How it works:
 * - Uses a temporary BuilderCodec.Builder with the public append()/add() API
 *   to create BuilderField instances for HyTame fields
 * - Injects those fields into CapturedNPCMetadata.CODEC's mutable entries map
 *   (single reflection point: the protected 'entries' field)
 * - Each field's setter/getter lambdas read/write from a WeakHashMap keyed
 *   by CapturedNPCMetadata instance reference
 * - BSON encode: entries.values() includes our fields; getter returns null
 *   for vanilla instances -> field skipped
 * - BSON decode: findEntry() finds our entries by key; setter stores in companion map
 * - Backward compatible: old chunk data without HyTame keys -> fields not found -> not set
 *
 * Note: stringTreeMap (used for JSON decode) is NOT updated because coop resident
 * data is chunk state serialized via BSON, not JSON asset data.
 */
public class CoopCodecExtender {

    private static final Map<CapturedNPCMetadata, HyTameCoopData> companionMap =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean extended = false;

    /**
     * Extend CapturedNPCMetadata.CODEC with HyTame fields.
     * Must be called once at plugin startup, before any world/chunk loading.
     */
    @SuppressWarnings("unchecked")
    public static void extend() {
        if (extended) return;

        try {
            BuilderCodec<CapturedNPCMetadata> codec = CapturedNPCMetadata.CODEC;

            // 1. Create a temporary builder to leverage the public API for field creation
            BuilderCodec.Builder<CapturedNPCMetadata> tempBuilder =
                    BuilderCodec.builder(CapturedNPCMetadata.class, codec.getSupplier());

            // 2. Define each HyTame field using the public append()/add() API
            appendField(tempBuilder, "HyTame.Id", Codec.STRING,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setHytameIdStr(v),
                    meta -> Optional.ofNullable(companionMap.get(meta)).map(HyTameCoopData::getHytameIdStr).orElse(null));

            appendField(tempBuilder, "HyTame.OwnerUuid", Codec.STRING,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setOwnerUuidStr(v),
                    meta -> Optional.ofNullable(companionMap.get(meta)).map(HyTameCoopData::getOwnerUuidStr).orElse(null));

            appendField(tempBuilder, "HyTame.OwnerName", Codec.STRING,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setOwnerName(v),
                    meta -> Optional.ofNullable(companionMap.get(meta)).map(HyTameCoopData::getOwnerName).orElse(null));

            appendField(tempBuilder, "HyTame.CustomName", Codec.STRING,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setCustomName(v),
                    meta -> Optional.ofNullable(companionMap.get(meta)).map(HyTameCoopData::getCustomName).orElse(null));

            appendField(tempBuilder, "HyTame.AnimalType", Codec.STRING,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setAnimalType(v),
                    meta -> Optional.ofNullable(companionMap.get(meta)).map(HyTameCoopData::getAnimalType).orElse(null));

            appendField(tempBuilder, "HyTame.Tamed", Codec.BOOLEAN,
                    (meta, v) -> companionMap.computeIfAbsent(meta, k -> new HyTameCoopData()).setTamed(Boolean.TRUE.equals(v)),
                    meta -> Optional.ofNullable(companionMap.get(meta)).filter(HyTameCoopData::isTamed).map(d -> true).orElse(null));

            // 3. Build temp codec to access created fields via public getEntries()
            BuilderCodec<CapturedNPCMetadata> tempCodec = tempBuilder.build();

            // 4. Get the real codec's mutable entries map via reflection (ONLY reflection point)
            Field entriesField = BuilderCodec.class.getDeclaredField("entries");
            entriesField.setAccessible(true);
            Map<String, List<BuilderField<CapturedNPCMetadata, ?>>> realEntries =
                    (Map<String, List<BuilderField<CapturedNPCMetadata, ?>>>) entriesField.get(codec);

            // 5. Copy fields from temp codec into the real codec's entries
            for (Map.Entry<String, List<BuilderField<CapturedNPCMetadata, ?>>> entry : tempCodec.getEntries().entrySet()) {
                realEntries.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }

            extended = true;
            System.out.println("[COOP-CODEC] CapturedNPCMetadata.CODEC extended with 6 HyTame fields (entries now has " + realEntries.size() + " keys)");
            log("CapturedNPCMetadata.CODEC extended with 6 HyTame fields");

        } catch (Exception e) {
            System.out.println("[COOP-CODEC] FAILED to extend CODEC: " + e.getMessage());
            HyTamePlugin plugin = HyTamePlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().atWarning().log("[CoopCodecExtender] Failed to extend CODEC: " + e.getMessage());
            }
            e.printStackTrace();
        }
    }

    /**
     * Append a field to the builder using the public append()/add() API.
     */
    private static <FT> void appendField(
            BuilderCodec.Builder<CapturedNPCMetadata> builder,
            String key,
            Codec<FT> codecType,
            BiConsumer<CapturedNPCMetadata, FT> setter,
            Function<CapturedNPCMetadata, FT> getter) {
        builder.append(new KeyedCodec<>(key, codecType), setter, getter).add();
    }

    // --- Public API ---

    /**
     * Associate HyTame data with a CapturedNPCMetadata instance.
     * The data will be encoded to BSON when the chunk saves.
     */
    public static void setHyTameData(CapturedNPCMetadata metadata, HyTameCoopData data) {
        if (metadata == null || data == null) return;
        companionMap.put(metadata, data);
        log("setHyTameData: " + data);
    }

    /**
     * Retrieve HyTame data from a CapturedNPCMetadata instance.
     * Returns null for vanilla (non-tamed) metadata instances.
     */
    public static HyTameCoopData getHyTameData(CapturedNPCMetadata metadata) {
        if (metadata == null) return null;
        return companionMap.get(metadata);
    }

    /**
     * Check if a metadata instance has HyTame data associated.
     */
    public static boolean hasHyTameData(CapturedNPCMetadata metadata) {
        return metadata != null && companionMap.containsKey(metadata);
    }

    // --- CoopBlock.residents accessor ---

    private static Field residentsField;

    /**
     * Get the list of CoopResidents from a CoopBlock via reflection.
     * (CoopBlock.residents is protected with no public getter)
     */
    @SuppressWarnings("unchecked")
    public static List<CoopBlock.CoopResident> getResidents(CoopBlock coopBlock) {
        if (coopBlock == null) return null;
        try {
            if (residentsField == null) {
                residentsField = CoopBlock.class.getDeclaredField("residents");
                residentsField.setAccessible(true);
            }
            return (List<CoopBlock.CoopResident>) residentsField.get(coopBlock);
        } catch (Exception e) {
            log("Failed to access CoopBlock.residents: " + e.getMessage());
            return null;
        }
    }

    private static void log(String message) {
        if (!HyTamePlugin.isVerboseLogging()) return;
        HyTamePlugin plugin = HyTamePlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().atInfo().log("[CoopCodecExtender] " + message);
        }
    }
}
