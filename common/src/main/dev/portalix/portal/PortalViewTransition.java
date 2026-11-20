package dev.portalix.portal;

public final class PortalViewTransition {
  public static final int LOAD_DELAY_TICKS = 30;
  public static final int DEFAULT_FADE_TICKS = 15;
  public static final double SLOWDOWN = 1.5;
  public static final double LOAD_RAMP_TICKS = DEFAULT_FADE_TICKS * SLOWDOWN;

  private PortalViewTransition() {}

  public static int loadingRadius(long elapsed, int preview, int limit) {
    double progress = Math.clamp(elapsed / LOAD_RAMP_TICKS, 0, 1);
    return preview + (int) ((limit - preview) * progress);
  }

  public static double retainedRadius(double departed, double elapsed, int limit) {
    double remainingArea = departed * departed - 4 * Math.max(0, elapsed - LOAD_DELAY_TICKS) / SLOWDOWN;
    return Math.clamp(Math.sqrt(Math.max(0, remainingArea)), PortalViewLimits.preview(limit), limit);
  }
}
