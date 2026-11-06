package dev.portalix.client.gun.model;

import static dev.portalix.client.gun.model.GunVertexSkinning.SOURCE_TO_MODEL;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.item.PortalixVariant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

final class GunModelRenderer {
  private final Matrix4f rigidTransform = new Matrix4f();
  private final List<PreparedGroup> groups;
  private final boolean world;
  private final GunModel mesh;
  private final Map<GunModelAsset.Group, SkinnedGroup> skinned = new IdentityHashMap<>();

  GunModelRenderer(GunModel mesh) {
    this.mesh = mesh;
    this.groups = mesh.groups.stream().flatMap(group -> prepare(group).stream()).toList();
    this.world = mesh.world;
    // Pipeline registration runs before TextureManager is available during client initialization.
    for (PreparedGroup group : groups) {
      GunMeshFeatureRenderer.pipeline(group.createPass(0, false, world, group.texture).pipeline());
      GunMeshFeatureRenderer.pipeline(group.createPass(0, true, world, group.texture).pipeline());
      if (group.mode == GunModelAsset.MaterialMode.SELF_ILLUMINATED)
        GunMeshFeatureRenderer.pipeline(group.createPass(1, false, world, group.texture).pipeline());
    }
  }

  void warmup(Matrix4f[] matrices) {
    for (PreparedGroup group : groups) {
      var vertices = group.bone >= 0 ? group.rigidVertices : skin(group.group, matrices);
      group.gpu.upload(vertices);
      group.mirroredGpu.upload(vertices);
    }
  }

  public void submit(
      PoseStack pose,
      SubmitNodeCollector collector,
      Matrix4f[] matrices,
      int light,
      boolean icon,
      PortalixVariant variant) {
    var shaders = IrisDrawContext.capture();
    for (PreparedGroup prepared : this.groups) {
      GunModelAsset.Group group = prepared.group;
      if (!variant.potato() && group.material().toLowerCase(Locale.ROOT).contains("potat"))
        continue;
      pose.pushPose();
      if (prepared.bone >= 0) {
        rigidTransform.set(SOURCE_TO_MODEL).mul(matrices[prepared.bone]).mul(SOURCE_TO_MODEL);
        pose.mulPose(rigidTransform);
      }
      boolean mirrored = pose.last().pose().determinant() < 0.0F;
      int passes = prepared.mode == GunModelAsset.MaterialMode.SELF_ILLUMINATED ? 2 : 1;
      for (int passIndex = 0; passIndex < passes; passIndex++) {
        RenderType pass = prepared.pass(passIndex, icon, world, variant);
        GunModel.PoseLease lease =
            prepared.bone >= 0 ? GunModel.PoseLease.IMMUTABLE : mesh.retain(matrices);
        try {
          ClientPlatform.INSTANCE.submit(collector,
              pass.hasBlending()
                  ? ClientPlatform.Phase.TRANSLUCENT_CUSTOM_GEOMETRY
                  : ClientPlatform.Phase.SOLID,
              new GunMeshFeatureRenderer.Submit(
                  new Matrix4f(pose.last().pose()),
                  pass,
                  prepared.gpu(mirrored),
                  prepared.bone >= 0
                      ? () -> prepared.rigidVertices
                      : () -> this.skin(group, matrices),
                  light,
                  lease,
                  shaders));
        } catch (RuntimeException | Error failure) {
          lease.close();
          throw failure;
        }
      }
      pose.popPose();
    }
  }

  private static List<PreparedGroup> prepare(GunModelAsset.Group group) {

    Map<Integer, List<Integer>> triangles = new LinkedHashMap<>();
    float[] source = group.vertices();
    for (int tri = 0; tri < source.length; tri += 42) {
      int bone = rigidBone(source, tri);
      if (bone != rigidBone(source, tri + 14) || bone != rigidBone(source, tri + 28)) bone = -1;
      triangles.computeIfAbsent(bone, ignored -> new ArrayList<>()).add(tri);
    }
    List<PreparedGroup> prepared = new ArrayList<>();
    for (var entry : triangles.entrySet()) {
      float[] vertices = new float[entry.getValue().size() * 42];
      int offset = 0;
      for (int tri : entry.getValue()) {
        System.arraycopy(source, tri, vertices, offset, 42);
        offset += 42;
      }
      prepared.add(
          new PreparedGroup(
              new GunModelAsset.Group(group.material(), group.mode(), vertices),
              PortalixMod.id(
                  "textures/model/" + group.material().toLowerCase(Locale.ROOT) + ".png"),
              entry.getKey()));
    }
    return prepared;
  }

