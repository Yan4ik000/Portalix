package dev.portalix.server.carry;

import dev.portalix.PortalixMod;
import dev.portalix.api.CarryPermission;
import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.carry.EntityCarryWorld;
import dev.portalix.compat.minecraft.CarriedEntityMotion;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryInputPayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.portal.traversal.PortalCrossingSync;
import dev.portalix.server.EntityObservers;
import dev.portalix.server.GunStateBroadcaster;
import dev.portalix.server.PortalServerEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class CarryController {
  private static final double RANGE = 2.0;
  private static final double THROW_SPEED = 1.25;
  private static final double MAX_HOLDER_DISTANCE = 5.0;
  private static final int MIN_RELEASE_PICKUP_DELAY_TICKS = 10;

  private static final TagKey<EntityType<?>> CARRYABLE =
      TagKey.create(Registries.ENTITY_TYPE, PortalixMod.id("carryable"));

  private static final TagKey<EntityType<?>> CARRY_BLACKLIST =
      TagKey.create(Registries.ENTITY_TYPE, PortalixMod.id("carry_blacklist"));
  private static final UUID ZERO_UUID = GunStatePayload.EMPTY_TARGET;

  private final GunStateBroadcaster broadcast;

  public CarryController(GunStateBroadcaster broadcast) {
    this.broadcast = broadcast;
  }

  private final Map<UUID, Hold> heldByOwner = new HashMap<>();
  private final Map<UUID, Flight> flights = new HashMap<>();
  private final List<Hold> pendingRelease = new ArrayList<>();
  private final Map<UUID, Hold> heldByEntity = new HashMap<>();
  private long generation;

  public int targetId(UUID owner) {
    Hold hold = this.heldByOwner.get(owner);
    return hold == null ? -1 : hold.entity.getId();
  }

  public UUID targetUuid(UUID owner) {
    Hold hold = this.heldByOwner.get(owner);
    return hold == null ? ZERO_UUID : hold.entity.getUUID();
  }

  public int holdTicks(UUID owner) {
    Hold hold = this.heldByOwner.get(owner);
    return hold == null ? 0 : hold.age;
  }

  public boolean isHolding(UUID id) {
    return this.heldByOwner.containsKey(id);
  }

  private boolean isCarried(Entity entity) {
    return this.heldByEntity.containsKey(entity.getUUID());
  }

  private static boolean canCarry(Entity entity) {
    return entity.isAlive()
        && !entity.isPassenger()
        && !entity.isVehicle()
        && !entity.typeHolder().is(CARRY_BLACKLIST)
        && entity.typeHolder().is(CARRYABLE);
  }

  public void toggleGrab(ServerPlayer player) {
    if (isHolding(player.getUUID())) {
      requestRelease(player, false);
      return;
    }
    if (!GunPermissions.canCarry(player)) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    var eye = PortalEyePose.sample(player);
    var ray =
        PortalRaycast.cast(
            player,
            eye.position(),
            eye.forward().scale(RANGE),
            entity -> canCarry(entity) && !isCarried(entity),
            Block.COLLIDER,
            Fluid.NONE,
            .1);
    Entity target = ray.hit() instanceof EntityHitResult hit ? hit.getEntity() : null;
    if (target == null
        || !CarryPermission.EVENT
            .invoker()
            .allow(player, target, CarryPermission.Operation.GRAB, target.position())) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    flights.remove(target.getUUID());
    var references = new ArrayList<PortalLinkId>();
    for (var link : eye.eyePath()) references.add(PortalLinkId.fromLink(link));
    references.addAll(ray.links().stream().map(PortalLinkId::fromLink).toList());
    var route = PortalRoutes.simplifyIds(references);
    Hold hold = new Hold(target, player, ++generation, CarryAnchor.resolveRoute(player, route));
    heldByOwner.put(player.getUUID(), hold);
    heldByEntity.put(target.getUUID(), hold);
    CarryBindings.bind(player.level(), player.getId(), target.getId());
    CarryBindings.route(target, route);
    CarriedEntityMotion.grab(target);

    broadcast.sendGrab(target, player, grabSnapshot(hold));
    broadcast.sendEvent(player, GunEventKind.GRABBED);
  }

  public boolean requestRelease(ServerPlayer player, boolean launch) {
    Hold hold = heldByOwner.get(player.getUUID());
    if (hold == null) return false;
    Release next = launch ? Release.THROW : Release.DROP;
    if (hold.release == next) return false;
    hold.release = next;
    return true;
  }

  public void receiveInput(ServerPlayer player, CarryInputPayload input) {
    Hold hold = heldByOwner.get(player.getUUID());
    if (hold == null
        || hold.entity.getId() != input.entityId()
        || !hold.entity.getUUID().equals(input.entityUuid())
        || hold.generation != input.generation()
        || input.sequence() <= hold.sequence) return;
    long now = player.level().getGameTime();
    hold.releaseVelocity = Vec3.ZERO;
    CarryMotionPath movement =
        validHolder(player, hold.entity)
            ? CarryMovement.validate(player, hold.entity, input, MAX_HOLDER_DISTANCE)
            : null;
    if (movement == null) {
      release(player.getUUID(), player, false);
      return;
    }
    hold.sequence = input.sequence();
    if (!CarryMovement.apply(player, hold.entity, hold.collisions, movement)) {
      release(player.getUUID(), player, false);
      return;
    }
    Entity entity = hold.entity;
    hold.releaseVelocity = movement.movement(1);
    hold.movementTick = now;
    hold.appliedSequence = input.sequence();
    hold.simulationTick = entity.level().getGameTime();
    entity.setDeltaMovement(Vec3.ZERO);
    entity.fallDistance = 0.0;
  }

  private static boolean validHolder(ServerPlayer player, Entity entity) {
    return GunPermissions.canCarry(player)
        && entity.isAlive()
        && entity.level() == player.level()
        && CarryAnchor.routeDistance(
                player, entity.getBoundingBox().getCenter(), CarryBindings.route(entity))
            <= MAX_HOLDER_DISTANCE
        && !entity.isPassenger()
        && !entity.isVehicle();
  }

  public void tick(MinecraftServer server) {
    var iterator = flights.values().iterator();
    while (iterator.hasNext()) {
      Flight flight = iterator.next();
      Entity entity = flight.entity;
      if (entity.isRemoved()
          || entity.level().getEntity(entity.getId()) != entity
          || EntityObservers.tracking(entity).isEmpty()) {
        iterator.remove();
        continue;
      }
      boolean complete =
          entity.isPassenger()
              || entity.position().distanceToSqr(flight.position) < 1.0E-10
                  && (entity.onGround() || entity.getDeltaMovement().lengthSqr() < 1.0E-10);
      broadcast.sendCarryState(
          entity, server.getPlayerList().getPlayer(flight.owner), flightSnapshot(flight, complete));
      flight.position = entity.position();
      if (complete) iterator.remove();
    }

    this.pendingRelease.clear();
    for (Hold hold : this.heldByOwner.values()) {
      ServerPlayer player = server.getPlayerList().getPlayer(hold.owner);
      Entity entity = hold.entity;
      if (validHolder(player, entity)) {
        ++hold.age;
        if (entity.level().getGameTime() > hold.movementTick + 1) hold.releaseVelocity = Vec3.ZERO;
        Vec3 look = CarryAnchor.routeLookDirection(player, CarryBindings.resolve(entity));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)) + hold.yawOffset;
        CarriedEntityMotion.hold(entity, yaw);
        hold.simulationTick = entity.level().getGameTime();
        hold.appliedSequence = hold.sequence;
        broadcast.sendCarryState(
            entity,
            player,
            new CarryStatePayload(
                entity.getId(),
                entity.getUUID(),
                hold.generation,
                hold.appliedSequence,
                hold.simulationTick,
                entity.position(),
                hold.releaseVelocity,
                false,
                false));
        if (hold.release != null) this.pendingRelease.add(hold);
      } else {
        hold.release = Release.DROP;
        this.pendingRelease.add(hold);
      }
    }
    for (Hold hold : this.pendingRelease) {
      boolean launch = hold.release == Release.THROW;
      hold.release = null;
      release(hold.owner, server.getPlayerList().getPlayer(hold.owner), launch);
    }
    this.pendingRelease.clear();
  }

  public void release(UUID id, ServerPlayer player, boolean launch) {
    Hold existing = this.heldByOwner.get(id);
    if (existing != null && !GunPermissions.canCarry(player)) {
      launch = false;
      existing.releaseVelocity = Vec3.ZERO;
    }
    if (launch
        && existing != null
        && player != null
        && !CarryPermission.EVENT
            .invoker()
            .allow(
                player,
                existing.entity,
                CarryPermission.Operation.THROW,
                existing.entity.position())) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    Hold hold = this.heldByOwner.remove(id);
    if (hold != null) {
      Entity entity = hold.entity;
      if (entity.level().getGameTime() > hold.movementTick + 1) hold.releaseVelocity = Vec3.ZERO;
      this.heldByEntity.remove(entity.getUUID());
      var releaseRoute = CarryBindings.resolve(entity);
      CarryBindings.unbindOwner(hold.level, hold.ownerId);
      CarriedEntityMotion.release(entity, MIN_RELEASE_PICKUP_DELAY_TICKS);

      entity.setDeltaMovement(
          launch && player != null && releaseRoute != null
              ? CarryAnchor.routeLookDirection(player, releaseRoute)
                  .scale(THROW_SPEED)
                  .add(hold.releaseVelocity)
              : hold.releaseVelocity);
      entity.hurtMarked = true;
      flights.put(entity.getUUID(), new Flight(entity, id, hold.generation));
      broadcast.sendRelease(
          entity, player, launch, hold.generation, hold.appliedSequence, hold.simulationTick);
      if (player != null) {
        broadcast.sendEvent(player, launch ? GunEventKind.THROWN : GunEventKind.RELEASED);
      }
    }
  }

  public void portalsClosing(ServerLevel level, List<PortalLink> links) {
    var retired = new HashSet<PortalLinkId>();
    for (var link : links) {
      retired.add(PortalLinkId.fromLink(link));
      retired.add(PortalLinkId.fromLink(link.inverse()));
    }
    var owners = new ArrayList<UUID>();
    for (var entry : heldByOwner.entrySet()) {
      Hold hold = entry.getValue();
      if (hold.level == level
          && CarryBindings.route(hold.entity).stream().anyMatch(retired::contains))
        owners.add(entry.getKey());
    }
    for (UUID owner : owners)
      release(owner, level.getServer().getPlayerList().getPlayer(owner), false);
  }

  public void clear() {
    for (UUID id : new ArrayList<>(this.heldByOwner.keySet())) {
      release(id, null, false);
    }
    this.heldByEntity.clear();
    this.flights.clear();
    this.pendingRelease.clear();
  }

  public void trackingStarted(Entity entity, ServerPlayer observer) {
    Hold hold = heldByEntity.get(entity.getUUID());
    if (hold != null && hold.entity == entity) {
      synchronizeHold(hold, observer);
      return;
    }
    Flight flight = flights.get(entity.getUUID());
    if (flight != null && ModNetworking.canPlayerReceive(observer, CarryStatePayload.TYPE))
      PortalCrossingSync.sendAfterCrossing(entity, observer, flightSnapshot(flight, false));
  }

  public void synchronize(ServerPlayer observer) {
    for (var entry : heldByOwner.entrySet()) {
      var hold = entry.getValue();
      if (hold.entity.level() == observer.level()
          && (entry.getKey().equals(observer.getUUID())
              || EntityObservers.tracking(hold.entity).contains(observer)))
        synchronizeHold(hold, observer);
    }
    for (var flight : flights.values()) {
      if (flight.entity.level() == observer.level()
          && EntityObservers.tracking(flight.entity).contains(observer)
          && ModNetworking.canPlayerReceive(observer, CarryStatePayload.TYPE))
        PortalCrossingSync.sendAfterCrossing(
            flight.entity, observer, flightSnapshot(flight, false));
    }
  }

  private static void synchronizeHold(Hold hold, ServerPlayer observer) {
    if (!ModNetworking.canPlayerReceive(observer, CarryGrabPayload.TYPE)) return;
    PortalServerEvents.observePortals(observer, CarryBindings.route(hold.entity));
    PortalCrossingSync.sendAfterCrossing(hold.entity, observer, grabSnapshot(hold));
  }

  private static CarryStatePayload flightSnapshot(Flight flight, boolean complete) {
    Entity entity = flight.entity;
    return new CarryStatePayload(
        entity.getId(),
        entity.getUUID(),
        flight.generation,
        0,
        entity.level().getGameTime(),
        entity.position(),
        entity.getDeltaMovement(),
        true,
        complete);
  }

  private static CarryGrabPayload grabSnapshot(Hold hold) {
    return new CarryGrabPayload(
        hold.entity.getId(),
        hold.entity.getUUID(),
        hold.generation,
        hold.appliedSequence,
        hold.ownerId,
        hold.owner,
        hold.simulationTick,
        hold.entity.position(),
        hold.releaseVelocity,
        CarryBindings.route(hold.entity));
  }

  private static final class Flight {
    final Entity entity;
    final UUID owner;
    final long generation;
    Vec3 position;

    Flight(Entity entity, UUID owner, long generation) {
      this.entity = entity;
      this.owner = owner;
      this.generation = generation;
      position = entity.position();
    }
  }

  private enum Release {
    DROP,
    THROW
  }

  private static final class Hold {
    final Entity entity;
    final EntityCarryWorld collisions;
    final long generation;
    final Level level;
    final float yawOffset;
    final int ownerId;
    final UUID owner;
    Vec3 releaseVelocity;
    int age;
    long sequence;
    long appliedSequence;
    Release release;
    long simulationTick;
    long movementTick;

    Hold(Entity entity, ServerPlayer player, long generation, List<PortalLink> route) {
      this.generation = generation;
      this.simulationTick = entity.level().getGameTime();
      this.movementTick = simulationTick;
      this.ownerId = player.getId();
      this.owner = player.getUUID();
      this.entity = entity;
      this.collisions = new EntityCarryWorld(entity);
      this.level = entity.level();
      this.releaseVelocity = entity.getDeltaMovement();
      Vec3 look = CarryAnchor.routeLookDirection(player, route);
      this.yawOffset = entity.getYRot() - (float) Math.toDegrees(Math.atan2(-look.x, look.z));
    }
  }
}
