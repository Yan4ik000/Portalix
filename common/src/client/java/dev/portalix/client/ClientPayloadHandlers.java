package dev.portalix.client;

import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunEventPayload;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.network.portal.PortalStatePayload;
import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

final class ClientPayloadHandlers {
  private ClientPayloadHandlers() {}

  static void register(ClientNetworking.Registration registration) {
    registerReceiver(registration,
        CarryGrabPayload.TYPE,
        (session, grab) -> session.carryController.grab(grab));
    registerReceiver(registration,
        CarryStatePayload.TYPE,
        (session, update) -> session.carryController.receive(update));
    registerReceiver(registration,
        CarryReleasePayload.TYPE,
        (session, release) -> session.carryController.release(release));
    registerReceiver(registration,
        GunShotPayload.TYPE,
        (session, shot) -> session.shots.onShotFired(shot));
    registerReceiver(registration,
        PortalImpactPayload.TYPE,
        (session, impact) -> session.shots.onPortalImpact(impact));
    registerReceiver(registration,
        PortalStatePayload.TYPE,
        (session, state) -> session.portals.applySnapshot(state));
    registerReceiver(registration, GunStatePayload.TYPE, PortalClientSession::applySnapshot);
    registerReceiver(registration, GunEventPayload.TYPE, PortalClientSession::receiveEvent);
  }

  private static <T extends CustomPacketPayload> void registerReceiver(
      ClientNetworking.Registration registration,
      CustomPacketPayload.Type<T> type,
      BiConsumer<PortalClientSession, T> receiver) {
    registration.register(
        type,
        (payload, context) -> {
          PortalixClient.ensureSession(context);
          receiver.accept(PortalixClient.session(), payload);
        });
  }
}
