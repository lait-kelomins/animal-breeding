package com.animaltaming.persistence.codec;

import com.animaltaming.api.model.BehaviorMode;
import com.animaltaming.api.model.TamedAnimal;
import com.google.gson.*;

import java.util.UUID;

/**
 * JSON codec for TamedAnimal serialization/deserialization.
 * Provides bidirectional conversion with validation.
 */
public class TamedAnimalCodec {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Encode a TamedAnimal to JSON string.
     *
     * @param animal the animal to encode
     * @return JSON string
     */
    public String encode(TamedAnimal animal) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", animal.id().toString());
        obj.addProperty("ownerId", animal.ownerId().toString());
        obj.addProperty("ownerName", animal.ownerName());
        obj.addProperty("speciesId", animal.speciesId());
        obj.addProperty("mode", animal.mode().name());
        obj.addProperty("homeX", animal.homeX());
        obj.addProperty("homeY", animal.homeY());
        obj.addProperty("homeZ", animal.homeZ());
        obj.addProperty("maxFollowDistance", animal.maxFollowDistance());
        obj.addProperty("tamedTimestamp", animal.tamedTimestamp());
        if (animal.customName() != null) {
            obj.addProperty("customName", animal.customName());
        }
        return gson.toJson(obj);
    }

    /**
     * Encode a TamedAnimal to JsonElement.
     *
     * @param animal the animal to encode
     * @return JsonElement
     */
    public JsonElement encodeToElement(TamedAnimal animal) {
        return JsonParser.parseString(encode(animal));
    }

    /**
     * Decode a TamedAnimal from JSON string.
     *
     * @param json the JSON string
     * @return decoded TamedAnimal
     * @throws IllegalArgumentException if JSON is invalid or missing required fields
     */
    public TamedAnimal decode(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return decodeFromElement(obj);
    }

    /**
     * Decode a TamedAnimal from JsonElement.
     *
     * @param element the JSON element
     * @return decoded TamedAnimal
     * @throws IllegalArgumentException if JSON is invalid or missing required fields
     */
    public TamedAnimal decodeFromElement(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }

        JsonObject obj = element.getAsJsonObject();

        // Required fields
        UUID id = getRequiredUUID(obj, "id");
        UUID ownerId = getRequiredUUID(obj, "ownerId");
        String ownerName = getRequiredString(obj, "ownerName");
        String speciesId = getRequiredString(obj, "speciesId");
        BehaviorMode mode = getRequiredEnum(obj, "mode", BehaviorMode.class);
        double homeX = getRequiredDouble(obj, "homeX");
        double homeY = getRequiredDouble(obj, "homeY");
        double homeZ = getRequiredDouble(obj, "homeZ");
        double maxFollowDistance = getRequiredDouble(obj, "maxFollowDistance");
        long tamedTimestamp = getRequiredLong(obj, "tamedTimestamp");

        // Optional fields
        String customName = obj.has("customName") && !obj.get("customName").isJsonNull()
                ? obj.get("customName").getAsString()
                : null;

        return new TamedAnimal(
                id, ownerId, ownerName, speciesId, mode,
                homeX, homeY, homeZ, maxFollowDistance,
                tamedTimestamp, customName
        );
    }

    private UUID getRequiredUUID(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        try {
            return UUID.fromString(obj.get(field).getAsString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID for field: " + field);
        }
    }

    private String getRequiredString(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return obj.get(field).getAsString();
    }

    private double getRequiredDouble(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return obj.get(field).getAsDouble();
    }

    private long getRequiredLong(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return obj.get(field).getAsLong();
    }

    private <T extends Enum<T>> T getRequiredEnum(JsonObject obj, String field, Class<T> enumClass) {
        String value = getRequiredString(obj, field);
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for enum field: " + field);
        }
    }
}
