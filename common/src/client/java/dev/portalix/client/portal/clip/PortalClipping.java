package dev.portalix.client.portal.clip;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.portal.entity.PortalEntityClipMask;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class PortalClipping {
  public static final BindGroupLayout LAYOUT =
      BindGroupLayout.builder().withUniform("PortalClip", UniformType.UNIFORM_BUFFER).build();
  private static DynamicUniformStorage<Planes> storage;
  private static GpuBufferSlice disabled;
  private static Vec3 disabledCamera;
  private static GpuBufferSlice current;
  private static final Vector4f DISABLED = new Vector4f(0, 0, 0, 1);
  private static PortalEndpoint scenePortal;

  private PortalClipping() {}

  public static Vector4f plane(
      Matrix4fc projection, Matrix4fc rotation, Vec3 camera, PortalEndpoint exit) {
    return plane(projection, rotation, camera, exit, 0);
  }

  public static Vector4f plane(
      Matrix4fc projection, Matrix4fc rotation, Vec3 camera, PortalEndpoint exit, float bias) {
    return plane(
        planeTransform(projection, rotation, IrisShaders.shadowPass()), camera, exit, bias);
  }

  private static Vector4f plane(Matrix4fc transform, Vec3 camera, PortalEndpoint exit, float bias) {
    return transform.transform(
        new Vector4f(
            exit.normal().toVector3f(),
            (float) (camera.subtract(exit.center()).dot(exit.normal()) - bias)),
        new Vector4f());
  }

  public static void begin(
      Matrix4fc projection, Matrix4fc rotation, Vec3 camera, PortalEndpoint exit) {
    scenePortal = exit;
    current =
        write(
            new Planes(
                plane(projection, rotation, camera, exit),
                DISABLED,
                disabledEdges(),
                0,
                false,
                camera));
  }

  public static void end() {
    scenePortal = null;
    current = null;
  }

  private static GpuBufferSlice write(Planes planes) {
    if (storage == null) storage = new DynamicUniformStorage<>("Portal clip planes", 128, 16);
    return storage.writeUniform(planes);
  }

  public static void withEntity(PortalEntityClipMask mask, Runnable render) {
    if (mask == null) {
      render.run();
      return;
    }
    var client = Minecraft.getInstance();
    var camera = PortalSceneManager.cameraState();
    if (camera == null)
      camera = client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
    var previous = current;
    var projection = PortalSceneManager.surfaceProjection();
    var rotation = new Matrix4f(camera.viewRotationMatrix);
    boolean shadowPass = IrisShaders.shadowPass();
    if (shadowPass) {
      IrisShaders.applyShadowMatrices(projection, rotation);
    } else if (mask.shadow()) RenderSystem.getProjectionType().applyLayeringTransform(rotation, 1);
    var portal = mask.portal();
    var transform = planeTransform(projection, rotation, shadowPass);
    var local = mask.aperture();
    var edges = new Vector4f[local.length];
    for (int i = 0; i < edges.length; i++)
      edges[i] = aperturePlane(transform, camera.pos, portal, local[i]);
    current =
        write(
            new Planes(
                scenePortal == null || shadowPass
                    ? DISABLED
                    : plane(transform, camera.pos, scenePortal, 0),
                plane(transform, camera.pos, portal, mask.bias()),
                edges,
                mask.replica() ? 1 : -1,
                mask.oval(),
                camera.pos));
    try {
      render.run();
    } finally {
      current = previous;
    }
  }

  public static boolean active() {
    return current != null;
  }

  public static void without(Runnable render) {
    var previous = current;
    current = null;
    try {
      render.run();
    } finally {
      current = previous;
    }
  }

  public static GpuBufferSlice uniform() {
    if (current != null) return current;
    var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
    if (disabled == null || !camera.equals(disabledCamera)) {
      disabled = write(new Planes(DISABLED, DISABLED, disabledEdges(), 0, false, camera));
      disabledCamera = camera;
    }
    return disabled;
  }

  public static void endFrame() {
    disabled = null;
    disabledCamera = null;
    if (storage != null) storage.endFrame();
  }

  public static void close() {
    current = null;
    if (storage != null) storage.close();
    storage = null;
    disabled = null;
    disabledCamera = null;
  }

  static Vector4f aperturePlane(
      Matrix4fc transform, Vec3 camera, PortalEndpoint portal, Vector4f local) {
    var normal =
        portal
            .right()
            .scale(local.x)
            .add(portal.up().scale(local.y))
            .add(portal.normal().scale(local.z));
    return transform.transform(
        new Vector4f(
            normal.toVector3f(), (float) (camera.subtract(portal.center()).dot(normal) + local.w)),
        new Vector4f());
  }

  static Matrix4f planeTransform(Matrix4fc projection, Matrix4fc modelView, boolean homogeneous) {
    var transform = new Matrix4f(projection);
    if (!homogeneous) {
      // Perspective clipping uses (clip.x, clip.y, clip.w, 1) to avoid depth precision loss.
      transform.setRow(2, projection.getRow(3, new Vector4f()));
      transform.setRow(3, new Vector4f(0, 0, 0, 1));
    }
    return transform.invert().transpose().mul(new Matrix4f(modelView).invert().transpose());
  }

  private static Vector4f[] disabledEdges() {
    return new Vector4f[] {DISABLED, DISABLED, DISABLED, DISABLED};
  }

  private record Planes(
      Vector4f scene, Vector4f entity, Vector4f[] edges, float mode, boolean oval, Vec3 camera)
      implements DynamicUniformStorage.DynamicUniform {
    @Override
    public void write(ByteBuffer buffer) {
      var builder = Std140Builder.intoBuffer(buffer).putVec4(scene).putVec4(entity);
      for (var edge : edges) builder.putVec4(edge);
      builder.putVec4(mode, oval ? 1 : 0, 0, 0);

      builder.putVec4(
          (float) (Math.floor(camera.x) - camera.x),
          (float) (Math.floor(camera.y) - camera.y),
          (float) (Math.floor(camera.z) - camera.z),
          0);
    }
  }
}
