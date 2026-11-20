package dev.portalix.network.gun;

public enum GunEventKind {
  FIRE_BLUE,
  FIRE_ORANGE,
  GRABBED,
  RELEASED,
  RESET,
  GRAB_FAILED,
  THROWN;

  public boolean endsHold() {
    return this == RELEASED || this == RESET || this == THROWN;
  }
}
