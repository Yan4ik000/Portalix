package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkSkyLightSources.class)
abstract class PortalSkySourceScanMixin {
  @Shadow @Final private int minY;

  @Shadow
  private static boolean isEdgeOccluded(BlockState top, BlockState bottom) {
    throw new AssertionError();
  }

  @WrapMethod(method = "findLowestSourceY")
  private int portalix$surfaceScan(
      ChunkAccess chunk, int topSection, int x, int z, Operation<Integer> original) {
    if (!(chunk instanceof LevelChunk levelChunk)
        || !levelChunk.getLevel().isClientSide()
        || !chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE))
      return original.call(chunk, topSection, x, z);

    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    BlockState top = Blocks.AIR.defaultBlockState();
    while (y > minY) {
      var section = chunk.getSection(chunk.getSectionIndex(y));
      if (section.hasOnlyAir()) {
        top = Blocks.AIR.defaultBlockState();
        y = (y & ~15) - 1;
        continue;
      }
      var bottom = section.getBlockState(x, y & 15, z);
      if (isEdgeOccluded(top, bottom)) return y + 1;
      top = bottom;
      y--;
    }
    return minY;
  }
}
