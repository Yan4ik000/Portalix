package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.server.portal.PlayerPortalMoveState;
import dev.portalix.test.mixin.ServerGamePacketListenerImplAccessor;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

final class PlayerTraversalRegression {
  private PlayerTraversalRegression() {}

  static void run(RegressionWorld world) {
    var player = world.player;
    landWhileCompressed(world);
    MovementPacketRegression.checkBatchedMovement(world, 10, 10);
    MovementPacketRegression.checkReplayCorrection(world);
    for (double offset : new double[] {-.5, 0, .5}) slowFloorExit(world, offset);
    crouchedRolledPassage(world);
    for (double approach : new double[] {2, 4}) {
      for (Direction to : new Direction[] {Direction.SOUTH, Direction.EAST, Direction.UP, Direction.DOWN})
        cross(world, to, approach, MoverType.PLAYER, 4);
    }
    for (MoverType mover : new MoverType[] {MoverType.PISTON, MoverType.SHULKER_BOX, MoverType.SHULKER})
      cross(world, Direction.EAST, .1, mover, mover == MoverType.PISTON ? 1 : .5);
    forcedBlockEffects(world, MoverType.SHULKER_BOX, false);
    forcedBlockEffects(world, MoverType.PISTON, false);
    forcedBlockEffects(world, MoverType.SHULKER_BOX, true);
    var nether = world.level.getServer().getLevel(Level.NETHER);
    check(nether != null, "Dimension-change fixture has no Nether");
    for (var destination : new ServerLevel[] {nether, world.level}) {
      PortalPassagePose.set(player, true);
      check(player.teleport(new TeleportTransition(destination, player.position(), Vec3.ZERO,
              player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING)) == player,
          "Native dimension transition did not retain the test player");
      check(player.level() == destination && PortalPassagePose.scale(player) == 1,
          "Native dimension transition retained portal compression");
    }
    MovementPacketRegression.checkKeepAliveOrdering(world);
    MovementPacketRegression.checkOrderedPongPurposes(world);
    world.reset();
    groundedRiptide(world);
    world.reset();
    MovementPacketRegression.checkVanillaMovement(world);
    MovementPacketRegression.checkObservationMovement(world);
  }

