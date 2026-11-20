package dev.portalix.portal.query;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.query.PortalInteractionGeometry.Occlusion;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public final class PortalInteractionPath {
  private PortalInteractionPath() {}

  public static double distance(Player player, AABB target, double maximum) {
    if (PortalTopology.links(player.level()).isEmpty()) return Double.POSITIVE_INFINITY;
    return closest(player, PortalEyePose.sample(player), target, 0, maximum, false)
        .map(Route::distance).orElse(Double.POSITIVE_INFINITY);
  }

  public static Optional<Route> attack(
      Player player, ItemStack weapon, AABB target, double buffer) {
    if (PortalTopology.links(player.level()).isEmpty()) return Optional.empty();
    var range = player.getAttackRangeWith(weapon);
    float margin = range.hitboxMargin();
    double minimum = range.effectiveMinRange(player) - margin - buffer;
    double maximum = range.effectiveMaxRange(player) + margin + buffer;
    var pose = PortalEyePose.sample(player);
    var ray = PortalRaycast.castInteraction(player, pose.position(), pose.forward().scale(maximum));
    Vec3 virtualEye = pose.position();
    for (int i = 0; i < ray.segments().size(); i++) {
      var segment = ray.segments().get(i);
      if (hitsTarget(player, segment, target, margin)) {
        double distance = Math.sqrt(target.distanceToSqr(virtualEye));
        if (distance >= minimum && distance <= maximum)
          return Optional.of(route(pose, ray.links().subList(0, i), distance));
      }
      if (i < ray.links().size()) virtualEye = ray.links().get(i).transform().point(virtualEye);
    }
    return closest(player, pose, target, minimum, maximum, true);
  }

  private static boolean hitsTarget(Player player, PortalRaycast.Segment segment, AABB target, float margin) {
    if (target.contains(segment.from()) || target.clip(segment.from(), segment.to()).isPresent()) return true;
    if (margin <= 0) return false;
    var contact = target.inflate(margin).clip(segment.from(), segment.to());
    if (contact.isEmpty()) return false;
    Vec3 from = contact.orElseThrow();
    var hit = player.level().clipIncludingBorder(new ClipContext(
        from, target.getCenter(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    return target.clip(from, hit.getLocation()).isPresent();
  }

  private static Optional<Route> closest(
      Player player, PortalEyePose.Pose pose, AABB target, double minimum, double maximum,
      boolean attack) {
    var search = new Search(player, pose, target, minimum, maximum, attack);
    search.visit(pose.position(), List.of());
    return search.selected;
  }

  private static final class Search {
    private final Player player;
    private final PortalEyePose.Pose pose;
    private final AABB target;
    private final double minimum;
    private final boolean attack;
    private final HashMap<PortalLink, Boolean> readiness = new HashMap<>();
    private Optional<Route> selected = Optional.empty();
    private double limit;

    Search(Player player, PortalEyePose.Pose pose, AABB target, double minimum, double maximum,
        boolean attack) {
      this.player = player;
      this.pose = pose;
      this.target = target;
      this.minimum = minimum;
      this.limit = maximum;
      this.attack = attack;
    }

    void visit(Vec3 virtualEye, List<PortalLink> path) {
      double distanceToBounds = Math.sqrt(target.distanceToSqr(virtualEye));
      if (distanceToBounds >= minimum && distanceToBounds <= limit) {
        var point = PortalInteractionGeometry.closestFrom(virtualEye, target, path, limit,
            attack ? candidate -> occlusion(virtualEye, candidate, path) : null);
        if (point.isPresent()) {
          double sightlineDistance = point.orElseThrow().distanceTo(virtualEye);
          double distance = attack ? distanceToBounds : sightlineDistance;
          if (sightlineDistance <= limit && (selected.isEmpty() || sightlineDistance < limit)) {
            selected = Optional.of(route(pose, path, distance));
            limit = sightlineDistance;
          }
        }
      }
      if (path.size() == PortalRaycast.INTERACTION_CROSSINGS) return;
      var inverse = path.isEmpty() ? null : path.getLast().inverse();
      for (var link : entrances(player.level(), virtualEye, limit)) {
        if (link.equals(inverse) || !reachable(virtualEye, link, limit)
            || !readiness.computeIfAbsent(link, candidate -> PortalEntityMovement.canTraverse(player, candidate)))
          continue;
        var next = new ArrayList<>(path);
        next.add(link);
        visit(link.transform().point(virtualEye), List.copyOf(next));
      }
    }

    private @Nullable Occlusion occlusion(Vec3 virtualEye, Vec3 point, List<PortalLink> path) {
      Vec3 direction = point.subtract(virtualEye);
      double distance = direction.length();
      for (var link : path.reversed()) direction = link.transform().inverse().vector(direction);
      var ray = PortalRaycast.castInteraction(player, pose.position(), direction);
      if (ray.links().equals(path) && ray.distance() + PortalAperture.PLANE_EPSILON >= distance) return null;
      int leg = ray.links().size();
      if (leg > path.size() || !path.subList(0, leg).equals(ray.links())
          || !(ray.hit() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
          || hit.isWorldBorderHit()) return Occlusion.UNRESOLVED;

      var level = player.level();
      var pos = hit.getBlockPos();
      var shape = level.getBlockState(pos).getShape(level, pos, CollisionContext.of(player));
      // A component of the voxel outline is sufficient to explain this hit. Its
      // shadow is clipped to the physical leg where Minecraft reported the obstruction.
      for (var part : shape.toAabbs()) {
        var box = part.move(pos);
        if (!box.inflate(PortalAperture.PLANE_EPSILON).contains(hit.getLocation())) continue;
        var blocker = ConvexPolyhedron.box(box);
        if (leg > 0) {
          var exit = path.get(leg - 1).exit().endpoint();
          blocker = blocker.clip(ConvexPolyhedron.Plane.through(exit.normal().scale(-1), exit.center()));
        }
        if (leg < path.size()) {
          var entrance = path.get(leg).entrance().endpoint();
          var front = entrance.center().add(entrance.normal().scale(PortalAperture.PLANE_EPSILON));
          blocker = blocker.clip(ConvexPolyhedron.Plane.through(entrance.normal().scale(-1), front));
        }
        if (blocker.vertices().isEmpty()) continue;
        for (int hop = leg; hop < path.size(); hop++) blocker = blocker.transform(path.get(hop).transform());
        var shadow = blocker.shadowFrom(virtualEye);
        if (shadow.stream().allMatch(plane -> plane.normal().dot(point) - plane.offset()
            <= PortalAperture.PLANE_EPSILON)) return new Occlusion(shadow);
      }
      return Occlusion.UNRESOLVED;
    }
  }

  private static Route route(PortalEyePose.Pose pose, List<PortalLink> selected, double distance) {
    var route = new ArrayList<>(pose.eyePath());
    for (var link : selected) PortalRoutes.append(route, link);
    return new Route(List.copyOf(route), distance, pose.position(), List.copyOf(selected));
  }

  private static List<PortalLink> entrances(Level level, Vec3 eye, double maximum) {
    if (!(maximum >= 0)) return List.of();
    double reach = maximum + PortalAperture.MAX_APERTURE_EXTENT + PortalAperture.PLANE_EPSILON;
    return PortalTopology.entrances(level, eye.x - reach, eye.z - reach, eye.x + reach, eye.z + reach);
  }

  private static boolean reachable(Vec3 eye, PortalLink link, double maximum) {
    var entrance = link.entrance().endpoint();
    double reach = maximum + PortalAperture.MAX_APERTURE_EXTENT + PortalAperture.PLANE_EPSILON;
    return entrance.center().distanceToSqr(eye) <= reach * reach
        && PortalAperture.signedDistance(entrance, eye) >= -PortalAperture.PLANE_EPSILON;
  }

  public record Route(List<PortalLink> links, double distance, Vec3 eye, List<PortalLink> sightline) {
    public Vec3 point(Vec3 point) {
      for (var link : links) point = link.transform().point(point);
      return point;
    }

    public boolean visible(AABB target) {
      return PortalInteractionGeometry.closest(eye, target, sightline).isPresent();
    }
  }
}
