package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.clip.PortalFrustumPlanes;
import dev.portalix.client.portal.clip.PortalFrustumState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class PortalFrustumMixin implements PortalFrustumState {
  @Shadow @Final private FrustumIntersection intersection;
  @Shadow private Vector4f viewVector;
  @Unique private boolean portalix$aperture;
  @Unique private PortalCullPlane portalix$exit;
  @Unique private Vec3 portalix$preparationCell;

  @WrapOperation(
      method = {"calculateFrustum", "set"},
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/joml/FrustumIntersection;set(Lorg/joml/Matrix4fc;)Lorg/joml/FrustumIntersection;"))
  private FrustumIntersection portalix$halfSpacePlanes(
      FrustumIntersection intersection, Matrix4fc matrix, Operation<FrustumIntersection> original) {
    return PortalFrustumPlanes.set(intersection, matrix);
  }

  @Override
  public boolean portalix$isAperture() {
    return portalix$aperture;
  }

  @Override
  public void portalix$aperture(Vector3fc direction, PortalCullPlane exit) {
    portalix$aperture = true;
    viewVector = new Vector4f(direction, 0);
    portalix$exit = exit;
    portalix$preparationCell = null;
  }

  @Override
  public PortalCullPlane portalix$exitPlane() {
    return portalix$exit;
  }

  @Override
  public void portalix$exitPlane(PortalCullPlane plane) {
    portalix$exit = plane;
  }

  @Override
  public Vec3 portalix$preparationCell() {
    return portalix$preparationCell;
  }

  @Override
  public void portalix$preparationCell(Vec3 origin) {
    portalix$preparationCell = origin;
  }

  @Inject(method = "set", at = @At("TAIL"))
  private void portalix$copyWindow(Frustum source, CallbackInfo ci) {
    portalix$aperture = ((PortalFrustumState) source).portalix$isAperture();
    portalix$exit = ((PortalFrustumState) source).portalix$exitPlane();
    portalix$preparationCell = ((PortalFrustumState) source).portalix$preparationCell();
  }

  @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
  private void portalix$exitBox(
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      CallbackInfoReturnable<Integer> cir) {
    if (portalix$preparationCell != null)
      cir.setReturnValue(PortalFrustumPlanes.intersectPreparationCell(
          intersection, portalix$preparationCell, minX, minY, minZ, maxX, maxY, maxZ));
    else if (portalix$exit != null && !portalix$exit.intersects(minX, minY, minZ, maxX, maxY, maxZ))
      cir.setReturnValue(FrustumIntersection.OUTSIDE);
  }

  @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("RETURN"), cancellable = true)
  private void portalix$intersectingExitBox(
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      CallbackInfoReturnable<Integer> cir) {
    if (portalix$exit != null
        && cir.getReturnValueI() == FrustumIntersection.INSIDE
        && !portalix$exit.contains(minX, minY, minZ, maxX, maxY, maxZ))
      cir.setReturnValue(FrustumIntersection.INTERSECT);
  }

  @Inject(method = "pointInFrustum", at = @At("HEAD"), cancellable = true)
  private void portalix$exitPoint(
      double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
    if (portalix$exit != null && portalix$exit.distance(x, y, z) < 0) cir.setReturnValue(false);
  }

  @Inject(method = "offsetToFullyIncludeCameraCube", at = @At("HEAD"), cancellable = true)
  private void portalix$keepWindow(int size, CallbackInfoReturnable<Frustum> cir) {

    if (portalix$aperture) cir.setReturnValue((Frustum) (Object) this);
  }
}
