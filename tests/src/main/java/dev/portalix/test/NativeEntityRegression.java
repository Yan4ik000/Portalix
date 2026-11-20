package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.network.portal.EntityPortalCrossingPayload;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalItemRotation;
import dev.portalix.portal.traversal.PortalPassengerGroup;
import dev.portalix.test.mixin.RegressionMinecartBehaviorAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class NativeEntityRegression {
  private NativeEntityRegression() {}

  static void run(RegressionWorld world) {
    itemPersistence(world);
    world.reset();
    boatReplayWithLaggingPassenger(world);
    world.reset();
    nestedPassengerGeometry(world);
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
    externalBoatConvoy(world);
  }

  private static void externalBoatConvoy(RegressionWorld world) {
    var entrance = new PortalEndpoint(world.level.dimension(), new Vec3(144.5, 104, 144.5),
        new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1, 2);
    var exit = new PortalEndpoint(world.level.dimension(), new Vec3(192.5, 104, 144.5),
        new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1, 2);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    PlayerPortalVisibility.of(world.player).ready(link);
    var boat = world.add(EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND));
    var passenger = world.add(EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND));
    boat.setPos(entrance.center().add(0, .1 - boat.getBbHeight() / 2, 0));
    check(world.player.startRiding(boat, true, false) && passenger.startRiding(boat, true, false)
            && boat.getControllingPassenger() == world.player, "Could not seat both boat passengers");
    PortalPassengerGroup.positionPassengers(boat);
    boat.move(MoverType.PISTON, new Vec3(0, -.5, 0));
    var group = List.<Entity>of(boat, world.player, passenger);
    for (var member : group) {
      var crossing = EntityPortalState.of(member).lastCrossing();
      check(crossing != null && crossing.link().equals(link), "Boat convoy member missed the piston crossing");
    }
    world.channel.flushOutbound();
    world.channel.runPendingTasks();
    int corrections = 0;
    for (var packet : world.channel.outboundMessages()) {
      if (!(packet instanceof ClientboundBundlePacket bundle)) continue;
      var crossings = new ArrayList<EntityPortalCrossingPayload>();
      boolean corrected = false;
      for (var child : bundle.subPackets()) {
        if (child instanceof ClientboundCustomPayloadPacket custom
            && custom.payload() instanceof EntityPortalCrossingPayload crossing) crossings.add(crossing);
        if (child instanceof ClientboundPlayerPositionPacket) corrected = true;
      }
      if (crossings.isEmpty() || crossings.getFirst().entityId() != boat.getId()) continue;
      check(corrected && crossings.stream().map(EntityPortalCrossingPayload::entityUuid).toList()
              .equals(group.stream().map(Entity::getUUID).toList()),
          "The driver's position correction did not carry the whole convoy's ordered crossing events");
      for (int i = 0; i < group.size(); i++)
        check(crossings.get(i).crossings().equals(List.of(EntityPortalState.of(group.get(i)).lastCrossing())),
            "The external boat correction included crossings from another movement");
      corrections++;
    }
    check(corrections == 1, "Expected one bundled position correction for the external boat crossing");
    world.player.stopRiding();
  }

  private static void boatReplayWithLaggingPassenger(RegressionWorld world) {
    var source = new PortalEndpoint(world.level.dimension(), new Vec3(144.5, 104, 144.5),
        new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1, 2);
    var exit = new PortalEndpoint(world.level.dimension(), new Vec3(192.5, 104, 144.5),
        new Vec3(0, 1, 0), new Vec3(0, 0, 1), 1, 2);
    world.surface(source, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    for (var pos : BlockPos.betweenClosed(new BlockPos(189, 103, 141), new BlockPos(195, 103, 148)))
      world.block(pos, Blocks.STONE.defaultBlockState());
    var link = world.link(source, exit);
    world.publish(link);
    PlayerPortalVisibility.of(world.player).ready(link);
    PlayerPortalVisibility.of(world.player).ready(link.inverse());
    var boat = EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND);
    check(boat != null, "Boat factory failed");
    world.add(boat);
    Vec3 start = new Vec3(192.5, 104.43884155832265, 145.68968187003136);
    Vec3 movement = new Vec3(0, -.36, .24503177745027643);
    boat.setPos(start.add(0, .32, 0));
    world.player.setPos(boat.position());
    check(world.player.startRiding(boat, true, false), "Player could not board the replay boat");
    PortalPassengerGroup.positionPassengers(boat);
    Vec3 previousPassengerFeet = world.player.position();
    boat.setPos(start);
    var laggingHulls = PortalPassengerGroup.hulls(boat);
    var laggingBounds = PortalPassengerGroup.bounds(boat);
    var arrival = PortalPassengerGroup.arrival(boat, link.inverse(), source.center().add(0, 1, 0));
    check(PortalEntityMovement.canReplay(boat, Vec3.ZERO, movement),
        "Lagging passenger geometry prevented replay near the floor portal rim");
    near(world.player.position(), previousPassengerFeet, 1e-10,
        "Querying passenger geometry changed the native passenger position");
    PortalPassengerGroup.positionPassengers(boat);
    var seatedHulls = PortalPassengerGroup.hulls(boat);
    for (int i = 0; i < laggingHulls.size(); i++)
      nearHull(laggingHulls.get(i), seatedHulls.get(i),
          "Vehicle packet ordering changed the seated passenger collision hulls");
    nearHull(laggingBounds, PortalPassengerGroup.bounds(boat),
        "Vehicle packet ordering changed the seated passenger bounds");
    var seatedArrival = PortalPassengerGroup.arrival(boat, link.inverse(), source.center().add(0, 1, 0));
    for (int i = 0; i < arrival.size(); i++) {
      near(arrival.get(i).after(), seatedArrival.get(i).after(), 1e-10,
          "Lagging passenger changed the arrival seat");
      nearHull(arrival.get(i).hull(), seatedArrival.get(i).hull(),
          "Lagging passenger changed the arrival collision hull");
    }
    var scene = PortalCollisionWorld.query(boat, link.inverse(), laggingBounds.expandTowards(movement));
    Vec3 expected = scene.collide(seatedHulls, movement, boat.maxUpStep(), boat.onGround());
    check(expected.y > movement.y + .1, "The floor rim did not exercise passenger collision");
    world.player.setPos(previousPassengerFeet);
    var replay = PortalEntityMovement.moveAuthoritatively(boat, MoverType.SELF, movement, movement);
    check(replay != null && replay.crossings().isEmpty(), "Boat rim replay unexpectedly crossed a portal");
    near(boat.position(), start.add(expected), 1e-8,
        "Lagging passenger changed authoritative replay collision");
    near(boat.position(), new Vec3(start.x, 104.4125, start.z + movement.z), 1e-7,
        "Boat replay did not stop at the native seated passenger floor contact");
    check(world.player.getVehicle() == boat, "Boat replay detached the player");
    world.player.stopRiding();
  }

  private static void nestedPassengerGeometry(RegressionWorld world) {
    var vehicle = EntityTypes.OAK_BOAT.create(world.level, EntitySpawnReason.COMMAND);
    var passenger = EntityTypes.PIG.create(world.level, EntitySpawnReason.COMMAND);
    var nested = EntityTypes.CHICKEN.create(world.level, EntitySpawnReason.COMMAND);
    check(vehicle != null && passenger != null && nested != null, "Nested passenger factory failed");
    world.add(vehicle);
    world.add(passenger);
    world.add(nested);
    vehicle.snapTo(144.5, 107, 144.5, 37, 0);
    passenger.setPos(vehicle.position());
    nested.setPos(vehicle.position());
    check(passenger.startRiding(vehicle, true, false) && nested.startRiding(passenger, true, false),
        "Nested passengers could not mount");
    PortalPassengerGroup.positionPassengers(vehicle);
    var before = PortalPassengerGroup.hulls(vehicle);
    Vec3 passengerFeet = passenger.position(), nestedFeet = nested.position();
    Vec3 displacement = new Vec3(.3, -.32, -.2);
    vehicle.setPos(vehicle.position().add(displacement));
    var after = PortalPassengerGroup.hulls(vehicle);
    check(after.size() == 3, "Nested collision geometry omitted a passenger");
    for (int i = 0; i < before.size(); i++) {
      near(after.get(i).getCenter(), before.get(i).getCenter().add(displacement), 1e-10,
          "Vehicle movement did not carry nested passenger collision geometry");
    }
    near(passenger.position(), passengerFeet, 1e-10, "Nested geometry query moved the passenger");
    near(nested.position(), nestedFeet, 1e-10, "Nested geometry query moved the nested passenger");
  }

  private static void nearHull(AABB actual, AABB expected, String message) {
    near(actual.getMinPosition(), expected.getMinPosition(), 1e-10, message);
    near(actual.getMaxPosition(), expected.getMaxPosition(), 1e-10, message);
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
    minecartArrivalGeometry(cart, link);
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

  private static void minecartArrivalGeometry(Minecart cart, PortalLink link) {
    Vec3 start = cart.position();
    boolean onRails = cart.isOnRails();
    cart.setOnRails(true);
    cart.setPos(start.add(0, .025, 0));
    PortalPassengerGroup.positionPassengers(cart);
    var sourceHulls = PortalPassengerGroup.hulls(cart);
    if (sourceHulls.size() > 1)
      check(Math.abs(sourceHulls.get(1).minY - sourceHulls.getFirst().minY) < 1e-10,
          "Rail passenger geometry did not exercise track-bed clipping");
    for (double height : new double[] {-8, 8}) {
      Vec3 target = cart.position().add(48, height, 48);
      var arrival = PortalPassengerGroup.arrival(cart, link, target);
      for (int i = 0; i < arrival.size(); i++) {
        Vec3 offset = arrival.get(i).after().subtract(arrival.get(i).before());
        nearHull(arrival.get(i).hull(), sourceHulls.get(i).move(offset),
            "A different arrival height changed the minecart group's local collision shape");
      }
    }
    cart.setPos(start);
    cart.setOnRails(onRails);
    PortalPassengerGroup.positionPassengers(cart);
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
