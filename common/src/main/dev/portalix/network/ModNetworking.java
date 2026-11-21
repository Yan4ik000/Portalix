package dev.portalix.network;

import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunCommandPayload;
import dev.portalix.network.gun.GunEventPayload;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.network.portal.EntityPortalCrossingPayload;
import dev.portalix.network.portal.EntityPortalInitPayload;
import dev.portalix.network.portal.PlayerPortalMoveAckPayload;
import dev.portalix.network.portal.PlayerPortalMovePayload;
import dev.portalix.network.portal.PortalCameraViewDemandPayload;
import dev.portalix.network.portal.PortalOwnedStatePayload;
import dev.portalix.network.portal.PortalStatePayload;
import dev.portalix.network.portal.PortalViewDemandPayload;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.network.portal.PortalViewSettingsPayload;
import dev.portalix.network.portal.PortalViewStatusPayload;
import dev.portalix.network.portal.ProjectilePortalMovePayload;
import dev.portalix.network.portal.ProjectileStatePayload;
import dev.portalix.platform.PortalPlatform;
import dev.portalix.portal.traversal.ProjectileStateSync;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.server.portal.PlayerPortalMovement;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
  public static final int PROTOCOL_VERSION = 1;

  private ModNetworking() {}

  public interface Registration {
    <T extends CustomPacketPayload> void clientbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    <T extends CustomPacketPayload> void serverbound(
        CustomPacketPayload.Type<T> type,
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        BiConsumer<ServerPlayer, T> receiver);

    <T extends CustomPacketPayload> void optionalClientbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    <T extends CustomPacketPayload> void optionalBidirectional(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        BiConsumer<ServerPlayer, T> receiver);

    <T extends CustomPacketPayload> void optionalServerbound(
        CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
        BiConsumer<ServerPlayer, T> receiver);
  }

  public static void register(Registration registration) {
    registration.clientbound(GunEventPayload.TYPE, GunEventPayload.CODEC);
    registration.clientbound(GunShotPayload.TYPE, GunShotPayload.CODEC);
    registration.clientbound(GunStatePayload.TYPE, GunStatePayload.CODEC);
    registration.clientbound(PortalImpactPayload.TYPE, PortalImpactPayload.CODEC);
    registration.clientbound(CarryGrabPayload.TYPE, CarryGrabPayload.CODEC);
    registration.clientbound(CarryStatePayload.TYPE, CarryStatePayload.CODEC);
    registration.clientbound(CarryReleasePayload.TYPE, CarryReleasePayload.CODEC);
    registration.clientbound(EntityPortalCrossingPayload.TYPE, EntityPortalCrossingPayload.CODEC);
    registration.clientbound(PlayerPortalMoveAckPayload.TYPE, PlayerPortalMoveAckPayload.CODEC);
    registration.clientbound(ProjectileStatePayload.TYPE, ProjectileStatePayload.CODEC);
    registration.clientbound(EntityPortalInitPayload.TYPE, EntityPortalInitPayload.CODEC);
    registration.clientbound(PortalStatePayload.TYPE, PortalStatePayload.CODEC);
    registration.optionalClientbound(PortalOwnedStatePayload.TYPE, PortalOwnedStatePayload.CODEC);
    registration.serverbound(
        ProjectilePortalMovePayload.TYPE, ProjectilePortalMovePayload.CODEC,
        ProjectileStateSync::requestSnapshot);
    registration.serverbound(
        PlayerPortalMovePayload.TYPE, PlayerPortalMovePayload.CODEC,
        PlayerPortalMovement::receive);
    registration.serverbound(
        PortalViewReadyPayload.TYPE, PortalViewReadyPayload.CODEC,
        PortalServerEvents::acknowledgeView);
    registration.optionalBidirectional(
        PortalViewStatusPayload.TYPE, PortalViewStatusPayload.CODEC,
        (player, status) -> {
          if (status.ready())
            PortalServerEvents.acknowledgeView(player, new PortalViewReadyPayload(status.entrance(), status.generation()));
        });
    registration.serverbound(
        PortalViewDemandPayload.TYPE, PortalViewDemandPayload.CODEC,
        PortalServerEvents::requestViews);
    registration.optionalServerbound(
        PortalCameraViewDemandPayload.TYPE, PortalCameraViewDemandPayload.CODEC,
        PortalServerEvents::requestCameraViews);
    registration.optionalServerbound(
        PortalViewSettingsPayload.TYPE, PortalViewSettingsPayload.CODEC,
        PortalServerEvents::requestViewSettings);
    registration.serverbound(
        GunCommandPayload.TYPE, GunCommandPayload.CODEC,
        (player, payload) -> PortalServerEvents.receiveGunCommand(player, payload.command()));
  }

  public static boolean canPlayerReceive(ServerPlayer player, CustomPacketPayload.Type<?> type) {
    return PortalPlatform.INSTANCE.canPlayerReceive(player, type);
  }

  public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
    PortalPlatform.INSTANCE.sendToPlayer(player, payload);
  }
}
