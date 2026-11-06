package dev.portalix.client.portal;

import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.query.PortalRaycast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class PortalMousePicking {
  private static PortalRaycast.Result selected;
  private static List<PortalLink> selectedPath = List.of();

  private PortalMousePicking() {}

  public static PortalRaycast.Result pick(
      Entity source, PortalCameraController.View pose, double blockReach, double entityReach,
      AttackRange range) {
    if (pose == null) return null;
    double ordinaryReach = Math.max(blockReach, entityReach);
    double attackReach = 0;
    if (range != null) {
      var movement = source.getKnownMovement();
      for (var link : pose.spacePath()) movement = link.transform().vector(movement);
      attackReach = range.effectiveMaxRange(source) + Math.max(0, movement.dot(pose.forward()));
    }
    double unionReach = Math.max(ordinaryReach, attackReach);
    if (!affects(source, pose, unionReach)) return null;
    var blocks = blockTrace(source, pose, unionReach);
    if (blocks.links().isEmpty() && pose.spacePath().isEmpty() && !PortalCameraController.active())
      return null;
    var ray = range == null ? null : attack(source, range,
        attackReach == unionReach ? blocks : blockTrace(source, pose, attackReach));
    if (ray != null && ray.hit().getType() == HitResult.Type.BLOCK && ray.distance() >= blockReach)
      ray = null;
    boolean attack = ray != null && ray.hit().getType() == HitResult.Type.ENTITY;
    if (ray == null || ray.hit().getType() == HitResult.Type.MISS)
      ray = ordinary(source, pose,
          ordinaryReach == unionReach ? blocks : blockTrace(source, pose, ordinaryReach));
    double reach = ray.hit().getType() == HitResult.Type.ENTITY ? entityReach : blockReach;
    if (!attack && ray.distance() >= reach) {
      var hit = ray.hit();
      var miss = BlockHitResult.miss(hit.getLocation(), Direction.getApproximateNearest(pose.forward()),
          BlockPos.containing(hit.getLocation()));
      return new PortalRaycast.Result(miss, ray.segments(), ray.links(), ray.distance());
    }
    return ray;
  }

  private static PortalRaycast.Result blockTrace(
      Entity source, PortalCameraController.View pose, double reach) {
    return PortalRaycast.cast(pose.position(), pose.forward().scale(reach),
        PortalTopology.links(source.level()), link -> PortalEntityMovement.canTraverse(source, link),
        (from, to) -> source.level().clipIncludingBorder(
            new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, source)));
  }

  private static PortalRaycast.Result ordinary(
      Entity source, PortalCameraController.View pose, PortalRaycast.Result blocks) {
    var body = source.getBoundingBox();
    var eye = source.getEyePosition();
    for (var link : pose.spacePath()) {
      body = link.transform().bounds(body);
      eye = link.transform().point(eye);
    }
    // Keep the current body as well as the interpolated camera body in the swept query.
    body = body.minmax(body.move(pose.position().subtract(eye)));
    double travelled = 0;
    for (int index = 0; index < blocks.segments().size(); index++) {
      var segment = blocks.segments().get(index);
      var displacement = segment.to().subtract(segment.from());
      double lengthSquared = displacement.lengthSqr();
      var query = body.expandTowards(displacement).minmax(new AABB(segment.from(), segment.to())).inflate(1);
      var hit = ProjectileUtil.getEntityHitResult(source, segment.from(), segment.to(), query,
          EntitySelector.CAN_BE_PICKED, lengthSquared);
      if (hit != null && hit.getLocation().distanceToSqr(segment.from()) < lengthSquared)
        return withEntityHit(blocks, index, hit, travelled + segment.from().distanceTo(hit.getLocation()));
      travelled += Math.sqrt(lengthSquared);
      if (index < blocks.links().size())
        body = blocks.links().get(index).transform().bounds(body.move(displacement));
    }
    return blocks;
  }

  private static PortalRaycast.Result attack(
      Entity source, AttackRange range, PortalRaycast.Result blocks) {
    double minimum = range.effectiveMinRange(source);
    double travelled = 0;
    for (int index = 0; index < blocks.segments().size(); index++) {
      var segment = blocks.segments().get(index);
      double length = segment.from().distanceTo(segment.to());
      double skip = Math.max(0, minimum - travelled);
      if (skip <= length) {
        var from = length == 0 ? segment.from()
            : segment.from().lerp(segment.to(), skip / length);
        var candidates = ProjectileUtil.getManyEntityHitResult(
            source.level(), source, from, segment.to(),
            AABB.ofSize(from, range.hitboxMargin(), range.hitboxMargin(), range.hitboxMargin())
                .expandTowards(segment.to().subtract(from)).inflate(1),
            EntitySelector.CAN_BE_PICKED, range.hitboxMargin(), ClipContext.Block.OUTLINE, true);
        EntityHitResult closest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var candidate : candidates) {
          double next = segment.from().distanceTo(candidate.getLocation());
          if (next < distance) {
            closest = candidate;
            distance = next;
          }
        }
        if (closest != null) {
          return withEntityHit(blocks, index, closest, travelled + distance);
        }
      }
      travelled += length;
    }
    return blocks;
  }

  private static PortalRaycast.Result withEntityHit(
      PortalRaycast.Result blocks, int index, EntityHitResult hit, double distance) {
    var path = new ArrayList<>(blocks.segments().subList(0, index));
    path.add(new PortalRaycast.Segment(blocks.segments().get(index).from(), hit.getLocation()));
    return new PortalRaycast.Result(hit, List.copyOf(path),
        List.copyOf(blocks.links().subList(0, index)), distance);
  }

  public static boolean affects(Entity source, PortalCameraController.View pose, double reach) {
    if (pose == null) return false;
    var displacement = pose.forward().scale(reach);
    if (!PortalCameraController.active()
        && pose.spacePath().isEmpty()
        && PortalTopology.links(source.level()).stream()
            .noneMatch(
                link ->
                    PortalAperture.crossing(
                            link.entrance().endpoint(), pose.position(), displacement)
                        .isPresent())) return false;
    return true;
  }

  public static void select(PortalRaycast.Result ray) {
    select(ray, List.of());
  }

  public static void select(PortalRaycast.Result ray, List<PortalLink> originPath) {
    selected = ray;
    var path = new ArrayList<>(originPath);
    if (ray != null) for (var link : ray.links()) PortalRoutes.append(path, link);
    selectedPath = List.copyOf(path);
  }

  public static boolean outlineIn(List<PortalLink> path) {
    var client = Minecraft.getInstance();
    if (selected == null || selected.hit() != client.hitResult) return path.isEmpty();
    var camera = PortalCameraController.currentView();
    var frame = new ArrayList<PortalLink>();
    if (camera != null) frame.addAll(camera.spacePath());
    for (var link : path) PortalRoutes.append(frame, link);
    return selectedPath.equals(frame);
  }

}
