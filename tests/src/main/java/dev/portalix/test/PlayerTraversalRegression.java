package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.PortalEntityMovement;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class PlayerTraversalRegression {
  private PlayerTraversalRegression() {}

  static void run(RegressionWorld world) {
    var player = world.player;
    for (Direction to : new Direction[] {Direction.SOUTH, Direction.EAST, Direction.UP, Direction.DOWN}) {
      Direction from = Direction.NORTH;
      int distance = 48;
      var entrance = world.endpoint(from, new Vec3(144, 104, 144));
      var exit = world.endpoint(to, new Vec3(144 + distance, 104, 144));
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
      Vec3 center = entrance.center().add(entrance.normal().scale(2));
      player.setPos(center.subtract(0, player.getBbHeight() / 2, 0));
      player.setOldPosAndRot();
      player.setOnGround(false);
      player.fallDistance = 12;
      Vec3 velocity = entrance.normal().scale(-4);
      player.setDeltaMovement(velocity);
      var movement = PortalEntityMovement.moveAuthoritatively(player, MoverType.PLAYER, velocity, velocity);
      check(movement != null && movement.crossings().size() == 1,
          "Player movement did not cross the " + to + " exit");
      check(player.position().distanceToSqr(exit.center()) < 16,
          "Player arrived outside the exit scene");
      check(player.fallDistance == 0, "Player retained entrance fall damage after crossing");
      check(world.level.noCollision(player, player.getBoundingBox().deflate(1e-6)),
          "Player arrived inside a solid block " + from + " -> " + to);
      check(Float.isFinite(player.getYRot()) && Float.isFinite(player.getXRot()),
          "Player crossing produced invalid look angles");
      PlayerPortalVisibility.of(player).retainLinks(java.util.List.of());
      check(!PortalEntityMovement.canTraverse(player, link),
          "A retired readiness acknowledgement remains usable");
      world.reset();
    }
  }
}
