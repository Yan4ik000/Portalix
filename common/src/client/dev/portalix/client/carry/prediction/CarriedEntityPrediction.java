package dev.portalix.client.carry.prediction;

import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryCollisionWorld;
import dev.portalix.carry.EntityCarryWorld;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.portal.PortalLink;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class CarriedEntityPrediction {
  public enum Phase {
    VANILLA,
    LOCAL_HOLD,
    REMOTE_HOLD,
    FLIGHT,
    SETTLING
  }

  private final Entity entity;
  private final CarryCollisionWorld collisions;
  private final CarrySnapshotClock snapshotClock = new CarrySnapshotClock();
  private CarryInterpolation visual;
  private Phase phase = Phase.VANILLA;
  private long generation;
  private long serverTick = Long.MIN_VALUE;
  private int ownerId = -1;
  private UUID ownerUuid;
  private boolean released;
  private boolean localOwner;
  private Hold hold;
  private CarryFlightPrediction flight;
  private CarryPositionCorrection settleCorrection;
  private Vec3 physicalVelocity = Vec3.ZERO;

  public CarriedEntityPrediction(Entity entity, float partial) {
    this.entity = entity;
    collisions = new EntityCarryWorld(entity);
    visual = new CarryInterpolation(actual(partial), actualMovement());
  }

  public Entity entity() {
    return entity;
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

  public Quaternionf rotation() {
    return visual.rotation();
  }

  public boolean grab(CarryGrabPayload grab, boolean local, double now, float partial) {
    if (!matches(grab.entityId(), grab.entityUuid()) || grab.generation() < generation) return false;
    if (grab.generation() == generation) {
      if (released || grab.tick() < serverTick || grab.ownerId() != ownerId
          || ownerUuid == null || !ownerUuid.equals(grab.ownerUuid())) return false;
      if (grab.tick() == serverTick && (phase == Phase.LOCAL_HOLD || phase == Phase.REMOTE_HOLD)) return true;
    }
    capture(now, partial);
    snapshotClock.receive(grab.tick(), now);
    generation = grab.generation();
    serverTick = grab.tick();
    ownerId = grab.ownerId();
    ownerUuid = grab.ownerUuid();
    released = false;
    localOwner = local;
    physicalVelocity = grab.velocity();
    settleCorrection = null;
    if (local) {
      startLocalHold(grab.tick(), grab.position(), grab.velocity(), now);
    } else {
      hold = null;
      flight = extrapolation(grab.tick(), grab.position(), grab.velocity(), now);
      phase = Phase.REMOTE_HOLD;
    }
    return true;
  }

  private void startLocalHold(long tick, Vec3 position, Vec3 velocity, double now) {
    Vec3 displayed = visual.position();
    Vec3 movement = visual.velocity();
    hold = new Hold(collisions, tick, center(position), velocity);
    setHoldVisual(now, displayed, movement);
    flight = null;
    phase = Phase.LOCAL_HOLD;
  }

  public void advance(Player holder, double now) {
    if (phase != Phase.LOCAL_HOLD) throw new IllegalStateException("No local carry body");
    var route = CarryBindings.resolve(entity);
    if (route == null) {
      suspend(now, 0);
      return;
    }
    hold.prediction.advance(CarryAnchor.holdTarget(holder), route, now, snapshotClock.tickDuration());
  }

  public void update(CarryStatePayload update, double now, float partial) {
    if (!matches(update.entityId(), update.entityUuid()) || update.generation() < generation)
      return;
    if (update.generation() == generation) {
      if (update.tick() < serverTick || update.tick() == serverTick && !update.released()
          || released && phase != Phase.FLIGHT) return;
    } else if (!update.released()) return;
    snapshotClock.receive(update.tick(), now);
    if (update.released() && (update.generation() > generation || !released)) {
      capture(now, partial);
      beginRelease(update.generation());
      flight = extrapolation(update.tick(), update.position(), update.velocity(), now);
      phase = Phase.FLIGHT;
    }
    if (update.released()) {
      if (phase != Phase.FLIGHT) return;
      updateExtrapolation(update, now, partial);
      if (update.complete()) settle(now, partial);
    } else if (phase == Phase.LOCAL_HOLD) {
      serverTick = update.tick();
      physicalVelocity = update.velocity();
      reconcile(update, now, partial);
    } else if (phase == Phase.REMOTE_HOLD) {
      updateExtrapolation(update, now, partial);
    } else if (ownerId >= 0 && !released) {
      serverTick = update.tick();
      physicalVelocity = update.velocity();
      if (entity.level().getEntity(ownerId) instanceof Player owner
          && owner.getUUID().equals(ownerUuid) && GunPermissions.canCarry(owner)
          && CarryBindings.holderId(entity) == ownerId && CarryBindings.resolve(entity) != null) {
        capture(now, partial);
        settleCorrection = null;
        if (localOwner) startLocalHold(serverTick, update.position(), physicalVelocity, now);
        else {
          flight = extrapolation(serverTick, update.position(), physicalVelocity, now);
          phase = Phase.REMOTE_HOLD;
        }
      }
    }
  }

  private void updateExtrapolation(CarryStatePayload update, double now, float partial) {
    sample(now, partial);
    serverTick = update.tick();
    physicalVelocity = update.velocity();
    flight.update(
        update.tick(),
        center(update.position()),
        update.velocity(),
        snapshotClock.tickDuration(),
        now,
        visual.inputPosition(),
        visual.inputVelocity());
  }

  public void release(CarryReleasePayload release, double now, float partial) {
    if (!matches(release.entityId(), release.entityUuid())
        || release.generation() < generation
        || release.generation() == generation && (released || release.tick() < serverTick)) return;
    capture(now, partial);
    snapshotClock.receive(release.tick(), now);
    double interval = snapshotClock.tickDuration();
    Vec3 movement = visual.inputVelocity();
    if (release.thrown())
      movement = movement.add(release.velocity().subtract(physicalVelocity).scale(1 / interval));
    beginRelease(release.generation());
    serverTick = release.tick();
    physicalVelocity = release.velocity();
    flight =
        new CarryFlightPrediction(
            release.tick(),
            center(release.position()),
            release.velocity(),
            interval,
            now,
            visual.inputPosition(),
            movement);
    phase = Phase.FLIGHT;
  }

  private void beginRelease(long generation) {
    CarryBindings.unbindTarget(entity);
    this.generation = generation;
    released = true;
    ownerId = -1;
    ownerUuid = null;
    hold = null;
    settleCorrection = null;
  }

  public void lostOwner(double now, float partial) {
    ownerId = -1;
    ownerUuid = null;
    suspend(now, partial);
  }

  public void suspend(double now, float partial) {
    if (phase != Phase.LOCAL_HOLD && phase != Phase.REMOTE_HOLD) return;
    capture(now, partial);
    settle(now, partial);
  }

  private void settle(double now, float partial) {
    settleCorrection =
        new CarryPositionCorrection(
            visual.inputPosition().subtract(actual(partial)),
            visual.inputVelocity().subtract(actualMovement()),
            now);
    hold = null;
    flight = null;
    phase = Phase.SETTLING;
  }

  public void tick(double now) {
    if (phase == Phase.LOCAL_HOLD && hold.correction != null && hold.correction.complete(now))
      hold.correction = null;
    if (phase != Phase.SETTLING || !settleCorrection.complete(now)) return;

    visual = new CarryInterpolation(actual(0), actualMovement());
    phase = Phase.VANILLA;
    settleCorrection = null;
  }

  public Vec3 sample(double now, float partial) {
    return sample(now, partial, -1);
  }

  public Vec3 renderSample(double now, float partial, long frame) {
    return sample(now, partial, frame);
  }

  private Vec3 sample(double now, float partial, long frame) {
    if (phase == Phase.VANILLA) return actual(partial);
    if (phase == Phase.LOCAL_HOLD) {
      if (now > visual.sampledAt()) visual.sample(hold.prediction.sample(now), now);
      now = visual.sampledAt();
      Vec3 position = visual.position();
      if (hold.correction != null) {
        if (frame >= 0 && hold.sampledFrame == frame && hold.sampledTime == now
            && hold.sampledCorrection == hold.correction && position.equals(hold.sampledPosition))
          return hold.displayedPosition;
        Vec3 displayed = visual.offsetPosition(collisions, hold.correction.offset(now));
        if (displayed != null) {
          if (frame >= 0) {
            hold.sampledFrame = frame;
            hold.sampledTime = now;
            hold.sampledCorrection = hold.correction;
            hold.sampledPosition = position;
            hold.displayedPosition = displayed;
          }
          return displayed;
        }
        hold.correction = null;
      }
      return position;
    }
    if (flight != null)
      return visual.sample(collisions, flight.position(now), flight.velocity(now), now);
    return visual.sample(
        collisions,
        actual(partial).add(settleCorrection.offset(now)),
        actualMovement().add(settleCorrection.velocity(now)),
        now);
  }

  public AABB bounds(double now) {
    AABB hull = entity.getBoundingBox();
    AABB bounds = hull.minmax(hull.move(visual.position().subtract(hull.getCenter())));
    double padding = .1;
    if (hold != null) {
      if (hold.correction != null) padding += hold.correction.boundsPadding();
      return bounds.minmax(hold.prediction.bounds(hull, now)).inflate(padding);
    }
    if (flight != null) {
      bounds =
          bounds
              .minmax(hull.move(flight.position(now).subtract(hull.getCenter())))
              .minmax(hull.move(flight.position(now + 1).subtract(hull.getCenter())));
    } else if (settleCorrection != null) {
      bounds = bounds.inflate(settleCorrection.boundsPadding());
    }
    return bounds.inflate(padding);
  }

  public void teleport(Vec3 position, double now) {
    physicalVelocity = entity.getDeltaMovement();
    visual = new CarryInterpolation(center(position), physicalVelocity.scale(1 / snapshotClock.tickDuration()));
    if (phase == Phase.LOCAL_HOLD) {
      hold = new Hold(collisions, serverTick, center(position), physicalVelocity);
    } else if (phase == Phase.FLIGHT || phase == Phase.REMOTE_HOLD)
      flight = extrapolation(serverTick, position, physicalVelocity, now);
    else {
      phase = Phase.VANILLA;
      hold = null;
      flight = null;
      settleCorrection = null;
    }
  }

  public void crossed(PortalLink link) {
    var transform = link.transform();
    physicalVelocity = transform.vector(physicalVelocity);
    visual.rebase(link);
    if (phase == Phase.LOCAL_HOLD) {
      hold.prediction.crossed(link);
      if (hold.correction != null)
        hold.correction = hold.correction.transform(transform);
      return;
    }
    if (flight != null) flight.transform(transform);
    if (settleCorrection != null) settleCorrection = settleCorrection.transform(transform);
  }

  private void capture(double now, float partial) {
    if (phase == Phase.VANILLA) visual = new CarryInterpolation(actual(partial), actualMovement());
    else {
      Vec3 displayed = sample(now, partial);
      if (phase == Phase.LOCAL_HOLD && hold.correction != null) {
        visual.correct(displayed, hold.correction.velocity(visual.sampledAt()));
        hold.correction = null;
      }
    }
  }

  private void reconcile(CarryStatePayload update, double now, float partial) {
    Vec3 displayed = sample(now, partial);
    double time = visual.sampledAt();
    Vec3 movement = visual.velocity();
    if (hold.correction != null)
      movement = movement.add(visual.worldVector(hold.correction.velocity(time)));
    if (hold.prediction.confirm(update.tick(), center(update.position()), update.velocity(),
        time, snapshotClock.tickDuration()))
      setHoldVisual(time, displayed, movement);
  }

  private void setHoldVisual(double now, Vec3 displayed, Vec3 movement) {
    visual.sample(hold.prediction.sample(now), now);
    Vec3 offset = visual.inputVector(displayed.subtract(visual.position()));
    Vec3 velocity = visual.inputVector(movement.subtract(visual.velocity()));
    hold.correction = (offset.lengthSqr() > 1e-12 || velocity.lengthSqr() > 1e-12)
            && visual.offsetPosition(collisions, offset) != null
        ? new CarryPositionCorrection(offset, velocity, now)
        : null;
  }

  private CarryFlightPrediction extrapolation(
      long tick, Vec3 position, Vec3 velocity, double now) {
    return new CarryFlightPrediction(
        tick,
        center(position),
        velocity,
        snapshotClock.tickDuration(),
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
    if (entity instanceof AbstractMinecart cart
        && cart.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp())
      return center(behavior.getCartLerpPosition(partial));
    Vec3 feet = entity.oldPosition().lerp(entity.position(), partial);
    if (entity.getVehicle() instanceof AbstractMinecart cart
        && cart.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp())
      feet = feet.add(behavior.getCartLerpPosition(partial)
          .subtract(cart.oldPosition().lerp(cart.position(), partial)));
    return center(feet);
  }

  private Vec3 actualMovement() {
    return actual(1).subtract(actual(0));
  }

  private static final class Hold {
    final CarryHoldPrediction prediction;
    CarryPositionCorrection correction;
    long sampledFrame = -1;
    double sampledTime;
    CarryPositionCorrection sampledCorrection;
    Vec3 sampledPosition;
    Vec3 displayedPosition;

    Hold(CarryCollisionWorld collisions, long tick, Vec3 position, Vec3 velocity) {
      prediction = new CarryHoldPrediction(collisions, tick, position, velocity);
    }
  }
}
