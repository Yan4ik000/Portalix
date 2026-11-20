package dev.portalix.client.gun.animation;

public enum GunAnimationClip {
  IDLE("idle", true),
  CARRY_IDLE("idle_carrying", true),
  SPRINT_IDLE("lowidle", true),
  DRAW("draw", false),
  HOLSTER("holster", false),
  FIRE("fire1", false),
  GRAB("idle_to_carrying", false),
  RELEASE("carrying_to_idle", false),
  RESET("fizzle", false),
  DRY_FIRE("dryfire", false),
  LOWER("idletolow", false),
  RAISE("lowtoidle", false);

  private final String asset;
  private final boolean loop;

  GunAnimationClip(String asset, boolean loop) {
    this.asset = asset;
    this.loop = loop;
  }

  public String asset() {
    return this.asset;
  }

  public boolean looping() {
    return this.loop;
  }
}
