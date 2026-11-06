package dev.portalix.client.portal.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.mixin.camera.CameraAccessor;
import dev.portalix.client.portal.camera.CameraOrientationAccess;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.clip.PortalFrustumState;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalEllipse;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class PortalSceneCamera extends Camera {
  private final Matrix4f view = new Matrix4f();
  private final Matrix4f projection = new Matrix4f();
  private final Matrix4f culling = new Matrix4f();
  private final Matrix4f rasterCulling = new Matrix4f();
  private final Vector3f forward = new Vector3f();
  private final Vector3f up = new Vector3f();
  private final Vector3f left = new Vector3f();
  private Frustum frustum;
  private Frustum renderFrustum;
  private float far;
  private long environmentTick = Long.MIN_VALUE;
  private ClientLevel environmentLevel;
  private PortalEndpoint exit;
  private Vec3 environmentPosition;
  private Vec3 extractedPosition;
  private Vec3 preparationCell;
  private Vec3 extractedPreparationCell;
  private PortalEndpoint extractedExit;
  private boolean extractedArrival;
  private final Matrix4f extractedView = new Matrix4f();
  private final Matrix4f extractedCulling = new Matrix4f();
  private final Matrix4f extractedRasterCulling = new Matrix4f();
  private long visibilityRevision;
  private boolean arrival;

  boolean arrival() {
    return arrival;
  }

  Frustum renderFrustum() {
    return renderFrustum;
  }

  boolean fullScreenRender() {
    return arrival && rasterCulling.equals(culling);
  }

  boolean frustumChanged() {
    return !(position().equals(extractedPosition)
        && exit.equals(extractedExit)
        && arrival == extractedArrival
        && view.equals(extractedView)
        && culling.equals(extractedCulling)
        && rasterCulling.equals(extractedRasterCulling));
  }

  boolean consumeTerrainPreparationChange() {
    if (!frustumChanged()) return false;
    boolean preparationChanged = preparationCell == null
        || !preparationCell.equals(extractedPreparationCell)
        || !exit.equals(extractedExit)
        || arrival != extractedArrival
        || !view.equals(extractedView)
        || !culling.equals(extractedCulling);
    extractedPosition = position();
    extractedPreparationCell = preparationCell;
    extractedExit = exit;
    extractedArrival = arrival;
    extractedView.set(view);
    extractedCulling.set(culling);
    extractedRasterCulling.set(rasterCulling);
    visibilityRevision++;
    return preparationChanged;
  }

  long visibilityRevision() {
    return visibilityRevision;
  }

  Vec3 environmentPosition() {
    return environmentPosition;
  }

  void configure(
      ClientLevel level,
      Camera root,
      Vec3 position,
      Quaternionf orientation,
      Matrix4fc projection,
      float far,
      PortalSceneViewport viewport,
      PortalEndpoint exit,
      boolean arrival,
      Matrix4fc viewEffects) {
    float near = ((CameraAccessor) root).portalix$projection().zNear();
    if (environmentLevel != level || !exit.equals(this.exit)) {
      attributeProbe().reset();
      environmentTick = Long.MIN_VALUE;
    }
    this.environmentLevel = level;
    this.exit = exit;
    this.arrival = arrival;
    preparationCell = arrival && PortalTerrainBackend.usesVanillaChunkEvents()
        ? new Vec3(Math.floor(position.x / 8) * 8, Math.floor(position.y / 8) * 8,
            Math.floor(position.z / 8) * 8)
        : null;
    double distance = PortalAperture.signedDistance(exit, position);
    if (distance < 0) {
      Vec3 relative = position.subtract(exit.center());
      environmentPosition =
          exit.center()
              .add(
                  exit.right()
                      .scale(
                          Math.clamp(
                              relative.dot(exit.right()), -exit.width() / 2, exit.width() / 2)))
              .add(
                  exit.up()
                      .scale(
                          Math.clamp(
                              relative.dot(exit.up()), -exit.height() / 2, exit.height() / 2)))
              .add(exit.normal().scale(.001));
      if (exit.normal().y == 1)
        environmentPosition = new PortalEllipse(exit, 0).project(environmentPosition);
    } else environmentPosition = position;
    setLevel(level);
    setEntity(root.entity());
    ((CameraOrientationAccess) (Object) this).portalix$setPose(position, orientation);
    var access = (CameraAccessor) (Object) this;
    var window = Minecraft.getInstance().getWindow();
    float fov = (float) Math.toDegrees(2 * Math.atan(1 / Math.abs(projection.m11())));
    access
        .portalix$projection()
        .setupPerspective(near, far, fov, window.getWidth(), window.getHeight());
    orientation.transform(new Vector3f(0, 0, -1), forward);
    orientation.transform(new Vector3f(0, 1, 0), up);
    orientation.transform(new Vector3f(-1, 0, 0), left);
    view.rotation(new Quaternionf(orientation).conjugate());
    if (viewEffects != null) view.mulLocal(viewEffects);
    this.projection.set(projection);
    this.far = far;

    Matrix4f cull =
        PortalSceneViewport.cullingProjection(
            projection, near, far, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
    var raster = viewport == null ? new Matrix4f(cull) : viewport.projection(cull);
    rasterCulling.set(raster);
    var windowDirection = new Vector3f(raster.m20() / raster.m00(), raster.m21() / raster.m11(), -1);
    orientation.transform(windowDirection).normalize();
    renderFrustum = new Frustum(view, raster);
    ((PortalFrustumState) renderFrustum)
        .portalix$aperture(windowDirection, PortalCullPlane.of(exit));
    renderFrustum.prepare(position.x, position.y, position.z);
    culling.set(arrival ? cull : raster);
    if (arrival) {
      frustum = new Frustum(view, cull);
      var direction = new Vector3f(cull.m20() / cull.m00(), cull.m21() / cull.m11(), -1);
      orientation.transform(direction).normalize();
      ((PortalFrustumState) frustum).portalix$aperture(direction, null);
      ((PortalFrustumState) frustum).portalix$preparationCell(preparationCell);
      frustum.prepare(position.x, position.y, position.z);
    } else frustum = renderFrustum;
    if (environmentTick != level.getGameTime()) {
      attributeProbe().tick(level, environmentPosition);
      environmentTick = level.getGameTime();
    }
  }

  @Override
  public FogType getFluidInCamera() {
    if (environmentLevel == null) return FogType.NONE;
    var block = BlockPos.containing(environmentPosition);
    var fluid = environmentLevel.getFluidState(block);
    if (fluid.is(FluidTags.WATER)
        && environmentPosition.y < block.getY() + fluid.getHeight(environmentLevel, block))
      return FogType.WATER;
    var plane = getNearPlane(getFov());
    for (var offset :
        List.of(
            plane.getPointOnPlane(0, 0),
            plane.getTopLeft(),
            plane.getTopRight(),
            plane.getBottomLeft(),
            plane.getBottomRight())) {
      Vec3 point = environmentPosition.add(offset);
      if (PortalAperture.signedDistance(exit, point) < 0) continue;
      var pos = BlockPos.containing(point);
      var sample = environmentLevel.getFluidState(pos);
      if (sample.is(FluidTags.LAVA)
          && point.y <= pos.getY() + sample.getHeight(environmentLevel, pos)) return FogType.LAVA;
      if (environmentLevel.getBlockState(pos).is(Blocks.POWDER_SNOW)) return FogType.POWDER_SNOW;
    }
    return FogType.NONE;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public boolean isDetached() {
    return !PortalSceneManager.hidesCameraSource();
  }

  @Override
  public float getFov() {
    return (float) Math.toDegrees(2 * Math.atan(1 / Math.abs(projection.m11())));
  }

  @Override
  public Frustum getCullFrustum() {
    return frustum;
  }

  @Override
  public Vector3fc forwardVector() {
    return forward;
  }

  @Override
  public Vector3fc upVector() {
    return up;
  }

  @Override
  public Vector3fc leftVector() {
    return left;
  }

  @Override
  public Matrix4f getViewRotationMatrix(Matrix4f output) {
    return output.set(view);
  }

  @Override
  public Matrix4f getViewRotationProjectionMatrix(Matrix4f output) {
    return output.set(projection).mul(view);
  }

  @Override
  public void extractRenderState(CameraRenderState state, float partialTick) {
    super.extractRenderState(state, partialTick);
    state.initialized = true;
    state.isPanoramicMode = false;
    state.isFrustumCaptured = false;
    state.smartCull = Minecraft.getInstance().smartCull;
    state.pos = position();
    state.blockPos = blockPosition().immutable();
    state.xRot = xRot();
    state.yRot = yRot();
    state.orientation.set(rotation());
    state.cullFrustum.set(frustum);
    state.projectionMatrix.set(projection);
    state.viewRotationMatrix.set(view);
    state.depthFar = far;
    state.fogType = getFluidInCamera();
  }
}
