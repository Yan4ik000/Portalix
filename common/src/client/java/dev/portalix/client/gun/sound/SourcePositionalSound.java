package dev.portalix.client.gun.sound;

import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.sound.GunSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class SourcePositionalSound extends AbstractTickableSoundInstance {
  private final Minecraft client;
  private final Entity emitter;
  private final int soundLevel;
  protected final float initialVolume;
  private final boolean fadeAmbient;
  private int ticks;
  private volatile boolean playbackStarted;

  public SourcePositionalSound(
      Minecraft client, GunSounds event, Entity emitter, Vec3 position, boolean loop) {
    super(
        event.sound(),
        emitter == null ? SoundSource.BLOCKS : SoundSource.PLAYERS,
        SoundInstance.createUnseededRandom());
    this.client = client;
    this.emitter = emitter;
    soundLevel = event.soundLevel();
    initialVolume = event.volume(random);
    volume = initialVolume;
    pitch = event.pitch(random);
    looping = loop;
    attenuation = Attenuation.NONE;
    updatePosition(emitter == null ? position : PortalEyePose.sample(emitter).position());
    fadeAmbient = event == GunSounds.PORTAL_AMBIENT;
    if (fadeAmbient) volume = 0;
  }

  @Override
  public boolean canStartSilent() {
    return true;
  }

  public int portalSoundLevel() {
    return soundLevel;
  }

  public void onPlaybackStarted() {
    playbackStarted = true;
  }

  protected boolean playbackStarted() {
    return playbackStarted;
  }

  @Override
  public boolean canPlaySound() {
    return emitter == null || !emitter.isSilent();
  }

  @Override
  public void tick() {
    if (emitter != null) {
      if (emitter.isRemoved() || emitter.level() != client.level) {
        stop();
        return;
      }
      updatePosition(PortalEyePose.sample(emitter).position());
    }
    if (fadeAmbient && playbackStarted) volume = initialVolume * Math.min(1, ++ticks / 2F);
  }

  private void updatePosition(Vec3 position) {
    relative =
        emitter != null
            && emitter == client.getCameraEntity()
            && client.options.getCameraType().isFirstPerson();
    x = relative ? 0 : position.x;
    y = relative ? 0 : position.y;
    z = relative ? 0 : position.z;
  }
}
