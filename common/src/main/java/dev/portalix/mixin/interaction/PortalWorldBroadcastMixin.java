package dev.portalix.mixin.interaction;

import dev.portalix.mixin.world.tracking.ChunkMapAccessor;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.audio.PortalSoundPropagation;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.PortalEntityTracking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
abstract class PortalWorldBroadcastMixin {
  @Inject(method = "broadcast", at = @At("TAIL"))
  private void portalix$remote(
      Player except,
      double x,
      double y,
      double z,
      double range,
      ResourceKey<Level> dimension,
      Packet<?> packet,
      CallbackInfo callback) {
    var source = new Vec3(x, y, z);
    double squared = range * range;
    boolean sound =
        packet instanceof ClientboundSoundPacket || packet instanceof ClientboundSoundEntityPacket;
    PortalSoundPropagation.Routes routes = null;
    for (var player : ((PlayerList) (Object) this).getPlayers()) {
      if (player == except
          || !player.level().dimension().equals(dimension)
          || player.position().distanceToSqr(source) < squared) continue;
      if (sound || packet instanceof ClientboundLevelEventPacket) {
        if (routes == null)
          routes = PortalSoundPropagation.prepare(PortalTopology.links(player.level()));
        var path = routes.findPath(
            PortalEyePose.sample(player).position(), source, distance -> Math.max(0, 1 - distance / range));
        if (path.distance() < range) {
          Packet<?> delivered = packet;
          if (packet instanceof ClientboundSoundEntityPacket entitySound) {
            var tracker =
                (PortalEntityTracking.Tracker)
                    ((ChunkMapAccessor) player.level().getChunkSource().chunkMap)
                        .portalix$entityTrackers().get(entitySound.getId());
            if (tracker == null || !tracker.portalix$hasObserver(player)) {
              delivered =
                  new ClientboundSoundPacket(
                      entitySound.getSound(), entitySound.getSource(), x, y, z,
                      entitySound.getVolume(), entitySound.getPitch(), entitySound.getSeed());
            }
          }
          player.connection.send(delivered);
          continue;
        }
        if (sound) continue;
      }
      for (var eye : PlayerPortalVisibility.of(player).eyes()) {
        if (eye.distanceToSqr(source) < squared) {
          player.connection.send(packet);
          break;
        }
      }
    }
  }
}
