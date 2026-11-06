package dev.portalix.test;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.carry.CarryBindings;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.PortalPassagePose;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class RegressionWorld implements AutoCloseable {
  final ServerLevel level;
  final ServerPlayer player;
  final EmbeddedChannel channel;
  private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
  private final List<Entity> entities = new ArrayList<>();
  private final List<ChunkPos> forcedChunks = new ArrayList<>();
  private final List<PortalLink> originalLinks;
  private final Vec3 playerPosition;
  private final Vec3 playerVelocity;
  private final float playerYaw;
  private final float playerPitch;
  private final GameType playerMode;
  private final ItemStack mainHand;
  private final ItemStack offHand;
  private final Pose playerPose;
  private final float playerScale;
  private final boolean playerSneaking;
  private final double playerFallDistance;
  private long identity;

  RegressionWorld(ServerLevel level, ServerPlayer player, EmbeddedChannel channel) {
    this.level = level;
    this.player = player;
    this.channel = channel;
    originalLinks = PortalTopology.links(level);
    playerPosition = player.position();
    playerVelocity = player.getDeltaMovement();
    playerYaw = player.getYRot();
    playerPitch = player.getXRot();
    playerMode = player.gameMode();
    mainHand = player.getMainHandItem().copy();
    offHand = player.getOffhandItem().copy();
    playerPose = player.getPose();
    playerScale = PortalPassagePose.scale(player);
    playerSneaking = player.isShiftKeyDown();
    playerFallDistance = player.fallDistance;
    try {
      for (int x = 7; x <= 13; x++) {
        for (int z = 7; z <= 13; z++) {
          var chunk = new ChunkPos(x, z);
          if (!level.getForceLoadedChunks().contains(chunk.pack())) {
            forcedChunks.add(chunk);
            level.setChunkForced(x, z, true);
          }
          level.getChunk(x, z);
        }
      }
      player.setGameMode(GameType.CREATIVE);
      player.snapTo(152.5, 104, 152.5, 0, 0);
      PortalTopology.updateLinks(level, List.of());
    } catch (RuntimeException | Error failure) {
      try { close(); }
      catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  boolean ready() {
    for (int x = 7; x <= 13; x++)
      for (int z = 7; z <= 13; z++)
        if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z))) return false;
    return true;
  }

  PortalEndpoint endpoint(Direction face, Vec3 base) {
    Vec3 normal = PortalSurface.vector(face);
    Vec3 up = PortalSurface.up(face, 0);
    return new PortalEndpoint(level.dimension(), base.add(up.cross(normal).scale(.5)),
        normal, up, 1, 2);
  }

  void surface(PortalEndpoint endpoint, BlockState support, BlockState clearance) {
    clear(endpoint.center(), 3);
    var footprint = PortalSurface.placementFootprint(endpoint);
    for (BlockPos pos : footprint.blocks()) {
      block(pos, support);
    }
    for (BlockPos pos : footprint.blocks()) block(pos.relative(footprint.face()), clearance);
  }

  void clear(Vec3 center, int radius) {
    BlockPos base = BlockPos.containing(center);
    for (BlockPos pos : BlockPos.betweenClosed(base.offset(-radius, -radius, -radius),
        base.offset(radius, radius, radius))) block(pos, Blocks.AIR.defaultBlockState());
  }

  void block(BlockPos pos, BlockState state) {
    BlockPos key = pos.immutable();
    blocks.putIfAbsent(key, level.getBlockState(key));
    level.setBlock(key, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
  }

  PortalLink link(PortalEndpoint entrance, PortalEndpoint exit) {
    var blue = new PortalInstance(entrance, level.getGameTime(), ++identity, PortalixVariant.STANDARD);
    var orange = new PortalInstance(exit, level.getGameTime(), ++identity, PortalixVariant.STANDARD);
    return new PortalLink(UUID.randomUUID(), Math.max(blue.identity(), orange.identity()), blue, orange);
  }

  void publish(PortalLink link) {
    PortalTopology.updateLinks(level, List.of(link, link.inverse()));
  }

  <T extends Entity> T add(T entity) {
    ServerRegressionSuite.check(level.addFreshEntity(entity), "Could not add test entity " + entity);
    entities.add(entity);
    entity.setNoGravity(true);
    return entity;
  }

  ItemEntity item(Vec3 center) {
    var entity = add(new ItemEntity(level, center.x, center.y, center.z,
        new ItemStack(Items.IRON_INGOT)));
    entity.setPos(center.subtract(0, entity.getBbHeight() / 2, 0));
    entity.setDeltaMovement(Vec3.ZERO);
    entity.setNeverPickUp();
    entity.setOldPosAndRot();
    return entity;
  }

  void reset() {
    CarryBindings.unbindOwner(level, player.getId());
    for (Entity entity : entities) entity.discard();
    entities.clear();
    PortalTopology.updateLinks(level, List.of());
    for (var entry : blocks.entrySet())
      level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    blocks.clear();
    PortalPassagePose.set(player, false);
    player.setPose(Pose.STANDING);
    player.setShiftKeyDown(false);
    player.setSprinting(false);
    player.setGameMode(GameType.CREATIVE);
  }

  @Override
  public void close() {
    try {
      reset();
      PortalTopology.updateLinks(level, originalLinks);
      player.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
      player.setItemSlot(EquipmentSlot.OFFHAND, offHand);
      player.setGameMode(playerMode);
      player.setPose(playerPose);
      PortalPassagePose.set(player, playerScale);
      player.setShiftKeyDown(playerSneaking);
      player.fallDistance = playerFallDistance;
      PlayerPortalVisibility.of(player).retainLinks(originalLinks);
      player.connection.teleport(playerPosition.x, playerPosition.y, playerPosition.z,
          playerYaw, playerPitch);
      player.setDeltaMovement(playerVelocity);
    } finally {
      for (var chunk : forcedChunks) level.setChunkForced(chunk.x(), chunk.z(), false);
    }
  }
}
