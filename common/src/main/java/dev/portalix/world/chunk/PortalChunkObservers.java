package dev.portalix.world.chunk;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PlayerPortalVisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongToIntFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

public final class PortalChunkObservers {
  public static final PortalChunkObservers EMPTY =
      new PortalChunkObservers(List.of(), List.of(), List.of(), ChunkTrackingView.EMPTY, 0, 0, 0);
  private static final int TIER_SHIFT = 48;
  private static final long DISTANCE_MASK = (1L << TIER_SHIFT) - 1;
  private final List<ChunkPos> positions;
  private final List<ChunkPos> critical;
  private final List<Demand> demands;
  private final ChunkTrackingView primaryView;
  private final int radius;
  private final int minY;
  private final int maxY;

  private PortalChunkObservers(
      List<ChunkPos> positions, List<ChunkPos> critical, List<Demand> demands,
      ChunkTrackingView primaryView, int radius, int minY, int maxY) {
    this.positions = positions;
    this.critical = critical;
    this.demands = demands;
    this.primaryView = primaryView;
    this.radius = radius;
    this.minY = minY;
    this.maxY = maxY;
  }

  public static PortalChunkObservers of(ServerPlayer player) {
    var visibility = PlayerPortalVisibility.of(player);
    var eyes = visibility.eyes();
    var positions = new ArrayList<ChunkPos>(eyes.size() + 1);
    positions.add(player.chunkPosition());
    var critical = new ArrayList<ChunkPos>();
    critical.add(player.chunkPosition());
    for (var eye : eyes) positions.add(new ChunkPos(Mth.floor(eye.x) >> 4, Mth.floor(eye.z) >> 4));
    var demands = new ArrayList<Demand>(visibility.observers().size());
    for (var observer : visibility.observers()) {
      var exit = observer.exit();
      var center = chunk(exit.center());
      if (!positions.contains(center)) positions.add(center);
      if (!critical.contains(center)) critical.add(center);
      demands.add(new Demand(chunk(observer.eye()), center, observer.arrival(),
          cone(observer.eye(), exit)));
    }
    return new PortalChunkObservers(positions, critical, demands, player.getChunkTrackingView(),
        visibility.viewRadius(), player.level().getMinY(), player.level().getMaxY());
  }

  public static LongToIntFunction loadingPriorities(List<PortalChunkObservers> observers) {
    var regions = new Long2ObjectOpenHashMap<List<PortalChunkObservers>>();
    for (var observer : observers) {
      var occupied = new LongOpenHashSet();
      if (observer.primaryView instanceof ChunkTrackingView.Positioned primary)
        addRegions(occupied, primary.center(), primary.viewDistance() + 1);
      else observer.primaryView.forEach(position -> occupied.add(region(position.x(), position.z())));
      for (var critical : observer.critical) addRegions(occupied, critical, 1);
      for (var demand : observer.demands) {
        addRegions(occupied, demand.eye, observer.radius + 1);
        if (demand.arrival) addRegions(occupied, demand.exit, observer.radius + 1);
      }
      for (long region : occupied)
        regions.computeIfAbsent(region, ignored -> new ArrayList<>()).add(observer);
    }
    return position -> {
      var nearby = regions.get(region(ChunkPos.getX(position), ChunkPos.getZ(position)));
      int tier = 4;
      if (nearby != null) for (var observer : nearby) {
        tier = Math.min(tier, observer.loadingTier(position));
        if (tier == 0) break;
      }
      return tier;
    };
  }

  private static void addRegions(LongOpenHashSet regions, ChunkPos center, int radius) {
    for (int x = (center.x() - radius) >> 5; x <= (center.x() + radius) >> 5; x++)
      for (int z = (center.z() - radius) >> 5; z <= (center.z() + radius) >> 5; z++)
        regions.add(ChunkPos.pack(x, z));
  }

  private static long region(int x, int z) {
    return ChunkPos.pack(x >> 5, z >> 5);
  }

  public boolean isEmpty() {
    return positions.isEmpty();
  }

  public long distanceSquared(long chunk) {
    return distanceSquared(chunk, positions);
  }

