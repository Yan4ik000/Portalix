package dev.portalix.client.portal.scene;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class PortalViewHorizon {
  private static double resumeLoadingAt = Double.NEGATIVE_INFINITY;
  private static double arrivalRadius;
  private static final Map<PortalLink, Departure> DEPARTURES = new HashMap<>();
  private static double frameTick;

  private record Departure(double radius, long tick) {
    double visibleRadius(double now, int preview, int limit, double fadeTicks) {
      double elapsed = now - tick;
      double start = Math.clamp(radius, preview, limit);
      double progress = Math.clamp((elapsed - PortalViewTransition.LOAD_DELAY_TICKS) / fadeTicks, 0, 1);
      double fading = start + (preview - start) * progress;
      return Math.min(fading, PortalViewTransition.retainedRadius(radius, elapsed, preview, limit));
    }
  }

  private PortalViewHorizon() {}

  static boolean transitioning() {
    return resumeLoadingAt != Double.NEGATIVE_INFINITY;
  }

  static int terrainRadius(PortalLink view, int limit) {
    if (view != null) return (int) Math.ceil(remoteRadius(view, frameTick, limit));
    return holdingPrimary() ? (int) Math.ceil(arrivalRadius(limit)) : limit;
  }

  static double fogRadius(PortalLink view, int limit) {
    return view == null ? primaryFogRadius(frameTick, limit) : remoteRadius(view, frameTick, limit);
  }

  public static float farPlane(PortalEndpoint exit, Vec3 eye, int radius, float ordinaryFar) {
    double reach = (radius + 2) * (16.0 * Math.sqrt(3));
    return Math.max(ordinaryFar, (float) (eye.distanceTo(PortalViewLimits.origin(exit)) + reach));
  }

  static boolean holdingPrimary() {
    return frameTick < resumeLoadingAt || ClientPortalMovement.awaitingCrossing();
  }

  public static void acceptLoadingDeadline(long tick) {
    resumeLoadingAt = tick > 0 ? tick : Double.NEGATIVE_INFINITY;
  }

  public static void bodyCrossed(PortalLink link, long tick) {
    int limit = Minecraft.getInstance().options.getEffectiveRenderDistance();
    var returnView = link.inverse();
    double departed = primaryFogRadius(tick, limit);
    arrivalRadius = remoteRadius(link, tick, limit);
    DEPARTURES.remove(link);
    if (departed > PortalixClient.portalPreviewRadius(returnView, limit))
      DEPARTURES.put(returnView, new Departure(departed, tick));
    else DEPARTURES.remove(returnView);
    resumeLoadingAt = tick + PortalViewTransition.LOAD_DELAY_TICKS;
  }

  public static void reset() {
    resumeLoadingAt = Double.NEGATIVE_INFINITY;
    arrivalRadius = 0;
    DEPARTURES.clear();
  }

  public static void beginFrame(DeltaTracker delta) {
    var client = Minecraft.getInstance();
    frameTick = client.level.getGameTime() + (double) delta.getGameTimeDeltaPartialTick(false);
    if (!DEPARTURES.isEmpty()) {
      int limit = client.options.getEffectiveRenderDistance();
      double fadeTicks = horizonFadeTicks();
      DEPARTURES.entrySet().removeIf(entry -> {
        int preview = PortalixClient.portalPreviewRadius(entry.getKey(), limit);
        return entry.getValue().visibleRadius(frameTick, preview, Math.max(limit, preview), fadeTicks) <= preview;
      });
    }
    if (resumeLoadingAt != Double.NEGATIVE_INFINITY && !holdingPrimary()
        && frameTick >= resumeLoadingAt + horizonFadeTicks()) resumeLoadingAt = Double.NEGATIVE_INFINITY;
  }

  private static double horizonFadeTicks() {
    double seconds = Minecraft.getInstance().options.chunkSectionFadeInTime().get();
    // Already compiled terrain still needs a horizon transition when section fading is disabled.
    return (seconds > 0 ? seconds * 20 : PortalViewTransition.DEFAULT_FADE_TICKS) * PortalViewTransition.SLOWDOWN;
  }

  private static double remoteRadius(PortalLink view, double tick, int limit) {
    int preview = PortalixClient.portalPreviewRadius(view, limit);
    var departure = DEPARTURES.get(view);
    return departure == null ? preview
        : departure.visibleRadius(tick, preview, Math.max(limit, preview), horizonFadeTicks());
  }

  private static double arrivalRadius(int limit) {
    return arrivalRadius > 0 ? Math.min(arrivalRadius, limit) : PortalViewLimits.preview(limit);
  }

  private static double primaryFogRadius(double tick, int limit) {
    double start = arrivalRadius(limit);
    if (ClientPortalMovement.awaitingCrossing() || tick < resumeLoadingAt) return start;
    if (resumeLoadingAt == Double.NEGATIVE_INFINITY) return limit;
    return start + (limit - start) * Math.min(1, (tick - resumeLoadingAt) / horizonFadeTicks());
  }
}
