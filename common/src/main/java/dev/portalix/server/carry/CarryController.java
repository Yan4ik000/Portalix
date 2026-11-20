package dev.portalix.server.carry;

import dev.portalix.PortalixMod;
import dev.portalix.api.CarryPermission;
import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryBindings;
import dev.portalix.carry.CarryFollowMotion;
import dev.portalix.carry.EntityCarryWorld;
import dev.portalix.compat.minecraft.CarriedEntityMotion;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.gun.GunPermissions;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class CarryController {
  private static final double RANGE = 2.0;
  private static final double THROW_SPEED = 1.25;
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
  private final Map<UUID, Hold> heldByEntity = new HashMap<>();
  private long generation;
  private long tick;

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
    if (target == null) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    var level = player.level();
    var topology = PortalTopology.links(level);
    var ownerPosition = player.position();
    var targetPosition = target.position();
    var targetBounds = target.getBoundingBox();
    var gun = player.getMainHandItem();
    int slot = player.getInventory().getSelectedSlot();
    if (!CarryPermission.EVENT.invoker().allow(player, target, CarryPermission.Operation.GRAB, targetPosition)
        || player.level() != level || level.getEntity(player.getId()) != player
        || target.level() != level || level.getEntity(target.getId()) != target
        || !player.position().equals(ownerPosition) || !target.getBoundingBox().equals(targetBounds)
        || PortalTopology.links(level) != topology
        || !GunPermissions.canCarry(player) || player.getMainHandItem() != gun
        || player.getInventory().getSelectedSlot() != slot
        || isHolding(player.getUUID()) || !canCarry(target) || isCarried(target)) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    var references = new ArrayList<PortalLinkId>();
    for (var link : eye.eyePath()) references.add(PortalLinkId.fromLink(link));
    references.addAll(ray.links().stream().map(PortalLinkId::fromLink).toList());
    var route = PortalRoutes.simplifyIds(references);
    var resolved = CarryAnchor.resolveRoute(player, route);
    if (resolved == null) {
      broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
      return;
    }
    flights.remove(target.getUUID());
    Hold hold = new Hold(target, player, ++generation, tick, resolved);
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

  public void checkEquipment(ServerPlayer player) {
    var hold = heldByOwner.get(player.getUUID());
    if (hold != null && !hold.hasEquippedGun(player))
      drop(player.getUUID(), player);
  }

  private boolean holdValid(ServerPlayer player, Hold hold) {
    Entity entity = hold.entity;
    return heldByOwner.get(hold.owner) == hold
        && heldByEntity.get(entity.getUUID()) == hold
        && hold.hasEquippedGun(player)
        && entity.level() == hold.level
        && player.level() == hold.level
        && hold.level.getEntity(entity.getId()) == entity
        && hold.level.getEntity(player.getId()) == player
        && entity.isAlive()
        && !entity.isPassenger()
        && !entity.isVehicle()
        && CarryBindings.holderId(entity) == hold.ownerId;
  }

  public void tick(MinecraftServer server) {
    tick++;
    var iterator = flights.values().iterator();
    while (iterator.hasNext()) {
      Flight flight = iterator.next();
      Entity entity = flight.entity;
      if (entity.isRemoved() || entity.level().getEntity(entity.getId()) != entity) {
        iterator.remove();
        continue;
      }
      boolean complete =
          entity.isPassenger()
              || entity.position().distanceToSqr(flight.position) < 1.0E-10
                  && (entity.onGround() || entity.getDeltaMovement().lengthSqr() < 1.0E-10);
      broadcast.sendCarryState(entity, null, flightSnapshot(flight, complete));
      flight.position = entity.position();
      if (complete) iterator.remove();
    }

    if (heldByOwner.isEmpty()) return;
    for (Hold hold : List.copyOf(heldByOwner.values())) {
      if (heldByOwner.get(hold.owner) != hold) continue;
      ServerPlayer player = server.getPlayerList().getPlayer(hold.owner);
      Entity entity = hold.entity;
      if (!holdValid(player, hold)
          || CarryAnchor.routeDistance(player, entity.getBoundingBox().getCenter(), CarryBindings.route(entity))
              > CarryAnchor.MAX_HOLDER_DISTANCE) {
        finishRelease(hold, player, false);
        continue;
      }
      ++hold.age;
      if (hold.release == Release.THROW) {
        var topology = PortalTopology.links(hold.level);
        var ownerPosition = player.position();
        var targetPosition = entity.position();
        var route = CarryBindings.route(entity);
        boolean allowed = CarryPermission.EVENT.invoker()
            .allow(player, entity, CarryPermission.Operation.THROW, targetPosition);
        if (!holdValid(player, hold)
            || PortalTopology.links(hold.level) != topology
            || !player.position().equals(ownerPosition) || !entity.position().equals(targetPosition)
            || !CarryBindings.route(entity).equals(route)) {
          finishRelease(hold, player, false);
          continue;
        }
        if (!allowed && hold.release == Release.THROW) {
          hold.release = null;
          broadcast.sendEvent(player, GunEventKind.GRAB_FAILED);
        }
      }
      if (hold.release != null) {
        finishRelease(hold, player, hold.release == Release.THROW);
        continue;
      }
      var route = CarryBindings.resolve(entity);
      if (route == null) {
        finishRelease(hold, player, false);
        continue;
      }
      var hull = entity.getBoundingBox();
      Vec3 desired = CarryAnchor.routeHoldPosition(player, route, hull);
      var movement = hold.motion.step(hold.collisions, hull.getCenter(),
          desired, GunFireTiming.TICK_SECONDS);
      var completed = CarryMovement.apply(player, entity, movement,
          () -> hold.release == null && holdValid(player, hold));
      if (heldByOwner.get(hold.owner) != hold) continue;
      route = hold.release == null && holdValid(player, hold) ? CarryBindings.resolve(entity) : null;
      if (completed == null || route == null) {
        hold.releaseVelocity = Vec3.ZERO;
        finishRelease(hold, player, false);
        continue;
      }
      hold.releaseVelocity = completed.movement(1);
      if (completed != movement)
        hold.motion.reset(hold.releaseVelocity.scale(1 / GunFireTiming.TICK_SECONDS));
      Vec3 look = CarryAnchor.routeLookDirection(player, route);
      float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)) + hold.yawOffset;
      CarriedEntityMotion.hold(entity, yaw);
      hold.simulationTick = tick;
      broadcast.sendCarryState(
          entity,
          player,
          new CarryStatePayload(
              entity.getId(),
              entity.getUUID(),
              hold.generation,
              hold.simulationTick,
              entity.position(),
              hold.releaseVelocity,
              false,
              false));
    }
  }

  public void drop(UUID id, ServerPlayer player) {
    Hold hold = heldByOwner.get(id);
    if (hold != null) finishRelease(hold, player, false);
  }

  private void finishRelease(Hold hold, ServerPlayer player, boolean launch) {
    if (!heldByOwner.remove(hold.owner, hold)) return;
    Entity entity = hold.entity;
    if (!hold.hasEquippedGun(player) || player.level() != hold.level || entity.level() != hold.level) {
      launch = false;
      hold.releaseVelocity = Vec3.ZERO;
    } else if (tick > hold.simulationTick + 1) hold.releaseVelocity = Vec3.ZERO;
    this.heldByEntity.remove(entity.getUUID(), hold);
    var releaseRoute = CarryBindings.resolve(entity);
    launch &= releaseRoute != null;
    CarryBindings.unbindOwner(hold.level, hold.ownerId);
    CarriedEntityMotion.release(entity, MIN_RELEASE_PICKUP_DELAY_TICKS);

    entity.setDeltaMovement(
        launch
            ? CarryAnchor.routeLookDirection(player, releaseRoute)
                .scale(THROW_SPEED)
                .add(hold.releaseVelocity)
            : hold.releaseVelocity);
    entity.syncVelocity = true;
    flights.put(entity.getUUID(), new Flight(entity, hold.generation));
    broadcast.sendRelease(
        entity, player, launch, hold.generation, tick);
    if (player != null) {
      broadcast.sendEvent(player, launch ? GunEventKind.THROWN : GunEventKind.RELEASED);
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
      drop(owner, level.getServer().getPlayerList().getPlayer(owner));
  }

  public void clear() {
    for (UUID id : new ArrayList<>(this.heldByOwner.keySet())) {
      drop(id, null);
    }
    this.heldByEntity.clear();
    this.flights.clear();
  }

  public void trackingStarted(Entity entity, ServerPlayer observer) {
    Hold hold = heldByEntity.get(entity.getUUID());
    if (hold != null && hold.entity == entity) {
      synchronizeHold(hold, observer);
      return;
    }
    Flight flight = flights.get(entity.getUUID());
    if (flight != null && flight.entity == entity
        && ModNetworking.canPlayerReceive(observer, CarryStatePayload.TYPE))
      PortalCrossingSync.sendAfterCrossing(entity, observer, flightSnapshot(flight, false));
    Hold owned = heldByOwner.get(entity.getUUID());
    if (owned != null && owned.ownerId == entity.getId() && owned.level == entity.level()
        && EntityObservers.tracking(owned.entity).contains(observer))
      synchronizeHold(owned, observer);
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

  private CarryStatePayload flightSnapshot(Flight flight, boolean complete) {
    Entity entity = flight.entity;
    return new CarryStatePayload(
        entity.getId(),
        entity.getUUID(),
        flight.generation,
        tick,
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
        hold.ownerId,
        hold.owner,
        hold.simulationTick,
        hold.entity.position(),
        hold.releaseVelocity,
        CarryBindings.route(hold.entity));
  }

  private static final class Flight {
    final Entity entity;
    final long generation;
    Vec3 position;

    Flight(Entity entity, long generation) {
      this.entity = entity;
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
    final CarryFollowMotion motion;
    final long generation;
    final Level level;
    final float yawOffset;
    final int ownerId;
    final UUID owner;
    final UUID gun;
    final Item gunItem;
    final int gunSlot;
    Vec3 releaseVelocity;
    int age;
    Release release;
    long simulationTick;

    Hold(Entity entity, ServerPlayer player, long generation, long tick, List<PortalLink> route) {
      this.generation = generation;
      this.simulationTick = tick;
      this.ownerId = player.getId();
      this.owner = player.getUUID();
      var stack = player.getMainHandItem();
      this.gun = PortalixIdentity.ensure(stack);
      this.gunItem = stack.getItem();
      this.gunSlot = player.getInventory().getSelectedSlot();
      this.entity = entity;
      this.collisions = new EntityCarryWorld(entity);
      this.motion = new CarryFollowMotion(
          entity.getDeltaMovement().scale(1 / GunFireTiming.TICK_SECONDS));
      this.level = entity.level();
      this.releaseVelocity = entity.getDeltaMovement();
      Vec3 look = CarryAnchor.routeLookDirection(player, route);
      this.yawOffset = entity.getYRot() - (float) Math.toDegrees(Math.atan2(-look.x, look.z));
    }

    boolean hasEquippedGun(ServerPlayer player) {
      return GunPermissions.canCarry(player)
          && player.getId() == ownerId
          && player.getInventory().getSelectedSlot() == gunSlot
          && player.getMainHandItem().is(gunItem)
          && gun.equals(PortalixIdentity.get(player.getMainHandItem()));
    }
  }
}
