package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.portalix.client.compat.TerrainIntegration;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
abstract class PortalClipPrecompileMixin {
  @ModifyExpressionValue(method = "lambda$reload$1", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/RenderPipelines;optionalPipelines()Ljava/util/List;"))
  private static List<RenderPipeline> portalix$optionalClippedPipelines(List<RenderPipeline> optional) {
    var required = RenderPipelines.requiredPipelines();
    var pipelines = new ArrayList<RenderPipeline>(optional.size() * 2 + required.size());
    pipelines.addAll(optional);
    var terrain = TerrainIntegration.INSTANCE;
    var renderer = Minecraft.getInstance().levelRenderer;
    if (terrain != null && renderer != null) terrain.visitTerrainPipelines(renderer, pipelines::add);
    int ordinaryCount = pipelines.size();
    // The current terrain renderer can still draw behind the resource reload overlay.
    PortalClipPipelineCache.retainVariantsFor(pipelines.subList(optional.size(), ordinaryCount));
    for (var pipeline : required) pipelines.add(PortalClipPipelineCache.clipped(pipeline));
    for (int i = 0; i < ordinaryCount; i++) pipelines.add(PortalClipPipelineCache.clipped(pipelines.get(i)));
    return pipelines;
  }
}
