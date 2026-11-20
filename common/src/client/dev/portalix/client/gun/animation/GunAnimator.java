package dev.portalix.client.gun.animation;

import dev.portalix.client.gun.model.GunModel;
import dev.portalix.client.gun.model.GunModels;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.network.gun.GunEventKind;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.joml.Quaternionf;

public final class GunAnimator {
  private static final double TICK_SECONDS = GunFireTiming.TICK_SECONDS;
  private final IntSupplier target;
  private final GunAnimationChannel posture;
  private final GunAnimationChannel carry;
  private final GunAnimationChannel action;
  private final float[] neutral;
  private final float[] pose;
  private final float[] layerPose;
  private final Quaternionf qa = new Quaternionf();
  private final Quaternionf qb = new Quaternionf();
  private static final double INACTIVE_RELEASE_TIME = -100.0;
  private double time;
  private double lastSampleTime;
  private double grabStart;
  private double releaseStart = INACTIVE_RELEASE_TIME;
  private boolean sprinting;
  private boolean equipped = true;
  private boolean orange;
  private boolean energized;

  public GunAnimator() {
    this(() -> -1);
  }

  public GunAnimator(IntSupplier target) {
    this(target, GunModels.view());
  }

  public GunAnimator(IntSupplier target, GunModel mesh) {
    this.target = Objects.requireNonNull(target);
    GunAnimationSet library = Objects.requireNonNull(mesh).animations();
    neutral = library.pose(GunAnimationClip.IDLE.asset(), 0.0F, true);
    pose = new float[neutral.length];
    layerPose = new float[neutral.length];
    posture = new GunAnimationChannel(library, neutral, 0.18F, GunAnimationClip.IDLE);
    carry = new GunAnimationChannel(library, neutral, 0.18F, null);
    action = new GunAnimationChannel(library, neutral, 0.06F, null);
  }

  private double now() {
    return Math.max(time, lastSampleTime);
  }

  public float releaseAge(float partial) {
    return (float) Math.max(0.0, renderTime(partial) - releaseStart);
  }

  public float holdStopAge() {
    return (float) (releaseStart - grabStart);
  }

  public float holdAge(float partial) {
    return (float) Math.max(0.0, renderTime(partial) - grabStart);
  }

  private double renderTime(float partial) {
    return Math.max(lastSampleTime, time + Math.clamp(partial, 0.0F, 1.0F) * TICK_SECONDS);
  }

  public boolean isDrawing() {
    return posture.clip() == GunAnimationClip.DRAW && !posture.finished(time);
  }

  public boolean isHolstering() {
    return posture.clip() == GunAnimationClip.HOLSTER;
  }

  public boolean isHolstered() {
    return isHolstering() && posture.finished(time);
  }

  public void holster() {
    carry.snap(null, now());
    action.snap(null, now());
    posture.play(GunAnimationClip.HOLSTER, now());
  }

  public boolean isCarrying() {
    return target.getAsInt() >= 0;
  }

  public boolean orange() {
    return orange;
  }

  public boolean hasEnergy() {
    return energized;
  }

  public void synchronizePortals(int activeMask, boolean color) {
    orange = color;
    energized = (activeMask & 3) != 0;
  }

  public void setEquipped(boolean value) {
    equipped = value;
    if (!value) {
      posture.snap(null, now());
      carry.snap(null, now());
      action.snap(null, now());
    }
  }

  public void synchronizeHold(int ticks) {
    if (isCarrying()) {
      grabStart = now() - Math.max(0, ticks) * TICK_SECONDS;
      releaseStart = INACTIVE_RELEASE_TIME;
      if (carry.clip() == null || carry.clip() == GunAnimationClip.RELEASE)
        carry.play(GunAnimationClip.CARRY_IDLE, now());
    } else if (carry.clip() == GunAnimationClip.GRAB
        || carry.clip() == GunAnimationClip.CARRY_IDLE) {
      releaseStart = now();
      carry.play(GunAnimationClip.RELEASE, now());
    }
    updatePosture();
  }

  public void tick() {
    time += TICK_SECONDS;
    if (!equipped) {
      return;
    }
    GunAnimationClip resting = idle();
    if (!isHolstering())
      posture.finish(
          time,
          posture.clip() == GunAnimationClip.DRAW && resting == GunAnimationClip.SPRINT_IDLE
              ? GunAnimationClip.LOWER
              : resting);
    carry.finish(time, isCarrying() ? GunAnimationClip.CARRY_IDLE : null);
    action.finish(time, null);
  }

  public void play(GunAnimationClip selected) {
    Objects.requireNonNull(selected);
    switch (selected) {
      case FIRE, RESET, DRY_FIRE -> action.play(selected, now());
      case GRAB, RELEASE, CARRY_IDLE -> carry.play(selected, now());
      case DRAW -> posture.snap(selected, now());
      default -> posture.play(selected, now());
    }
  }

  private GunAnimationClip idle() {
    return sprinting && !isCarrying() ? GunAnimationClip.SPRINT_IDLE : GunAnimationClip.IDLE;
  }

  public void onGunEvent(GunEventKind event) {
    if (isHolstering()) return;
    if (event.endsHold()) {
      releaseStart = now();
      carry.play(GunAnimationClip.RELEASE, now());
    }
    if (!equipped) return;
    switch (event) {
      case FIRE_BLUE, FIRE_ORANGE, THROWN -> play(GunAnimationClip.FIRE);
      case GRABBED -> {
        grabStart = now();
        releaseStart = INACTIVE_RELEASE_TIME;
        play(GunAnimationClip.GRAB);
      }
      case RESET -> {
        energized = false;
        play(GunAnimationClip.RESET);
      }
      case GRAB_FAILED -> play(GunAnimationClip.DRY_FIRE);
      default -> {}
    }
    updatePosture();
  }

  public void setSprinting(boolean value) {
    if (value != sprinting) {
      sprinting = value;
      updatePosture();
    }
  }

  private void updatePosture() {
    GunAnimationClip current = posture.clip();
    if (!equipped || current == GunAnimationClip.DRAW || current == GunAnimationClip.HOLSTER)
      return;
    boolean lowered = current == GunAnimationClip.SPRINT_IDLE || current == GunAnimationClip.LOWER;
    boolean shouldLower = idle() == GunAnimationClip.SPRINT_IDLE;
    if (lowered != shouldLower)
      posture.play(shouldLower ? GunAnimationClip.LOWER : GunAnimationClip.RAISE, now());
  }

  public float[] sample(float partial) {
    double at = renderTime(partial);
    lastSampleTime = at;
    posture.sample(at, pose);
    if (carry.active(at)) {
      carry.sample(at, layerPose);
      GunAnimationSet.additive(pose, neutral, layerPose, qa, qb);
    }
    if (action.active(at)) {
      action.sample(at, layerPose);
      GunAnimationSet.additive(pose, neutral, layerPose, qa, qb);
    }
    return pose;
  }
}
