package dev.portalix.api;

public enum PortalColor {
  BLUE(1),
  ORANGE(2);

  private final int mask;

  PortalColor(int mask) {
    this.mask = mask;
  }

  public int mask() {
    return this.mask;
  }
}
