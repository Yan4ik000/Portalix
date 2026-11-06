package dev.portalix.client.carry.prediction;

import dev.portalix.carry.CarryAnchor;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryCollisionWorld;
import dev.portalix.carry.CarryFollowMotion;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.carry.EntityCarryWorld;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryInputPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class CarriedEntityPrediction {
  public enum Phase {
    VANILLA,
    LOCAL_HOLD,
    REMOTE_HOLD,
    FLIGHT,
    HANDOFF,
    SETTLING
  }

  private final Entity entity;
  private final CarryCollisionWorld collisions;
  private CarryInterpolation visual;
  private Phase phase = Phase.VANILLA;
  private long generation;
  private long serverTick = Long.MIN_VALUE;
  private int ownerId = -1;
  private UUID ownerUuid;
  private boolean released;
  private Hold hold;
  private CarryFlightPrediction flight;
  private CarryHandoff transition;
  private Vec3 physicalVelocity = Vec3.ZERO;

  public CarriedEntityPrediction(Entity entity, float partial) {
    this.entity = entity;
    collisions = new EntityCarryWorld(entity);
    visual = new CarryInterpolation(actual(partial), actualMovement());
  }

  public Entity entity() {
    return entity;
  }

  public boolean present() {
    return !entity.isRemoved() && entity.level().getEntity(entity.getId()) == entity;
  }

  public long generation() {
    return generation;
  }

  public Phase phase() {
    return phase;
  }

  public int ownerId() {
    return ownerId;
  }

  public UUID ownerUuid() {
    return ownerUuid;
  }

  public Vec3 velocity() {
    return phase == Phase.VANILLA ? actualMovement() : visual.velocity();
  }

  public Quaternionf rotation() {
    return visual.rotation();
  }

  public void grab(CarryGrabPayload grab, boolean local, double now, float partial) {
    if (!matches(grab.entityId(), grab.entityUuid()) || grab.generation() <= generation) return;
    capture(now, partial);
    generation = grab.generation();
    serverTick = grab.tick();
    ownerId = grab.ownerId();
    ownerUuid = grab.ownerUuid();
    released = false;
    physicalVelocity = grab.velocity();
    transition = null;
    if (local) {
      visual = new CarryInterpolation(visual.position(), visual.velocity());
      hold = new Hold(visual, grab.velocity(), now);
      hold.observedCrossings = CarryBindings.crossings(entity);
      flight = null;
      phase = Phase.LOCAL_HOLD;
    } else {
      hold = null;
      flight = extrapolation(grab.tick(), now, grab.position(), grab.velocity(), now);
      phase = Phase.REMOTE_HOLD;
    }
  }

  public CarryInputPayload advance(Player holder, long sequence, double now) {
    if (phase != Phase.LOCAL_HOLD) throw new IllegalStateException("No local carry body");
    sample(now, 0);
    long acknowledged = CarryBindings.crossings(entity);
    long crossed = acknowledged - hold.observedCrossings;
    for (long i = 0; i < crossed && !hold.pendingCrossings.isEmpty(); i++)
      hold.pendingCrossings.removeFirst();
    hold.observedCrossings = acknowledged;
    var references = new ArrayList<>(CarryBindings.route(entity));
    references.addAll(hold.pendingCrossings);
    var route = CarryAnchor.resolveRoute(entity, PortalRoutes.simplifyIds(references));
    if (route == null) {
      lostOwner(now, 0);
      return null;
    }
    Vec3 start = visual.position();
    var hull = entity.getBoundingBox();
    Vec3 desired =
        CarryAnchor.routeHoldPosition(holder, route, hull.move(start.subtract(hull.getCenter())));
    if (hold.path == null)
      hold.motion = new CarryFollowMotion(visual.velocity().scale(1 / GunFireTiming.TICK_SECONDS));
    hold.path = hold.motion.step(collisions, start, desired, GunFireTiming.TICK_SECONDS);
    hold.pathStart = now;
    hold.acknowledgements.submit(sequence, now + 1);
    for (var node : hold.path.nodes())
      if (node.crossing() != null)
        hold.pendingCrossings.add(PortalLinkId.fromLink(node.crossing()));
    return new CarryInputPayload(
        entity.getId(),
        entity.getUUID(),
        generation,
        sequence,
        hold.path.nodes().stream()
            .map(
                n ->
                    new CarryInputPayload.Node(
                        n.point(),
                        n.crossing() == null ? null : PortalLinkId.fromLink(n.crossing())))
            .toList());
  }

  public void update(CarryStatePayload update, double now, float partial) {
    if (!matches(update.entityId(), update.entityUuid()) || update.generation() < generation)
      return;
    if (update.generation() > generation) {
      if (!update.released()) return;
      capture(now, partial);
      generation = update.generation();
      ownerId = -1;
      ownerUuid = null;
      released = true;
      hold = null;
      transition = null;
      flight = extrapolation(update.tick(), now, update.position(), update.velocity(), now);
      phase = Phase.FLIGHT;
      serverTick = Long.MIN_VALUE;
    }
    if (update.tick() < serverTick || update.tick() == serverTick && !update.released()) return;
    if (update.released()) {
      if (phase != Phase.FLIGHT) return;
      sample(now, partial);
      serverTick = update.tick();
      physicalVelocity = update.velocity();
      flight.update(
          update.tick(),
          center(update.position()),
          update.velocity(),
          now,
          visual.inputPosition(),
          visual.inputVelocity());
      if (update.complete()) {
        phase = Phase.HANDOFF;
        Vec3 nativePosition = entity.getPositionCodec().getBase();

        if (nativePosition.distanceToSqr(update.position()) <= 3.0 / (4096 * 4096))
          nativePosition(nativePosition, now, partial);
      }
    } else if (phase == Phase.LOCAL_HOLD) {
      serverTick = update.tick();
      physicalVelocity = update.velocity();
      hold.acknowledgements.confirm(update.sequence());
    } else if (phase == Phase.REMOTE_HOLD) {
      sample(now, partial);
      serverTick = update.tick();
      physicalVelocity = update.velocity();
      flight.update(
          update.tick(),
          center(update.position()),
          update.velocity(),
          now,
          visual.inputPosition(),
          visual.inputVelocity());
    }
  }

  public void release(CarryReleasePayload release, double now, float partial) {
    if (!matches(release.entityId(), release.entityUuid())
        || release.generation() < generation
        || release.generation() == generation && released) return;
    capture(now, partial);
    Vec3 movement = visual.inputVelocity();
    if (release.thrown()) movement = movement.add(release.velocity().subtract(physicalVelocity));
    double sourceTime = hold == null ? now : hold.acknowledgements.acknowledgedTime(release.sequence(), now);
    generation = release.generation();
    serverTick = release.tick();
    released = true;
    ownerId = -1;
    ownerUuid = null;
    hold = null;
    transition = null;
    physicalVelocity = release.velocity();
    flight =
        new CarryFlightPrediction(
            release.tick(),
            sourceTime,
            center(release.position()),
            release.velocity(),
            now,
            visual.inputPosition(),
            movement);
    phase = Phase.FLIGHT;
  }

  public void lostOwner(double now, float partial) {
    if (phase != Phase.LOCAL_HOLD && phase != Phase.REMOTE_HOLD) return;
    sample(now, partial);
    settle(now, partial);
  }

  public void nativePosition(Vec3 position, double now, float partial) {
    if (phase != Phase.HANDOFF) return;
    sample(now, partial);

    EntityMotion.snapPosition(entity, position);
    settle(now, partial);
  }

  private void settle(double now, float partial) {
    transition =
        new CarryHandoff(
            visual.inputPosition().subtract(actual(partial)),
            visual.inputVelocity().subtract(actualMovement()),
            now);
    ownerId = -1;
    ownerUuid = null;
    hold = null;
    flight = null;
    phase = Phase.SETTLING;
  }

  public void tick(double now) {
    if (phase != Phase.SETTLING || !transition.complete(now)) return;

    visual = new CarryInterpolation(actual(0), actualMovement());
    phase = Phase.VANILLA;
    transition = null;
  }

  public Vec3 sample(double now, float partial) {
    if (phase == Phase.VANILLA) return actual(partial);
    if (phase == Phase.LOCAL_HOLD) {
      if (hold.path == null)
        return visual.sample(collisions, hold.start.position(now), hold.start.velocity(now), now);
      float pathPartial = hold.partial(now);
      return visual.samplePath(hold.path, pathPartial, hold.pathStart);
    }
    if (flight != null)
      return visual.sample(collisions, flight.position(now), flight.velocity(now), now);
    return visual.sample(
        collisions,
        actual(partial).add(transition.offset(now)),
        actualMovement().add(transition.movement(now)),
        now);
  }

  public AABB bounds(double now) {
    AABB hull = entity.getBoundingBox();
    AABB bounds = hull.minmax(hull.move(visual.position().subtract(hull.getCenter())));
    if (hold != null && hold.path != null) {
      return bounds.minmax(hold.path.bounds(hull)).inflate(.1);
    }
    CarryFlightPrediction motion = flight != null ? flight : hold != null ? hold.start : null;
    if (motion != null) {
      bounds =
          bounds
              .minmax(hull.move(motion.position(now).subtract(hull.getCenter())))
              .minmax(hull.move(motion.position(now + 1).subtract(hull.getCenter())));
    } else if (transition != null)
      bounds =
          bounds.inflate(
              transition.position().length()
                  + transition.velocity().length() * CarryHandoff.DURATION);
    return bounds.inflate(.1);
  }

  public void teleport(Vec3 position, double now) {
    physicalVelocity = entity.getDeltaMovement();
    visual = new CarryInterpolation(center(position), physicalVelocity);
    if (phase == Phase.LOCAL_HOLD) {
      hold = new Hold(visual, physicalVelocity, now);
      hold.observedCrossings = CarryBindings.crossings(entity);
    } else if (phase == Phase.FLIGHT || phase == Phase.REMOTE_HOLD)
      flight = extrapolation(serverTick, now, position, physicalVelocity, now);
    else {
      phase = Phase.VANILLA;
      hold = null;
      flight = null;
      transition = null;
    }
  }

  public void crossed(PortalLink link) {
    physicalVelocity = entity.getDeltaMovement();
    visual.rebase(link);
    if (phase == Phase.LOCAL_HOLD) {
      return;
    }
    var transform = link.transform();
    if (flight != null) flight.transform(transform);
    if (transition != null)
      transition =
          new CarryHandoff(
              transform.vector(transition.position()),
              transform.vector(transition.velocity()),
              transition.start());
  }

  private void capture(double now, float partial) {
    if (phase == Phase.VANILLA) visual = new CarryInterpolation(actual(partial), actualMovement());
    else sample(now, partial);
  }

  private CarryFlightPrediction extrapolation(
      long tick, double sourceTime, Vec3 position, Vec3 velocity, double now) {
    return new CarryFlightPrediction(
        tick,
        sourceTime,
        center(position),
        velocity,
        now,
        visual.inputPosition(),
        visual.inputVelocity());
  }

  private boolean matches(int id, UUID uuid) {
    return entity.getId() == id && entity.getUUID().equals(uuid);
  }

  private Vec3 center(Vec3 position) {
    return position.add(0, entity.getBbHeight() * .5, 0);
  }

  private Vec3 actual(float partial) {
    return center(entity.getPosition(partial));
  }

  private Vec3 actualMovement() {
    return entity.position().subtract(entity.xo, entity.yo, entity.zo);
  }

  private static final class Hold {
    final CarryAcknowledgements acknowledgements = new CarryAcknowledgements();
    final CarryFlightPrediction start;
    CarryFollowMotion motion;
    CarryMotionPath path;
    double pathStart;
    final List<PortalLinkId> pendingCrossings = new ArrayList<>();
    long observedCrossings;

    Hold(CarryInterpolation visual, Vec3 velocity, double now) {
      start =
          new CarryFlightPrediction(
              0, now, visual.position(), velocity, now, visual.position(), visual.velocity());
    }

    float partial(double now) {
      return (float) Math.clamp(now - pathStart, 0, 1);
    }
  }
}
