package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.server.portal.PlayerPortalMoveState;
import java.util.List;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class PortalNativeMovementValidationMixin extends ServerCommonPacketListenerImpl {
  @Shadow public ServerPlayer player;

  protected PortalNativeMovementValidationMixin(
      MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
    super(server, connection, cookie);
  }

  @Shadow
  private boolean shouldCheckPlayerMovement(boolean fallFlying) {
    throw new AssertionError();
  }

  @Shadow
  private static double clampHorizontal(double value) {
    throw new AssertionError();
  }

  @Shadow
  private static double clampVertical(double value) {
    throw new AssertionError();
  }

  @Inject(
      method = "handleMovePlayer",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampHorizontal(D)D", ordinal = 0),
      cancellable = true, require = 1, allow = 1)
  private void portalix$playerMovementBudget(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
    var replay = PlayerPortalMoveState.of(player);
    if (player.isPassenger() || player.isSleeping()) return;
    boolean started = replay.movementStarted();
    if (!started && !replay.continuesMovement(player)) return;
    var target = new Vec3(clampHorizontal(packet.getX(player.getX())),
        clampVertical(packet.getY(player.getY())), clampHorizontal(packet.getZ(player.getZ())));
    boolean fallFlying = player.isFallFlying();
    boolean checkDistance = player.level().tickRateManager().runsNormally()
        && shouldCheckPlayerMovement(fallFlying);
    if (!replay.beginNativeMovement(Util.getMillis(), player.getDeltaMovement(),
        target.distanceTo(player.position()), fallFlying, checkDistance)) {
      ServerPlayerMotion.synchronizePosition(player, List.of());
      ci.cancel();
    } else if (!started) {
      ServerPlayerMotion.rebaseMovementBudget(player, player);
    }
  }

  @Inject(
      method = "handleMoveVehicle",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clampHorizontal(D)D", ordinal = 0),
      cancellable = true, require = 1, allow = 1)
  private void portalix$vehicleMovementBudget(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
    var replay = PlayerPortalMoveState.of(player);
    var vehicle = player.getRootVehicle();
    boolean started = replay.movementStarted();
    if (!started && !replay.continuesMovement(vehicle)) return;
    var target = new Vec3(clampHorizontal(packet.position().x),
        clampVertical(packet.position().y), clampHorizontal(packet.position().z));
    if (!replay.beginNativeMovement(Util.getMillis(), vehicle.getDeltaMovement(),
        target.distanceTo(vehicle.position()), false, !isSingleplayerOwner())) {
      player.connection.send(ClientboundMoveVehiclePacket.fromEntity(vehicle));
      ci.cancel();
    } else if (!started) {
      ServerPlayerMotion.rebaseMovementBudget(player, vehicle);
    }
  }

  @Definition(id = "player", field =
      "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;player:Lnet/minecraft/server/level/ServerPlayer;")
  @Definition(id = "getDeltaMovement", method =
      "Lnet/minecraft/server/level/ServerPlayer;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;")
  @Definition(id = "lengthSqr", method = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D")
  @Expression("this.player.getDeltaMovement().lengthSqr()")
  @ModifyExpressionValue(method = "handleMovePlayer", at = @At("MIXINEXTRAS:EXPRESSION"),
      require = 1, allow = 1)
  private double portalix$trustedPlayerImpulse(double original,
      @Share("nativeImpulse") LocalDoubleRef impulse) {
    return portalix$trustedTickImpulse(original, impulse);
  }

  @Definition(id = "vehicle", local = @Local(type = Entity.class, name = "vehicle"))
  @Definition(id = "getDeltaMovement", method =
      "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;")
  @Definition(id = "lengthSqr", method = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D")
  @Expression("vehicle.getDeltaMovement().lengthSqr()")
  @ModifyExpressionValue(method = "handleMoveVehicle", at = @At("MIXINEXTRAS:EXPRESSION"),
      require = 1, allow = 1)
  private double portalix$trustedVehicleImpulse(double original,
      @Share("nativeImpulse") LocalDoubleRef impulse) {
    return portalix$trustedTickImpulse(original, impulse);
  }

  @Unique
  private double portalix$trustedTickImpulse(double original, LocalDoubleRef impulse) {
    impulse.set(original);
    var replay = PlayerPortalMoveState.of(player);
    return replay.movementStarted() ? replay.movementImpulseSquared(original) : original;
  }

  @WrapOperation(
      method = "handleMovePlayer",
      at = @At(value = "INVOKE", ordinal = 1, target =
          "Lnet/minecraft/server/level/ServerChunkCache;move(Lnet/minecraft/server/level/ServerPlayer;)V"),
      require = 1, allow = 1)
  private void portalix$observePlayerMotion(ServerChunkCache chunks, ServerPlayer player, Operation<Void> original,
      @Local(name = "startX") double x, @Local(name = "startY") double y, @Local(name = "startZ") double z) {
    ServerPlayerMotion.withAcceptedMovement(player, player, x, y, z, () -> original.call(chunks, player));
  }

  @WrapOperation(
      method = "handleMoveVehicle",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/level/ServerChunkCache;move(Lnet/minecraft/server/level/ServerPlayer;)V"),
      require = 1, allow = 1)
  private void portalix$observeVehicleMotion(ServerChunkCache chunks, ServerPlayer player, Operation<Void> original,
      @Local(name = "vehicle") Entity vehicle,
      @Local(name = "oldX") double x, @Local(name = "oldY") double y, @Local(name = "oldZ") double z) {
    ServerPlayerMotion.withAcceptedMovement(player, vehicle, x, y, z, () -> original.call(chunks, player));
  }

  @WrapOperation(
      method = {"handleMovePlayer", "handleMoveVehicle"},
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;handlePlayerKnownMovement(Lnet/minecraft/world/phys/Vec3;)V"),
      require = 2, allow = 2)
  private void portalix$recordNativeMovement(
      ServerGamePacketListenerImpl listener, Vec3 movement, Operation<Void> original,
      @Share("nativeImpulse") LocalDoubleRef impulse) {
    var replay = PlayerPortalMoveState.of(player);
    // Vanilla is responsible for ordinary movement. Only its accepted distance counts if this tick later replays a portal move.
    replay.recordNativeMovement(movement.length(), impulse.get());
    var subject = player.getRootVehicle();
    original.call(listener, replay.replayedMovement(subject) ? replay.recordReplay(subject, movement) : movement);
  }
}
