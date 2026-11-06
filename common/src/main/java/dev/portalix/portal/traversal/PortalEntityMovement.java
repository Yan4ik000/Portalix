package dev.portalix.portal.traversal;

import dev.portalix.carry.CarryBindings;
import dev.portalix.network.portal.ProjectileStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.physics.PortalBallistics;
import dev.portalix.portal.physics.PortalExitVelocity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class PortalEntityMovement {
  private PortalEntityMovement() {}

  public static boolean canTraverse(Entity entity, PortalLink link) {
    return entity
            .getRootVehicle()
            .getSelfAndPassengers()
            .noneMatch(member -> member instanceof Animal && member.countPlayerPassengers() > 0)
        && PortalTraversalReadiness.ready(entity, link);
  }

  public static MoveResult moveAuthoritatively(
      Entity subject, MoverType mover, Vec3 displacement, Vec3 incomingVelocity) {
    var state = EntityPortalState.of(subject);
    state.authoritative = true;
    state.replayVelocity = incomingVelocity;
    state.lastMove = null;
    var players = ServerPlayerMotion.captureStatistics(subject);
    try {
      subject.move(mover, displacement);
      if (state.lastMove == null) {
        PortalPassengerGroup.positionPassengers(subject);
        ServerPlayerMotion.updateStatistics(players);
      }
      return state.lastMove;
    } finally {
      state.authoritative = false;
      state.replayVelocity = null;
    }
  }

  public static boolean move(Entity entity, MoverType type, Vec3 displacement) {
    var state = EntityPortalState.of(entity);
    if (state.moving
        || entity.noPhysics
        || entity.isPassenger()
        || (serverControlled(entity) && !state.authoritative)
        || displacement.lengthSqr() == 0 && !PortalPassagePose.compact(entity)
        || (!entity.isLocalInstanceAuthoritative() && entity.level().isClientSide())) return false;
    PortalLink link = PortalEntityCollisions.movementPortal(entity, displacement);
    if (link == null && !PortalPassagePose.compact(entity)) return false;
    Vec3 initialFeet = entity.position();
    boolean initialGrounded = entity.onGround();
    Pose initialPose = entity.getPose();
    float initialScale = PortalPassagePose.scale(entity);
    boolean sneaking = entity.isShiftKeyDown();
    Vec3 initialVelocity =
        state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement();
    var crossings = new ArrayList<Crossing>();
    var zeroProgress = new HashSet<PortalLink>();
    state.moving = true;
    try {
      if (link == null) {
        moveSegment(entity, type, displacement);
        return true;
      }
      Vec3 remaining = displacement;
      double remainingFraction = 1;
      double gravity = EntityMotion.stepGravity(entity, type, initialVelocity, displacement);
      while (link != null) {
        state.collisionLink = link;
        if (entity instanceof Player player) {
          Vec3 adjusted = EntityMotion.backOffFromEdge(player, remaining, type);
          if (!adjusted.equals(remaining)) gravity = 0;
          remaining = adjusted;
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        var hulls = PortalPassengerGroup.hulls(entity);
        var query = PortalPassengerGroup.bounds(hulls).expandTowards(remaining);
        var selected = PortalEntityCollisions.firstCrossing(entity, hulls, remaining, query);
        var crossing = selected.map(value -> value.passage().crossing());
        if (selected.isPresent()) {
          link = selected.orElseThrow().link();
          state.collisionLink = link;
          if (!selected.orElseThrow().passage().straight()) gravity = 0;
        }
        Vec3 tickVelocity =
            state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement();
        Vec3 segment;
        if (gravity != 0 && crossing.isPresent()) {
          double elapsed = 1 - remainingFraction;
          var boundary =
              PortalBallistics.crossing(
                  link.entrance().endpoint(), center, tickVelocity, gravity, elapsed);
          if (boundary.isPresent()) {
            double duration = boundary.getAsDouble();
            Vec3 ballisticSegment =
                PortalBallistics.displacement(tickVelocity, gravity, elapsed, duration);
            Vec3 ballisticRemaining =
                PortalBallistics.displacement(
                    tickVelocity, gravity, elapsed + duration, remainingFraction - duration);
            crossing =
                Optional.of(
                    new PortalAperture.Crossing(
                        duration / remainingFraction,
                        crossing.orElseThrow().point(),
                        ballisticRemaining));
            segment = ballisticSegment;
          } else {
            crossing = Optional.empty();
            segment = null;
          }
        } else
          segment =
              crossing.isEmpty() ? null : remaining.subtract(crossing.orElseThrow().remaining());
        if (crossing.isEmpty()) {
          moveSegment(entity, type, remaining);
          return true;
        }
        var at = crossing.orElseThrow();
        if (at.fraction() > 1e-9) zeroProgress.clear();
        else if (!zeroProgress.add(link)) return true;
        moveSegment(entity, type, segment);
        Vec3 reached = entity.getBoundingBox().getCenter();
        double dist = PortalAperture.signedDistance(link.entrance().endpoint(), reached);
        Vec3 onPlane = reached.subtract(link.entrance().endpoint().normal().scale(dist));
        if (dist > PortalAperture.PLANE_EPSILON
            || dist < -PortalAperture.CAVITY_DEPTH
            || !PortalAperture.containsPassage(link.entrance().endpoint(), onPlane)) {
          return true;
        }
        remainingFraction *= 1 - at.fraction();
        Vec3 crossingVelocity =
            state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement();
        var event =
            crossAtPlane(
                entity,
                link,
                gravity == 0
                    ? remaining
                    : PortalBallistics.velocity(tickVelocity, gravity, 1 - remainingFraction),
                1 - remainingFraction,
                new Vec3(entity.xo, entity.yo, entity.zo),
                gravity);
        if (event == null) {
          var blockedHulls = PortalPassengerGroup.hulls(entity);
          var blockedQuery =
              PortalPassengerGroup.bounds(blockedHulls)
                  .expandTowards(at.remaining())
                  .expandTowards(0, entity.maxUpStep(), 0);
          state.collisionScene =
              PortalCollisionWorld.query(entity, link, blockedQuery)
                  .blockCrossing(link.entrance().endpoint(), entity.getBoundingBox(), blockedQuery);
          moveSegment(entity, type, at.remaining());
          return true;
        }
        crossings.add(event);
        remaining =
            gravity == 0
                ? link.transform()
                    .vector(at.remaining())
                    .add(
                        event
                            .velocity()
                            .subtract(link.transform().vector(crossingVelocity))
                            .scale(remainingFraction))
                : PortalBallistics.displacement(
                    event.velocity(), gravity, 1 - remainingFraction, remainingFraction);

        if (remaining.lengthSqr() == 0) return true;
        link = PortalEntityCollisions.movementPortal(entity, remaining);
        if (link == null) {
          state.collisionLink = null;
          moveSegment(entity, type, remaining);
          return true;
        }
      }
      return true;
    } finally {
      state.collisionLink = null;
      state.collisionScene = null;
      state.moving = false;
      state.lastMove =
          new MoveResult(
              type,
              initialFeet,
              initialVelocity,
              initialGrounded,
              initialPose,
              initialScale,
              sneaking,
              displacement,
              List.copyOf(crossings),
              entity.position(),
              state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement(),
              state.sequence);
      PortalCrossingSync.completed(entity, state.lastMove);
    }
  }

  private static void moveSegment(Entity entity, MoverType type, Vec3 movement) {
    var players = ServerPlayerMotion.captureStatistics(entity);
    Vec3 before = entity.position();
    var state = EntityPortalState.of(entity);
    state.segmentMoving = true;
    try {
      entity.move(type, movement);
    } finally {
      state.segmentMoving = false;
    }
    if (state.replayVelocity != null) {
      Vec3 actual = entity.position().subtract(before);
      state.replayVelocity =
          new Vec3(
              Math.abs(actual.x - movement.x) > 1e-7 ? 0 : state.replayVelocity.x,
              Math.abs(actual.y - movement.y) > 1e-7 ? 0 : state.replayVelocity.y,
              Math.abs(actual.z - movement.z) > 1e-7 ? 0 : state.replayVelocity.z);
    }
    Vec3 velocity = state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement();
    if (entity instanceof LivingEntity living
        && living.isFallFlying()
        && (entity.onGround()
            || entity.horizontalCollision && Mth.equal(velocity.x, 0) && Mth.equal(velocity.z, 0)))
      living.stopFallFlying();
    PortalPassengerGroup.positionPassengers(entity);
    ServerPlayerMotion.updateStatistics(players);
  }

  public static void moveWithinSpace(Entity entity, MoverType type, Vec3 movement) {
    var state = EntityPortalState.of(entity);
    var hull = entity.getBoundingBox();
    state.moving = true;
    state.collisionLink = PortalEntityCollisions.intersectingPortal(entity, hull, movement);
    state.collisionScene =
        state.collisionLink == null
            ? null
            : PortalCollisionWorld.query(entity, state.collisionLink, hull.expandTowards(movement));
    try {
      moveSegment(entity, type, movement);
    } finally {
      state.collisionLink = null;
      state.collisionScene = null;
      state.moving = false;
    }
  }

  public static Crossing crossAtPlane(Entity entity, PortalLink link, Vec3 inwardDirection) {
    return crossAtPlane(
        entity, link, inwardDirection, 0, new Vec3(entity.xo, entity.yo, entity.zo), 0);
  }

  private static Crossing crossAtPlane(
      Entity entity,
      PortalLink link,
      Vec3 inwardDirection,
      double moveFraction,
      Vec3 previousFeet,
      double gravity) {
    var state = EntityPortalState.of(entity);
    Vec3 reached = entity.getBoundingBox().getCenter();
    double reachedDist = PortalAperture.signedDistance(link.entrance().endpoint(), reached);
    Vec3 onPlane = reached.subtract(link.entrance().endpoint().normal().scale(reachedDist));
    if (!canTraverse(entity, link)
        || !PortalTopology.links(entity.level()).contains(link)
        || (serverControlled(entity) && !state.authoritative)
        || reachedDist > PortalAperture.PLANE_EPSILON
        || reachedDist < -PortalAperture.CAVITY_DEPTH
        || !PortalAperture.containsPassage(link.entrance().endpoint(), onPlane)
        || inwardDirection.dot(link.entrance().endpoint().normal()) >= 0) return null;
    Vec3 before = entity.position();
    float beforeEyeHeight = entity.getEyeHeight();
    float beforeYaw = entity.getYRot(), beforePitch = entity.getXRot();
    Vec3 afterCenter = link.transform().point(onPlane);
    Vec3 incomingVelocity =
        state.replayVelocity != null ? state.replayVelocity : entity.getDeltaMovement();
    Vec3 velocity =
        exitVelocity(entity, link, incomingVelocity, afterCenter, moveFraction, gravity);
    Vec3 serverImpulse =
        state.replayVelocity != null && serverControlled(entity)
            ? exitVelocity(
                entity, link, entity.getDeltaMovement(), afterCenter, moveFraction, gravity)
            : velocity;
    float previousScale = PortalPassagePose.scale(entity);
    PortalPassagePose.enter(entity, link);
    var fitted =
        PortalAperture.fitHull(link.exit().endpoint(), entity.getBoundingBox(), afterCenter);
    if (fitted.isEmpty()) {
      PortalPassagePose.set(entity, previousScale);
      return null;
    }
    afterCenter = fitted.orElseThrow();
    Vec3 after = afterCenter.subtract(entity.getBoundingBox().getCenter().subtract(before));
    var convoy = PortalPassengerGroup.arrival(entity, link, after);
    var arrivalScene =
        PortalCollisionWorld.query(
            entity,
            link.inverse(),
            PortalPassengerGroup.bounds(
                convoy.stream().map(PortalPassengerGroup.Arrival::hull).toList()));
    for (var member : convoy) {
      if (!canTraverse(member.entity(), link) || arrivalScene.intersects(member.hull())) {
        PortalPassagePose.set(entity, previousScale);
        return null;
      }
    }
    var head =
        PortalLookAngles.transform(link.transform(), entity.getViewYRot(1), entity.getViewXRot(1));
    float yaw = convoy.getFirst().yaw(), pitch = convoy.getFirst().pitch();
    entity.setPos(after);
    entity.setDeltaMovement(serverImpulse);
    if (state.replayVelocity != null) state.replayVelocity = velocity;
    entity.setYRot(yaw);
    entity.setXRot(pitch);
    if (entity instanceof LivingEntity living) {
      float headYaw = head.yaw();
      float bodyYaw = PortalLookAngles.transform(link.transform(), living.yBodyRot, 0).yaw();
      living.setYHeadRot(headYaw);
      living.yHeadRotO = headYaw;
      living.yBodyRot = bodyYaw;
      living.yBodyRotO = bodyYaw;
    }
    entity.yRotO = yaw;
    entity.xRotO = pitch;
    Vec3 earlierRenderFeet = EntityMotion.rebaseHistory(
        entity, link, before, after, new Vec3(entity.xo, entity.yo, entity.zo),
        entity.tickCount - 1, moveFraction);
    entity.resetFallDistance();
    entity.setOnGround(false);
    state.fallImmune = true;
    var event =
        new Crossing(
            ++state.sequence,
            link,
            before,
            after,
            velocity,
            yaw,
            pitch,
            moveFraction,
            previousFeet,
            beforeEyeHeight,
            beforeYaw,
            beforePitch,
            PortalPassagePose.scale(entity));
    publishCrossing(entity, event, earlierRenderFeet);
    for (int i = 1; i < convoy.size(); i++)
      crossPassenger(convoy.get(i), link, velocity, serverImpulse, moveFraction);
    return event;
  }

  private static Vec3 exitVelocity(
      Entity entity,
      PortalLink link,
      Vec3 incoming,
      Vec3 afterCenter,
      double moveFraction,
      double gravity) {
    if (entity instanceof LivingEntity living && living.isFallFlying())
      return link.transform().vector(incoming);
    if (entity instanceof Player player) {
      return PortalExitVelocity.toPlayerVelocity(
              link.transform(),
              PortalBallistics.velocity(incoming, gravity, moveFraction),
              afterCenter,
              entity.getBbHeight(),
              entity.getGravity(),
              EntityMotion.airDrag(player))
          .add(0, gravity * (moveFraction - .5), 0);
    }
    if (entity instanceof Projectile) return link.transform().vector(incoming);
    return PortalExitVelocity.adjustedVelocity(
        link.transform(), incoming, afterCenter, entity.getBbHeight(), entity.getGravity());
  }

  private static boolean serverControlled(Entity entity) {
    return entity instanceof ServerPlayer
        || entity.getControllingPassenger() instanceof ServerPlayer;
  }

  private static void crossPassenger(
      PortalPassengerGroup.Arrival arrival,
      PortalLink link,
      Vec3 velocity,
      Vec3 serverImpulse,
      double fraction) {
    Entity passenger = arrival.entity();
    float beforeYaw = passenger.getYRot(), beforePitch = passenger.getXRot();
    var state = EntityPortalState.of(passenger);
    var head =
        PortalLookAngles.transform(
            link.transform(), passenger.getViewYRot(1), passenger.getViewXRot(1));
    passenger.setPos(arrival.after());
    passenger.setDeltaMovement(serverImpulse);
    passenger.setYRot(arrival.yaw());
    passenger.yRotO = arrival.yaw();
    passenger.setXRot(arrival.pitch());
    passenger.xRotO = arrival.pitch();
    if (passenger instanceof LivingEntity living) {
      living.setYHeadRot(head.yaw());
      living.yHeadRotO = living.getYHeadRot();
      living.yBodyRot = PortalLookAngles.transform(link.transform(), living.yBodyRot, 0).yaw();
      living.yBodyRotO = living.yBodyRot;
    }
    Vec3 earlierRenderFeet = EntityMotion.rebaseHistory(
        passenger, link, arrival.before(), arrival.after(), arrival.previousFeet(), 0, fraction);
    passenger.resetFallDistance();
    passenger.setOnGround(false);
    state.fallImmune = true;
    publishCrossing(
        passenger,
        new Crossing(
            ++state.sequence,
            link,
            arrival.before(),
            arrival.after(),
            velocity,
            arrival.yaw(),
            arrival.pitch(),
            fraction,
            arrival.previousFeet(),
            passenger.getEyeHeight(),
            beforeYaw,
            beforePitch,
            PortalPassagePose.scale(passenger)),
        earlierRenderFeet);
  }

  public static void publishCrossing(Entity entity, Crossing crossing, Vec3 earlierRenderFeet) {
    boolean predicted = entity.level().isClientSide() && entity instanceof Projectile;
    if (predicted) EntityPortalState.of(entity).predictions.add(crossing);
    applyCrossing(entity, crossing, earlierRenderFeet, true);
    if (predicted) ProjectileStateSync.predicted(entity);
  }

  public static void reconcileCrossing(Entity entity, Crossing crossing, Vec3 earlierRenderFeet) {
    reconcileCrossing(entity, crossing, earlierRenderFeet, true);
  }

  public static void completeAcceptedCrossing(Entity entity, Crossing crossing) {
    reconcileCrossing(entity, crossing, crossing.previousFeet(), false);
  }

  private static void reconcileCrossing(Entity entity, Crossing crossing, Vec3 earlierRenderFeet,
      boolean publishVisualCrossing) {
    var state = EntityPortalState.of(entity);
    var match =
        state.predictions.stream()
            .filter(
                prediction ->
                    prediction.eventId() == crossing.eventId()
                        && prediction.link().equals(crossing.link()))
            .findFirst();
    if (match.isPresent()) {
      PortalProjectileRotation.acknowledge(entity, state.predictions, state.predictions.indexOf(match.get()), crossing);
      state.predictions.removeIf(prediction -> prediction.eventId() <= crossing.eventId());
      if (state.predictions.isEmpty()) state.crossed(crossing);
      return;
    }
    if (!state.predictions.isEmpty()) {
      undoPredictedCrossings(entity);
      state.predictions.clear();
      state.resetCamera();
      state.sequence = crossing.eventId();
    }
    applyCrossing(entity, crossing, earlierRenderFeet, publishVisualCrossing);
  }

  public static boolean acknowledgePredictions(Entity entity, List<Crossing> crossings) {
    var predictions = EntityPortalState.of(entity).predictions;
    if (predictions.isEmpty() || crossings.stream().anyMatch(crossing -> predictions.stream().noneMatch(
        prediction -> prediction.eventId() == crossing.eventId() && prediction.link().equals(crossing.link()))))
      return false;
    for (var crossing : crossings) reconcileCrossing(entity, crossing, crossing.previousFeet());
    return true;
  }

  public static Vec3 predictedMotion(Entity entity, Vec3 motion) {
    var predictions = EntityPortalState.of(entity).predictions;
    if (predictions.isEmpty()) return motion;
    for (var crossing : predictions)
      motion = crossing.link().transform().vector(motion);
    return motion;
  }

  public static void cancelPredictions(Entity entity) {
    var state = EntityPortalState.of(entity);
    if (!state.hasPredictions()) return;
    Vec3 position = entity.position();
    Vec3 velocity = entity.getDeltaMovement();
    float yaw = entity.getYRot(), pitch = entity.getXRot();
    for (var crossing : state.predictions.reversed()) {
      var inverse = crossing.link().transform().inverse();
      position = inverse.point(position);
      velocity = inverse.vector(velocity);
      var angles = PortalEntityAngles.transform(entity, inverse, yaw, pitch);
      yaw = angles.yaw();
      pitch = angles.pitch();
    }
    undoPredictedCrossings(entity);
    state.resetPredictionsToAuthority();
    EntityMotion.snap(entity, position, yaw, pitch, velocity);
  }

  public static void projectileSnapshot(Entity entity, ProjectileStatePayload snapshot) {
    var state = EntityPortalState.of(entity);
    if (!state.acceptSnapshot(snapshot.tick(), snapshot.event()) || state.predictions.isEmpty())
      return;
    undoPredictedCrossings(entity);
    state.resetPredictions(snapshot.event());
    EntityMotion.snap(entity, snapshot.position(), snapshot.yaw(), snapshot.pitch(), snapshot.velocity());
  }

  private static void undoPredictedCrossings(Entity entity) {
    for (var prediction : EntityPortalState.of(entity).predictions.reversed()) {
      PortalProjectileRotation.rotate(entity, prediction, true);
      CarryBindings.undoCrossing(entity, prediction.link());
      ProjectilePortalMovement.crossed(entity, prediction.link().inverse());
      if (entity instanceof ProjectilePortalMovement.Guided guided)
        guided.portalix$transformGuidance(prediction.link().transform().inverse());
    }
  }

  private static void applyCrossing(Entity entity, Crossing crossing, Vec3 earlierRenderFeet,
      boolean publishVisualCrossing) {
    if (entity instanceof ItemEntity item)
      PortalItemRotation.crossed(item, crossing.link().transform());
    PortalProjectileRotation.rotate(entity, crossing, false);
    if (entity instanceof ProjectilePortalMovement.Guided guided)
      guided.portalix$transformGuidance(crossing.link().transform());
    var state = EntityPortalState.of(entity);
    state.poseStarted = entity.tickCount;
    state.crossed(crossing);
    if (publishVisualCrossing) state.cameraCrossed(entity, crossing, earlierRenderFeet);
    CarryBindings.crossed(entity, crossing.link());
    ProjectilePortalMovement.crossed(entity, crossing.link());
    if (publishVisualCrossing) PortalCrossingSync.crossed(entity, crossing);
  }

  @FunctionalInterface
  public interface CrossingListener {
    void crossed(Entity entity, Crossing crossing);
  }

  @FunctionalInterface
  public interface CompletionListener {
    void onMovementCompleted(Entity entity, MoveResult result);
  }

  public record Crossing(
      long eventId,
      PortalLink link,
      Vec3 beforeFeet,
      Vec3 afterFeet,
      Vec3 velocity,
      float yaw,
      float pitch,
      double moveFraction,
      Vec3 previousFeet,
      float beforeEyeHeight,
      float beforeYaw,
      float beforePitch,
      float poseScale) {}

  public record MoveResult(
      MoverType mover,
      Vec3 beforeFeet,
      Vec3 beforeVelocity,
      boolean grounded,
      Pose beforePose,
      float beforeScale,
      boolean sneaking,
      Vec3 requested,
      List<Crossing> crossings,
      Vec3 afterFeet,
      Vec3 afterVelocity,
      long eventId) {}
}
