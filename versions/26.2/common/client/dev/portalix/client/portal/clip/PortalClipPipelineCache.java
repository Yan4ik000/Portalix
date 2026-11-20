package dev.portalix.client.portal.clip;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.ShaderDefines;

public final class PortalClipPipelineCache {
  private static final ShaderDefines CLIPPED =
      ShaderDefines.builder().define("PORTALIX_CLIP").build();
  private static final Map<RenderPipeline, RenderPipeline> VARIANTS = new IdentityHashMap<>();

  private PortalClipPipelineCache() {}

  public static void clear() {
    VARIANTS.clear();
  }

  public static RenderPipeline select(RenderPipeline pipeline) {
    return PortalClipping.active() && !IrisShaders.overrides(pipeline)
        ? clipped(pipeline)
        : pipeline;
  }

  public static boolean usesClipUniform(RenderPipeline pipeline) {
    return pipeline.getBindGroupLayouts().contains(PortalClipping.LAYOUT)
        || IrisShaders.overridesGeometry(pipeline);
  }

  public static RenderPipeline clipped(RenderPipeline pipeline) {
    if (pipeline.getShaderDefines().flags().contains("PORTALIX_CLIP")) return pipeline;
    return VARIANTS.computeIfAbsent(
        pipeline,
        source ->
            new PipelineVariant(source, source.getLocation().withSuffix("_portal_clip"))
                .bindGroupLayout(PortalClipping.LAYOUT)
                .shaderDefines(CLIPPED)
                .build());
  }
}
