package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.api.CarryPermission;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.carry.CarryAnchor;
import dev.portalix.carry.CarryBindings;
import dev.portalix.mixin.traversal.EntityCollisionAccessor;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixVariant;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.server.portal.PortalClosureResolver;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.GunStateBroadcaster;
import dev.portalix.server.carry.CarryController;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class CarryRegression implements ServerRegressionSuite.Scenario {
  private static final ScopedValue<Entity> THROW_VETO = ScopedValue.newInstance();

  static {
    CarryPermission.EVENT.register((player, entity, operation, point) ->
        operation != CarryPermission.Operation.THROW || !THROW_VETO.isBound() || entity != THROW_VETO.get());
  }

  private final RegressionWorld world;
  private CarryController controller;
  private Entity target;
  private PortalEndpoint entrance;
  private PortalEndpoint exit;
  private int actorIndex;
  private int stage;
  private int preparedAt;
  private int previousTick;

  CarryRegression(RegressionWorld world) { this.world = world; }

  static void run(RegressionWorld world) {
    world.clear(new Vec3(160, 105, 160), 5);
    world.player.snapTo(160.5, 104, 160.5, 0, 0);
    var controller = new CarryController(new GunStateBroadcaster(player ->
        new GunStatePayload(player.getId(), player.getUUID(), -1,
            GunStatePayload.EMPTY_TARGET, 0, 0, false)), holder -> true);
    Vec3 center = world.player.getEyePosition().add(0, 0, 1.5);
    var target = world.item(center);
    UUID owner = world.player.getUUID();
    try {
      for (var variant : List.of(PortalixVariant.POTATO, PortalixVariant.ORANGE, PortalixVariant.BLUE)) {
        world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(variant)));
        controller.toggleGrab(world.player);
        check(controller.targetId(owner) == target.getId()
            && controller.targetUuid(owner).equals(target.getUUID()),
            variant + " did not grab the object in front of the player");
        controller.clear();
      }

      var standard = new ItemStack(ModItems.gun(PortalixVariant.STANDARD));
      world.player.setItemSlot(EquipmentSlot.MAINHAND, standard);
      controller.toggleGrab(world.player);
      check(controller.targetId(owner) == target.getId()
          && controller.targetUuid(owner).equals(target.getUUID())
          && CarryBindings.holderId(target) == world.player.getId()
          && CarryBindings.heldEntityId(world.level, world.player.getId()) == target.getId(),
          "Grab did not establish both ownership directions");
      var anchor = CarryAnchor.routeHoldPosition(world.player, List.of(), target.getBoundingBox());
      target.setPos(anchor.subtract(0, target.getBbHeight() / 2, 0));
      var movements = ((EntityCollisionAccessor) target).portalix$movementRecords();
      int before = movements.size();
      controller.tick(world.level.getServer());
      check(movements.size() == before, "A stationary hold recorded a native movement");
      check(controller.requestRelease(world.player, false), "Drop was not queued");
      controller.tick(world.level.getServer());
      check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1,
          "Drop did not release the object");
      near(target.getDeltaMovement(), Vec3.ZERO, 1e-8, "A stationary drop gained momentum");

      target.setPos(center.subtract(0, target.getBbHeight() / 2, 0));
      controller.toggleGrab(world.player);
      check(controller.isHolding(owner), "Object could not be grabbed before changing guns");
      var recolored = new ItemStack(ModItems.gun(PortalixVariant.BLUE));
      recolored.set(PortalixIdentity.ID.get(), PortalixIdentity.get(standard));
      world.player.setItemSlot(EquipmentSlot.MAINHAND, recolored);
      controller.tick(world.level.getServer());
      check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1,
          "Equipping another Portalix retained the previous gun's hold");
      world.player.setItemSlot(EquipmentSlot.MAINHAND, standard);

      controller.toggleGrab(world.player);
      check(controller.isHolding(owner), "Object could not be grabbed for a throw");
      check(controller.requestRelease(world.player, true), "Throw was not queued");
      controller.tick(world.level.getServer());
      check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1,
          "Throw retained the ownership binding");
      check(target.getDeltaMovement().z > 1 && Math.abs(target.getDeltaMovement().x) < 1e-8,
          "Throw impulse does not follow the holder's view");

      target.setDeltaMovement(Vec3.ZERO);
      world.player.setGameMode(GameType.ADVENTURE);
      controller.toggleGrab(world.player);
      check(!controller.isHolding(owner), "Adventure player grabbed an entity");
      world.player.setGameMode(GameType.CREATIVE);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
      controller.toggleGrab(world.player);
      check(!controller.isHolding(owner), "Player grabbed an entity without a portalix");
    } finally {
      controller.clear();
      world.reset();
    }
    animals(world);
    slowedHold(world);
  }

  @Override
  public boolean tick() {
    int now = world.level.getServer().getTickCount();
    if (target == null) {
      preparePortal();
      preparedAt = previousTick = now;
      stage = 0;
      return false;
    }
    check(now > previousTick, "Carry scenario did not advance the server clock");
    previousTick = now;
    controller.tick(world.level.getServer());
    String scenario = actorIndex == 0 ? "item" : "animal";
    UUID owner = world.player.getUUID();
    Vec3 destination = exit.center().add(.75, 0, 0);
    if (stage == 0) {
      check(controller.isHolding(owner), scenario + " server carry simulation lost ownership");
      if (now - preparedAt < 24) return false;
      near(target.getBoundingBox().getCenter(), destination, 1e-6,
          scenario + " server carry simulation did not follow the holder through the portal");
      near(target.getDeltaMovement(), Vec3.ZERO, 1e-8,
          scenario + " native held tick moved the target after its portal crossing");
      world.player.snapTo(entrance.center().x, entrance.center().y - world.player.getEyeHeight(),
          entrance.center().z - 1.125, 0, 0);
      world.player.setOldPosAndRot();
      stage = 1;
      return false;
    }
    Vec3 followed = destination.add(.25, 0, 0);
    if (stage == 1) {
      check(controller.isHolding(owner) && controller.targetUuid(owner).equals(target.getUUID())
          && CarryBindings.holderId(target) == world.player.getId()
          && CarryBindings.heldEntityId(world.level, world.player.getId()) == target.getId(),
          scenario + " movement beyond the portal lost ownership or its remote route");
      near(target.getBoundingBox().getCenter(), followed, 1e-6,
          scenario + " subsequent server carry simulation did not follow the holder");
      PortalClosureResolver.updateLinks(world.level, List.of());
      stage = 2;
      return false;
    }
    check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1
        && CarryBindings.heldEntityId(world.level, world.player.getId()) == -1,
        scenario + " retired portal route retained a hold or either ownership binding");
    near(target.getBoundingBox().getCenter(), followed, 1e-6,
        scenario + " releasing an obsolete portal route teleported the target");
    near(target.getDeltaMovement(), new Vec3(.25, 0, 0), 1e-6,
        scenario + " route invalidation lost the last server-simulated movement on release");
    check(world.level.getEntity(target.getId()) == target && target.isAlive()
        && world.level.noCollision(target, target.getBoundingBox().deflate(1e-6)),
        scenario + " route invalidation removed the native target or left it inside the backing wall");
    controller.clear();
    controller = null;
    target = null;
    world.reset();
    return ++actorIndex == 2;
  }

  private void preparePortal() {
    entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
    exit = world.endpoint(Direction.EAST, new Vec3(192, 104, 192));
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    PlayerPortalVisibility.of(world.player).ready(link);
    PlayerPortalVisibility.of(world.player).ready(link.inverse());
    world.player.snapTo(entrance.center().x, entrance.center().y - world.player.getEyeHeight(),
        entrance.center().z - 2.25, 0, 0);
    world.player.setDeltaMovement(Vec3.ZERO);
    world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(PortalixVariant.STANDARD)));
    Vec3 start = entrance.center().add(0, 0, -.75);
    if (actorIndex == 1) {
      var pig = TestEntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
      check(pig != null, "Portal carry animal factory failed");
      world.add(pig);
      pig.setNoAi(true);
      pig.setPos(start.subtract(0, pig.getBbHeight() / 2, 0));
      target = pig;
    } else target = world.item(start);
    target.setDeltaMovement(Vec3.ZERO);
    target.setYRot(37);
    target.setXRot(0);
    target.setOldPosAndRot();
    controller = new CarryController(new GunStateBroadcaster(player ->
        new GunStatePayload(player.getId(), player.getUUID(), -1,
            GunStatePayload.EMPTY_TARGET, 0, 0, false)), holder -> true);
    UUID owner = world.player.getUUID();
    controller.toggleGrab(world.player);
    check(controller.targetId(owner) == target.getId() && controller.targetUuid(owner).equals(target.getUUID()),
        "Portal fixture did not establish a local grab");
    near(target.getBoundingBox().getCenter(), start, 1e-6,
        "Grabbing moved the target before a server simulation step");
    world.player.snapTo(entrance.center().x, entrance.center().y - world.player.getEyeHeight(),
        entrance.center().z - 1.375, 0, 0);
    world.player.setOldPosAndRot();
  }

  @Override
  public void close() {
    if (controller != null) controller.clear();
  }

  private static void slowedHold(RegressionWorld world) {
    var player = world.player;
    world.clear(new Vec3(160, 105, 160), 5);
    player.snapTo(160.5, 104, 160.5, 0, 0);
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(PortalixVariant.STANDARD)));
    var pig = TestEntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
    check(pig != null, "Could not create the held mob");
    world.add(pig);
    pig.setPos(player.getEyePosition().add(0, -pig.getBbHeight() / 2, 1.5));
    var controller = new CarryController(new GunStateBroadcaster(owner ->
        new GunStatePayload(owner.getId(), owner.getUUID(), -1, GunStatePayload.EMPTY_TARGET, 0, 0, false)), holder -> true);
    try {
      controller.toggleGrab(player);
      check(controller.targetId(player.getUUID()) == pig.getId(), "Could not grab the mob before entering the web");
      var anchor = CarryAnchor.routeHoldPosition(player, List.of(), pig.getBoundingBox());
      pig.setPos(anchor.subtract(0, pig.getBbHeight() / 2, 0));
      controller.tick(world.level.getServer());
      world.block(BlockPos.containing(anchor), Blocks.COBWEB.defaultBlockState());
      world.level.tickNonPassenger(pig);
      Vec3 before = pig.position();
      player.setPos(player.position().add(.4, 0, 0));
      player.setOldPosAndRot();
      ScopedValue.where(THROW_VETO, (Entity) pig).run(() -> {
        controller.requestRelease(player, true);
        controller.tick(world.level.getServer());
      });
      Vec3 actual = pig.position().subtract(before);
      check(controller.targetId(player.getUUID()) == pig.getId(), "A rejected throw or a shortened step released the mob");
      near(actual, new Vec3(.1, 0, 0), 1e-6, "The vetoed throw must still advance the hold at cobweb speed");
      controller.requestRelease(player, false);
      controller.tick(world.level.getServer());
      near(pig.getDeltaMovement(), actual, 1e-6, "Dropping a slowed mob used its planned full-speed step");
    } finally {
      controller.clear();
      world.reset();
    }
  }

  private static void animals(RegressionWorld world) {
    for (boolean noAi : new boolean[] {false, true}) {
      world.clear(new Vec3(160, 105, 160), 5);
      world.player.snapTo(160.5, 104, 160.5, 0, 0);
      world.player.setDeltaMovement(Vec3.ZERO);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(PortalixVariant.STANDARD)));
      Mob animal = noAi ? TestEntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND)
          : TestEntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
      check(animal != null, "Carry animal factory failed");
      world.add(animal);
      animal.setNoGravity(false);
      animal.setNoAi(noAi);
      Vec3 center = world.player.getEyePosition().add(0, 0, 1.5);
      animal.setPos(center.subtract(0, animal.getBbHeight() / 2, 0));
      animal.setDeltaMovement(Vec3.ZERO);
      animal.setLeashedTo(world.player, true);
      var controller = new CarryController(new GunStateBroadcaster(player ->
          new GunStatePayload(player.getId(), player.getUUID(), -1,
              GunStatePayload.EMPTY_TARGET, 0, 0, false)), holder -> true);
      float health = animal.getHealth();
      try {
        controller.toggleGrab(world.player);
        check(controller.targetId(world.player.getUUID()) == animal.getId()
            && CarryBindings.holderId(animal) == world.player.getId(),
            "Eligible native animal was not grabbed");
        Vec3 heldPosition = animal.position();
        world.level.tickNonPassenger(animal);
        near(animal.position(), heldPosition, 1e-6, "Held animal native AI/gravity moved the carry target");
        check(!animal.isNoGravity() && animal.isNoAi() == noAi,
            "Holding an animal permanently changed its native AI/gravity flags");
        check(controller.requestRelease(world.player, false), "Animal drop was not queued");
        controller.tick(world.level.getServer());
        check(!controller.isHolding(world.player.getUUID()) && CarryBindings.holderId(animal) == -1,
            "Animal drop retained carry ownership");
        world.level.tickNonPassenger(animal);
        if (!noAi) check(animal.getDeltaMovement().y < 0, "Dropped animal did not resume native gravity");
        check(animal.isNoAi() == noAi && !animal.isNoGravity()
            && animal.getLeashHolder() == world.player && animal.getHealth() == health,
            "Dropping an animal damaged it or changed its AI, gravity or nearby leash");

        animal.setPos(heldPosition);
        animal.setDeltaMovement(Vec3.ZERO);
        controller.toggleGrab(world.player);
        check(controller.isHolding(world.player.getUUID()), "Animal could not be grabbed again");
        check(controller.requestRelease(world.player, true), "Animal throw was not queued");
        controller.tick(world.level.getServer());
        check(!controller.isHolding(world.player.getUUID()) && animal.getDeltaMovement().z > 1,
            "Native animal throw lost the view-directed impulse or retained its binding");
        world.level.tickNonPassenger(animal);
        if (!noAi) check(animal.position().z > heldPosition.z && animal.getDeltaMovement().y < 0,
            "Thrown animal did not resume native living travel and gravity");
        check(animal.isNoAi() == noAi && !animal.isNoGravity(),
            "Native throw tick changed the animal's AI/gravity flags");
      } finally {
        controller.clear();
        animal.removeLeash();
      }
      world.reset();
    }
    world.clear(new Vec3(160, 105, 160), 5);
    world.player.snapTo(160.5, 104, 160.5, 0, 0);
    world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(PortalixVariant.STANDARD)));
    var vehicle = TestEntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
    var passenger = TestEntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND);
    check(vehicle != null && passenger != null, "Carry passenger fixture factory failed");
    world.add(vehicle);
    world.add(passenger);
    vehicle.setNoAi(true);
    passenger.setNoAi(true);
    vehicle.setPos(world.player.getEyePosition().add(0, -.45, 1.5));
    passenger.setPos(vehicle.position());
    check(passenger.startRiding(vehicle, true, false), "Carry passenger fixture could not mount");
    vehicle.positionRider(passenger);
    var controller = new CarryController(new GunStateBroadcaster(player ->
        new GunStatePayload(player.getId(), player.getUUID(), -1, GunStatePayload.EMPTY_TARGET, 0, 0, false)), holder -> true);
    try {
      controller.toggleGrab(world.player);
      check(!controller.isHolding(world.player.getUUID()) && CarryBindings.holderId(vehicle) == -1
          && CarryBindings.holderId(passenger) == -1 && passenger.getVehicle() == vehicle,
          "Carry detached or grabbed an occupied native passenger group");
    } finally {
      controller.clear();
    }
    world.reset();
  }

}
