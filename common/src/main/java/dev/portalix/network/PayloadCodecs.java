package dev.portalix.network;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalLink;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;

public final class PayloadCodecs {
  private PayloadCodecs() {}

  public static void writeVector(FriendlyByteBuf buffer, Vec3 vector) {
    buffer.writeDouble(vector.x);
    buffer.writeDouble(vector.y);
    buffer.writeDouble(vector.z);
  }

  public static Vec3 readVector(FriendlyByteBuf buffer) {
    return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
  }

  public static void writePortal(FriendlyByteBuf buffer, PortalInstance member) {
    var endpoint = member.endpoint();
    buffer.writeIdentifier(endpoint.dimension().identifier());
    writeVector(buffer, endpoint.center());
    writeVector(buffer, endpoint.normal());
    writeVector(buffer, endpoint.up());
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
            readVector(buffer),
            readVector(buffer),
            readVector(buffer),
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

  public static void writeLink(FriendlyByteBuf buffer, PortalLinkId link) {
    buffer.writeUUID(link.gun());
    buffer.writeVarLong(link.generation());
    buffer.writeVarLong(link.entrance());
    buffer.writeVarLong(link.exit());
  }

  public static PortalLinkId readLink(FriendlyByteBuf buffer) {
    return new PortalLinkId(
        buffer.readUUID(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong());
  }
}
