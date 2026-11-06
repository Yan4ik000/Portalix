package dev.portalix.portal.placement;

import dev.portalix.api.PortalEndpoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class PortalSurface {
  public static final double WIDTH = 1;
  public static final double HEIGHT = 2;

  public static final double MAX_BUMP_DISTANCE_SQUARED = (WIDTH * WIDTH + HEIGHT * HEIGHT) / 2;
  static final double EPSILON = 1e-8;

  private PortalSurface() {}

  public record Footprint(Direction face, List<BlockPos> blocks) {
    public Footprint {
      blocks = List.copyOf(blocks);
    }
  }

  public static Vec3 vector(Direction direction) {
    return Vec3.atLowerCornerOf(direction.getUnitVec3i());
  }

  public static Vec3 up(Direction face, float yaw) {
    if (face.getAxis() != Direction.Axis.Y) return vector(Direction.UP);
    double radians = Math.toRadians(yaw % 360.0);
    return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
  }

  public static Footprint placementFootprint(PortalEndpoint endpoint) {
    return new Footprint(
        Direction.getApproximateNearest(endpoint.normal()),
        blocks(PortalRectangle.of(endpoint), endpoint.normal()));
  }

  static List<BlockPos> blocks(PortalRectangle rectangle, Vec3 normal) {
    Vec3 backing = rectangle.center().subtract(normal.scale(.5));
    double x = rectangle.extent(new Vec3(1, 0, 0));
    double y = rectangle.extent(new Vec3(0, 1, 0));
    double z = rectangle.extent(new Vec3(0, 0, 1));
    List<BlockPos> result = new ArrayList<>();
    for (int ix = lower(backing.x, x); ix <= upper(backing.x, x); ix++) {
      for (int iy = lower(backing.y, y); iy <= upper(backing.y, y); iy++) {
        for (int iz = lower(backing.z, z); iz <= upper(backing.z, z); iz++) {
          BlockPos block = new BlockPos(ix, iy, iz);
          if (rectangle.overlaps(blockFace(block, normal))) result.add(block);
        }
      }
    }
    return result;
  }

  private static int lower(double center, double extent) {
    return (int) Math.floor(center - extent + EPSILON);
  }

  private static int upper(double center, double extent) {
    return (int) Math.floor(center + extent - EPSILON);
  }

  static PortalRectangle blockFace(BlockPos block, Vec3 normal) {
    Vec3 up = Math.abs(normal.y) > .5 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
    return new PortalRectangle(
        Vec3.atCenterOf(block).add(normal.scale(.5)), up.cross(normal), up, .5, .5);
  }

  public static boolean coplanar(PortalEndpoint a, PortalEndpoint b) {
    return a.dimension().equals(b.dimension())
        && a.normal().dot(b.normal()) > 1 - EPSILON
        && Math.abs(b.center().subtract(a.center()).dot(a.normal())) < EPSILON;
  }

  public static boolean overlapsPortal(PortalEndpoint a, PortalEndpoint b) {
    return coplanar(a, b) && PortalRectangle.of(a).overlaps(PortalRectangle.of(b));
  }
}
