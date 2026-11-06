package dev.portalix.client.gun.sound;

import dev.portalix.sound.GunSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

final class CarryLoopSound extends SourcePositionalSound {
  // Loop marker in the original object_use_lp_01.wav, retained by the sound exporter.
  private static final double LOOP_START_TICKS = 192390 * 20.0 / 44100;

  private final Player owner;
  private final boolean loopingStage;
  int age;
  private double playedTicks;
  private int fadeTicks = -1;
  private int maxFadeTicks;
  private float fadeVolume;
  private float fadePitch;

  CarryLoopSound(Minecraft client, Player owner) {
    this(client, owner, false, 0);
  }

  CarryLoopSound(Minecraft client, Player owner, boolean loopingStage, int age) {
    super(
        client,
        loopingStage ? GunSounds.HOLD : GunSounds.HOLD_INTRO,
        owner,
        owner.getEyePosition(),
        loopingStage);
    this.owner = owner;
    this.loopingStage = loopingStage;
    this.age = age;
    volume = initialVolume * envelope(age);
    pitch = loopingStage ? 1 : .7F;
  }

  Player owner() {
    return owner;
  }

  boolean readyForLoop() {
    return !loopingStage && playedTicks >= LOOP_START_TICKS;
  }

  void abort() {
    stop();
  }

  void finish() {
    finish(14);
  }

  void finish(int ticks) {
    if (fadeTicks >= 0) return;
    fadeTicks = 0;
    maxFadeTicks = ticks;
    fadeVolume = volume;
    fadePitch = pitch;
  }

  @Override
  public void tick() {
    super.tick();
    if (isStopped() || !playbackStarted()) return;
    if (fadeTicks >= 0) {
      float remaining = Math.max(0, 1 - (float) ++fadeTicks / maxFadeTicks);
      volume = fadeVolume * remaining;
      pitch = .7F + (fadePitch - .7F) * remaining;
      if (fadeTicks >= maxFadeTicks) stop();
    } else {
      playedTicks += Math.clamp(getPitch(), .5F, 2F);
      volume = initialVolume * envelope(++age);
      pitch = .7F + .3F * Math.min(1, age / 3F);
    }
  }

  static float envelope(int ageTicks) {
    float seconds = ageTicks / 20F;
    return Math.min(1, seconds / .15F) * (1 - .5F * Math.clamp((seconds - 3) / 12, 0, 1));
  }
}
