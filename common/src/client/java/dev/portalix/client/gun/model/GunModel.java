package dev.portalix.client.gun.model;

import static dev.portalix.client.gun.model.GunVertexSkinning.SOURCE_TO_MODEL;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.gun.animation.GunAnimationSet;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class GunModel {
  final List<GunModelAsset.Group> groups;
  private final Matrix4f[] idleMatrices;
  private final Matrix4f[] bind;
  private final Vector3f idleGrip = new Vector3f();
  private final Vector3f gripOffset;
  private final Map<GunAttachment, Vector3f> idleSockets = new EnumMap<>(GunAttachment.class);
  private final Matrix4f[] globalScratch;
  private final List<PoseBuffer> poseBuffers = new ArrayList<>();
  private final Map<Matrix4f[], PoseBuffer> buffersByMatrices = new IdentityHashMap<>();
  private PoseBuffer lastBuffer;
  private final Matrix4f localScratch = new Matrix4f();
  private final Matrix4f gripScratch = new Matrix4f();
  private long poseRevision;

  private final Vector3f boundsMin = new Vector3f(Float.POSITIVE_INFINITY);
  private final Vector3f boundsMax = new Vector3f(Float.NEGATIVE_INFINITY);
  private final Map<GunAttachment, GunModelAsset.Attachment> attachments;
  final int[] parents;
  final Matrix4f[] inverseBind;
  final boolean world;
  private final int gripBone;
  private final Map<GunPose, Matrix4f[]> presentation;
  private final GunAnimationSet animations;
  private final GunModelRenderer renderer;
  private final int geometryRoot;
  private final float[] geometryPose;
  private final float[] referencePose;
  private final Matrix4f referenceRootInverse;
  private final Matrix4f geometryMotion = new Matrix4f();

  public GunModel(String kind) {
    GunModelAsset asset = GunModelLoader.load(kind);
    this.groups = asset.groups();
    this.attachments = asset.attachments();
    this.parents = asset.parents();
    this.inverseBind = asset.inverseBind();
    this.bind = new Matrix4f[this.parents.length];
    this.globalScratch = new Matrix4f[this.parents.length];
    for (int i = 0; i < this.parents.length; i++) {
      this.bind[i] = new Matrix4f(this.inverseBind[i]).invert();
      this.globalScratch[i] = new Matrix4f();
    }
    this.world = asset.world();
    this.gripBone = asset.gripBone();
    this.animations = new GunAnimationSet(asset.clips(), asset.animationFps());
    this.gripOffset = asset.gripOffset();
    this.presentation = asset.presentation();
    this.geometryRoot = geometryRoot();
    this.geometryPose = new float[parents.length * 7];
    this.referencePose = animations.pose("idle", 0.0F, false);
    this.skinMatrices(referencePose);
    this.referenceRootInverse =
        geometryRoot < 0 ? new Matrix4f() : new Matrix4f(globalScratch[geometryRoot]).invert();
    this.renderer = new GunModelRenderer(this);
    Matrix4f[] matrices = this.skinMatrices(referencePose);
    this.idleMatrices = new Matrix4f[matrices.length];
    for (int i = 0; i < matrices.length; i++) this.idleMatrices[i] = new Matrix4f(matrices[i]);
    this.grip(matrices, idleGrip);
    for (GunAttachment socket : GunAttachment.values())
      idleSockets.put(socket, attachment(socket, matrices));
    Vector3f point = new Vector3f();

    for (GunModelAsset.Group group : this.groups) {
      float[] vertices = new float[group.vertices().length / 14 * 8];
      GunVertexSkinning.skin(group.vertices(), matrices, vertices);

      for (Entry<GunPose, Matrix4f[]> entry : this.presentation.entrySet()) {
        for (Matrix4f transform : entry.getValue()) {
          Matrix4f applied = new Matrix4f(transform);
          if (entry.getKey() == GunPose.WORLD_HAND) {
            Vector3f grip = this.grip(matrices);
            Vector3f offset = this.gripOffset;
            applied.translate(-grip.x + offset.x, -grip.y + offset.y, -grip.z + offset.z);
          }

          for (int i = 0; i < vertices.length; i += 8) {
            applied.transformPosition(point.set(vertices[i], vertices[i + 1], vertices[i + 2]));
            this.boundsMin.min(point);
            this.boundsMax.max(point);
          }
        }
      }
    }
  }

  public void submitAnimated(
      PoseStack pose,
      SubmitNodeCollector collector,
      float[] sampled,
      Matrix4f[] skin,
      int light,
      PortalixVariant variant) {
    if (geometryRoot < 0) {
      submit(pose, collector, skin, light, false, variant);
      return;
    }
    skin[geometryRoot].mul(bind[geometryRoot], geometryMotion).mul(referenceRootInverse);
    SOURCE_TO_MODEL.mul(geometryMotion, localScratch).mul(SOURCE_TO_MODEL, geometryMotion);
    System.arraycopy(sampled, 0, geometryPose, 0, sampled.length);
    for (int bone = geometryRoot; bone >= 0; bone = parents[bone]) {
      System.arraycopy(referencePose, bone * 7, geometryPose, bone * 7, 7);
    }
    pose.pushPose();
    pose.mulPose(geometryMotion);
    submit(pose, collector, skinMatrices(geometryPose, true), light, false, variant);
    pose.popPose();
  }

  private int geometryRoot() {
    int root = -1;
    for (GunModelAsset.Group group : groups) {
      float[] vertices = group.vertices();
      for (int i = 0; i < vertices.length; i += 14) {
        for (int j = 8; j < 14; j += 2) {
          if (vertices[i + j + 1] == 0) continue;
          int bone = (int) vertices[i + j];
          if (root < 0) root = bone;
          while (!descendsFrom(bone, root)) {
            root = parents[root];
            if (root < 0) return -1;
          }
        }
      }
    }
    return root;
  }

  private boolean descendsFrom(int bone, int ancestor) {
    for (int current = bone; current >= 0; current = parents[current]) {
      if (current == ancestor) return true;
    }
    return false;
  }

  public Matrix4f[] idleMatrices() {
    return this.idleMatrices;
  }

  public void close() {
    renderer.close();
  }

  public void warmup() {
    renderer.warmup(idleMatrices);
  }

  public void getExtents(Consumer<Vector3fc> output) {
    output.accept(new Vector3f(this.boundsMin));
    output.accept(new Vector3f(this.boundsMax));
  }

  public Vector3fc[] extents() {
    return new Vector3fc[] {new Vector3f(boundsMin), new Vector3f(boundsMax)};
  }

  public GunAnimationSet animations() {
    return this.animations;
  }

  public Matrix4f[] skinMatrices(float[] pose) {
    return skinMatrices(pose, false);
  }

  private Matrix4f[] skinMatrices(float[] pose, boolean geometry) {
    if (lastBuffer != null
        && lastBuffer.geometry == geometry
        && Arrays.equals(pose, lastBuffer.pose))
      return lastBuffer.matrices;
    for (PoseBuffer candidate : poseBuffers) {
      if (candidate.geometry == geometry && Arrays.equals(pose, candidate.pose)) {
        lastBuffer = candidate;
        return candidate.matrices;
      }
    }
    PoseBuffer buffer = null;
    for (PoseBuffer candidate : poseBuffers) {
      if (candidate.geometry == geometry && candidate.users == 0) {
        buffer = candidate;
        break;
      }
    }
    if (buffer == null) {
      buffer = new PoseBuffer(parents.length, geometry);
      poseBuffers.add(buffer);
      buffersByMatrices.put(buffer.matrices, buffer);
    }
    for (int i = 0; i < this.parents.length; i++) {
      int k = i * 7;
      localScratch.translationRotate(
          pose[k], pose[k + 1], pose[k + 2], pose[k + 3], pose[k + 4], pose[k + 5], pose[k + 6]);
      if (parents[i] < 0) globalScratch[i].set(localScratch);
      else globalScratch[parents[i]].mul(localScratch, globalScratch[i]);
      globalScratch[i].mul(inverseBind[i], buffer.matrices[i]);
    }
    System.arraycopy(pose, 0, buffer.pose, 0, pose.length);
    buffer.revision = ++poseRevision;
    lastBuffer = buffer;
    return buffer.matrices;
  }

  long revision(Matrix4f[] matrices) {
    PoseBuffer buffer = buffersByMatrices.get(matrices);
    return buffer == null ? 0 : buffer.revision;
  }

  PoseLease retain(Matrix4f[] matrices) {
    PoseBuffer buffer = buffersByMatrices.get(matrices);
    if (buffer == null) return PoseLease.IMMUTABLE;
    buffer.users++;
    return new PoseLease(buffer);
  }

  static final class PoseLease implements AutoCloseable {
    static final PoseLease IMMUTABLE = new PoseLease(null);
    private PoseBuffer buffer;

    private PoseLease(PoseBuffer buffer) {
      this.buffer = buffer;
    }

    @Override
    public void close() {
      if (buffer != null) {
        buffer.users--;
        buffer = null;
      }
    }
  }

  private static final class PoseBuffer {
    final Matrix4f[] matrices;
    final float[] pose;
    long revision;
    int users;
    final boolean geometry;

    PoseBuffer(int bones, boolean geometry) {
      this.geometry = geometry;
      pose = new float[bones * 7];
      matrices = new Matrix4f[bones];
      for (int i = 0; i < bones; i++) matrices[i] = new Matrix4f();
    }
  }

  public Vector3f attachment(GunAttachment name, Matrix4f[] skin) {
    return attachment(name, skin, new Vector3f());
  }

  public Vector3f attachment(GunAttachment name, Matrix4f[] skin, Vector3f dest) {
    if (skin == idleMatrices && idleSockets.containsKey(name))
      return dest.set(idleSockets.get(name));
    GunModelAsset.Attachment a = this.attachments.get(name);
    skin[a.bone()].transformPosition(a.bindPosition(), dest);
    return dest.set(-dest.x, dest.z, dest.y);
  }

  public boolean isWorld() {
    return world;
  }

  public Matrix4f particleFrame(GunAttachment socket, Matrix4f[] skin) {
    var attachment = attachments.get(socket);
    return new Matrix4f(SOURCE_TO_MODEL)
        .mul(skin[attachment.bone()])
        .mul(bind[attachment.bone()])
        .setTranslation(attachment(socket, skin));
  }

  public void applyPresentation(GunPose name, PoseStack pose, boolean left) {
    Matrix4f[] matrices = this.presentation.get(name);
    pose.mulPose(matrices[left ? 1 : 0]);
  }

  public void alignGrip(PoseStack pose, Vector3f grip) {
    Vector3f offset = this.gripOffset;
    pose.translate(-grip.x + offset.x, -grip.y + offset.y, -grip.z + offset.z);
  }

  public Vector3f grip(Matrix4f[] skin) {
    return grip(skin, new Vector3f());
  }

  public Vector3f grip(Matrix4f[] skin, Vector3f dest) {
    if (skin == idleMatrices) return dest.set(idleGrip);
    skin[gripBone].mul(this.bind[gripBone], gripScratch).getTranslation(dest);
    return dest.set(-dest.x, dest.z, dest.y);
  }

  public void submit(
      PoseStack pose,
      SubmitNodeCollector collector,
      Matrix4f[] matrices,
      int light,
      boolean icon,
      PortalixVariant variant) {
    this.renderer.submit(pose, collector, matrices, light, icon, variant);
  }
}
