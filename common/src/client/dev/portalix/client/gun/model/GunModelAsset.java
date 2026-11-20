package dev.portalix.client.gun.model;

import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

record GunModelAsset(
    List<Group> groups,
    Map<String, float[][]> clips,
    Map<GunAttachment, Attachment> attachments,
    int[] parents,
    Matrix4f[] inverseBind,
    boolean world,
    float animationFps,
    int gripBone,
    Vector3f gripOffset,
    Map<GunPose, Matrix4f[]> presentation) {
  GunModelAsset {
    groups = List.copyOf(groups);
    clips = Map.copyOf(clips);
    attachments = Map.copyOf(attachments);
    presentation = Map.copyOf(presentation);
  }

  record Attachment(int bone, Vector3f bindPosition) {}

  record Group(String material, MaterialMode mode, float[] vertices) {}

  enum MaterialMode {
    SOLID,
    ADDITIVE,
    SELF_ILLUMINATED
  }
}
