package dev.portalix.resource;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.sound.GunSounds;
import dev.portalix.sound.SourceSoundAttenuation;
import dev.portalix.sound.SourceSoundSettings;
import java.io.IOException;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class BundledAssetsTest {
  @Test
  void everySoundHasValidSettingsAudioAndTranslatedSubtitles() throws IOException {
    var sounds = ModAssetLoader.json("/assets/portalix/sounds.json");
    var english = ModAssetLoader.json("/assets/portalix/lang/en_us.json");
    var russian = ModAssetLoader.json("/assets/portalix/lang/ru_ru.json");
    var settings = ModAssetLoader.json("/assets/portalix/source_sound_settings.json");
    var random = RandomSource.create(0x534f554e44L);
    for (GunSounds sound : GunSounds.values()) {
      assertTrue(sounds.has(sound.id()), sound.id());
      var definition = sounds.getAsJsonObject(sound.id());
      if (definition.has("subtitle")) {
        String subtitle = definition.get("subtitle").getAsString();
        assertTrue(english.has(subtitle), subtitle);
        assertTrue(russian.has(subtitle), subtitle);
      }
      var audio = definition.getAsJsonArray("sounds");
      assertFalse(audio.isEmpty(), sound.id());
      for (var entry : audio) {
        String name = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("name").getAsString();
        String[] parts = name.split(":", 2);
        String path = "/assets/" + parts[0] + "/sounds/" + parts[1] + ".ogg";
        try (var stream = ModAssetLoader.open(path)) {
          assertArrayEquals(new byte[] {'O', 'g', 'g', 'S'}, stream.readNBytes(4), path);
        }
      }
      var setting = settings.getAsJsonObject(sound.id());
      assertEquals(setting.get("soundLevel").getAsInt(), SourceSoundSettings.soundLevel(sound));
      float gain = setting.get("engineGain").getAsFloat() * .2f;
      float minVolume = setting.get("volume").getAsFloat() * gain;
      float maxVolume = setting.get("volumeMax").getAsFloat() * gain;
      float minPitch = setting.get("pitchMin").getAsFloat(), maxPitch = setting.get("pitchMax").getAsFloat();
      assertEquals(minVolume, SourceSoundSettings.volume(sound), 1e-6);
      for (int sample = 0; sample < 5; sample++) {
        float volume = SourceSoundSettings.volume(sound, random), pitch = SourceSoundSettings.pitch(sound, random);
        assertTrue(Float.isFinite(volume) && volume >= minVolume - 1e-6 && volume <= maxVolume + 1e-6, sound.id());
        assertTrue(Float.isFinite(pitch) && pitch >= minPitch && pitch <= maxPitch, sound.id());
      }
    }
  }

  @Test
  void soundAttenuationStaysFiniteBoundedAndNonIncreasingAcrossItsAudibleRange() {
    for (int level : new int[] {0, 75, 105}) {
      float previous = Float.POSITIVE_INFINITY;
      for (int sourceDistance = 0; sourceDistance <= 32768; sourceDistance += 1024) {
        float gain = SourceSoundAttenuation.gain(level, sourceDistance * SourceUnits.SOURCE_UNIT_BLOCKS);
        assertTrue(Float.isFinite(gain) && gain >= 0 && gain <= 1, "level=" + level + " distance=" + sourceDistance);
        assertTrue(gain <= Math.max(previous, .001f) + 1e-5, "attenuation rises at level=" + level + " distance=" + sourceDistance);
        previous = gain;
        if (level == 0) assertEquals(1, gain);
      }
    }
  }
}
