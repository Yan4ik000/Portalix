package dev.portalix.portal.placement;

import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.platform.PortalPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPortalPlacement {
  private static final TagKey<Block> CLEARABLE =
      TagKey.create(Registries.BLOCK, PortalixMod.id("portal_clearable"));
  private static final TagKey<Block> CLEARANCE_BLACKLIST =
      TagKey.create(Registries.BLOCK, PortalixMod.id("portal_clearance_blacklist"));

  private BlockPortalPlacement() {}

  public static BlockHitResult trace(ServerPlayer owner, Vec3 start, Vec3 end) {
    return LoadedBlockRaycast.cast(owner, start, end);
  }

  public static Optional<PortalEndpoint> find(
      ServerPlayer owner, BlockHitResult hit, float yaw, List<PortalEndpoint> occupied) {
    ServerLevel level = owner.level();
    if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) return Optional.empty();
    var face = hit.getDirection();
    if (!support(level, hit.getBlockPos())) return Optional.empty();
    var desired =
        new PortalEndpoint(
            level.dimension(),
            hit.getLocation(),
            PortalSurface.vector(face),
            PortalSurface.up(face, yaw),
            PortalSurface.WIDTH,
            PortalSurface.HEIGHT);
    return PortalPlacementSolver.findPlacement(
        desired, occupied, pos -> availableFor(owner, pos, face));
  }

  private static boolean support(ServerLevel level, BlockPos pos) {
    if (!loaded(level, pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
    return supportsPortal(level.getBlockState(pos), new LoadedBlockRaycast(level), pos);
  }

  private static boolean supportsPortal(BlockState state, BlockGetter level, BlockPos pos) {
    return !leaves(state)
        && state.getFluidState().isEmpty()
        && Block.isShapeFullBlock(state.getCollisionShape(level, pos))
        && Block.isShapeFullBlock(state.getShape(level, pos));
  }

  private static boolean leaves(BlockState state) {
    return state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES);
  }

  private static boolean loaded(ServerLevel level, BlockPos pos) {
    return level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
  }

  static boolean isRail(BlockState state) {
    return state.getBlock() instanceof BaseRailBlock || state.is(BlockTags.RAILS);
  }

  static boolean canRemain(BlockState state, Direction face) {
    return state.isAir() || face.getAxis().isHorizontal() && isRail(state);
  }

  static boolean clearable(BlockState state, BlockGetter level, BlockPos pos) {
    if (state.isAir()
        || isRail(state)
        || leaves(state)
        || state.is(CLEARANCE_BLACKLIST)
        || !state.getFluidState().isEmpty()
        || state.hasBlockEntity()
        || state.getDestroySpeed(level, pos) < 0) return false;
    Block block = state.getBlock();
    if (block instanceof SnowLayerBlock)
      return state.getValue(SnowLayerBlock.LAYERS) < SnowLayerBlock.MAX_HEIGHT;

    // Class-based defaults also cover blocks supplied by mods that inherit these plant types.
    return state.is(CLEARABLE)
        || block instanceof VegetationBlock
        || block instanceof GrowingPlantBlock
        || block instanceof VineBlock
        || block instanceof HangingRootsBlock
        || block instanceof HangingMossBlock
        || block instanceof MossyCarpetBlock
        || block instanceof BigDripleafBlock
        || block instanceof BigDripleafStemBlock
        || block instanceof SporeBlossomBlock
        || block instanceof SugarCaneBlock
        || state.is(Blocks.MOSS_CARPET);
  }

  private static boolean available(ServerLevel level, BlockPos pos, Direction face) {
    BlockPos front = pos.relative(face);
    return support(level, pos)
        && loaded(level, front)
        && (canRemain(level.getBlockState(front), face)
            || clearable(level.getBlockState(front), level, front));
  }

  private static boolean availableFor(ServerPlayer owner, BlockPos pos, Direction face) {
    ServerLevel level = owner.level();
    BlockPos front = pos.relative(face);
    return owner.isAlive()
        && !owner.isSpectator()
        && available(level, pos, face)
        && owner.mayInteract(level, pos)
        && level.mayInteract(owner, pos)
        && owner.mayInteract(level, front)
        && level.mayInteract(owner, front)
        && (canRemain(level.getBlockState(front), face)
            || owner.mayBuild() && owner.gameMode() != GameType.ADVENTURE);
  }

  public static boolean isValid(MinecraftServer server, PortalEndpoint endpoint) {
    ServerLevel level = server.getLevel(endpoint.dimension());
    if (level == null) return false;
    var footprint = PortalSurface.placementFootprint(endpoint);
    for (BlockPos pos : footprint.blocks()) {
      BlockPos front = pos.relative(footprint.face());
      if (!loaded(level, pos) || !loaded(level, front)) return true;
      if (!available(level, pos, footprint.face())
          || !canRemain(level.getBlockState(front), footprint.face()))
        return false;
    }
    return true;
  }

  public static boolean prepare(ServerPlayer owner, PortalEndpoint endpoint) {
    ServerLevel level = owner.level().getServer().getLevel(endpoint.dimension());
    if (level == null || level != owner.level()) return false;
    var footprint = PortalSurface.placementFootprint(endpoint);
    var clearing = new ArrayList<BlockPos>();
    for (BlockPos pos : footprint.blocks()) {
      BlockPos front = pos.relative(footprint.face());
      if (!availableFor(owner, pos, footprint.face())) return false;
      BlockState state = level.getBlockState(front);
      if (!canRemain(state, footprint.face())) {
        if (!PortalPlatform.INSTANCE.beforeBlockBreak(level, owner, front, state)) return false;
        clearing.add(front);
      }
    }
    for (BlockPos front : clearing) {
      BlockState state = level.getBlockState(front);
      if (canRemain(state, footprint.face())) continue;
      if (!clearable(state, level, front)
          || !level.destroyBlock(front, !owner.hasInfiniteMaterials(), owner)) return false;
      PortalPlatform.INSTANCE.afterBlockBreak(level, owner, front, state);
    }
    return true;
  }
}
