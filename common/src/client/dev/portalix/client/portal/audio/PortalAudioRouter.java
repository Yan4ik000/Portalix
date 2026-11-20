package dev.portalix.client.portal.audio;

import dev.portalix.client.gun.sound.SourcePositionalSound;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.audio.PortalSoundPropagation;
import dev.portalix.sound.SourceSoundAttenuation;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class PortalAudioRouter {
  private static Supplier<PortalSoundPropagation.Routes> routes = () -> PortalTopology.soundRoutes(null);
  private static Vec3 listener = Vec3.ZERO;
  private static PortalSoundPropagation.Transmission transmission = (start, end, radius) -> 1;

  private PortalAudioRouter() {}

  public static void configure(
      Supplier<PortalSoundPropagation.Routes> supplier,
      PortalSoundPropagation.Transmission segmentTransmission) {
    routes = supplier;
    transmission = segmentTransmission;
  }

  public static void reset() {
    listener = Vec3.ZERO;
  }

  public static boolean positional(SoundInstance sound) {
    return sound instanceof SourcePositionalSound
        || sound.getSource() != SoundSource.MUSIC
            && !sound.isRelative()
            && sound.getAttenuation() == SoundInstance.Attenuation.LINEAR;
  }

  public static double delayDistanceSquared(Vec3 listener, Vec3 emitter) {
    return routes.get().delayDistanceSquared(listener, emitter);
  }

  public static boolean withinDistance(Vec3 listener, Vec3 emitter, double range) {
    return routes.get().withinDistance(listener, emitter, range);
  }

  public static Playback playback(SoundInstance sound) {
    return playback(sound, transmission);
  }

  public static Playback playback(
      SoundInstance sound, PortalSoundPropagation.Transmission segmentTransmission) {
    var emitter = new Vec3(sound.getX(), sound.getY(), sound.getZ());
    if (sound.isRelative()) {
      return new Playback(List.of(), emitter, 1, 1, true);
    }
    DoubleUnaryOperator gain;
    int soundLevel;
    if (sound instanceof SourcePositionalSound imported) {
      soundLevel = imported.portalSoundLevel();
      gain = distance -> SourceSoundAttenuation.gain(soundLevel, distance);
    } else {
      soundLevel = 75;
      double range = Math.max(1, sound.getVolume()) * sound.getSound().getAttenuationDistance();
      gain = distance -> Math.max(0, 1 - distance / range);
    }
    var best = routes.get().findPath(
        listener, emitter, gain, SourceSoundAttenuation.occlusionRadius(soundLevel), segmentTransmission);
    return new Playback(best.links(), best.position(), best.distanceGain(), best.transmission(), false);
  }

  public static Vec3 subtitlePosition(SoundInstance sound) {
    var playback = playback(sound);
    return playback.relative() ? listener : playback.position();
  }

  public static void updateListener(Vec3 listener) {
    PortalAudioRouter.listener = listener;
  }

  public static List<PortalSoundPropagation.Observer> observers(Vec3 listener, double range) {
    return routes.get().observers(listener, range);
  }

  public record Playback(
      List<PortalLink> route, Vec3 position, double gain, double occlusion, boolean relative) {}
}
