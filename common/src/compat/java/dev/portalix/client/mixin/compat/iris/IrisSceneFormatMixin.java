package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.iris.IrisScene;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IrisRenderingPipeline.class)
abstract class IrisSceneFormatMixin {
  @WrapOperation(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings;setVertexFormat(Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType;)V"),
      allow = 1)
  private void portalix$sharedTerrainFormat(
      WorldRenderingSettings settings, ChunkVertexType format, Operation<Void> original) {
    if (!IrisScene.preparing()) original.call(settings, format);
  }
}
