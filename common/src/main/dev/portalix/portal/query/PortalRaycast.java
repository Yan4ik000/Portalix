package dev.portalix.portal.query;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PortalRaycast {
  static final int INTERACTION_CROSSINGS = 2;

  private PortalRaycast() {}

  public static Result cast(
      Entity source,
      Vec3 from,
      Vec3 displacement,
      Predicate<Entity> entities,
      ClipContext.Block blocks,
      ClipContext.Fluid fluids,
      double entityMargin) {
    return cast(source, from, displacement, (start, end) ->
        clip(source, start, end, entities, blocks, fluids, entityMargin));
  }

  public static Result cast(Entity source, Vec3 from, Vec3 displacement, Query query) {
    return cast(
        from, displacement, candidates(source.level()),
        link -> PortalEntityMovement.canTraverse(source, link), query, null, false, Integer.MAX_VALUE);
  }

  public static Result castInteraction(Entity source, Vec3 from, Vec3 displacement) {
    return cast(from, displacement, candidates(source.level()),
        link -> PortalEntityMovement.canTraverse(source, link),
        (start, end) -> source.level().clipIncludingBorder(
            new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, source)),
        null, false, INTERACTION_CROSSINGS);
  }

  public static Result castProjectile(
      Entity source, Vec3 movement, Predicate<Entity> entities,
      ClipContext.Block blocks) {
    if (PortalTopology.links(source.level()).isEmpty()) return null;
    float margin = ProjectileUtil.computeMargin(source);
    return castCrossing(source, source.position(), movement, null, (start, end) ->
        clip(source, start, end, entities, blocks, ClipContext.Fluid.NONE, margin));
  }

  public static Result castArrow(Entity source, Vec3 from, Vec3 movement, BlockHitResult nativeHit) {
    if (PortalTopology.links(source.level()).isEmpty()) return null;
    return castCrossing(source, from, movement, nativeHit, (start, end) ->
        source.level().clipIncludingBorder(
            new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)));
  }

  private static Result castCrossing(
      Entity source, Vec3 from, Vec3 movement, HitResult nativeHit, Query query) {
    return cast(
        from, movement, candidates(source.level()),
        link -> PortalEntityMovement.canTraverse(source, link), query, nativeHit, true, Integer.MAX_VALUE);
  }

  private static HitResult clip(
      Entity source, Vec3 start, Vec3 end, Predicate<Entity> entities, ClipContext.Block blocks,
      ClipContext.Fluid fluids, double margin) {
    var level = source.level();
    HitResult hit = level.clipIncludingBorder(new ClipContext(start, end, blocks, fluids, source));
    Vec3 limit = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    AABB bounds = new AABB(start, limit).inflate(Math.max(1, margin));
    var entityHit =
        ProjectileUtil.getEntityHitResult(level, source, start, limit, bounds, entities, (float) margin);
    return entityHit == null ? hit : entityHit;
  }

  private static Candidates candidates(Level level) {
    if (PortalTopology.links(level).isEmpty()) return (from, to) -> List.of();
    return (from, to) -> PortalTopology.intersecting(level, new AABB(from, to));
  }

  public static Result cast(
      Vec3 from,
      Vec3 displacement,
      List<PortalLink> links,
      Predicate<PortalLink> ready,
      Query query) {
    return cast(from, displacement, (start, end) -> links, ready, query, null, false, Integer.MAX_VALUE);
  }

  private static Result cast(
      Vec3 from, Vec3 displacement, Candidates candidates, Predicate<PortalLink> ready,
      Query query, HitResult firstHit, boolean requireCrossing, int maxCrossings) {
    ArrayList<Segment> path = null;
    ArrayList<PortalLink> crossed = null;
    HashSet<PortalLink> zeroProgress = null;
    Vec3 start = from, remaining = displacement;
    double distance = 0;
    while (true) {
      Vec3 end = start.add(remaining);
      HitResult hit = firstHit;
      firstHit = null;
      double length = remaining.length();
      double hitDistance = hit == null ? 0 : hitDistance(start, hit, length);
      PortalLink nearest = null;
      PortalAperture.Crossing crossing = null;
      double portalDistance = Double.POSITIVE_INFINITY;
      var entrances = crossed != null && crossed.size() >= maxCrossings
          ? List.<PortalLink>of() : candidates.find(start, end);
      for (PortalLink link : entrances) {
        if (PortalAperture.signedDistance(link.entrance().endpoint(), start)
            < -PortalAperture.PLANE_EPSILON) continue;
        var candidate = PortalAperture.crossing(link, start, remaining);
        if (candidate.isEmpty()) continue;
        double candidateDistance = length * candidate.get().fraction();
        if (candidateDistance >= portalDistance) continue;
        if (hit == null) {
          hit = query.clip(start, end);
          hitDistance = hitDistance(start, hit, length);
        }
        if ((candidateDistance <= hitDistance
                || sameBackingSurface(
                    link.entrance().endpoint(), hit, candidateDistance - hitDistance))
            && ready.test(link)) {
          nearest = link;
          crossing = candidate.get();
          portalDistance = candidateDistance;
        }
      }
      if (nearest == null) {
        if (path == null && requireCrossing) return null;
        if (hit == null) {
          hit = query.clip(start, end);
          hitDistance = hitDistance(start, hit, length);
        }
        var segment = new Segment(start, hit.getType() == HitResult.Type.MISS ? end : hit.getLocation());
        if (path == null)
          return new Result(hit, List.of(segment), List.of(), hitDistance);
        path.add(segment);
        return new Result(hit, List.copyOf(path), List.copyOf(crossed), distance + hitDistance);
      }
      if (path == null) {
        path = new ArrayList<>();
        crossed = new ArrayList<>();
      }
      if (portalDistance > 1e-9 && zeroProgress != null) zeroProgress.clear();
      if (portalDistance <= 1e-9 && zeroProgress == null) zeroProgress = new HashSet<>();
      if (portalDistance <= 1e-9 && !zeroProgress.add(nearest)) {
        HitResult loop =
            BlockHitResult.miss(
                start, Direction.getApproximateNearest(remaining), BlockPos.containing(start));
        return new Result(loop, List.copyOf(path), List.copyOf(crossed), distance);
      }
      path.add(new Segment(start, crossing.point()));
      crossed.add(nearest);
      distance += portalDistance;
      var transform = nearest.transform();
      start = transform.point(crossing.point());
      var exit = nearest.exit().endpoint();
      start = start.subtract(exit.normal().scale(PortalAperture.signedDistance(exit, start)));
      remaining = transform.vector(crossing.remaining());
    }
  }

  private static double hitDistance(Vec3 start, HitResult hit, double length) {
    return hit.getType() == HitResult.Type.MISS ? length : start.distanceTo(hit.getLocation());
  }

  private static boolean sameBackingSurface(PortalEndpoint portal, HitResult hit, double distanceError) {
    // Native voxel clipping can report a cube edge just in front of its portal plane.
    return distanceError <= PortalAperture.PLANE_EPSILON
        && hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
        && Math.abs(PortalAperture.signedDistance(portal, block.getLocation())) <= PortalAperture.PLANE_EPSILON
        && PortalAperture.containsPassage(portal, block.getLocation())
        && PortalSurface.placementFootprint(portal).blocks().contains(block.getBlockPos());
  }

  @FunctionalInterface
  public interface Query {
    HitResult clip(Vec3 from, Vec3 to);
  }

  @FunctionalInterface
  private interface Candidates {
    List<PortalLink> find(Vec3 from, Vec3 to);
  }

  public record Segment(Vec3 from, Vec3 to) {}

  public record Result(
      HitResult hit, List<Segment> segments, List<PortalLink> links, double distance) {}
}
