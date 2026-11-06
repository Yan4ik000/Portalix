package dev.portalix.client.gun.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.portalix.client.gun.model.GunModelAsset.Attachment;
import dev.portalix.client.gun.model.GunModelAsset.Group;
import dev.portalix.client.gun.model.GunModelAsset.MaterialMode;
import dev.portalix.resource.ModAssetLoader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class GunModelLoader {
  private static final int MESH_MAGIC = 0x50474d32;

  private GunModelLoader() {}

  private static int count(DataInputStream in, int maximum, String label) throws IOException {
    int value = in.readInt();
    if (value >= 0 && value <= maximum) {
      return value;
    } else {
      throw new IOException("Invalid " + label + " count: " + value);
    }
  }

  private static float finite(DataInputStream in) throws IOException {
    float value = in.readFloat();
    if (!Float.isFinite(value)) {
      throw new IOException("Nonfinite mesh attribute");
    } else {
      return value;
    }
  }

  private static Vector3f metadataVector(JsonObject metadata, String name) {
    JsonArray values = metadata.getAsJsonArray(name);
    return new Vector3f(
        values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
  }

  static GunModelAsset load(String kind) {
    List<MeshGroup> meshGroups = new ArrayList<>();
    List<Group> groups = new ArrayList<>();
    Map<String, float[][]> clips = new HashMap<>();
    Map<String, Attachment> meshAttachments = new HashMap<>();
    Map<GunAttachment, Attachment> attachments = new EnumMap<>(GunAttachment.class);
    int[] parents;
    Matrix4f[] inverseBind;
    float animationFps;
    int gripBone;
    Vector3f gripOffset;
    Map<GunPose, Matrix4f[]> presentation = new EnumMap<>(GunPose.class);

    try (InputStream raw = ModAssetLoader.open("/assets/portalix/meshes/" + kind + ".pgmesh");
        DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
      if (in.readInt() != MESH_MAGIC) {
        throw new IOException("Bad mesh header");
      }

      int bones = count(in, 256, "bones");
      if (bones == 0) {
        throw new IOException("Mesh has no bones");
      }

      parents = new int[bones];
      inverseBind = new Matrix4f[bones];

      for (int i = 0; i < bones; i++) {
        parents[i] = in.readInt();
        if (parents[i] < -1 || parents[i] >= i) {
          throw new IOException("Invalid bone hierarchy at " + i);
        }
      }

      for (int i = 0; i < bones; i++) {
        float[] matrix = new float[12];

        for (int j = 0; j < 12; j++) {
          matrix[j] = finite(in);
        }

        inverseBind[i] =
            new Matrix4f(
                matrix[0], matrix[4], matrix[8], 0.0F,
                matrix[1], matrix[5], matrix[9], 0.0F,
                matrix[2], matrix[6], matrix[10], 0.0F,
                matrix[3], matrix[7], matrix[11], 1.0F);
      }

      int groupCount = count(in, 128, "groups");

      for (int i = 0; i < groupCount; i++) {
        String material = readString(in);
        float[] vertices = new float[count(in, 300000, "vertices") * 14];

        for (int j = 0; j < vertices.length; j++) {
          vertices[j] = finite(in);
        }

        meshGroups.add(new MeshGroup(material, vertices));
      }

      int animations = count(in, 128, "animations");

      for (int i = 0; i < animations; i++) {
        String name = readString(in);
        float[][] frames = new float[count(in, 36000, "frames")][bones * 7];

        for (float[] frame : frames) {
          for (int j = 0; j < frame.length; j++) {
            frame[j] = finite(in);
          }
        }

        clips.put(name, frames);
      }

      int attachmentCount = count(in, 256, "attachments");

      for (int i = 0; i < attachmentCount; i++) {
        String name = readString(in);
        Attachment attachment =
            new Attachment(in.readInt(), new Vector3f(finite(in), finite(in), finite(in)));
        if (meshAttachments.putIfAbsent(name, attachment) != null)
          throw new IOException("Duplicate mesh attachment: " + name);
      }

      if (in.read() != -1) {
        throw new IOException("Trailing mesh data");
      }

      List<String> missingSockets = new ArrayList<>();
      for (GunAttachment socket : GunAttachment.values()) {
        Attachment attachment = meshAttachments.get(socket.key());
        if (attachment == null) missingSockets.add(socket.key());
        else attachments.put(socket, attachment);
      }
      if (!missingSockets.isEmpty())
        throw new IOException("Missing mesh attachments: " + String.join(", ", missingSockets));
      for (Attachment attachment : meshAttachments.values()) {
        if (attachment.bone() < 0 || attachment.bone() >= bones) {
          throw new IOException("Invalid attachment bone");
        }
      }

      if (!clips.containsKey("idle")) {
        throw new IOException("Missing idle animation");
      }

      for (Entry<String, float[][]> entry : clips.entrySet()) {
        if (entry.getValue().length == 0) {
          throw new IOException("Empty animation " + entry.getKey());
        }
      }

      for (MeshGroup group : meshGroups) {
        if (group.vertices().length % 42 != 0) {
          throw new IOException("Incomplete triangle " + group.material());
        }

        for (int i = 0; i < group.vertices().length; i += 14) {
          float weights = 0.0F;

          for (int j = 8; j < 14; j += 2) {
            float bone = group.vertices()[i + j];
            float weight = group.vertices()[i + j + 1];
            if (bone != (int) bone || bone < 0.0F || bone >= bones || weight < 0.0F) {
              throw new IOException("Invalid vertex skin");
            }

            weights += weight;
          }

          if (Math.abs(weights - 1.0F) > 0.001) {
            throw new IOException("Invalid vertex weight sum");
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Cannot load Portalix " + kind + " mesh", e);
    }

    try {
      JsonObject metadata = ModAssetLoader.json("/assets/portalix/meshes/" + kind + ".json");
      List<String> missingKeys = new ArrayList<>();
      for (String key :
          List.of(
              "version", "animationFps", "gripBone", "worldGripOffset", "presentation", "groups")) {
        if (!metadata.has(key) || metadata.get(key).isJsonNull()) missingKeys.add(key);
      }
      if (metadata.has("presentation") && metadata.get("presentation").isJsonObject()) {
        for (GunPose pose : GunPose.values()) {
          if (!metadata.getAsJsonObject("presentation").has(pose.key()))
            missingKeys.add("presentation." + pose.key());
        }
      }
      if (metadata.has("groups") && metadata.get("groups").isJsonObject()) {
        for (MeshGroup group : meshGroups) {
          if (!metadata.getAsJsonObject("groups").has(group.material()))
            missingKeys.add("groups." + group.material());
        }
      }
      if (!missingKeys.isEmpty())
        throw new IOException("Missing mesh metadata: " + String.join(", ", missingKeys));
      animationFps = metadata.get("animationFps").getAsFloat();
      if (!Float.isFinite(animationFps) || animationFps <= 0)
        throw new IOException("Invalid animation frame rate");
      if (metadata.get("version").getAsInt() != 1) {
        throw new IOException("Unsupported mesh metadata");
      }

      gripOffset = metadataVector(metadata, "worldGripOffset");
      if (!gripOffset.isFinite()) {
        throw new IOException("Invalid mesh metadata vector worldGripOffset");
      }

      for (GunPose pose : GunPose.values()) {
        JsonArray operations = metadata.getAsJsonObject("presentation").getAsJsonArray(pose.key());
        presentation.put(pose, readPresentation(operations));
      }

      gripBone = metadata.get("gripBone").getAsInt();
      if (gripBone < 0 || gripBone >= parents.length) {
        throw new IOException("Invalid grip bone");
      }

      for (MeshGroup group : meshGroups) {
        JsonObject description =
            metadata.getAsJsonObject("groups").getAsJsonObject(group.material());
        MaterialMode mode = MaterialMode.valueOf(description.get("mode").getAsString());
        groups.add(new Group(group.material(), mode, group.vertices()));
      }
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("Cannot load Portalix " + kind + " metadata", e);
    }
    return new GunModelAsset(
        groups,
        clips,
        attachments,
        parents,
        inverseBind,
        kind.equals("world"),
        animationFps,
        gripBone,
        gripOffset,
        presentation);
  }

  private static Matrix4f[] readPresentation(JsonArray operations) throws IOException {
    Matrix4f right = new Matrix4f();
    Matrix4f left = new Matrix4f();
    for (JsonElement item : operations) {
      JsonObject operation = item.getAsJsonObject();
      JsonArray values = operation.getAsJsonArray("values");
      float x = values.get(0).getAsFloat();
      float y = values.size() > 1 ? values.get(1).getAsFloat() : 0.0F;
      float z = values.size() > 2 ? values.get(2).getAsFloat() : 0.0F;
      if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
        throw new IOException("Invalid presentation transform");
      switch (operation.get("kind").getAsString()) {
        case "translate" -> {
          right.translate(x, y, z);
          left.translate(x, y, z);
        }
        case "scale" -> {
          right.scale(x, y, z);
          left.scale(operation.get("mirror").getAsBoolean() ? -x : x, y, z);
        }
        case "rotateX" -> {
          float radians = (float) Math.toRadians(x);
          right.rotateX(radians);
          left.rotateX(radians);
        }
        case "rotateY" -> {
          float radians = (float) Math.toRadians(x);
          right.rotateY(radians);
          left.rotateY(radians);
        }
        default -> throw new IOException("Unknown presentation transform");
      }
    }
    return new Matrix4f[] {right, left};
  }

  private record MeshGroup(String material, float[] vertices) {}

  private static String readString(DataInputStream in) throws IOException {
    int length = count(in, 4096, "string bytes");
    byte[] bytes = in.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("Truncated mesh string");
    } else {
      return new String(bytes, StandardCharsets.UTF_8);
    }
  }
}
