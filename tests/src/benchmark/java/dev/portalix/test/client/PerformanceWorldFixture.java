package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.server.portal.GunPortalRegistry;
import dev.portalix.test.client.mixin.PortalServerEventsAccessor;
import dev.portalix.test.client.mixin.PortalServerSessionAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

final class PerformanceWorldFixture {
  static final int LOCATIONS = 12;
  record Location(int x, int y, int z) {
    Vec3 feet() { return new Vec3(x + .5, y, z - 2.5); }
    Vec3 aim() { return new Vec3(x + .5, y + 1, z - 6); }
    PortalEndpoint entrance() { return endpoint(x + .5, y + 1, z - 6, 1); }
    PortalEndpoint exit() { return endpoint(x + .5, y + 1, z, -1); }
    List<BlockPos> terrainProbes() {
      return List.of(new BlockPos(x, y - 1, z - 2), new BlockPos(x, y + 1, z - 6));
    }
  }
  record Leg(UUID gun, Location source, Location destination, int livePairs) {}

  private final ServerPlayer player;
  private final ServerLevel level;
  private final GunPortalRegistry registry;
  private final List<Location> locations = new ArrayList<>();
  private final UUID gun = UUID.randomUUID();

  PerformanceWorldFixture(ServerPlayer player) {
    this.player = player;
    level = player.level();
    check(!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource),
        "The performance route requires normal generated terrain");
    level.getGameRules().set(GameRules.SPAWN_MOBS, true, level.getServer());
    var session = PortalServerEventsAccessor.portalix$session();
    registry = ((PortalServerSessionAccessor) (Object) session).portalix$registry();
    locations.add(location(0));
    var start = locations.getFirst().feet();
    check(player.teleportTo(level, start.x, start.y, start.z, Set.of(), 180, 0, true),
        "Cannot place the performance observer at the route start");
    player.getAbilities().flying = false;
    player.onUpdateAbilities();
  }

  Leg prepare(int hop) {
    int sourceIndex = hop % LOCATIONS;
    int destinationIndex = (hop + 1) % LOCATIONS;
    if (destinationIndex == locations.size()) locations.add(location(destinationIndex));
    var source = locations.get(sourceIndex);
    var destination = locations.get(destinationIndex);
    for (int chunkX = (destination.x - 2) >> 4; chunkX <= (destination.x + 2) >> 4; chunkX++)
      for (int chunkZ = (destination.z - 2) >> 4; chunkZ <= (destination.z + 2) >> 4; chunkZ++)
        level.getChunk(chunkX, chunkZ);
    // Keep the remote view closed until the measured shot connects the new pair.
    registry.clear(gun);
    // Unused support panels must not hide the distant terrain through either endpoint.
    panel(source.entrance(), Blocks.QUARTZ_BLOCK.defaultBlockState());
    panel(source.exit(), Blocks.AIR.defaultBlockState());
    panel(destination.entrance(), Blocks.AIR.defaultBlockState());
    panel(destination.exit(), Blocks.QUARTZ_BLOCK.defaultBlockState());
    // Only the destination is stored in advance; the player must shoot the local endpoint.
    check(registry.apply(player, gun, PortalColor.ORANGE, destination.exit(), PortalixVariant.STANDARD),
        "Cannot store the distant performance destination");
    if (hop == 0) {
      var stack = new ItemStack(ModItems.PORTALIX.get());
      stack.set(PortalixIdentity.ID.get(), gun);
      player.getInventory().setItem(0, stack);
      player.getInventory().setSelectedSlot(0);
      player.connection.send(new ClientboundSetHeldSlotPacket(0));
    }
    registry.synchronize(player);
    check(PortalTopology.links(level).size() <= 2,
        "The performance route must use a single portal pair");
    return new Leg(gun, source, destination, PortalTopology.links(level).size() / 2);
  }

  private Location location(int index) {
    int x = 4096 + index * 2048;
    int z = 4096 + (index % 3) * 2048;
    level.getChunk(x >> 4, z >> 4);
    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
    var location = new Location(x, y, z);
    for (var position : BlockPos.betweenClosed(new BlockPos(x - 7, y - 1, z - 9),
        new BlockPos(x + 7, y + 4, z + 4))) {
      level.setBlockAndUpdate(position,
          position.getY() == y - 1 ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }
    return location;
  }

  private void panel(PortalEndpoint endpoint, BlockState state) {
    for (int horizontal = -2; horizontal <= 2; horizontal++) {
      for (int vertical = -1; vertical <= 2; vertical++) {
        var point = endpoint.center().add(horizontal, vertical, 0).subtract(endpoint.normal().scale(.5));
        level.setBlockAndUpdate(BlockPos.containing(point), state);
        level.setBlockAndUpdate(BlockPos.containing(point.add(endpoint.normal())), Blocks.AIR.defaultBlockState());
      }
    }
  }

  private static PortalEndpoint endpoint(double x, double y, double z, int normalZ) {
    return new PortalEndpoint(net.minecraft.world.level.Level.OVERWORLD, new Vec3(x, y, z),
        new Vec3(0, 0, normalZ), new Vec3(0, 1, 0), 1, 2);
  }
}