  private static void forcedBlockEffects(RegressionWorld world, MoverType mover, boolean teleport) {
    var player = world.player;
    var entrance = world.endpoint(Direction.DOWN, new Vec3(144, 106, 144));
    var exit = world.endpoint(Direction.EAST, new Vec3(192, 104, 144));
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var cells = PortalSurface.placementFootprint(entrance).blocks();
    var fire = cells.getLast().below();
    world.block(fire.below(), Blocks.STONE.defaultBlockState());
    world.block(fire, Blocks.FIRE.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    PlayerPortalVisibility.of(player).ready(link);
    player.setGameMode(GameType.SURVIVAL);
    Vec3 start = entrance.center().subtract(0, player.getBbHeight() / 2 + .025, 0);
    player.connection.teleport(start.x, start.y, start.z, 0, 0);
    player.clearFire();
    try {
      var push = cells.getFirst().below();
      if (mover == MoverType.SHULKER_BOX) {
        var pos = push.below();
        var state = Blocks.SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING, Direction.UP);
        world.block(pos, state);
        var box = (ShulkerBoxBlockEntity) world.level.getBlockEntity(pos);
        check(box != null && box.triggerEvent(ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT, 1),
            "Could not open the shulker box fixture");
        ShulkerBoxBlockEntity.tick(world.level, pos, state, box);
      } else {
        var state = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, Direction.UP);
        world.block(push, state);
        var piston = new PistonMovingBlockEntity(push, state, Blocks.STONE.defaultBlockState(), Direction.UP,
            true, false);
        world.level.setBlockEntity(piston);
        PistonMovingBlockEntity.tick(world.level, push, state, piston);
      }
      var movement = EntityPortalState.of(player).lastMove();
      check(movement != null && movement.mover() == mover && movement.crossings().size() == 1
              && movement.crossings().getFirst().link().equals(link),
          "The " + mover + " fixture did not push the player through its entrance");
      check(ServerPlayerMotion.awaitingTeleport(player), "Forced crossing lost its client position acknowledgement");
      boolean immediate = mover == MoverType.PISTON;
      check(player.isOnFire() == immediate, "Incorrect immediate entrance fire effect for " + mover);
      player.clearFire();
      if (teleport) {
        Vec3 destination = exit.center().add(4, -2, 0);
        player.connection.teleport(destination.x, destination.y, destination.z, 53, 17);
        check(player.position().equals(destination) && player.oldPosition().equals(destination)
                && player.getYRot() == 53 && player.getXRot() == 17,
            "Vanilla teleport did not reset the server pose after a forced crossing");
      }
      player.doTick();
      check(player.isOnFire() == (!immediate && !teleport),
          "Incorrect deferred entrance fire effect for " + mover + " with native teleport=" + teleport);
    } finally {
      player.clearFire();
      player.setHealth(player.getMaxHealth());
      world.reset();
    }
  }

  private static void groundedRiptide(RegressionWorld world) {
    var player = world.player;
    var entrance = world.endpoint(Direction.DOWN, new Vec3(144, 106, 144));
    var exit = world.endpoint(Direction.UP, new Vec3(192, 104, 144));
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    PlayerPortalVisibility.of(player).ready(link);
    Vec3 feet = entrance.center().add(0, -1 - player.getBbHeight() / 2, 0);
    world.block(BlockPos.containing(feet), Blocks.WATER.defaultBlockState());
    player.snapTo(feet, 0, 0);
    player.setDeltaMovement(Vec3.ZERO);
    ServerPlayerMotion.advanceFluidState(player);
    player.setOnGround(true);
    check(player.isInWater(), "The grounded Riptide launch needs water");
    var trident = new ItemStack(Items.TRIDENT);
    trident.enchant(world.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        .getOrThrow(Enchantments.RIPTIDE), 1);
    player.setItemInHand(InteractionHand.MAIN_HAND, trident);
    long previous = EntityPortalState.of(player).crossingSequence();
    check(Items.TRIDENT.releaseUsing(trident, world.level, player,
        Items.TRIDENT.getUseDuration(trident, player) - 20), "Native Riptide release failed");
    var state = EntityPortalState.of(player);
    check(state.crossingSequence() == previous + 1 && state.lastCrossing().link().equals(link)
            && player.position().distanceToSqr(exit.center()) < 4,
        "The server did not apply Riptide's independent upward step through the ceiling portal");
    var move = state.lastMove();
    check(!PortalEntityMovement.clientPredicted(player, move.mover(), move.requested(), move.beforeVelocity()),
        "Riptide's server-issued lift could also be submitted as a predicted travel step");
  }

  private static void cross(RegressionWorld world, Direction to, double approach, MoverType mover, double distance) {
    var player = world.player;
    Direction from = Direction.NORTH;
    int spacing = 48;
    var entrance = world.endpoint(from, new Vec3(144, 104, 144));
    var exit = world.endpoint(to, new Vec3(144 + spacing, 104, 144));
    int chunkX = (int) Math.floor(exit.center().x) >> 4;
    int chunkZ = (int) Math.floor(exit.center().z) >> 4;
    for (int x = chunkX - 1; x <= chunkX + 1; x++)
      for (int z = chunkZ - 1; z <= chunkZ + 1; z++) world.level.getChunk(x, z);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var link = world.link(entrance, exit);
    world.publish(link);
    check(!PortalEntityMovement.canTraverse(player, link),
        "Player can enter an exit scene before readiness acknowledgement");
    PlayerPortalVisibility.of(player).ready(link);
    PlayerPortalVisibility.of(player).ready(link.inverse());
    check(PortalEntityMovement.canTraverse(player, link),
        "Acknowledged loaded exit scene is not traversable");
    PlayerPortalMoveState.of(player).endClientTick(Util.getMillis());
    Vec3 center = entrance.center().add(entrance.normal().scale(approach));
    player.setPos(center.subtract(0, player.getBbHeight() / 2, 0));
    Vec3 start = player.position();
    player.setOldPosAndRot();
    player.setOnGround(false);
    player.fallDistance = 12;
    Vec3 velocity = entrance.normal().scale(-distance);
    player.setDeltaMovement(velocity);
    PortalEntityMovement.MoveResult movement;
    if (PortalEntityMovement.clientPredicted(player, mover, velocity, velocity))
      movement = PortalEntityMovement.moveAuthoritatively(player, mover, velocity, velocity);
    else {
      player.move(mover, velocity);
      movement = dev.portalix.portal.traversal.EntityPortalState.of(player).lastMove();
    }
    check(movement != null && movement.crossings().size() == 1,
        "Player movement did not cross the " + to + " exit");
    var crossing = movement.crossings().getFirst();
    Vec3 expectedMovement = link.transform().vector(crossing.beforeFeet().subtract(start))
        .add(player.position().subtract(crossing.afterFeet()));
    check(player.getKnownMovement().distanceToSqr(expectedMovement) < 1e-10,
        "Player known movement lost segments or retained the entrance frame: " + to + "/" + approach);
    if (mover == MoverType.PISTON)
      check(Math.abs(expectedMovement.length() - .51) < 1e-7,
          "Piston limit must apply once before the portal splits and rotates its movement");
    var shot = EntityTypes.SNOWBALL.create(world.level, EntitySpawnReason.COMMAND);
    player.setOnGround(false);
    shot.shootFromRotation(player, 0, 0, 0, 1, 0);
    check(shot.getDeltaMovement().distanceToSqr(expectedMovement.add(0, 0, 1)) < 1e-10,
        "Projectile inherited movement in the wrong portal frame: " + to + "/" + approach);
    check(player.position().distanceToSqr(exit.center()) < 16,
        "Player arrived outside the exit scene");
    check(player.fallDistance == 0, "Player retained entrance fall damage after crossing");
    var body = player.getBoundingBox().deflate(1e-6);
    check(!PortalCollisionWorld.query(player, link.inverse(), body).intersects(body),
        "Player arrived inside a solid block " + from + " -> " + to);
    check(Float.isFinite(player.getYRot()) && Float.isFinite(player.getXRot()),
        "Player crossing produced invalid look angles");
    PlayerPortalVisibility.of(player).retainLinks(java.util.List.of());
    check(!PortalEntityMovement.canTraverse(player, link),
        "A retired readiness acknowledgement remains usable");
    world.reset();
  }

  private static void slowFloorExit(RegressionWorld world, double offset) {
    var player = world.player;
    var entrance = world.endpoint(Direction.UP, new Vec3(144, 104, 144));
    var exit = world.endpoint(Direction.SOUTH, new Vec3(192, 104, 144));
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var bottom = BlockPos.containing(exit.center().add(0, -2, 0));
    for (int x = -2; x <= 2; x++) {
      for (int z = -1; z <= 4; z++) world.block(bottom.offset(x, 0, z), Blocks.STONE.defaultBlockState());
      for (int y = 0; y <= 4; y++) world.block(bottom.offset(x, y, 2), Blocks.STONE.defaultBlockState());
    }
    var link = world.link(entrance, exit);
    world.publish(link);
    PlayerPortalVisibility.of(player).ready(link);
    PlayerPortalVisibility.of(player).ready(link.inverse());
    PlayerPortalMoveState.of(player).endClientTick(Util.getMillis());
    player.setGameMode(GameType.SURVIVAL);
    player.getAbilities().flying = false;
    Vec3 center = entrance.center().add(entrance.up().scale(offset)).add(0, .15, 0);
    player.setPos(center.subtract(0, player.getBbHeight() / 2, 0));
    player.setOldPosAndRot();
    player.setOnGround(false);
    var mappedBody = link.transform().bounds(player.getBoundingBox());
    Vec3 descent = new Vec3(0, -.4, 0);
    player.setDeltaMovement(descent);
    Vec3 beforeSimulation = player.position();
    player.move(MoverType.SELF, descent);
    check(player.position().equals(beforeSimulation) && !player.onGround()
            && player.getDeltaMovement().equals(descent),
        "Server self movement altered the player before authoritative portal replay");
    var movement = PortalEntityMovement.moveAuthoritatively(player, MoverType.SELF, descent, descent);
    check(movement != null && movement.crossings().size() == 1,
        "Slow floor movement did not reach the wall exit: offset=" + offset);
    var crossing = movement.crossings().getFirst();
    double outward = player.position().subtract(crossing.afterFeet()).dot(exit.normal());
    check(outward > .2 && outward < player.getBbWidth() / 2,
        "The slow-exit fixture must leave part of the body inside the aperture: " + outward);
    check(!player.onGround(),
        "The slow floor-to-wall tail landed on the aperture lip: offset=" + offset);
    check(Math.abs(player.getBbHeight() - mappedBody.getYsize()) < 1e-6,
        "The exit collision height did not follow the transformed source body: offset=" + offset);
    check(player.position().y > exit.center().y - exit.height() / 2,
        "The slow arrival has no clearance above the lower aperture edge: offset=" + offset);

    for (int tick = 0; tick < 5; tick++) {
      world.level.tickNonPassenger(player);
      PortalPassagePose.tick(player);
    }
    check(PortalPassagePose.compact(player),
        "Server ticks restored the passage body before the next client movement");
    var replay = PlayerPortalMoveState.of(player);
    replay.endClientTick(Util.getMillis());
    Vec3 leaving = exit.normal().scale(.1).add(0, -.04, 0);
    for (int step = 0; step < 4; step++) {
      world.level.tickNonPassenger(player);
      Vec3 before = player.position();
      double distance = before.subtract(exit.center()).dot(exit.normal());
      PortalEntityMovement.moveAuthoritatively(player, MoverType.PLAYER, leaving, leaving);
      check(player.position().subtract(before).dot(exit.normal()) > .099,
          "The wall aperture blocked outward movement: offset=" + offset + ", step=" + step);
      if (distance < player.getBbWidth() / 2)
        check(!player.onGround(), "The partial exit caught the lower lip: offset=" + offset);
      PortalPassagePose.tick(player);
      replay.endClientTick(Util.getMillis());
    }
    check(PortalPassagePose.scale(player) == 1 && player.getBbHeight() > 1.7,
        "The slow exit did not restore the standing body after clearing the wall: offset=" + offset);
    Vec3 beforeObstacle = player.position();
    Vec3 intoObstacle = exit.normal().scale(2);
    PortalEntityMovement.moveAuthoritatively(player, MoverType.PLAYER, intoObstacle, intoObstacle);
    check(player.position().subtract(beforeObstacle).dot(exit.normal()) < 1.5,
        "Passage compression allowed movement through a solid obstacle outside the aperture");
    check(world.level.noCollision(player, player.getBoundingBox().deflate(1e-6)),
        "The slow exit left the player inside a solid obstacle");
    PlayerPortalVisibility.of(player).retainLinks(List.of());
    world.reset();
  }

  private static void crouchedRolledPassage(RegressionWorld world) {
    var player = world.player;
    var source = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
    var entrance = new PortalEndpoint(source.dimension(), source.center(), source.normal(),
        new Vec3(Math.sqrt(.5), Math.sqrt(.5), 0), 1, 2);
    var exit = world.endpoint(Direction.SOUTH, new Vec3(192, 104, 144));
    player.setPose(Pose.CROUCHING);
    player.setShiftKeyDown(true);
    double height = player.getBoundingBox().getYsize();
    float eyeHeight = player.getEyeHeight();
    PortalPassagePose.enter(player, world.link(entrance, exit));
    check(player.getPose() == Pose.CROUCHING && player.getBoundingBox().getYsize() <= height + 1e-7,
        "Entering a rolled passage enlarged the native crouching collision body");
    check(player.getEyeHeight() <= eyeHeight + 1e-7,
        "Entering a rolled passage raised the crouching eye");
    world.reset();
  }

  private static void landWhileCompressed(RegressionWorld world) {
    var player = world.player;
    var connection = player.connection;
    connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    if (ServerPlayerMotion.awaitingTeleport(player)) {
      connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(
          ((ServerGamePacketListenerImplAccessor) connection).portalix$teleportId()));
    }
    Vec3 floor = new Vec3(152.5, 101, 152.5);
    world.clear(floor.add(0, 3, 0), 4);
    world.block(BlockPos.containing(floor).below(), Blocks.STONE.defaultBlockState());
    player.setGameMode(GameType.SURVIVAL);
    player.setPos(floor.add(0, 5, 0));
    player.setOnGround(false);
    player.fallDistance = 0;
    PortalPassagePose.set(player, true);
    Vec3 descent = new Vec3(0, -2, 0);
    PortalEntityMovement.moveAuthoritatively(player, MoverType.PLAYER, descent, descent);
    check(player.fallDistance == 2, "Replayed descent did not accumulate fall distance");
    float health = player.getHealth();
    PortalEntityMovement.moveAuthoritatively(player, MoverType.PLAYER, descent.scale(2), descent.scale(2));
    check(player.onGround() && player.fallDistance == 0 && player.getHealth() < health,
        "Landing after a replay did not apply ordinary fall damage");
    player.setHealth(health);
    world.reset();
  }
}
