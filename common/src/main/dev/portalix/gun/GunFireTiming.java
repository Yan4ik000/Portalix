package dev.portalix.gun;

public final class GunFireTiming {
  public static final int ACTION_COOLDOWN_TICKS = 4;
  public static final int CLIENT_REPEAT_TICKS = 10;
  public static final long RESET_CROSSING_COOLDOWN_NANOS = 500_000_000L;
  public static final double TICK_SECONDS = 0.05;
  public static final double PROJECTILE_BLOCKS_PER_TICK = 24.0;

  private GunFireTiming() {}

  public static int shotTravelTicks(double distance) {
    return Math.max(1, (int) Math.ceil(distance / PROJECTILE_BLOCKS_PER_TICK));
  }
}
