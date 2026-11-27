package dev.portalix.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.PortalixMod;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class ParticleRenderTypes {
  private static final RenderPipeline ADDITIVE =
      RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] {RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
              .withLocation(PortalixMod.id("pipeline/additive"))
              .withShaderDefine("NO_OVERLAY")
              .withShaderDefine("NO_CARDINAL_LIGHTING")
              .withColorTargetState(
                  new ColorTargetState(BlendFunction.LIGHTNING))
              .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final Map<Identifier, RenderType> additiveTypes = new HashMap<>();
  private static final Map<SourceRenderKey, RenderType> sourceTypes = new HashMap<>();
  private static final Map<SourcePipelineKey, RenderPipeline> sourcePipelines = new HashMap<>();

  public static RenderType source(Identifier texture, int overbright, boolean ignoreDepth) {
    return source(texture, overbright, ignoreDepth, SourceMode.ADDITIVE, false);
  }

  public static RenderType spritecard(Identifier texture, int overbright, boolean ignoreDepth) {
    return source(texture, overbright, ignoreDepth, SourceMode.SPRITECARD, false);
  }

  public static RenderType linearSpritecard(Identifier texture, int overbright, boolean ignoreDepth) {
    return source(texture, overbright, ignoreDepth, SourceMode.SPRITECARD, true);
  }

  public static RenderType linearTintedParticle(
      Identifier texture, int overbright, boolean ignoreDepth) {
    return source(texture, overbright, ignoreDepth, SourceMode.TINTED, true);
  }

  private static RenderType source(
      Identifier texture, int overbright, boolean ignoreDepth, SourceMode mode, boolean linear) {
    var key = new SourceRenderKey(texture, overbright, ignoreDepth, mode, linear);
    return sourceTypes.computeIfAbsent(key, ParticleRenderTypes::createSourceType);
  }

  private static RenderType createSourceType(SourceRenderKey key) {
    var pipelineKey = new SourcePipelineKey(key.overbright, key.ignoreDepth, key.mode, key.linear);
    var pipeline =
        sourcePipelines.computeIfAbsent(pipelineKey, ParticleRenderTypes::createSourcePipeline);
    return RenderType.create(
        "portalix_native_particle",
        RenderSetup.builder(pipeline).withTexture("Sampler0", key.texture).createRenderSetup());
  }

  private static RenderPipeline createSourcePipeline(SourcePipelineKey key) {
    return RenderPipelines.register(
        RenderPipeline.builder(new Snippet[] {RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
            .withLocation(
                PortalixMod.id(
                    "pipeline/source_particle_"
                        + key.overbright
                        + (key.ignoreDepth ? "_noz" : "")
                        + "_"
                        + key.mode.name().toLowerCase(Locale.ROOT)
                        + (key.linear ? "_linear" : "")))
            .withFragmentShader(PortalixMod.id("core/source_particle"))
            .withVertexShader(PortalixMod.id("core/source_particle"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withShaderDefine("SOURCE_OVERBRIGHT", key.overbright)
            .withShaderDefine("SOURCE_SPRITECARD", key.mode != SourceMode.ADDITIVE ? 1 : 0)
            .withShaderDefine("SOURCE_TINTED", key.mode == SourceMode.TINTED ? 1 : 0)
            .withShaderDefine("SOURCE_LINEAR_OUTPUT", key.linear ? 1 : 0)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(
                new DepthStencilState(
                    key.ignoreDepth ? CompareOp.ALWAYS_PASS : CompareOp.LESS_THAN_OR_EQUAL,
                    false))
            .withCull(false)
            .build());
  }

  public static RenderType additive(Identifier texture) {
    return additiveTypes.computeIfAbsent(
        texture,
        t ->
            RenderType.create(
                "portalix_additive",
                RenderSetup.builder(ADDITIVE).withTexture("Sampler0", t).createRenderSetup()));
  }

  public static void clear() {
    additiveTypes.clear();
    sourceTypes.clear();
  }

  private record SourceRenderKey(
      Identifier texture, int overbright, boolean ignoreDepth, SourceMode mode, boolean linear) {}

  private record SourcePipelineKey(
      int overbright, boolean ignoreDepth, SourceMode mode, boolean linear) {}

  private enum SourceMode {
    ADDITIVE,
    SPRITECARD,
    TINTED
  }
}
