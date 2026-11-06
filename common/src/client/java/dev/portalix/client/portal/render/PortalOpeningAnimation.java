package dev.portalix.client.portal.render;

public final class PortalOpeningAnimation {
  public static final float OPEN_SECONDS = 0.5F;
  public static final float LINK_SECONDS = 1.0F;
  private final double born;
  private double linkedAt;
  private boolean linked;

  public PortalOpeningAnimation(double born, boolean linked) {
    this.born = born;
    this.linkedAt = born;
    this.linked = linked;
  }

  public void link(boolean value, double time) {
    if (linked == value) return;
    linked = value;
    linkedAt = time;
  }

  public float openingProgress(double time) {
    return (float) Math.clamp((time - born) / OPEN_SECONDS, 0, 1);
  }

  public static float apertureScale(float progress) {
    float smooth = progress * progress * (3 - 2 * progress);
    return smooth * smooth;
  }

  public float unlinkedOpacity(double time) {
    return linked ? (float) Math.clamp(1 - (time - linkedAt) / LINK_SECONDS, 0, 1) : 1;
  }
}
