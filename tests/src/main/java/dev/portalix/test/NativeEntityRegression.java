package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.PortalItemRotation;
import dev.portalix.portal.traversal.PortalPassengerGroup;
import dev.portalix.test.mixin.RegressionMinecartBehaviorAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

final class NativeEntityRegression {
  private NativeEntityRegression() {}

  static void run(RegressionWorld world) {
    itemPersistence(world);
    world.reset();
    for (boolean passenger : new boolean[] {false, true}) {
      animal(world, passenger);
      world.reset();
    }
    leash(world);
    world.reset();
    for (boolean improved : new boolean[] {false, true}) {
      for (boolean passenger : new boolean[] {false, true}) {
        minecart(world, passenger, improved);
        world.reset();
        minecartAlongWall(world, passenger, improved);
        world.reset();
      }
    }
  }

  private static void minecartAlongWall(RegressionWorld world, boolean mounted, boolean improved) {
    world.player.setPos(152.5, 104, 152.5);
    var source = world.endpoint(Direction.NORTH, new Vec3(144, 104.001, 144));
    var exit = world.endpoint(Direction.SOUTH, new Vec3(192, 104.001, 192));
    world.surface(source, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(source, exit);
    world.publish(link);
    PlayerPortalVisibility.of(world.player).ready(link);
    for (int x = 138; x <= 149; x++) {
      var pos = new BlockPos(x, 103, 143);
      world.block(pos.below(), Blocks.STONE.defaultBlockState());
      world.block(pos, Blocks.RAIL.defaultBlockState()
          .setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST));
    }
    Minecart cart = EntityTypes.MINECART.create(world.level, EntitySpawnReason.COMMAND);
    check(cart != null, "Minecart factory failed");
    if (improved)
      ((RegressionMinecartBehaviorAccessor) cart).portalix$behavior(new NewMinecartBehavior(cart));
    world.add(cart);
    cart.setNoGravity(false);
    cart.setPos(139.5, improved ? 103.1 : 103.0625, 143.5);
    if (mounted) {
      world.player.setPos(cart.position());
      check(world.player.startRiding(cart, true, false), "Player could not board the parallel track");
      PortalPassengerGroup.positionPassengers(cart);
    }
    cart.setDeltaMovement(.4, 0, 0);
    for (int tick = 0; tick < 20; tick++) {
      if (improved) {
        cart.setOnRails(true);
        cart.getBehavior().stepAlongTrack(cart.getCurrentBlockPosOrRailBelow(), RailShape.EAST_WEST, .4);
        PortalPassengerGroup.positionPassengers(cart);
      } else world.level.tickNonPassenger(cart);
      check(EntityPortalState.of(cart).lastCrossing() == null,
          "A cart travelling along the wall crossed its side portal");
      check(PortalEntityCollisions.intersectingPortal(cart) == null,
          "A side portal took over collision handling across the rail-to-wall gap");
    }
    check(cart.getX() > 145 && Math.abs(cart.getZ() - 143.5) < 1e-7,
        "The side portal stopped or deflected the rail cart, improved=" + improved
            + ", mounted=" + mounted + ": " + cart.position());
    if (mounted) {
      check(world.player.getVehicle() == cart, "Passing a side portal detached the passenger");
      world.player.stopRiding();
    }
  }

