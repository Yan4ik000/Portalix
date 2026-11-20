package dev.portalix.test;

import static dev.portalix.test.PlacementTestHooks.Phase.AFTER_BREAK;
import static dev.portalix.test.PlacementTestHooks.Phase.AFTER_LOOT;
import static dev.portalix.test.PlacementTestHooks.Phase.BEFORE_BREAK;
import static dev.portalix.test.PlacementTestHooks.expectRejection;
import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.BlockPortalPlacement;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.server.portal.GunPortalRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PlacementRegression {
  private PlacementRegression() {}

  private static boolean prepare(ServerPlayer player, PortalEndpoint endpoint) {
    return BlockPortalPlacement.prepare(player, endpoint, PortalSurface.placementFootprint(endpoint));
  }

  private static boolean isValid(MinecraftServer server, PortalEndpoint endpoint) {
    return BlockPortalPlacement.isValid(server, endpoint, PortalSurface.placementFootprint(endpoint));
  }

  static void run(RegressionWorld world) {
    var server = world.level.getServer();
    for (Direction face : Direction.values()) {
      var endpoint = world.endpoint(face, new Vec3(144, 104, 144));
      for (Block support : new Block[] {Blocks.STONE, Blocks.GLASS, Blocks.OBSIDIAN}) {
        world.surface(endpoint, support.defaultBlockState(), Blocks.AIR.defaultBlockState());
        check(prepare(world.player, endpoint), face + " rejects " + support);
        check(isValid(server, endpoint), face + " invalid support " + support);
        var hit = new BlockHitResult(endpoint.center(), face,
            BlockPos.containing(endpoint.center().subtract(endpoint.normal().scale(.5))), false);
        var solved = BlockPortalPlacement.find(world.player, hit, 0, List.of());
        check(solved.isPresent(), face + " could not solve placement on " + support);
        check(isValid(server, solved.orElseThrow()),
            face + " solver accepted an unsupported footprint");
      }
      for (Block support : new Block[] {
          Blocks.OAK_LEAVES, Blocks.OAK_SLAB, Blocks.CHEST, Blocks.WATER}) {
        world.surface(endpoint, support.defaultBlockState(), Blocks.AIR.defaultBlockState());
        check(!prepare(world.player, endpoint),
            face + " accepts invalid support " + support);
        check(!isValid(server, endpoint),
            face + " retained invalid support " + support);
      }
      for (Block clearance : new Block[] {Blocks.SHORT_GRASS, Blocks.SNOW, Blocks.MOSS_CARPET}) {
        world.surface(endpoint, Blocks.STONE.defaultBlockState(), clearance.defaultBlockState());
        check(!isValid(server, endpoint),
            "An uncleared portal must not be valid: " + clearance);
        check(prepare(world.player, endpoint),
            face + " cannot clear " + clearance);
        check(isValid(server, endpoint),
            face + " failed to clear entire footprint for " + clearance);
      }
      for (Block clearance : new Block[] {
          Blocks.STONE, Blocks.OAK_LEAVES, Blocks.CHEST, Blocks.WATER, Blocks.BEDROCK}) {
        world.surface(endpoint, Blocks.STONE.defaultBlockState(), clearance.defaultBlockState());
        check(!prepare(world.player, endpoint),
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
        check(prepare(world.player, endpoint) == wall,
            face + " has incorrect rail clearance: " + rail);
        check(isValid(server, endpoint) == wall,
            face + " retained a portal with incorrect rail clearance: " + rail);
        check(world.level.getBlockState(track).is(rail),
            "Portal placement destroyed a rail: " + rail);
      }

      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.player.setGameMode(GameType.SPECTATOR);
      check(!prepare(world.player, endpoint), "Spectator placed a portal");
      world.player.setGameMode(GameType.ADVENTURE);
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      check(!prepare(world.player, endpoint),
          "Adventure placement cleared vegetation");
      world.player.setGameMode(GameType.CREATIVE);

      var footprint = PortalSurface.placementFootprint(endpoint);
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      BlockPos protectedFront = footprint.blocks().getLast().relative(face);
      expectRejection(BEFORE_BREAK, world.player, protectedFront, () -> false,
          () -> prepare(world.player, endpoint));
      for (var pos : footprint.blocks())
        check(world.level.getBlockState(pos.relative(face)).is(Blocks.SHORT_GRASS),
            "Loader block-break veto partially cleared the portal footprint");
      check(prepare(world.player, endpoint),
          "Placement remained vetoed after the loader callback scope ended");
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      expectRejection(BEFORE_BREAK, world.player, protectedFront, () -> {
        world.block(protectedFront, Blocks.STONE.defaultBlockState());
        return true;
      }, () -> prepare(world.player, endpoint));
      for (var pos : footprint.blocks()) {
        var front = pos.relative(face);
        var expected = front.equals(protectedFront) ? Blocks.STONE : Blocks.SHORT_GRASS;
        check(world.level.getBlockState(front).is(expected),
            "A break callback changed the footprint and placement still cleared approved blocks");
      }
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState());
      world.block(protectedFront, Blocks.CHEST.defaultBlockState());
      check(!prepare(world.player, endpoint),
          "Partly protected footprint was accepted");
      check(world.level.getBlockState(footprint.blocks().getFirst().relative(face))
          .is(Blocks.SHORT_GRASS), "Failed placement partially cleared its footprint");
      world.reset();
    }
    changeBlockDuringLoot(world);
    removeSupportAfterBreak(world);
    doublePlantLoot(world);
    registryLifecycle(world);
  }

  private static void changeBlockDuringLoot(RegressionWorld world) {
    var endpoint = world.endpoint(Direction.SOUTH, new Vec3(144, 104, 144));
    var footprint = PortalSurface.placementFootprint(endpoint);
    var first = footprint.blocks().getFirst().south();
    var last = footprint.blocks().getLast().south();
    var plants = Blocks.MOSS_CARPET.defaultBlockState();
    var replacement = Blocks.STONE.defaultBlockState();
    var area = new AABB(first).minmax(new AABB(last)).inflate(2);
    for (var change : List.of(new LootChange(first, first), new LootChange(first, last),
        new LootChange(last, first))) {
      world.surface(endpoint, Blocks.STONE.defaultBlockState(), plants);
      world.player.setGameMode(GameType.SURVIVAL);
      try {
        expectRejection(AFTER_LOOT, world.player, change.lootPos(), () -> {
          world.block(change.changedPos(), replacement);
          return true;
        }, () -> prepare(world.player, endpoint));
        for (var pos : footprint.blocks()) {
          var front = pos.south();
          check(world.level.getBlockState(front) == (front.equals(change.changedPos()) ? replacement : plants),
              "Placement lost a loot callback's block change or failed to roll back its own write");
        }
        check(world.level.getEntitiesOfClass(ItemEntity.class, area,
            item -> item.getItem().is(Items.MOSS_CARPET)).isEmpty(),
            "Failed placement published drops from loot evaluated before a block changed");
      } finally {
        world.level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(Items.MOSS_CARPET))
            .forEach(ItemEntity::discard);
        world.reset();
      }
    }
  }

  private record LootChange(BlockPos lootPos, BlockPos changedPos) {}

  private static void removeSupportAfterBreak(RegressionWorld world) {
    var registry = new GunPortalRegistry(world.level.getServer());
    var gun = UUID.randomUUID();
    var blue = world.endpoint(Direction.SOUTH, new Vec3(144, 104, 144));
    var orange = world.endpoint(Direction.WEST, new Vec3(192, 104, 192));
    var replacement = world.endpoint(Direction.SOUTH, new Vec3(148, 104, 144));
    var support = PortalSurface.placementFootprint(replacement).blocks().getFirst();
    var front = support.south();
    var area = new AABB(front).inflate(3);
    var variant = PortalixVariant.STANDARD;
    try {
      world.surface(blue, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(orange, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(replacement, Blocks.STONE.defaultBlockState(), Blocks.MOSS_CARPET.defaultBlockState());
      check(registry.apply(world.player, gun, PortalColor.BLUE, blue, variant), "Missing old blue portal");
      check(registry.apply(world.player, gun, PortalColor.ORANGE, orange, variant), "Missing old orange portal");
      var before = List.copyOf(PortalTopology.links(world.level));
      world.player.setGameMode(GameType.SURVIVAL);
      expectRejection(AFTER_BREAK, world.player, front, () -> {
        world.block(support, Blocks.AIR.defaultBlockState());
        return true;
      }, () -> registry.apply(world.player, gun, PortalColor.BLUE, replacement, variant));
      check(PortalTopology.links(world.level).equals(before), "Rejected replacement changed the original pair");
      check(world.level.getBlockState(support).isAir() && world.level.getBlockState(front).isAir(),
          "Rejected replacement reverted a published break or callback change");
      check(!world.level.getEntitiesOfClass(ItemEntity.class, area,
          item -> item.getItem().is(Items.MOSS_CARPET)).isEmpty(), "Published block drops were lost");
    } finally {
      world.level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(Items.MOSS_CARPET))
          .forEach(ItemEntity::discard);
      registry.clearAll();
      world.reset();
    }
  }

  private static void doublePlantLoot(RegressionWorld world) {
    var endpoint = world.endpoint(Direction.SOUTH, new Vec3(144, 104, 144));
    world.surface(endpoint, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    var lower = PortalSurface.placementFootprint(endpoint).blocks().getFirst().south();
    world.block(lower.below(), Blocks.DIRT.defaultBlockState());
    var grass = Blocks.TALL_GRASS.defaultBlockState();
    world.block(lower, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
    world.block(lower.above(), grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
    world.player.setGameMode(GameType.SURVIVAL);
    // With this loot seed both first rolls pass the seed-drop condition if both halves see a partner.
    world.level.getServer().getRandomSequences().reset(
        Identifier.withDefaultNamespace("blocks/tall_grass"), 0, 32, false, false);
    check(prepare(world.player, endpoint), "Could not clear paired tall grass");
    var drops = world.level.getEntitiesOfClass(ItemEntity.class, new AABB(lower).inflate(2),
        item -> item.getItem().is(Items.WHEAT_SEEDS));
    try {
      check(drops.stream().mapToInt(item -> item.getItem().getCount()).sum() == 1,
          "Clearing both halves must produce exactly one vanilla seed drop");
    } finally {
      drops.forEach(ItemEntity::discard);
      world.reset();
    }
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
