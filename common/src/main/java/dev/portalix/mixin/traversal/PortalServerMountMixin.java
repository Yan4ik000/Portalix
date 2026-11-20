package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
abstract class PortalServerMountMixin {
  @WrapOperation(
      method = "startRiding",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V"),
      require = 1, allow = 1)
  private void portalix$captureMountPosition(
      ServerGamePacketListenerImpl listener, PositionMoveRotation pose, Set<Relative> relatives,
      Operation<Void> original,
      @Share("mountPosition") LocalRef<Packet<? super ClientGamePacketListener>> position) {
    var player = (ServerPlayer) (Object) this;
    if (!PortalPassagePose.compact(player) && !PlayerPortalVisibility.of(player).hasReadyLinks()) {
      original.call(listener, pose, relatives);
      return;
    }
    // Applying a rider's new position before the mount lets the client replay it as an on-foot move.
    position.set(ServerPlayerMotion.captureMountPosition(player, () -> original.call(listener, pose, relatives)));
  }

  @WrapOperation(
      method = "startRiding",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
      require = 1, allow = 1)
  private void portalix$sendMount(
      ServerGamePacketListenerImpl listener, Packet<? super ClientGamePacketListener> passengers,
      Operation<Void> original,
      @Share("mountPosition") LocalRef<Packet<? super ClientGamePacketListener>> position) {
    var teleport = position.get();
    original.call(listener, teleport == null ? passengers : new ClientboundBundlePacket(List.of(teleport, passengers)));
  }
}