  private static void minecart(RegressionWorld world, boolean mounted, boolean improved) {
    var source = world.endpoint(Direction.NORTH, new Vec3(144, 104.001, 144));
    var destination = world.endpoint(Direction.SOUTH, new Vec3(192, 104.001, 192));
    world.surface(source, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(destination, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(source, destination);
    world.publish(link);
    PlayerPortalVisibility.of(world.player).ready(link);
    PlayerPortalVisibility.of(world.player).ready(link.inverse());
    for (var portal : List.of(link.entrance().endpoint(), link.exit().endpoint())) {
      for (int distance = 0; distance < 8; distance++) {
        Vec3 rail = portal.center().add(portal.normal().scale(distance + .5));
        BlockPos pos = BlockPos.containing(rail.x, portal.center().y - 1, rail.z);
        world.block(pos.below(), Blocks.STONE.defaultBlockState());
        world.block(pos, Blocks.RAIL.defaultBlockState()
            .setValue(BlockStateProperties.RAIL_SHAPE, RailShape.NORTH_SOUTH));
      }
    }
    Minecart cart = EntityTypes.MINECART.create(world.level, EntitySpawnReason.COMMAND);
    check(cart != null, "Minecart factory failed");
    if (improved)
      ((RegressionMinecartBehaviorAccessor) cart).portalix$behavior(new NewMinecartBehavior(cart));
    world.add(cart);
    cart.setNoGravity(false);
    var entrance = link.entrance().endpoint();
    cart.setPos(new Vec3(entrance.center().x, improved ? 103.1 : 103.0625, entrance.center().z - 3));
    if (mounted) {
      world.player.setPos(cart.position());
      check(world.player.startRiding(cart, true, false), "Player could not mount the rail cart");
      PortalPassengerGroup.positionPassengers(cart);
    }
    cart.setDeltaMovement(0, 0, .4);
    for (int tick = 0; tick < 40 && EntityPortalState.of(cart).lastCrossing() == null; tick++) {
      if (improved) {
        cart.setOnRails(true);
        cart.getBehavior().stepAlongTrack(cart.getCurrentBlockPosOrRailBelow(), RailShape.NORTH_SOUTH, .4);
        PortalPassengerGroup.positionPassengers(cart);
      } else world.level.tickNonPassenger(cart);
    }
    check(EntityPortalState.of(cart).lastCrossing() != null,
        "Rail minecart did not cross, improved=" + improved + ", mounted=" + mounted + ", cart=" + cart.getBoundingBox()
            + ", player=" + world.player.getBoundingBox() + ", motion=" + cart.getDeltaMovement());
    check(Math.abs(cart.getDeltaMovement().x) < 1e-7
        && cart.getDeltaMovement().z > 0 && cart.getDeltaMovement().z <= .4 + 1e-7,
        "Rail crossing changed the cart's track direction or speed: " + cart.getDeltaMovement());
    if (mounted) {
      check(world.player.getVehicle() == cart, "Rail crossing detached the player");
      check(EntityPortalState.of(world.player).lastCrossing() != null
          && EntityPortalState.of(world.player).lastCrossing().link().equals(link),
          "Rail passenger did not receive its crossing");
      world.player.stopRiding();
    }
  }

  private static PortalLink portals(RegressionWorld world, Direction face) {
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
    var exit = world.endpoint(face, new Vec3(192, 104, 192));
    world.clear(exit.center().add(exit.normal().scale(4)), 5);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    return link;
  }

  private static void itemPersistence(RegressionWorld world) {
    var link = portals(world, Direction.EAST);
    var entrance = link.entrance().endpoint();
    var item = world.item(entrance.center().add(entrance.normal().scale(.8)));
    item.getItem().setCount(23);
    item.setNoGravity(false);
    item.setDeltaMovement(entrance.normal().scale(-1));
    UUID identity = item.getUUID();
    for (int tick = 0; tick < 6 && EntityPortalState.of(item).lastCrossing() == null; tick++)
      world.level.tickNonPassenger(item);
    check(EntityPortalState.of(item).lastCrossing() != null
        && item.position().distanceToSqr(link.exit().endpoint().center()) < 9,
        "The saved item did not travel through the portal");
    var problems = new ProblemReporter.Collector();
    var saved = TagValueOutput.createWithContext(problems, world.level.registryAccess());
    item.saveWithoutId(saved);
    var restored = new ItemEntity(world.level, 0, 0, 0, ItemStack.EMPTY);
    restored.load(TagValueInput.create(problems, world.level.registryAccess(), saved.buildResult()));
    check(problems.isEmpty(), "Saving and loading a travelled item reported errors: " + problems.getReport());
    check(restored.getUUID().equals(identity) && ItemStack.matches(restored.getItem(), item.getItem()),
        "Saving and loading a travelled item lost its identity or stack");
    near(restored.position(), item.position(), 1e-8, "Saving and loading a travelled item changed its location");
    check(Math.abs(PortalItemRotation.rotation(restored).dot(PortalItemRotation.rotation(item))) > .99999,
        "Saving and loading a travelled item changed its orientation");
  }

  private static void animal(RegressionWorld world, boolean withPassenger) {
    PortalLink link = portals(world, Direction.EAST);
    Mob animal = withPassenger ? EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND)
        : EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND);
    check(animal != null, "Animal factory failed");
    world.add(animal);
    animal.setNoAi(false);
    animal.setPos(link.entrance().endpoint().center().add(0, -.45, -.7));
    animal.setDeltaMovement(0, 0, 1.6);
    Mob passenger = null;
    if (withPassenger) {
      passenger = EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND);
      check(passenger != null, "Passenger factory failed");
      world.add(passenger);
      passenger.setNoAi(true);
      passenger.setPos(animal.position());
      check(passenger.startRiding(animal, true, false), "Native animal passenger could not mount");
      animal.positionRider(passenger);
      double offset = link.entrance().endpoint().center().y - PortalPassengerGroup.bounds(animal).getCenter().y;
      animal.setPos(animal.position().add(0, offset, 0));
      animal.positionRider(passenger);
    }
    UUID identity = animal.getUUID();
    float health = animal.getHealth();
    for (int tick = 0; tick < 6 && EntityPortalState.of(animal).lastCrossing() == null; tick++)
      world.level.tickNonPassenger(animal);
    check(EntityPortalState.of(animal).lastCrossing() != null,
        "Native " + animal.getType() + " movement did not traverse" + (withPassenger ? " with passenger" : "")
            + ": position=" + animal.position() + ", velocity=" + animal.getDeltaMovement()
            + ", hull=" + PortalPassengerGroup.bounds(animal));
    check(world.level.getEntity(identity) == animal && animal.isAlive() && animal.getHealth() == health
        && !animal.isNoAi() && animal.isNoGravity(), "Traversal changed the animal's native identity, health or flags");
    check(animal.getDeltaMovement().x > 0 && Math.abs(animal.getDeltaMovement().z) < .15,
        "Native living travel lost the transformed exit-side momentum");
    if (passenger != null) {
      check(passenger.getVehicle() == animal && animal.getPassengers().contains(passenger)
          && passenger.position().distanceToSqr(animal.position()) < 4,
          "Native riding tick detached the animal passenger or left it at the entrance");
      check(EntityPortalState.of(passenger).lastCrossing() != null,
          "Native passenger traversal did not publish its crossing");
    }
    world.level.tickNonPassenger(animal);
    check(world.level.noCollision(animal, animal.getBoundingBox().deflate(1e-6)),
        "Native living travel embedded the animal in the exit wall");
    if (passenger != null) {
      passenger.discard();
      animal.discard();
    }
  }

  private static void leash(RegressionWorld world) {
    PortalLink link = portals(world, Direction.SOUTH);
    var pig = EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
    check(pig != null, "Leash animal factory failed");
    world.add(pig);
    pig.setNoAi(false);
    pig.setPos(link.entrance().endpoint().center().add(0, -.45, -.7));
    world.player.snapTo(pig.getX(), pig.getY(), pig.getZ() - 2, 0, 0);
    Vec3 holderPosition = world.player.position();
    pig.setLeashedTo(world.player, true);
    check(pig.getLeashHolder() == world.player, "Native leash fixture did not bind its holder");
    var existing = new HashSet<UUID>();
    for (Entity entity : world.level.getAllEntities()) existing.add(entity.getUUID());
    try {
      pig.setDeltaMovement(0, 0, 1.6);
      for (int tick = 0; tick < 6 && EntityPortalState.of(pig).lastCrossing() == null; tick++)
        world.level.tickNonPassenger(pig);
      check(EntityPortalState.of(pig).lastCrossing() != null, "Leashed animal did not cross through native travel");
      world.level.tickNonPassenger(pig);
      check(pig.getLeashHolder() == null, "A leash spanning distant portal scenes bypassed native snap distance");
      near(world.player.position(), holderPosition, 1e-8, "Leash traversal moved the unrelated holder");
      check(pig.isAlive(), "Native leash removal discarded the animal");
    } finally {
      var drops = new ArrayList<Entity>();
      for (Entity entity : world.level.getAllEntities())
        if (entity instanceof ItemEntity && !existing.contains(entity.getUUID())) drops.add(entity);
      drops.forEach(Entity::discard);
    }
  }
}
