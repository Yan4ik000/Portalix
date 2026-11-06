package dev.portalix.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.portalix.api.PortalEndpoint;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ClientPortalFixtures {
  private static final ResourceKey<Level> DIMENSION = ResourceKey.create(
      Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

  private ClientPortalFixtures() {}

  public static PortalEndpoint endpoint(Vec3 center, Direction normal) {
    return new PortalEndpoint(DIMENSION, center, Vec3.atLowerCornerOf(normal.getUnitVec3i()),
        normal.getAxis().isVertical() ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0), 1, 2);
  }

  public static void near(Vec3 expected, Vec3 actual, double epsilon, String message) {
    assertTrue(expected.distanceTo(actual) <= epsilon,
        message + ": expected " + expected + ", got " + actual);
  }
}
