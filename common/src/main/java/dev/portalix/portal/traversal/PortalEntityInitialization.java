package dev.portalix.portal.traversal;

import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.EntityPortalInitPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.server.portal.PlayerPortalMoveState;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

public final class PortalEntityInitialization {
  private PortalEntityInitialization() {}

  public static void send(Entity entity, ServerPlayer observer,
      Consumer<Packet<? super ClientGamePacketListener>> packets) {
    EntityPortalInitPayload.State state;
    if (entity instanceof AbstractArrow arrow) {
      var rotation = PortalProjectileRotation.residual(arrow);
      if (rotation == null) return;
      state = new EntityPortalInitPayload.Rotation(rotation);
    } else if (entity instanceof ItemEntity item && PortalCrossingSync.pending(entity)) {
      state = new EntityPortalInitPayload.Rotation(PortalItemRotation.rotation(item));
    } else if (entity instanceof FishingHook hook) {
      var route = ProjectilePortalMovement.tether(hook);
      var owner = hook.getPlayerOwner();
      if (route.isEmpty() && EntityPortalState.of(hook).sequence == 0
          && (owner == null || EntityPortalState.of(owner).sequence == 0) && owner != observer) return;
      long completed = 0;
      List<PortalLink> active = List.of();
      if (owner instanceof ServerPlayer player) {
        var movement = ((PlayerPortalMoveState.Access) player).portalix$transit();
        completed = movement.completedSequence();
        active = movement.activeCrossings(player);
      }
      state = new EntityPortalInitPayload.FishingTether(route, completed, active);
    } else return;
    if (!ModNetworking.canPlayerReceive(observer, EntityPortalInitPayload.TYPE)) return;
    if (entity instanceof FishingHook hook && hook.getPlayerOwner() != null)
      PortalCrossingSync.sendPending(hook.getPlayerOwner(), observer, packets);
    var pose = new PositionMoveRotation(entity.position(), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot());
    packets.accept(new ClientboundCustomPayloadPacket(new EntityPortalInitPayload(entity.getId(), entity.getUUID(),
        EntityPortalState.of(entity).sequence, pose, entity.onGround(), state)));
  }

  public static void apply(Entity entity, EntityPortalInitPayload packet,
      List<PortalLink> ownerCrossings) {
    switch (packet.state()) {
      case EntityPortalInitPayload.Rotation rotation -> {
        if (entity instanceof AbstractArrow arrow)
          ((PortalProjectileRotation.Access) arrow).portalix$projectileModelRotation(rotation.rotation());
        else if (entity instanceof ItemEntity item)
          ((PortalItemRotation.Access) item).portalix$rotation(rotation.rotation());
        else return;
      }
      case EntityPortalInitPayload.FishingTether tether -> {
        if (!(entity instanceof FishingHook hook)) return;
        ProjectilePortalMovement.initializeTether(hook, tether.route(), ownerCrossings);
        var interpolation = hook.getInterpolation();
        if (interpolation != null) interpolation.cancel();
      }
    }
    EntityPortalState.of(entity).initializeCrossingSequence(packet.event());
    var pose = packet.pose();
    // Keep the native cached spawn position as the anchor for subsequent relative packets.
    entity.snapTo(pose.position(), pose.yRot(), pose.xRot());
    entity.setDeltaMovement(pose.deltaMovement());
    entity.setOnGround(packet.onGround());
  }
}
