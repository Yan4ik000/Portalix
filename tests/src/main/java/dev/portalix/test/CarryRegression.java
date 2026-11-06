package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.carry.CarryBindings;
import dev.portalix.item.ModItems;
import dev.portalix.network.carry.CarryInputPayload;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.GunStateBroadcaster;
import dev.portalix.server.carry.CarryController;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class CarryRegression {
  private CarryRegression() {}

  static void run(RegressionWorld world) {
    for (Item gun : new Item[] {ModItems.PORTALIX.get(), ModItems.POTATO_PORTALIX.get(),
        ModItems.ORANGE_PORTALIX.get(), ModItems.BLUE_PORTALIX.get()}) {
      world.clear(new Vec3(160, 105, 160), 5);
      world.player.snapTo(160.5, 104, 160.5, 0, 0);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(gun));
      var controller = new CarryController(new GunStateBroadcaster(player ->
          new GunStatePayload(player.getId(), player.getUUID(), -1,
              GunStatePayload.EMPTY_TARGET, 0, 0, false)));
      Vec3 center = world.player.getEyePosition().add(0, 0, 1.5);
      var target = world.item(center);
      UUID owner = world.player.getUUID();
      try {
        controller.toggleGrab(world.player);
        check(controller.targetId(owner) == target.getId()
            && controller.targetUuid(owner).equals(target.getUUID()),
            gun + " did not grab the object in front of the player");
        check(CarryBindings.holderId(target) == world.player.getId()
            && CarryBindings.heldEntityId(world.level, world.player.getId()) == target.getId(),
            "Grab did not establish both ownership directions");
        check(controller.requestRelease(world.player, false), "Drop was not queued");
        controller.tick(world.level.getServer());
        check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1,
            "Drop did not release the object");
        near(target.getDeltaMovement(), Vec3.ZERO, 1e-8, "A stationary drop gained momentum");

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
      }
      world.reset();
    }
    animals(world);
    portalPaths(world);
  }

  private static void portalPaths(RegressionWorld world) {
    for (boolean animal : new boolean[] {false, true}) {
      var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
      var exit = world.endpoint(Direction.EAST, new Vec3(192, 104, 192));
      world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      var link = world.link(entrance, exit);
      world.publish(link);
      PlayerPortalVisibility.of(world.player).ready(link);
      PlayerPortalVisibility.of(world.player).ready(link.inverse());
      world.player.snapTo(entrance.center().x, entrance.center().y - world.player.getEyeHeight(),
          entrance.center().z - 2.25, 0, 0);
      world.player.setDeltaMovement(Vec3.ZERO);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORTALIX.get()));
      Vec3 start = entrance.center().add(0, 0, -.75);
      Entity target;
      if (animal) {
        var pig = EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
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
      var controller = new CarryController(new GunStateBroadcaster(player ->
          new GunStatePayload(player.getId(), player.getUUID(), -1,
              GunStatePayload.EMPTY_TARGET, 0, 0, false)));
      String scenario = animal ? "animal" : "item";
      UUID owner = world.player.getUUID();
      try {
        controller.toggleGrab(world.player);
        check(controller.targetId(owner) == target.getId() && controller.targetUuid(owner).equals(target.getUUID()), scenario + " portal fixture did not establish a local grab");
        Vec3 destination = exit.center().add(.75, 0, 0);
        controller.receiveInput(world.player, new CarryInputPayload(target.getId(), target.getUUID(), 1, 1,
            List.of(new CarryInputPayload.Node(start, null),
                new CarryInputPayload.Node(entrance.center(), null),
                new CarryInputPayload.Node(exit.center(), PortalLinkId.fromLink(link)),
                new CarryInputPayload.Node(destination, null))));
        check(controller.isHolding(owner), scenario + " legitimate carry path through the portal was rejected");
        near(target.getBoundingBox().getCenter(), destination, 1e-6,
            scenario + " accepted portal carry path did not reach the authored exit position");
        controller.tick(world.level.getServer());
        world.level.tickNonPassenger(target);
        near(target.getBoundingBox().getCenter(), destination, 1e-6,
            scenario + " native held tick moved the target after its portal crossing");
        near(target.getDeltaMovement(), Vec3.ZERO, 1e-8,
            scenario + " accepted carry movement leaked free-flight velocity");
        Vec3 followed = destination.add(.25, 0, 0);
        controller.receiveInput(world.player, new CarryInputPayload(target.getId(), target.getUUID(), 1, 2,
            List.of(new CarryInputPayload.Node(destination, null), new CarryInputPayload.Node(followed, null))));
        check(controller.isHolding(owner) && controller.targetUuid(owner).equals(target.getUUID())
            && CarryBindings.holderId(target) == world.player.getId()
            && CarryBindings.heldEntityId(world.level, world.player.getId()) == target.getId(),
            scenario + " movement beyond the portal lost ownership or its remote route");
        near(target.getBoundingBox().getCenter(), followed, 1e-6,
            scenario + " subsequent accepted remote-space carry input did not move the native entity");
        PortalTopology.updateLinks(world.level, List.of());
        controller.tick(world.level.getServer());
        check(!controller.isHolding(owner) && CarryBindings.holderId(target) == -1
            && CarryBindings.heldEntityId(world.level, world.player.getId()) == -1,
            scenario + " retired portal route retained a hold or either ownership binding");
        near(target.getBoundingBox().getCenter(), followed, 1e-6,
            scenario + " releasing an obsolete portal route teleported the target");
        near(target.getDeltaMovement(), new Vec3(.25, 0, 0), 1e-6,
            scenario + " route invalidation lost the last accepted movement on release");
        check(world.level.getEntity(target.getId()) == target && target.isAlive()
            && world.level.noCollision(target, target.getBoundingBox().deflate(1e-6)),
            scenario + " route invalidation removed the native target or left it inside the backing wall");
      } finally {
        controller.clear();
        world.reset();
      }
    }
  }

  private static void animals(RegressionWorld world) {
    for (boolean noAi : new boolean[] {false, true}) {
      world.clear(new Vec3(160, 105, 160), 5);
      world.player.snapTo(160.5, 104, 160.5, 0, 0);
      world.player.setDeltaMovement(Vec3.ZERO);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORTALIX.get()));
      Mob animal = noAi ? EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND)
          : EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
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
              GunStatePayload.EMPTY_TARGET, 0, 0, false)));
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
    world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORTALIX.get()));
    var vehicle = EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
    var passenger = EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND);
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
        new GunStatePayload(player.getId(), player.getUUID(), -1, GunStatePayload.EMPTY_TARGET, 0, 0, false)));
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
