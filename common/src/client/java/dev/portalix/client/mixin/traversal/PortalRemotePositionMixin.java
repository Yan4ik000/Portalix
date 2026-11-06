package dev.portalix.client.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.RemotePortalInterpolation;
import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
abstract class PortalRemotePositionMixin {
  @WrapOperation(
      method = {
          "handleMoveEntity(Lnet/minecraft/network/protocol/game/ClientboundMoveEntityPacket;)V",
          "handleEntityPositionSync(Lnet/minecraft/network/protocol/game/ClientboundEntityPositionSyncPacket;)V"
      },
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isLocalInstanceAuthoritative()Z"),
      require = 2, allow = 2)
  private boolean portalix$predictedPosition(Entity entity, Operation<Boolean> original) {
    return original.call(entity) || EntityPortalState.of(entity).hasPredictions();
  }

  @WrapOperation(
      method = "handleMinecartAlongTrack",
      at = @At(value = "INVOKE", target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"))
  private boolean portalix$minecart(
      List<NewMinecartBehavior.MinecartStep> queue,
      Collection<NewMinecartBehavior.MinecartStep> updates,
      Operation<Boolean> original,
      @Local AbstractMinecart minecart) {
    var interpolation = RemotePortalInterpolation.of(minecart);
    if (interpolation == null || !interpolation.active()) return original.call(queue, updates);
    for (var step : updates) {
      interpolation.target(
          step.position(), step.yRot(), step.xRot(), NewMinecartBehavior.POS_ROT_LERP_TICKS);
      minecart.lerpMotion(interpolation.sourceMotion(step.movement()));
    }
    return !updates.isEmpty();
  }

  @WrapOperation(
      method = "handleSetEntityData",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"))
  private void portalix$pose(
      SynchedEntityData data,
      List<SynchedEntityData.DataValue<?>> values,
      Operation<Void> original,
      @Local Entity entity) {
    var interpolation = RemotePortalInterpolation.of(entity);
    if (interpolation == null || !interpolation.active()) {
      original.call(data, values);
      return;
    }
    var immediate = new ArrayList<SynchedEntityData.DataValue<?>>();
    for (var value : values) {
      if (PortalPassagePose.isPoseField(entity, value.id()))
        interpolation.scale((Float) value.value());
      else immediate.add(value);
    }
    original.call(data, immediate);
  }

  @WrapOperation(
      method = "setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/world/entity/PositionMoveRotation;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/PositionMoveRotation;"),
      require = 1, allow = 1)
  private static PositionMoveRotation portalix$correctionBaseline(Entity entity,
      Operation<PositionMoveRotation> original, @Local(argsOnly = true) Set<Relative> relatives) {
    boolean predicted = EntityPortalState.of(entity).hasPredictions();
    boolean interpolating = entity.isInterpolating();
    // Relative packets use the native interpolation target, independently of the displayed pose.
    var nativeBaseline = original.call(entity);
    var remote = RemotePortalInterpolation.of(entity);
    if (remote != null) remote.finishAccepted();
    ClientPortalMovement.corrected(entity, relatives);
    PortalEntityMovement.cancelPredictions(entity);
    var interpolation = entity.getInterpolation();
    if (interpolation != null) interpolation.cancel();
    EntityPortalState.of(entity).resetCamera();
    var corrected = original.call(entity);
    if (predicted || !interpolating) return corrected;
    return new PositionMoveRotation(nativeBaseline.position(), corrected.deltaMovement(),
        nativeBaseline.yRot(), nativeBaseline.xRot());
  }

  @WrapOperation(
      method = "handleSetEntityMotion",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"))
  private void portalix$motion(Entity entity, Vec3 motion, Operation<Void> original) {
    var interpolation = RemotePortalInterpolation.of(entity);
    var predicted = PortalEntityMovement.predictedMotion(entity, motion);
    original.call(entity, interpolation == null ? predicted : interpolation.sourceMotion(predicted));
  }

  @WrapOperation(
      method = "handleEntityPositionSync",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
  private double portalix$routeDistance(
      Vec3 position, Vec3 target, Operation<Double> original, @Local Entity entity) {
    var interpolation = RemotePortalInterpolation.of(entity);
    return interpolation != null && interpolation.active() ? 0 : original.call(position, target);
  }
}
