package dev.portalix.mixin.interaction;

import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.audio.PortalSoundPropagation;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.EntityObservers;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
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
    Collection<ServerPlayer> soundObservers = null;
    ClientboundSoundPacket positionalSound = null;
    for (var player : ((PlayerList) (Object) this).getPlayers()) {
      if (player == except
          || !player.level().dimension().equals(dimension)
          || player.position().distanceToSqr(source) < squared) continue;
      if (sound || packet instanceof ClientboundLevelEventPacket) {
        if (routes == null)
          routes = PortalTopology.soundRoutes(player.level());
        if (routes.closerThan(PortalEyePose.sample(player).position(), source, range)) {
          Packet<?> delivered = packet;
          if (packet instanceof ClientboundSoundEntityPacket entitySound) {
            if (soundObservers == null) {
              var entity = player.level().getEntity(entitySound.getId());
              soundObservers = entity == null ? List.of() : EntityObservers.tracking(entity);
            }
            if (!soundObservers.contains(player)) {
              if (positionalSound == null) {
                positionalSound =
                    new ClientboundSoundPacket(
                        entitySound.getSound(), entitySound.getSource(), x, y, z,
                        entitySound.getVolume(), entitySound.getPitch(), entitySound.getSeed());
              }
              delivered = positionalSound;
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
