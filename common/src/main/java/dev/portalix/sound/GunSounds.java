package dev.portalix.sound;

import dev.portalix.PortalixMod;
import dev.portalix.platform.RegistryPlatform;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

public enum GunSounds {
  PORTAL_OPEN_BLUE("portal_open_blue"),
  PORTAL_OPEN_ORANGE("portal_open_orange"),
  PORTAL_CLOSE_BLUE("portal_close_blue"),
  PORTAL_CLOSE_ORANGE("portal_close_orange"),
  PORTAL_FIZZLE("portal_fizzle"),
  PORTAL_AMBIENT("portal_ambient"),
  PORTAL_OPEN_LAYER("portal_open_layer"),
  INVALID_SURFACE("invalid_surface"),
  FIRE_BLUE("fire_blue"),
  FIRE_ORANGE("fire_orange"),
  INVALID("invalid"),
  DRAW("draw"),
  FIZZLE("fizzle"),
  HOLD("hold"),
  HOLD_INTRO("hold_intro"),
  RELEASE("release"),
  GRAB_FAILED("grab_failed");

  private final String id;
  private Supplier<SoundEvent> event;

  GunSounds(String id) {
    this.id = id;
  }

  public String id() {
    return this.id;
  }

  public SoundEvent sound() {
    return Objects.requireNonNull(this.event, "Unregistered gun sound " + this.id).get();
  }

  public static void register() {
    for (GunSounds sound : values()) {
      sound.event =
          RegistryPlatform.INSTANCE.register(
              Registries.SOUND_EVENT,
              PortalixMod.id(sound.id),
              () -> SoundEvent.createVariableRangeEvent(PortalixMod.id(sound.id)));
    }
  }

  public float volume() {
    return SourceSoundSettings.volume(this);
  }

  public float volume(RandomSource random) {
    return SourceSoundSettings.volume(this, random);
  }

  public float pitch(RandomSource random) {
    return SourceSoundSettings.pitch(this, random);
  }

  public int soundLevel() {
    return SourceSoundSettings.soundLevel(this);
  }
}
