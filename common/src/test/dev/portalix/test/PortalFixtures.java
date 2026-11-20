package dev.portalix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PortalFixtures {
  public static final ResourceKey<Level> DIMENSION =
      ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));

  private PortalFixtures() {}

  public static List<PortalEndpoint> orientations() {
    var result = new ArrayList<PortalEndpoint>();
    for (Direction face : Direction.values()) {
      Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
      Vec3 up = Math.abs(normal.y) == 1 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
      result.add(endpoint(new Vec3(7.25, 64.5, -19.75), normal, up));
    }
    result.add(endpoint(new Vec3(7.25, 64.5, -19.75), new Vec3(0, 0, -1), new Vec3(1, 0, 0)));
    result.add(endpoint(new Vec3(-30_000_000, -64, 30_000_000), new Vec3(1, 2, 3), new Vec3(2, 3, -1)));
    return List.copyOf(result);
  }

  public static PortalEndpoint endpoint(Vec3 center, Vec3 normal, Vec3 up) {
    return new PortalEndpoint(DIMENSION, center, normal, up, 1, 2);
  }

  public static PortalEndpoint at(PortalEndpoint portal, Vec3 center) {
    return new PortalEndpoint(portal.dimension(), center, portal.normal(), portal.up(), 1, 2);
  }

  public static PortalLink link(PortalEndpoint entrance, PortalEndpoint exit) {
    return new PortalLink(
        new UUID(0x1234, 0x5678),
        2,
        new PortalInstance(entrance, 10, 1, PortalixVariant.BLUE),
        new PortalInstance(exit, 20, 2, PortalixVariant.ORANGE));
  }

  public static PortalLink link(UUID gun, long generation, long entrance, long exit) {
    return new PortalLink(gun, generation,
        new PortalInstance(endpoint(new Vec3(2, 4, 6), new Vec3(0, 0, 1), new Vec3(0, 1, 0)),
            10, entrance, PortalixVariant.BLUE),
        new PortalInstance(endpoint(new Vec3(20, 100, 30), new Vec3(0, 1, 0), new Vec3(0, 0, 1)),
            20, exit, PortalixVariant.ORANGE));
  }

  public static void assertVector(Vec3 expected, Vec3 actual, double tolerance) {
    assertEquals(expected.x, actual.x, tolerance, "x");
    assertEquals(expected.y, actual.y, tolerance, "y");
    assertEquals(expected.z, actual.z, tolerance, "z");
  }
}
