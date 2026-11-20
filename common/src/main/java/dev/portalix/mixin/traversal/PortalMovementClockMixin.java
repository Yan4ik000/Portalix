package dev.portalix.mixin.traversal;

import dev.portalix.server.portal.PlayerPortalMoveState;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class PortalMovementClockMixin {
  @Shadow @Final protected MinecraftServer server;
  @Shadow private boolean keepAlivePending;
  @Shadow private long keepAliveChallenge;
  @Shadow private long keepAliveTime;

  @Inject(method = "handleKeepAlive", at = @At("HEAD"))
  private void portalix$acknowledgeKeepAliveClock(ServerboundKeepAlivePacket packet, CallbackInfo ci) {
    if (!keepAlivePending || packet.getId() != keepAliveChallenge
        || !((Object) this instanceof ServerGamePacketListenerImpl listener)) return;
    var acknowledgement = new OrderedKeepAlive(keepAliveTime);
    var processor = server.packetProcessor();
    if (processor.isSameThread()) acknowledgement.handle(listener);
    else processor.scheduleIfPossible(listener, acknowledgement);
  }

  @Inject(method = "handlePong", at = @At("HEAD"))
  private void portalix$acknowledgeOrderedPong(ServerboundPongPacket packet, CallbackInfo ci) {
    if (!((Object) this instanceof ServerGamePacketListenerImpl listener)) return;
    PacketUtils.ensureRunningOnSameThread(packet, listener, server.packetProcessor());
    int id = packet.getId();
    if (id < 0) PlayerPortalMoveState.of(listener.player).acknowledgeClock(id);
    else if (id > 0) ((ChunkDeliveryAccess) listener.chunkSender).portalix$confirmRefresh(listener.player, id);
  }

  // Only queued locally: vanilla liveness stays on Netty, the clock follows movement packet order.
  private static final class OrderedKeepAlive extends ServerboundKeepAlivePacket {
    private OrderedKeepAlive(long sentAt) {
      super(sentAt);
    }

    @Override
    public void handle(ServerCommonPacketListener listener) {
      var player = ((ServerGamePacketListenerImpl) listener).player;
      PlayerPortalMoveState.of(player).acknowledgeKeepAlive(getId());
    }
  }
}
