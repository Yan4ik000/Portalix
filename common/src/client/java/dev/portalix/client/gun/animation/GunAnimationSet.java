package dev.portalix.client.gun.animation;

import java.util.Map;
import org.joml.Quaternionf;

public final class GunAnimationSet {
  private final Map<String, float[][]> clips;
  private final float fps;

  public GunAnimationSet(Map<String, float[][]> clips, float fps) {
    this.fps = fps;
    this.clips = Map.copyOf(clips);
  }

  private float[][] frames(String name) {
    float[][] value = this.clips.get(name);
    if (value == null) {
      throw new IllegalArgumentException("Unknown animation clip " + name);
    } else {
      return value;
    }
  }

  public float duration(String name) {
    return (this.frames(name).length - 1) / fps;
  }

  public float[] pose(String name, float seconds, boolean loop) {
    float[] out = new float[this.frames(name)[0].length];
    sample(name, seconds, loop, out, new Quaternionf(), new Quaternionf());
    return out;
  }

  public void sample(
      String name, float seconds, boolean loop, float[] out, Quaternionf qa, Quaternionf qb) {
    float[][] frames = this.frames(name);
    float frame =
        loop && frames.length > 1
            ? Math.max(0.0F, seconds) * fps % (frames.length - 1)
            : Math.min(Math.max(0.0F, seconds) * fps, frames.length - 1);
    int a = (int) frame;
    int b = Math.min(a + 1, frames.length - 1);
    blend(frames[a], frames[b], frame - a, out, qa, qb);
  }

  public static void blend(
      float[] a, float[] b, float t, float[] result, Quaternionf qa, Quaternionf qb) {
    for (int i = 0; i < a.length; i += 7) {
      for (int j = 0; j < 3; j++) {
        result[i + j] = a[i + j] + (b[i + j] - a[i + j]) * t;
      }

      qa.set(a[i + 3], a[i + 4], a[i + 5], a[i + 6]);
      qb.set(b[i + 3], b[i + 4], b[i + 5], b[i + 6]);

      if (!qa.equals(qb)) qa.slerp(qb, t);
      qa.normalize();
      result[i + 3] = qa.x;
      result[i + 4] = qa.y;
      result[i + 5] = qa.z;
      result[i + 6] = qa.w;
    }
  }

  public static void additive(
      float[] base, float[] reference, float[] action, Quaternionf qa, Quaternionf qb) {
    for (int i = 0; i < base.length; i += 7) {
      boolean neutral = true;
      for (int j = 0; j < 7; j++) neutral &= action[i + j] == reference[i + j];
      if (neutral) continue;
      for (int j = 0; j < 3; j++) base[i + j] += action[i + j] - reference[i + j];
      qa.set(reference[i + 3], reference[i + 4], reference[i + 5], reference[i + 6]);
      qb.set(action[i + 3], action[i + 4], action[i + 5], action[i + 6]);
      qa.conjugate().mul(qb);
      qb.set(base[i + 3], base[i + 4], base[i + 5], base[i + 6]).mul(qa).normalize();
      base[i + 3] = qb.x;
      base[i + 4] = qb.y;
      base[i + 5] = qb.z;
      base[i + 6] = qb.w;
    }
  }
}
