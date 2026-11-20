package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.world.snapshot.LocalChunkPalettes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketDecoder.class)
abstract class PortalLocalPacketDecoderMixin {
  @WrapMethod(method = "decode")
  private void portalix$localPalettes(
      ChannelHandlerContext context, ByteBuf input, List<Object> output, Operation<Void> original) {
    LocalChunkPalettes.decode(input, () -> original.call(context, input, output));
  }
}
