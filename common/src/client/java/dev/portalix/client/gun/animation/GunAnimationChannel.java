package dev.portalix.client.gun.animation;

import org.joml.Quaternionf;

final class GunAnimationChannel {
  private final GunAnimationSet library;
  private final float[] neutral;
  private final float blendSeconds;
  private final float[] captured;
  private final float[] outgoingReference;
  private final float[] outgoingPose;
  private final float[] scratch;
  private final Quaternionf qa = new Quaternionf();
  private final Quaternionf qb = new Quaternionf();
  private GunAnimationClip clip;
  private GunAnimationClip outgoing;
  private double began;
  private double outgoingBegan;
  private boolean transitioning;

  GunAnimationChannel(
      GunAnimationSet library, float[] neutral, float blendSeconds, GunAnimationClip initial) {
    this.library = library;
    this.neutral = neutral;
    this.blendSeconds = blendSeconds;
    this.clip = initial;
    captured = new float[neutral.length];
    outgoingReference = new float[neutral.length];
    outgoingPose = new float[neutral.length];
    scratch = new float[neutral.length];
  }

  GunAnimationClip clip() {
    return clip;
  }

  boolean finished(double now) {
    return clip != null && !clip.looping() && now - began >= library.duration(clip.asset());
  }

  void finish(double now, GunAnimationClip resting) {
    if (finished(now)) play(resting, now);
  }

  void play(GunAnimationClip selected, double now) {
    sample(now, scratch);
    System.arraycopy(scratch, 0, captured, 0, captured.length);
    outgoing = clip;
    outgoingBegan = began;
    raw(outgoing, outgoingBegan, now, outgoingReference);
    clip = selected;
    began = now;
    transitioning = true;
  }

  void snap(GunAnimationClip selected, double now) {
    clip = selected;
    began = now;
    outgoing = null;
    transitioning = false;
  }

  boolean active(double now) {
    return clip != null || (transitioning && now - began < blendSeconds);
  }

  void sample(double now, float[] out) {
    raw(clip, began, now, out);
    double elapsed = Math.max(0.0, now - began);
    if (transitioning && elapsed < blendSeconds) {
      raw(outgoing, outgoingBegan, now, outgoingPose);

      GunAnimationSet.additive(outgoingPose, outgoingReference, captured, qa, qb);
      float t = (float) (elapsed / blendSeconds);
      t = t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
      GunAnimationSet.blend(outgoingPose, out, t, out, qa, qb);
    }
  }

  private void raw(GunAnimationClip selected, double start, double now, float[] out) {
    if (selected == null) System.arraycopy(neutral, 0, out, 0, neutral.length);
    else
      library.sample(
          selected.asset(), (float) Math.max(0.0, now - start), selected.looping(), out, qa, qb);
  }
}
