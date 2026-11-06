package dev.portalix.portal.traversal;

import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.portal.geometry.PortalTransform;
import dev.portalix.portal.query.PortalRaycast;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ProjectilePortalMovement {
  private ProjectilePortalMovement() {}

  static List<PortalLink> tether(FishingHook hook) {
    return state(hook).tether;
  }

  static void initializeTether(FishingHook hook, List<PortalLink> route,
      List<PortalLink> ownerCrossings) {
    var visibleRoute = new ArrayList<>(ownerCrossings);
    visibleRoute.addAll(route);
    state(hook).tether = PortalRoutes.simplify(visibleRoute);
  }

  public static void crossed(Entity entity, PortalLink link) {
    if (entity instanceof FishingHook hook) {
      var route = new ArrayList<>(state(hook).tether);
      route.add(link);
      state(hook).tether = PortalRoutes.simplify(route);
    }
    if (entity instanceof Player player) ownerCrossed(player, link);
  }

  public static void ownerCrossed(Player player, PortalLink link) {
    if (player.fishing != null) {
      var route = new ArrayList<>(state(player.fishing).tether);
      route.addFirst(link.inverse());
      state(player.fishing).tether = PortalRoutes.simplify(route);
    }
  }

  public static double tetherDistance(FishingHook hook, Entity owner, double vanilla) {
    if (!(owner instanceof Player player) || state(hook).tether.isEmpty()) return vanilla;
    var eye = PortalEyePose.sample(player);
    var route = state(hook).tether;
    if (!eye.eyePath().isEmpty()) {
      var path = new ArrayList<>(route);
      for (var link : eye.eyePath()) path.addFirst(link.inverse());
      route = PortalRoutes.simplify(path);
    }
    var available = PortalTopology.links(hook.level());
    for (var reference : route) {
      var current = PortalRoutes.resolve(reference, available);
      if (current == null || !PortalEntityMovement.canTraverse(player, current)) return Double.POSITIVE_INFINITY;
    }
    double distance = PortalRoutes.distance(eye.position(), hook.position(), route);
    return distance * distance;
  }

  public static Vec3 tetherOwner(FishingHook hook, Entity owner) {
    Vec3 position = owner.position();
    for (var link : state(hook).tether) position = link.transform().point(position);
    return position;
  }

  public static double tetherCoordinate(
      FishingHook hook, Entity owner, Direction.Axis axis, double vanilla) {
    if (state(hook).tether.isEmpty()) return vanilla;
    Vec3 position = tetherOwner(hook, owner);
    return axis.choose(position.x, position.y, position.z);
  }

  public static HitResult movementRay(
      Entity projectile, Predicate<Entity> targets, Supplier<HitResult> original) {
    var state = state(projectile);
    state.input = projectile.getDeltaMovement();
    state.output = null;
    var trace =
        PortalRaycast.cast(
            projectile,
            projectile.position(),
            state.input,
            targets,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            ProjectileUtil.computeMargin(projectile));
    state.distance = trace.links().isEmpty() ? null : trace.distance();
    return trace.links().isEmpty() ? original.get() : trace.hit();
  }

  public static void move(Entity projectile, Runnable original) {
    long sequence = EntityPortalState.of(projectile).sequence;
    original.run();
    if (sequence != EntityPortalState.of(projectile).sequence)
      state(projectile).output = projectile.getDeltaMovement();
  }

  public static double hitDistance(Entity projectile, double vanilla) {
    Double distance = state(projectile).distance;
    return distance == null ? vanilla : distance * distance;
  }

  public static HitResult trace(
      Entity projectile,
      Predicate<Entity> targets,
      ClipContext.Block blocks,
      Supplier<HitResult> original) {
    var state = state(projectile);
    state.end = null;
    state.input = projectile.getDeltaMovement();
    state.output = null;
    var trace =
        PortalRaycast.cast(
            projectile,
            projectile.position(),
            state.input,
            targets,
            blocks,
            ClipContext.Fluid.NONE,
            ProjectileUtil.computeMargin(projectile));
    if (trace.links().isEmpty()) return original.get();
    double distance = 0;
    for (int i = 0; i < trace.links().size(); i++) {
      var segment = trace.segments().get(i);
      distance += segment.from().distanceTo(segment.to());
      cross(
          projectile,
          trace.links().get(i),
          segment.to(),
          Math.clamp(distance / state.input.length(), 0, 1));
    }
    state.end = trace.hit().getLocation();
    state.output = projectile.getDeltaMovement();
    projectile.setPos(state.end);
    return trace.hit();
  }

  public static void moveArrow(
      AbstractArrow arrow, PortalRaycast.Result trace, double movementLength,
      Consumer<BlockHitResult> step) {
    double distance = 0;
    for (int i = 0; i < trace.links().size(); i++) {
      var segment = trace.segments().get(i);
      distance += segment.from().distanceTo(segment.to());
      Vec3 plane = segment.to();
      step.accept(
          BlockHitResult.miss(
              plane,
              Direction.getApproximateNearest(arrow.getDeltaMovement()),
              BlockPos.containing(plane)));
      if (!arrow.isAlive() || arrow.position().distanceToSqr(plane) > 1e-12) return;
      cross(arrow, trace.links().get(i), plane, Math.clamp(distance / movementLength, 0, 1));
    }
    step.accept((BlockHitResult) trace.hit());
  }

  public static void cross(Entity projectile, PortalLink link, Vec3 before, double fraction) {
    float beforeYaw = projectile.getYRot(), beforePitch = projectile.getXRot();
    Vec3 after = link.transform().point(before);
    Vec3 velocity = link.transform().vector(projectile.getDeltaMovement());
    Vec3 previous = new Vec3(projectile.xo, projectile.yo, projectile.zo);
    projectile.setPos(after);
    projectile.setDeltaMovement(velocity);
    var angles = PortalEntityAngles.transform(projectile, link.transform(), beforeYaw, beforePitch);
    var previousAngles =
        PortalEntityAngles.transform(
            projectile, link.transform(), projectile.yRotO, projectile.xRotO);
    projectile.setYRot(angles.yaw());
    projectile.setXRot(angles.pitch());
    projectile.yRotO = previousAngles.yaw();
    projectile.xRotO = previousAngles.pitch();
    Vec3 earlierRenderFeet = EntityMotion.rebaseHistory(
        projectile, link, before, after, previous, 0, fraction);
    projectile.resetFallDistance();
    var movement = EntityPortalState.of(projectile);
    movement.fallImmune = true;
    PortalEntityMovement.publishCrossing(
        projectile,
        new PortalEntityMovement.Crossing(
            ++movement.sequence,
            link,
            before,
            after,
            velocity,
            projectile.getYRot(),
            projectile.getXRot(),
            fraction,
            previous,
            projectile.getEyeHeight(),
            beforeYaw,
            beforePitch,
            PortalPassagePose.scale(projectile)),
        earlierRenderFeet);
  }

  public static Vec3 position(Entity projectile, Vec3 vanilla) {
    var state = state(projectile);
    Vec3 end = state.end;
    state.end = null;
    return end == null ? vanilla : end;
  }

  public static Vec3 capturedVelocity(Entity projectile, Vec3 vanilla) {
    var state = state(projectile);
    if (state.output == null) return vanilla;
    double factor = state.input.lengthSqr() == 0 ? 1 : vanilla.length() / state.input.length();
    return state.output.scale(factor);
  }

  private static State state(Entity projectile) {
    return ((Access) projectile).portalix$projectileState();
  }

  public static final class State {
    Vec3 end;
    Vec3 input;
    Vec3 output;
    Double distance;
    List<PortalLink> tether = List.of();
  }

  public interface Access {
    State portalix$projectileState();
  }

  public interface Guided {
    void portalix$transformGuidance(PortalTransform transform);
  }
}
