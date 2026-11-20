package dev.portalix.client.mixin.traversal;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Connection.class)
abstract class OrderedKeepAliveMixin {
  @ModifyExpressionValue(
      method = "sendPacket",
      at = @At(value = "INVOKE", target = "Lio/netty/channel/EventLoop;inEventLoop()Z"),
      require = 1, allow = 1)
  private boolean portalix$orderClockResponse(boolean inEventLoop, Packet<?> packet) {
    // A Netty response must follow movement writes already queued by the client thread.
    return inEventLoop && !(packet instanceof ServerboundKeepAlivePacket);
  }
}
