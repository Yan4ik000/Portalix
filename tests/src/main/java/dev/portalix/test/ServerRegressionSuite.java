package dev.portalix.test;

import java.util.Locale;
import net.minecraft.world.phys.Vec3;

public final class ServerRegressionSuite {
  private ServerRegressionSuite() {}

  public enum Group {
    PLACEMENT("placement materials and orientations, firing, support removal and reset"),
    TRAVERSAL("native item and animal movement, passengers, leashes, save/load and normal ticks"),
    PLAYER_TRAVERSAL("player movement through wall, floor and ceiling portals"),
    PROJECTILES("native snowball, bow and trident use, portal flight and impacts"),
    COMBAT("melee, sprint, sweep, spear and shield combat through portals"),
    CARRY("grab, drop, throw and move objects through a portal"),
    CHUNKS("remote terrain and sound delivery, portal release, unload and reload"),
    CONTENT("crafting, gun variants, item identities and mineshaft loot");

    private final String description;

    Group(String description) { this.description = description; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String description() { return description; }
  }

  interface Scenario extends AutoCloseable {
    boolean tick();
    @Override default void close() {}
  }

  static Scenario start(RegressionWorld world, Group group) {
    if (!world.level.getServer().isSameThread())
      throw new IllegalStateException("Server scenarios must run on the server thread");
    check(world.ready(), "The scenario requires loaded, ticking chunks");
    switch (group) {
      case PLACEMENT -> {
        PlacementRegression.run(world);
        world.reset();
        return new GunShootingRegression(world);
      }
      case TRAVERSAL -> {
        NativeEntityRegression.run(world);
        world.reset();
        return new EntityTickRegression(world);
      }
      case PLAYER_TRAVERSAL -> PlayerTraversalRegression.run(world);
      case PROJECTILES -> NativeProjectileRegression.run(world);
      case COMBAT -> { return new CombatRegression(world); }
      case CARRY -> CarryRegression.run(world);
      case CHUNKS -> { return new ChunkLifecycleRegression(world); }
      case CONTENT -> ContentRegression.run(world);
    }
    return null;
  }

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static void near(Vec3 actual, Vec3 expected, double tolerance, String message) {
    check(actual.distanceToSqr(expected) <= tolerance * tolerance,
        message + ": expected " + expected + ", got " + actual);
  }
}
