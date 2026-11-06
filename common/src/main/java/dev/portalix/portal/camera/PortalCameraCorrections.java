package dev.portalix.portal.camera;

import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class PortalCameraCorrections {
  private final List<Discontinuity> changes = new ArrayList<>();
  private final List<PositionSample> positions = new ArrayList<>();
  private Vec3 previousCenter;
  private Vec3 previousRenderFeet;
  private int renderTick = Integer.MIN_VALUE;
  private Vec3 baseCorrection = Vec3.ZERO;
  private double baseTime;

  private void crossed(double time, PortalLink link, Vec3 eyeShift) {
    int index = changes.size();
    while (index > 0 && changes.get(index - 1).time() > time) index--;
    changes.add(index, new Discontinuity(time, link, eyeShift));
  }

  public void shifted(double time, Vec3 eyeShift) {
    crossed(time, null, eyeShift);
  }

  public void crossed(
      int tick,
      double time,
      PortalLink link,
      Vec3 eyeShift,
      Vec3 before,
      Vec3 after,
      Vec3 earlierFeet,
      Vec3 earlierRenderFeet,
      Vec3 center) {
    // Separate Minecraft moves within one tick still cross portals in event order.
    if (!changes.isEmpty()) time = Math.max(time, changes.getLast().time());
    previousRenderFeet =
        link.transform().point(renderTick == tick ? previousRenderFeet : earlierRenderFeet);
    renderTick = tick;
    if (previousCenter == null) {
      previousCenter =
          after
              .add(link.transform().vector(earlierFeet.subtract(before)))
              .add(center.subtract(after));
    } else {
      previousCenter = after.add(link.transform().vector(previousCenter.subtract(before)));
    }
    for (int i = 0; i < positions.size(); i++) {
      var sample = positions.get(i);
      positions.set(
          i,
          new PositionSample(
              sample.time(),
              after.add(link.transform().vector(sample.feet().subtract(before))),
              link.transform().point(sample.renderFeet()),
              after.add(link.transform().vector(sample.center().subtract(before)))));
    }
    positions.add(new PositionSample(time, after, after, center));
    crossed(time, link, eyeShift);
  }

  public double lastCrossingTime() {
    return positions.getLast().time();
  }

  public Vec3 position(Vec3 previous, Vec3 current, int tick, float partial) {
    return interpolate(previous, current, tick, partial, Anchor.FEET);
  }

  public Vec3 renderPosition(Vec3 previous, Vec3 current, int tick, float partial) {
    return interpolate(
        renderTick == tick ? previousRenderFeet : previous, current, tick, partial, Anchor.RENDER_FEET);
  }

  public boolean hasRenderPosition(int tick) {
    return renderTick == tick || hasPosition(tick);
  }

  public Vec3 center(Vec3 previous, Vec3 current, int tick, float partial) {
    boolean crossing = hasPosition(tick);
    return interpolate(crossing ? previousCenter : previous, current, tick, partial, Anchor.CENTER);
  }

  public boolean hasPosition(int tick) {
    return !positions.isEmpty()
        && positions.getLast().time() >= tick - 1
        && positions.getFirst().time() <= tick;
  }

  private Vec3 interpolate(Vec3 previous, Vec3 current, int tick, float partial, Anchor anchor) {
    double startTime = tick - 1;
    double time = startTime + partial;
    Vec3 start = previous;
    for (int index = 0; index < positions.size(); index++) {
      var sample = positions.get(index);
      if (sample.time() < startTime
          || anchor == Anchor.RENDER_FEET && sample.time() == tick - 1) continue;
      if (sample.time() > tick) break;
      if (anchor == Anchor.RENDER_FEET
          && index + 1 < positions.size()
          && sample.time() == positions.get(index + 1).time()) continue;
      Vec3 point =
          switch (anchor) {
            case FEET -> sample.feet();
            case RENDER_FEET -> sample.renderFeet();
            case CENTER -> sample.center();
          };
      if (sample.time() > time) {
        return start.lerp(point, (time - startTime) / (sample.time() - startTime));
      }
      startTime = sample.time();
      start = point;
    }
    return startTime == tick ? start : start.lerp(current, (time - startTime) / (tick - startTime));
  }

  public void discardThrough(double oldestSampleTime) {
    for (var change : changes) {
      if (change.time() > oldestSampleTime) break;
      baseCorrection = decay(baseCorrection, change.time() - baseTime);
      if (change.link() != null) baseCorrection = change.link().transform().vector(baseCorrection);
      baseCorrection = baseCorrection.add(change.eyeShift());
      baseTime = change.time();
    }
    baseCorrection = decay(baseCorrection, oldestSampleTime - baseTime);
    baseTime = oldestSampleTime;
    changes.removeIf(change -> change.time() <= oldestSampleTime);
    positions.removeIf(sample -> sample.time() <= oldestSampleTime);
    if (positions.isEmpty()) previousCenter = null;
    if (renderTick <= oldestSampleTime) {
      previousRenderFeet = null;
      renderTick = Integer.MIN_VALUE;
    }
  }

  public void clear() {
    changes.clear();
    positions.clear();
    previousCenter = null;
    previousRenderFeet = null;
    renderTick = Integer.MIN_VALUE;
    baseCorrection = Vec3.ZERO;
    baseTime = 0;
  }

  public Vec3 eyeCorrection(double sampleTime) {
    Vec3 correction = baseCorrection;
    double time = baseTime;
    for (var change : changes) {
      double next = Math.min(sampleTime, change.time());
      correction = decay(correction, next - time);
      if (change.link() != null) correction = change.link().transform().vector(correction);
      correction = correction.add(change.eyeShift());
      time = next;
    }
    return decay(correction, sampleTime - time);
  }

  private static Vec3 decay(Vec3 correction, double ticks) {
    return correction.scale(Math.exp(-3.465735912322998 * Math.max(0, ticks) / 20));
  }

  public List<PortalLink> rewind(double sampleTime) {
    if (changes.isEmpty() || changes.getLast().time() <= sampleTime) return List.of();
    var path = new ArrayList<PortalLink>();
    for (int i = changes.size() - 1; i >= 0; i--) {
      var change = changes.get(i);
      if (change.time() <= sampleTime) break;
      if (change.link() != null) path.add(change.link().inverse());
    }
    return List.copyOf(path);
  }

  public static Vec3 transformEarlier(
      Vec3 position,
      double sampleTime,
      double crossingTime,
      PortalLink link,
      Vec3 beforeAnchor,
      Vec3 afterAnchor) {
    return sampleTime < crossingTime
        ? afterAnchor.add(link.transform().vector(position.subtract(beforeAnchor)))
        : position;
  }

  private record Discontinuity(double time, PortalLink link, Vec3 eyeShift) {}

  private enum Anchor {
    FEET,
    RENDER_FEET,
    CENTER
  }

  private record PositionSample(double time, Vec3 feet, Vec3 renderFeet, Vec3 center) {}
}
