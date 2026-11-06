package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.api.PortalColor;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.BlockPortalPlacement;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.server.portal.GunPortalRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PlacementRegression {
  private PlacementRegression() {}

  static void run(RegressionWorld world) {
    var server = world.level.getServer();
    for (Direction face : Direction.values()) {
      var endpoint = world.endpoint(face, new Vec3(144, 104, 144));
      for (Block support : new Block[] {Blocks.STONE, Blocks.GLASS, Blocks.OBSIDIAN}) {
        world.surface(endpoint, support.defaultBlockState(), Blocks.AIR.defaultBlockState());
        check(BlockPortalPlacement.prepare(world.player, endpoint), face + " rejects " + support);
        check(BlockPortalPlacement.isValid(server, endpoint), face + " invalid support " + support);
        var hit = new BlockHitResult(endpoint.center(), face,
            BlockPos.containing(endpoint.center().subtract(endpoint.normal().scale(.5))), false);
        var solved = BlockPortalPlacement.find(world.player, hit, 0, List.of());
        check(solved.isPresent(), face + " could not solve placement on " + support);
        check(BlockPortalPlacement.isValid(server, solved.orElseThrow()),
            face + " solver accepted an unsupported footprint");
      }
      for (Block support : new Block[] {
          Blocks.OAK_LEAVES, Blocks.OAK_SLAB, Blocks.CHEST, Blocks.WATER}) {
        world.surface(endpoint, support.defaultBlockState(), Blocks.AIR.defaultBlockState());
        check(!BlockPortalPlacement.prepare(world.player, endpoint),
            face + " accepts invalid support " + support);
        check(!BlockPortalPlacement.isValid(server, endpoint),
            face + " retained invalid support " + support);
      }
      for (Block clearance : new Block[] {Blocks.SHORT_GRASS, Blocks.SNOW, Blocks.MOSS_CARPET}) {
        world.surface(endpoint, Blocks.STONE.defaultBlockState(), clearance.defaultBlockState());
        check(!BlockPortalPlacement.isValid(server, endpoint),
            "An uncleared portal must not be valid: " + clearance);
        check(BlockPortalPlacement.prepare(world.player, endpoint),
            face + " cannot clear " + clearance);
        check(BlockPortalPlacement.isValid(server, endpoint),
            face + " failed to clear entire footprint for " + clearance);
      }
      for (Block clearance : new Block[] {
          Blocks.STONE, Blocks.OAK_LEAVES, Blocks.CHEST, Blocks.WATER, Blocks.BEDROCK}) {
        world.surface(endpoint, Blocks.STONE.defaultBlockState(), clearance.defaultBlockState());
        check(!BlockPortalPlacement.prepare(world.player, endpoint),
            face + " destroys protected clearance " + clearance);
      }

      for (Block rail : new Block[] {
          Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.DETECTOR_RAIL, Blocks.ACTIVATOR_RAIL}) {
        world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
        BlockPos track = PortalSurface.placementFootprint(endpoint).blocks().stream()
            .map(pos -> pos.relative(face))
            .min(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
        world.block(track.below(), Blocks.STONE.defaultBlockState());
        world.block(track, rail.defaultBlockState());
        check(world.level.getBlockState(track).is(rail), "The rail fixture lost its track");
        boolean wall = face.getAxis().isHorizontal();
        check(BlockPortalPlacement.prepare(world.player, endpoint) == wall,
            face + " has incorrect rail clearance: " + rail);
        check(BlockPortalPlacement.isValid(server, endpoint) == wall,
            face + " retained a portal with incorrect rail clearance: " + rail);
        check(world.level.getBlockState(track).is(rail),
            "Portal placement destroyed a rail: " + rail);
      }

      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.player.setGameMode(GameType.SPECTATOR);
      check(!BlockPortalPlacement.prepare(world.player, endpoint), "Spectator placed a portal");
      world.player.setGameMode(GameType.ADVENTURE);
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      check(!BlockPortalPlacement.prepare(world.player, endpoint),
          "Adventure placement cleared vegetation");
      world.player.setGameMode(GameType.CREATIVE);

      var footprint = PortalSurface.placementFootprint(endpoint);
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      BlockPos protectedFront = footprint.blocks().getLast().relative(face);
      PlacementBreakVeto.reject(world.player, endpoint, protectedFront);
      for (var pos : footprint.blocks())
        check(world.level.getBlockState(pos.relative(face)).is(Blocks.SHORT_GRASS),
            "Loader block-break veto partially cleared the portal footprint");
      check(BlockPortalPlacement.prepare(world.player, endpoint),
          "Placement remained vetoed after the loader callback scope ended");
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      world.block(protectedFront, Blocks.CHEST.defaultBlockState());
      check(!BlockPortalPlacement.prepare(world.player, endpoint),
          "Partly protected footprint was accepted");
      check(world.level.getBlockState(footprint.blocks().getFirst().relative(face))
          .is(Blocks.SHORT_GRASS), "Failed placement partially cleared its footprint");
      world.reset();
    }
    registryLifecycle(world);
  }

  private static void registryLifecycle(RegressionWorld world) {
    var registry = new GunPortalRegistry(world.level.getServer());
    UUID gun = UUID.randomUUID();
    UUID otherGun = UUID.randomUUID();
    var blue = world.endpoint(Direction.SOUTH, new Vec3(144, 104, 144));
    var orange = world.endpoint(Direction.WEST, new Vec3(192, 104, 192));
    try {
      PortalixVariant variant = PortalixVariant.STANDARD;
      world.surface(blue, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(orange, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      check(registry.apply(world.player, gun, PortalColor.BLUE, blue, variant),
          "Failed initial " + variant + " placement");
      check(PortalTopology.links(world.level).isEmpty(),
          "An unpaired portal became traversable");
      check(registry.apply(world.player, gun, PortalColor.ORANGE, orange, variant),
          "Failed second " + variant + " placement");
      var links = PortalTopology.links(world.level);
      check(links.size() == 2,
          "A complete pair did not publish two directions");
      check(links.getFirst().inverse().equals(links.getLast()), "Pair directions disagree");
      check(!registry.apply(world.player, otherGun, PortalColor.BLUE, blue, variant),
          "Different guns can overlap");
      check(registry.apply(world.player, gun, PortalColor.BLUE, blue, variant),
          "Replacing a portal at the same location failed");
      BlockPos support = PortalSurface.placementFootprint(blue).blocks().getFirst();
      world.block(support, Blocks.AIR.defaultBlockState());
      registry.tick();
      check(PortalTopology.links(world.level).isEmpty(),
          "Support invalidation retained a broken link");
      registry.clear(gun);
      check(PortalTopology.links(world.level).isEmpty(),
          "Registry reset left active state");
    } finally {
      registry.clearAll();
    }
  }
}
