package dev.portalix.sound;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.portalix.resource.ModAssetLoader;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

public final class SourceSoundSettings {
  private static final float MASTER_VOLUME = 0.2F;
  private static final Map<GunSounds, Setting> SETTINGS = load();

  private SourceSoundSettings() {}

  private static Map<GunSounds, Setting> load() {
    Map<String, Setting> loaded =
        new Gson()
            .fromJson(
                ModAssetLoader.json("/assets/portalix/source_sound_settings.json"),
                new TypeToken<Map<String, Setting>>() {});
    Map<GunSounds, Setting> settings = new EnumMap<>(GunSounds.class);
    for (GunSounds sound : GunSounds.values()) {
      Setting setting = loaded.get(sound.id());
      if (setting == null
          || !Float.isFinite(setting.volume())
          || setting.volume() < 0.0F
          || !Float.isFinite(setting.volumeMax())
          || setting.volumeMax() < setting.volume()
          || setting.soundLevel() < 0
          || setting.soundLevel() > 140
          || !Float.isFinite(setting.engineGain())
          || setting.engineGain() <= 0
          || !Float.isFinite(setting.pitchMin())
          || !Float.isFinite(setting.pitchMax())
          || setting.pitchMin() <= 0.0F
          || setting.pitchMin() > setting.pitchMax()) {
        throw new IllegalStateException("Invalid Source sound setting " + sound.id());
      }
      settings.put(sound, setting);
    }
    return Map.copyOf(settings);
  }

  public static float volume(GunSounds event) {
    Setting setting = SETTINGS.get(event);
    return setting.volume() * setting.engineGain() * MASTER_VOLUME;
  }

  public static float volume(GunSounds event, RandomSource random) {
    Setting setting = SETTINGS.get(event);
    return (setting.volume() + (setting.volumeMax() - setting.volume()) * random.nextFloat())
        * setting.engineGain()
        * MASTER_VOLUME;
  }

  public static int soundLevel(GunSounds event) {
    return SETTINGS.get(event).soundLevel();
  }

  public static float pitch(GunSounds event, RandomSource random) {
    Setting setting = SETTINGS.get(event);
    return setting.pitchMin() + (setting.pitchMax() - setting.pitchMin()) * random.nextFloat();
  }

  private record Setting(
      float volume,
      float volumeMax,
      float pitchMin,
      float pitchMax,
      int soundLevel,
      float engineGain) {}
}
