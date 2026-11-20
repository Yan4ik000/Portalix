package dev.portalix.portal.placement;

import dev.portalix.PortalixMod;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.platform.PortalPlatform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockPortalPlacement {
  private static final TagKey<Block> CLEARABLE =
      TagKey.create(Registries.BLOCK, PortalixMod.id("portal_clearable"));
  private static final TagKey<Block> CLEARANCE_BLACKLIST =
      TagKey.create(Registries.BLOCK, PortalixMod.id("portal_clearance_blacklist"));
  private static final int CLEARANCE_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

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
    var availability = new HashMap<BlockPos, Boolean>();
    return PortalPlacementSolver.findPlacement(desired, occupied,
        pos -> availability.computeIfAbsent(pos.immutable(), cell -> availableFor(owner, level, cell, face)));
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
        && level.getWorldBorder().isWithinBounds(front)
        && loaded(level, front)
        && (canRemain(level.getBlockState(front), face)
            || clearable(level.getBlockState(front), level, front));
  }

  private static boolean availableFor(ServerPlayer owner, ServerLevel level, BlockPos pos, Direction face) {
    BlockPos front = pos.relative(face);
    return owner.level() == level
        && owner.isAlive()
        && !owner.isSpectator()
        && available(level, pos, face)
        && owner.mayInteract(level, pos)
        && level.mayInteract(owner, pos)
        && owner.mayInteract(level, front)
        && level.mayInteract(owner, front)
        && (canRemain(level.getBlockState(front), face)
            || owner.mayBuild() && owner.gameMode() != GameType.ADVENTURE);
  }

  public static boolean isValid(
      MinecraftServer server, PortalEndpoint endpoint, PortalSurface.Footprint footprint) {
    ServerLevel level = server.getLevel(endpoint.dimension());
    if (level == null) return false;
    var world = new LoadedBlockRaycast(level);
    var chunks = level.getChunkSource();
    var border = level.getWorldBorder();
    var face = footprint.face();
    for (BlockPos pos : footprint.blocks()) {
      BlockPos front = pos.relative(face);
      if (!border.isWithinBounds(pos) || !border.isWithinBounds(front)) return false;
      var support = chunks.getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
      if (support != null && !supportsPortal(support.getBlockState(pos), world, pos))
        return false;
      int x = front.getX() >> 4;
      int z = front.getZ() >> 4;
      var clearance = x == pos.getX() >> 4 && z == pos.getZ() >> 4
          ? support : chunks.getChunkNow(x, z);
      if (clearance != null && !canRemain(clearance.getBlockState(front), face)) return false;
    }
    return true;
  }

  public static boolean prepare(
      ServerPlayer owner, PortalEndpoint endpoint, PortalSurface.Footprint footprint) {
    return prepare(owner, endpoint, footprint, () -> true);
  }

  public static boolean prepare(
      ServerPlayer owner, PortalEndpoint endpoint, PortalSurface.Footprint footprint,
      BooleanSupplier stillApplicable) {
    ServerLevel level = owner.level().getServer().getLevel(endpoint.dimension());
    if (level == null || level != owner.level()) return false;
    var face = footprint.face();
    var clearing = new ArrayList<Clearance>();
    for (BlockPos pos : footprint.blocks()) {
      BlockPos front = pos.relative(face);
      if (!availableFor(owner, level, pos, face)) return false;
      BlockState state = level.getBlockState(front);
      if (!canRemain(state, face)) clearing.add(new Clearance(front, state));
    }
    if (clearing.isEmpty()) return stillApplicable.getAsBoolean();
    for (var entry : clearing) {
      if (!entry.canClear(level, owner, face)) return false;
      if (!PortalPlatform.INSTANCE.beforeBlockBreak(level, owner, entry.pos(), entry.state())) return false;
    }
    var pending = new ArrayList<Clearance>(clearing.size());
    for (var pos : footprint.blocks()) {
      if (!availableFor(owner, level, pos, face)) return false;
      var front = pos.relative(face);
      var state = level.getBlockState(front);
      if (canRemain(state, face)) continue;
      var entry = new Clearance(front, state);
      if (!clearing.contains(entry)) return false;
      pending.add(entry);
    }
    var air = Blocks.AIR.defaultBlockState();
    var cleared = new LinkedHashMap<Clearance, List<ItemStack>>();
    GameType gameMode = owner.gameMode();
    boolean infiniteMaterials = owner.hasInfiniteMaterials();
    boolean committed = false;
    try {
      for (var entry : pending) {
        var pos = entry.pos();
        if (!entry.canClear(level, owner, face) || !canCommitClearance(owner, gameMode, infiniteMaterials))
          return false;
        // Evaluate loot before replacing this block. Paired plants see their other half only once.
        var drops = infiniteMaterials
            ? List.<ItemStack>of()
            : Block.getDrops(entry.state(), level, pos, null, owner, ItemStack.EMPTY);
        if (!entry.canClear(level, owner, face) || !canCommitClearance(owner, gameMode, infiniteMaterials)
            || !level.setBlock(pos, air, CLEARANCE_UPDATE_FLAGS)) return false;
        cleared.put(entry, drops);
      }
      if (!canPlaceCleared(owner, level, footprint)
          || !cleared.isEmpty() && !canCommitClearance(owner, gameMode, infiniteMaterials)
          || !stillApplicable.getAsBoolean()) return false;
      for (var entry : cleared.keySet())
        if (!loaded(level, entry.pos()) || level.getBlockState(entry.pos()) != air) return false;
      committed = true;
    } finally {
      if (!committed)
        for (var entry : cleared.sequencedKeySet().reversed()) {
          if (!loaded(level, entry.pos()) || level.getBlockState(entry.pos()) != air) continue;
          level.setBlock(entry.pos(), entry.state(), CLEARANCE_UPDATE_FLAGS);
        }
    }
    cleared.forEach((entry, drops) -> entry.publish(level, owner, air, !infiniteMaterials, drops));
    // Neighbor updates and loader callbacks can change the footprint once the breaks are published.
    return canPlaceCleared(owner, level, footprint);
  }

  private static boolean canCommitClearance(ServerPlayer owner, GameType mode, boolean infiniteMaterials) {
    return owner.gameMode() == mode && owner.hasInfiniteMaterials() == infiniteMaterials && owner.mayBuild();
  }

  private static boolean canPlaceCleared(
      ServerPlayer owner, ServerLevel level, PortalSurface.Footprint footprint) {
    var face = footprint.face();
    for (var pos : footprint.blocks())
      if (!availableFor(owner, level, pos, face) || !canRemain(level.getBlockState(pos.relative(face)), face))
        return false;
    return true;
  }

  private record Clearance(BlockPos pos, BlockState state) {
    boolean canClear(ServerLevel level, ServerPlayer owner, Direction face) {
      return availableFor(owner, level, pos.relative(face.getOpposite()), face)
          && level.getBlockState(pos) == state;
    }

    void publish(ServerLevel level, ServerPlayer owner, BlockState air, boolean collectDrops, List<ItemStack> drops) {
      if (!(state.getBlock() instanceof BaseFireBlock))
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
      if (collectDrops) PortalPlatform.INSTANCE.publishBlockDrops(level, owner, pos, state, drops);
      state.affectNeighborsAfterRemoval(level, pos, false);
      level.updateNeighborsAt(pos, state.getBlock());
      int depth = Block.UPDATE_LIMIT - 1;
      state.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
      air.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
      air.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS, depth);
      level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(owner, state));
      PortalPlatform.INSTANCE.afterBlockBreak(level, owner, pos, state);
    }
  }
}
