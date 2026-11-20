package dev.portalix.portal;

public final class PortalViewTransition {
  public static final int LOAD_DELAY_TICKS = 30;

  private PortalViewTransition() {}

  public static double retainedRadius(double departed, double elapsed, int limit) {
    double remainingArea = departed * departed - 4 * Math.max(0, elapsed - LOAD_DELAY_TICKS);
    return Math.clamp(Math.sqrt(Math.max(0, remainingArea)), PortalViewLimits.preview(limit), limit);
  }
}
