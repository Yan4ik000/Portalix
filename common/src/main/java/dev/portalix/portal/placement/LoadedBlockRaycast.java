package dev.portalix.portal.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class LoadedBlockRaycast implements BlockGetter {
  private final ServerLevel level;

  LoadedBlockRaycast(ServerLevel level) {
    this.level = level;
  }

  static BlockHitResult cast(ServerPlayer owner, Vec3 start, Vec3 end) {
    var world = new LoadedBlockRaycast(owner.level());
    var context =
        new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner);
    return BlockGetter.traverseBlocks(
        start,
        end,
        context,
        (ray, pos) -> {
          if (world.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            AABB cell = new AABB(pos);
            Vec3 boundary = cell.contains(start) ? start : cell.clip(start, end).orElse(end);
            return miss(start, boundary);
          }
          BlockState state = world.getBlockState(pos);
          if (state.isAir()
              || BlockPortalPlacement.isRail(state)
              || BlockPortalPlacement.clearable(state, world, pos)) return null;
          return world.clipWithInteractionOverride(
              start, end, pos, ray.getBlockShape(state, world, pos), state);
        },
        ray -> miss(start, end));
  }

  private static BlockHitResult miss(Vec3 start, Vec3 end) {
    return BlockHitResult.miss(
        end, Direction.getApproximateNearest(start.subtract(end)), BlockPos.containing(end));
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
    return chunk == null ? null : chunk.getBlockEntity(pos);
  }

  @Override
  public int getHeight() {
    return level.getHeight();
  }

  @Override
  public int getMinY() {
    return level.getMinY();
  }
}
