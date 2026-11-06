package dev.portalix.client.mixin.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.portalix.client.portal.camera.CameraOrientationAccess;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.camera.PortalCameraEnvironment;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.traversal.EntityPortalState;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraTransitionMixin implements CameraOrientationAccess {
  @Shadow @Final private Quaternionf rotation;
  @Shadow @Final private Vector3f forwards;
  @Shadow @Final private Vector3f up;
  @Shadow @Final private Vector3f left;
  @Shadow private float eyeHeight;
  @Shadow private float eyeHeightOld;

  @Shadow
  protected abstract void setPosition(Vec3 position);

  @Shadow
  protected abstract void setRotation(float yaw, float pitch);

  @Unique private float portalix$frameSeconds;
  @Unique private float portalix$partialTick;
  @Unique private Entity portalix$eyeEntity;
  @Unique private double portalix$eyeShift;

  @Inject(method = "tick", at = @At("HEAD"))
  private void portalix$rebaseEye(CallbackInfo ci) {
    portalix$updateEyeHeight((Camera) (Object) this);
  }

  @Unique
  private void portalix$updateEyeHeight(Camera camera) {
    if (camera.entity() instanceof Player player) {
      double shift = EntityPortalState.of(player).cameraEyeHeightShift();
      if (portalix$eyeEntity == player) {
        eyeHeight += (float) (shift - portalix$eyeShift);
        eyeHeightOld += (float) (shift - portalix$eyeShift);
      }
      portalix$eyeShift = shift;
    }
    portalix$eyeEntity = camera.entity();
  }

  @Inject(method = "update", at = @At("HEAD"))
  private void portalix$frameTime(DeltaTracker tracker, CallbackInfo ci) {
    portalix$frameSeconds = tracker.getGameTimeDeltaTicks() / 20;
  }

  @WrapOperation(
      method = "update",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"))
  private void portalix$nearPlane(
      Camera camera,
      float near,
      float far,
      float fov,
      float width,
      float height,
      Operation<Void> original) {
    original.call(
        camera,
        PortalCameraController.nearPlane(camera, near, far, fov, width / height),
        far,
        fov,
        width,
        height);
  }

  @WrapOperation(
      method = "createProjectionMatrixForCulling",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"))
  private Matrix4f portalix$cullingNearPlane(
      Matrix4f matrix,
      float fov,
      float aspect,
      float near,
      float far,
      boolean zeroToOne,
      Operation<Matrix4f> original) {
    return original.call(
        matrix,
        fov,
        aspect,
        PortalCameraController.nearPlane(
            (Camera) (Object) this, near, far, (float) Math.toDegrees(fov), aspect),
        far,
        zeroToOne);
  }

  @WrapOperation(
      method = "getFluidInCamera",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/Camera;getNearPlane(F)Lnet/minecraft/client/Camera$NearPlane;"))
  private Camera.NearPlane portalix$fluidSamples(
      Camera camera,
      float fov,
      Operation<Camera.NearPlane> original,
      @Share("portalix$fluidLinks") LocalRef<List<PortalLink>> links) {
    links.set(PortalCameraEnvironment.samplingLinks(camera));
    return original.call(camera, fov);
  }

  @WrapOperation(
      method = "getFluidInCamera",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$fluidSample(
      Vec3 eye,
      Vec3 offset,
      Operation<Vec3> original,
      @Share("portalix$fluidLinks") LocalRef<List<PortalLink>> links) {
    var point = original.call(eye, offset);
    return links.get().isEmpty() ? point : PortalCameraEnvironment.sample(eye, point, links.get());
  }

  @WrapOperation(
      method = "update",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V"))
  private void portalix$transition(Camera camera, float partialTick, Operation<Void> original) {
    portalix$updateEyeHeight(camera);
    PortalCameraController.prepare(camera, portalix$frameSeconds);
    original.call(camera, partialTick);
    PortalCameraController.update(camera, partialTick);
  }

  @Inject(method = "alignWithEntity", at = @At("HEAD"))
  private void portalix$interpolationTime(float partialTick, CallbackInfo ci) {
    portalix$partialTick = partialTick;
  }

  @WrapOperation(
      method = "alignWithEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
  private void portalix$position(
      Camera camera, double x, double y, double z, Operation<Void> original) {
    Vec3 shift =
        PortalEyePose.interpolatedFeet(camera.entity(), portalix$partialTick)
            .subtract(camera.entity().getPosition(portalix$partialTick));
    original.call(camera, x + shift.x, y + shift.y, z + shift.z);
  }

  @WrapOperation(
      method = "alignWithEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/Camera;setPosition(Lnet/minecraft/world/phys/Vec3;)V"))
  private void portalix$minecartPosition(Camera camera, Vec3 position, Operation<Void> original) {
    if (EntityPortalState.of(camera.entity())
        .cameraHistory()
        .hasPosition(camera.entity().tickCount)) {
      position =
          PortalEyePose.interpolatedFeet(camera.entity(), portalix$partialTick)
              .add(0, portalix$eyeHeight(portalix$partialTick), 0);
    }
    original.call(camera, position);
  }

  @WrapOperation(
      method = "alignWithEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
  private float portalix$requestedDistance(Camera camera, float distance, Operation<Float> original) {
    return camera.entity() instanceof LocalPlayer player && !player.isSleeping()
        ? distance
        : original.call(camera, distance);
  }

  @WrapOperation(
      method = "alignWithEntity",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V"))
  private void portalix$thirdPerson(
      Camera camera, float forwards, float up, float right, Operation<Void> original) {
    if (camera.isDetached()
        && camera.entity() instanceof LocalPlayer player
        && !player.isSleeping())
      PortalCameraController.thirdPerson(camera, portalix$partialTick, -forwards);
    else original.call(camera, forwards, up, right);
  }

  @Override
  public float portalix$eyeHeight(float partialTick) {
    return Mth.lerp(partialTick, eyeHeightOld, eyeHeight);
  }

  @Override
  public float portalix$takeEyeOffset(float partialTick) {
    var camera = (Camera) (Object) this;
    portalix$updateEyeHeight(camera);
    float target = camera.entity().getEyeHeight();
    float offset = portalix$eyeHeight(partialTick) - target;
    eyeHeight = target;
    eyeHeightOld = target;
    return offset;
  }

  @Override
  public void portalix$setPose(Vec3 position, Quaternionf orientation) {
    var pose = new Quaternionf(orientation);
    float yaw = ((Camera) (Object) this).yRot();
    var angles = PortalLookAngles.from(pose);
    setPosition(position);
    setRotation(yaw + Mth.wrapDegrees(angles.yaw() - yaw), angles.pitch());
    rotation.set(pose);
    rotation.transform(forwards.set(0, 0, -1));
    rotation.transform(up.set(0, 1, 0));
    rotation.transform(left.set(-1, 0, 0));
  }
}
