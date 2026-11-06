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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PortalRaycast {
  private PortalRaycast() {}

  public static Result cast(
      Entity source,
      Vec3 from,
      Vec3 displacement,
      Predicate<Entity> entities,
      ClipContext.Block blocks,
      ClipContext.Fluid fluids,
      double entityMargin) {
    return cast(
        from,
        displacement,
        PortalTopology.links(source.level()),
        link -> PortalEntityMovement.canTraverse(source, link),
        (start, end) -> {
          HitResult hit =
              source
                  .level()
                  .clipIncludingBorder(new ClipContext(start, end, blocks, fluids, source));
          Vec3 limit = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
          AABB query = new AABB(start, limit).inflate(Math.max(1, entityMargin));
          var entityHit =
              ProjectileUtil.getEntityHitResult(
                  source.level(), source, start, limit, query, entities, (float) entityMargin);
          return entityHit == null ? hit : entityHit;
        });
  }

  public static Result cast(
      Vec3 from,
      Vec3 displacement,
      List<PortalLink> links,
      Predicate<PortalLink> ready,
      Query query) {
    var path = new ArrayList<Segment>();
    var crossed = new ArrayList<PortalLink>();
    var zeroProgress = new HashSet<PortalLink>();
    Vec3 start = from, remaining = displacement;
    double distance = 0;
    while (true) {
      Vec3 end = start.add(remaining);
      HitResult hit = query.clip(start, end);
      double hitDistance =
          hit.getType() == HitResult.Type.MISS
              ? remaining.length()
              : start.distanceTo(hit.getLocation());
      PortalLink nearest = null;
      PortalAperture.Crossing crossing = null;
      double portalDistance = Double.POSITIVE_INFINITY;
      for (PortalLink link : links) {
        if (!ready.test(link)) continue;

        if (PortalAperture.signedDistance(link.entrance().endpoint(), start)
            < -PortalAperture.PLANE_EPSILON) continue;
        var candidate = PortalAperture.crossing(link.entrance().endpoint(), start, remaining);
        if (candidate.isEmpty()) continue;
        double candidateDistance = remaining.length() * candidate.get().fraction();
        if (candidateDistance < portalDistance
            && (candidateDistance <= hitDistance
                || sameBackingSurface(link.entrance().endpoint(), hit, candidateDistance - hitDistance))) {
          nearest = link;
          crossing = candidate.get();
          portalDistance = candidateDistance;
        }
      }
      if (nearest == null) {
        path.add(
            new Segment(start, hit.getType() == HitResult.Type.MISS ? end : hit.getLocation()));
        return new Result(hit, List.copyOf(path), List.copyOf(crossed), distance + hitDistance);
      }
      if (portalDistance > 1e-9) zeroProgress.clear();
      else if (!zeroProgress.add(nearest)) {
        HitResult loop =
            BlockHitResult.miss(
                start, Direction.getApproximateNearest(remaining), BlockPos.containing(start));
        return new Result(loop, List.copyOf(path), List.copyOf(crossed), distance);
      }
      path.add(new Segment(start, crossing.point()));
      crossed.add(nearest);
      distance += portalDistance;
      start = nearest.transform().point(crossing.point());
      var exit = nearest.exit().endpoint();
      start = start.subtract(exit.normal().scale(PortalAperture.signedDistance(exit, start)));
      remaining = nearest.transform().vector(crossing.remaining());
    }
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

  public record Segment(Vec3 from, Vec3 to) {}

  public record Result(
      HitResult hit, List<Segment> segments, List<PortalLink> links, double distance) {
    public Vec3 vector(Vec3 vector) {
      for (PortalLink link : links) vector = link.transform().vector(vector);
      return vector;
    }
  }
}
