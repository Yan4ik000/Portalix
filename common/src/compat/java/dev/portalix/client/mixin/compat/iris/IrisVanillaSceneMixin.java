package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.portalix.client.compat.iris.IrisScene;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.pipeline.VanillaRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.BlockRenderType;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VanillaRenderingPipeline.class)
abstract class IrisVanillaSceneMixin {
  @WrapWithCondition(method = "<init>", at = {
      @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setDisableDirectionalShading(Z)V"),
      @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setUseSeparateAo(Z)V"),
      @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setSeparateEntityDraws(Z)V"),
      @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setVoxelizeLightBlocks(Z)V"),
      @At(value = "INVOKE", target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setBreaksAnisotropy(Z)V")
  }, require = 5, allow = 5)
  private boolean portalix$keepPrimarySettings(WorldRenderingSettings settings, boolean value) {
    return !IrisScene.preparing();
  }

  @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setAmbientOcclusionLevel(F)V"), allow = 1)
  private boolean portalix$keepPrimaryOcclusion(WorldRenderingSettings settings, float value) {
    return !IrisScene.preparing();
  }

  @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setVertexFormat(Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;)V"), allow = 1)
  private boolean portalix$keepPrimaryFormat(WorldRenderingSettings settings, ChunkVertexType format) {
    return !IrisScene.preparing();
  }

  @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE",
      target = "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setBlockTypeIds(Ljava/util/Map;)V"), allow = 1)
  private boolean portalix$keepPrimaryMaterials(WorldRenderingSettings settings, Map<Block, BlockRenderType> ids) {
    return !IrisScene.preparing();
  }
}
