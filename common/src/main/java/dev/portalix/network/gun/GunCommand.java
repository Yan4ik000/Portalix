package dev.portalix.network.gun;

public enum GunCommand {
  FIRE_BLUE,
  FIRE_ORANGE,
  TOGGLE_GRAB,
  RELEASE,
  RESET,
  SYNC_STATE;

  public boolean firing() {
    return this == FIRE_BLUE || this == FIRE_ORANGE;
  }
}
