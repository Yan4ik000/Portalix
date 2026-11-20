package dev.portalix.network;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

public final class PayloadCodecs {
  public static final StreamCodec<ByteBuf, PortalLinkId> LINK_ID = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, PortalLinkId::gun,
      ByteBufCodecs.VAR_LONG, PortalLinkId::generation,
      ByteBufCodecs.VAR_LONG, PortalLinkId::entrance,
      ByteBufCodecs.VAR_LONG, PortalLinkId::exit,
      PortalLinkId::new);
  public static final StreamCodec<FriendlyByteBuf, List<PortalLink>> PORTAL_LINKS =
      StreamCodec.of(PayloadCodecs::writePortalLink, PayloadCodecs::readPortalLink)
          .apply(ByteBufCodecs.list());

  private PayloadCodecs() {}

  public static void writePortal(FriendlyByteBuf buffer, PortalInstance member) {
    var endpoint = member.endpoint();
    buffer.writeIdentifier(endpoint.dimension().identifier());
    Vec3.STREAM_CODEC.encode(buffer, endpoint.center());
    Vec3.STREAM_CODEC.encode(buffer, endpoint.normal());
    Vec3.STREAM_CODEC.encode(buffer, endpoint.up());
    buffer.writeDouble(endpoint.width());
    buffer.writeDouble(endpoint.height());
    buffer.writeLong(member.bornTick());
    buffer.writeLong(member.identity());
    buffer.writeEnum(member.variant());
  }

  public static PortalInstance readPortal(FriendlyByteBuf buffer) {
    return new PortalInstance(
        new PortalEndpoint(
            ResourceKey.create(Registries.DIMENSION, buffer.readIdentifier()),
            Vec3.STREAM_CODEC.decode(buffer),
            Vec3.STREAM_CODEC.decode(buffer),
            Vec3.STREAM_CODEC.decode(buffer),
            buffer.readDouble(),
            buffer.readDouble()),
        buffer.readLong(),
        buffer.readLong(),
        buffer.readEnum(PortalixVariant.class));
  }

  public static void writePortalLink(FriendlyByteBuf buffer, PortalLink link) {
    buffer.writeUUID(link.gun());
    buffer.writeVarLong(link.generation());
    writePortal(buffer, link.entrance());
    writePortal(buffer, link.exit());
  }

  public static PortalLink readPortalLink(FriendlyByteBuf buffer) {
    return new PortalLink(buffer.readUUID(), buffer.readVarLong(), readPortal(buffer), readPortal(buffer));
  }
}
