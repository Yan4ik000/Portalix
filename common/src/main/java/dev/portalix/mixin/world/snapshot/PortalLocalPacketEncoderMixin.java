package dev.portalix.mixin.world.snapshot;

import dev.portalix.world.snapshot.ChunkPaletteSnapshots;
import dev.portalix.world.snapshot.LocalPaletteBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.local.LocalChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketEncoder.class)
abstract class PortalLocalPacketEncoderMixin extends MessageToByteEncoder<Object> {
  @Override
  protected ByteBuf allocateBuffer(ChannelHandlerContext context, Object packet, boolean direct)
      throws Exception {
    var buffer = super.allocateBuffer(context, packet, direct);
    if (context.channel() instanceof LocalChannel
        && packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
      var palettes = ((ChunkPaletteSnapshots) chunk.getChunkData()).portalix$palettes();
      if (palettes != null) return new LocalPaletteBuffer(buffer, palettes);
    }
    return buffer;
  }
}