  public long priority(long chunk) {
    long distance = Math.min(distanceSquared(chunk), DISTANCE_MASK);
    return ((long) tier(chunk) << TIER_SHIFT) | distance;
  }

  public int tier(long chunk) {
    if (distanceSquared(chunk, critical) <= 2) return 0;
    int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
    if (primaryView.contains(x, z)) return 1;
    int tier = 3;
    for (var demand : demands) {
      if (demand.cone != null && within(demand.eye, x, z)
          && intersects(demand.cone, x, z)) return 1;
      if (demand.arrival && within(demand.exit, x, z)) tier = 2;
    }
    return tier;
  }

  public int loadingTier(long chunk) {
    int tier = tier(chunk);
    if (tier == 0) return 0;
    if (tier != 1) return tier + 1;
    return primaryView.contains(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) ? 1 : 2;
  }

  public boolean samePriority(PortalChunkObservers other) {
    if (radius != other.radius || minY != other.minY || maxY != other.maxY
        || !positions.equals(other.positions) || !critical.equals(other.critical)
        || !primaryView.equals(other.primaryView) || demands.size() != other.demands.size())
      return false;
    for (int i = 0; i < demands.size(); i++) {
      var left = demands.get(i);
      var right = other.demands.get(i);
      if (!left.eye.equals(right.eye) || !left.exit.equals(right.exit)
          || left.arrival != right.arrival || !Arrays.equals(left.cone, right.cone)) return false;
    }
    return true;
  }

  private boolean within(ChunkPos center, int x, int z) {
    return ChunkTrackingView.isWithinDistance(center.x(), center.z(), radius, x, z, true);
  }

  private boolean intersects(Plane[] cone, int x, int z) {
    double minX = x * 16.0 - 16, maxX = x * 16.0 + 32;
    double minZ = z * 16.0 - 16, maxZ = z * 16.0 + 32;
    for (var plane : cone) {
      var normal = plane.normal;
      double furthest = plane.offset
          + normal.x * (normal.x >= 0 ? maxX : minX)
          + normal.y * (normal.y >= 0 ? maxY + 17.0 : minY - 16.0)
          + normal.z * (normal.z >= 0 ? maxZ : minZ);
      if (furthest < -PortalAperture.PLANE_EPSILON) return false;
    }
    return true;
  }

  private static Plane[] cone(Vec3 eye, PortalEndpoint exit) {
    if (PortalAperture.signedDistance(exit, eye) >= -PortalAperture.PLANE_EPSILON) return null;
    var right = exit.right().scale(exit.width() / 2);
    var up = exit.up().scale(exit.height() / 2);
    var center = exit.center();
    var corners = new Vec3[] {
        center.subtract(right).subtract(up), center.add(right).subtract(up),
        center.add(right).add(up), center.subtract(right).add(up)
    };
    var towardsCenter = center.subtract(eye);
    var planes = new Plane[5];
    for (int i = 0; i < 4; i++) {
      var normal = corners[i].subtract(eye).cross(corners[(i + 1) % 4].subtract(eye)).normalize();
      if (normal.dot(towardsCenter) < 0) normal = normal.scale(-1);
      planes[i] = new Plane(normal, -normal.dot(eye));
    }
    planes[4] = new Plane(exit.normal(), -exit.normal().dot(center));
    return planes;
  }

  private static ChunkPos chunk(Vec3 position) {
    return new ChunkPos(Mth.floor(position.x) >> 4, Mth.floor(position.z) >> 4);
  }

  private static long distanceSquared(long chunk, List<ChunkPos> positions) {
    long distance = Long.MAX_VALUE;
    int x = ChunkPos.getX(chunk), z = ChunkPos.getZ(chunk);
    for (var observer : positions) {
      long dx = (long) x - observer.x(), dz = (long) z - observer.z();
      distance = Math.min(distance, dx * dx + dz * dz);
    }
    return distance;
  }

  private record Demand(ChunkPos eye, ChunkPos exit, boolean arrival, Plane[] cone) {}

  private record Plane(Vec3 normal, double offset) {}
}
