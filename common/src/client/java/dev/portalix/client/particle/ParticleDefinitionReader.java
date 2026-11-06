package dev.portalix.client.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

final class ParticleDefinitionReader {
  private ParticleDefinitionReader() {}

  static float scalar(JsonObject value, String field) {
    var primitive = value.getAsJsonPrimitive(field);
    if (primitive.isBoolean()) return primitive.getAsBoolean() ? 1 : 0;
    return primitive.getAsFloat();
  }

  static Vector3f vector(JsonObject value, String field) {
    JsonArray values = value.getAsJsonArray(field);
    return new Vector3f(
        values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
  }

  static String name(JsonObject value) {
    return value.get("functionName").getAsString();
  }

  static List<JsonObject> operators(JsonObject definition, String group) {
    List<JsonObject> result = new ArrayList<>();
    for (JsonElement item : definition.getAsJsonArray(group)) result.add(item.getAsJsonObject());
    return result;
  }
}
