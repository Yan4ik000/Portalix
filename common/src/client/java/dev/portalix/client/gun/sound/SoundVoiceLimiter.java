package dev.portalix.client.gun.sound;

import dev.portalix.sound.GunSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class SoundVoiceLimiter {
  private record Voice(
      GunSounds event, SourcePositionalSound sound, Entity emitter, Vec3 position, double started) {
    boolean sameEmitter(Entity other, Vec3 origin) {
      return emitter == other && (emitter != null || position.equals(origin));
    }
  }

  private final Minecraft client;
  private final List<Voice> voices = new ArrayList<>();

  public SoundVoiceLimiter(Minecraft client) {
    this.client = client;
  }

  public SourcePositionalSound play(GunSounds event, Entity emitter, Vec3 position) {
    if (client.level == null) return null;
    double now =
        (client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false))
            / 20.0;
    tick();
    if (event == GunSounds.PORTAL_OPEN_BLUE
        || event == GunSounds.PORTAL_OPEN_ORANGE
        || event == GunSounds.INVALID_SURFACE) {
      for (Voice voice : voices) {
        if (voice.event == event
            && voice.sameEmitter(emitter, position)
            && now - voice.started < .1) return null;
      }
    }
    GunSounds group = group(event);
    if (group != null) {
      var matching =
          voices.stream()
              .filter(voice -> group(voice.event) == group && voice.sameEmitter(emitter, position))
              .toList();
      for (int i = 0; i < matching.size() - 1; i++) {
        Voice old = matching.get(i);
        client.getSoundManager().stop(old.sound);
        voices.remove(old);
      }
    }
    SourcePositionalSound sound =
        new SourcePositionalSound(client, event, emitter, position, false);
    voices.add(new Voice(event, sound, emitter, position, now));
    client.getSoundManager().play(sound);
    return sound;
  }

  private static GunSounds group(GunSounds event) {
    return switch (event) {
      case FIRE_BLUE, FIRE_ORANGE -> GunSounds.FIRE_BLUE;
      case PORTAL_OPEN_BLUE, PORTAL_OPEN_ORANGE -> GunSounds.PORTAL_OPEN_BLUE;
      case PORTAL_OPEN_LAYER, INVALID_SURFACE -> event;
      default -> null;
    };
  }

  public void stopEmitter(int id) {
    voices.removeIf(
        voice -> {
          if (voice.emitter == null || voice.emitter.getId() != id) return false;
          if (voice.event != GunSounds.FIRE_BLUE
              && voice.event != GunSounds.FIRE_ORANGE
              && voice.event != GunSounds.DRAW) return false;
          client.getSoundManager().stop(voice.sound);
          return true;
        });
  }

  public void tick() {
    voices.removeIf(voice -> !client.getSoundManager().isActive(voice.sound));
  }

  public void clear() {
    for (Voice voice : voices) client.getSoundManager().stop(voice.sound);
    voices.clear();
  }
}