  private static int rigidBone(float[] vertices, int offset) {
    int bone = -1;
    for (int j = 8; j < 14; j += 2) {
      float weight = vertices[offset + j + 1];
      if (weight == 0) continue;
      if (weight != 1 || bone >= 0) return -1;
      bone = (int) vertices[offset + j];
    }
    return bone;
  }

  private static final class PreparedGroup {
    final GunModelAsset.Group group;
    final GunModelAsset.MaterialMode mode;
    final Identifier texture;
    final int bone;
    final GunMeshFeatureRenderer.Vertices rigidVertices;
    final RenderType[] passes = new RenderType[PortalixVariant.values().length * 4];
    final GunMeshFeatureRenderer.Geometry gpu = new GunMeshFeatureRenderer.Geometry(false);
    final GunMeshFeatureRenderer.Geometry mirroredGpu = new GunMeshFeatureRenderer.Geometry(true);

    GunMeshFeatureRenderer.Geometry gpu(boolean mirrored) {
      return mirrored ? mirroredGpu : gpu;
    }

    PreparedGroup(
        GunModelAsset.Group group,
        Identifier texture,
        int bone) {
      this.group = group;
      this.mode = group.mode();
      this.texture = texture;
      this.bone = bone;
      if (bone >= 0) {
        rigidVertices = new GunMeshFeatureRenderer.Vertices(new float[group.vertices().length / 14 * 8]);
        Matrix4f[] identity = new Matrix4f[bone + 1];
        identity[bone] = new Matrix4f();
        GunVertexSkinning.skin(group.vertices(), identity, rigidVertices.values);
      } else rigidVertices = null;
    }

    RenderType pass(int index, boolean asIcon, boolean world, PortalixVariant variant) {
      int key = variant.ordinal() * 4 + (asIcon ? 2 : 0) + index;
      if (passes[key] != null) return passes[key];
      Identifier selected = texture;
      if (!variant.skin().isEmpty()
          && (group.material().equals("v_portalix") || group.material().equals("w_portalix")))
        selected = PortalixMod.id("textures/model/" + group.material() + variant.skin() + ".png");
      if (index == 0 && mode != GunModelAsset.MaterialMode.ADDITIVE)
        selected = GunOpaqueTexture.of(selected);
      return passes[key] = createPass(index, asIcon, world, selected);
    }

    private RenderType createPass(int index, boolean asIcon, boolean world, Identifier texture) {
      if (index == 1) {
        return GunMaterialTypes.emissive(texture);
      }
      if (mode == GunModelAsset.MaterialMode.ADDITIVE) {
        return GunMaterialTypes.additive(texture);
      }
      if (asIcon) {
        return GunMaterialTypes.icon(texture);
      }
      return world ? GunMaterialTypes.world(texture) : RenderTypes.entitySolid(texture);
    }
  }

  void close() {
    for (PreparedGroup group : groups) {
      group.gpu.close();
      group.mirroredGpu.close();
    }
  }

  private GunMeshFeatureRenderer.Vertices skin(GunModelAsset.Group group, Matrix4f[] matrices) {
    SkinnedGroup result = this.skinned.computeIfAbsent(group, SkinnedGroup::new);
    long revision = this.mesh.revision(matrices);
    if (result.matrices != matrices || result.revision != revision) {
      GunVertexSkinning.skin(group.vertices(), matrices, result.vertices.values);
      result.vertices.revision++;
      result.matrices = matrices;
      result.revision = revision;
    }

    return result.vertices;
  }

  private static final class SkinnedGroup {
    final GunMeshFeatureRenderer.Vertices vertices;
    Matrix4f[] matrices;
    long revision = -1;

    SkinnedGroup(GunModelAsset.Group group) {
      this.vertices = new GunMeshFeatureRenderer.Vertices(new float[group.vertices().length / 14 * 8]);
    }
  }
}
