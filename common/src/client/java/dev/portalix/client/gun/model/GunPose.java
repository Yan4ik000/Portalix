package dev.portalix.client.gun.model;

public enum GunPose {
  HAND("hand"),
  WORLD_HAND("worldHand"),
  ICON("icon"),
  GROUND("ground"),
  ITEM("item");
  private final String key;

  GunPose(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
