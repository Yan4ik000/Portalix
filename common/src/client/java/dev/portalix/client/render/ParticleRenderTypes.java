package dev.portalix.client.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.portalix.PortalixMod;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class ParticleRenderTypes {
  private static final Map<SourceRenderKey, RenderType> sourceTypes = new HashMap<>();
  private static final Map<SourcePipelineKey, ParticlePipelines> sourcePipelines = new HashMap<>();

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
    var pipelines =
        sourcePipelines.computeIfAbsent(pipelineKey, ParticleRenderTypes::createSourcePipeline);
    var setup = RenderSetup.builder(pipelines.classic).withTexture("Sampler0", key.texture);
    setup.setOitPipelines(pipelines.oit);
    return RenderType.create(
        "portalix_native_particle",
        setup.createRenderSetup());
  }

  private static ParticlePipelines createSourcePipeline(SourcePipelineKey key) {
    String name = "source_particle_" + key.overbright + (key.ignoreDepth ? "_noz" : "")
        + "_" + key.mode.name().toLowerCase(Locale.ROOT) + (key.linear ? "_linear" : "");
    var builder = RenderPipeline.builder()
        .withFragmentShader(PortalixMod.id("core/source_particle"))
        .withVertexShader(PortalixMod.id("core/source_particle"))
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withShaderDefine("SOURCE_OVERBRIGHT", key.overbright)
        .withShaderDefine("SOURCE_SPRITECARD", key.mode != SourceMode.ADDITIVE ? 1 : 0)
        .withShaderDefine("SOURCE_TINTED", key.mode == SourceMode.TINTED ? 1 : 0)
        .withShaderDefine("SOURCE_LINEAR_OUTPUT", key.linear ? 1 : 0)
        .withCull(false);
    var colorTarget = new ColorTargetState(
        Optional.of(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)),
        key.linear ? GpuFormat.RGBA16_FLOAT : GpuFormat.RGBA8_UNORM,
        ColorTargetState.WRITE_ALL);
    var oitBuilder = OitPipelineSet.builder(
        "portalix_" + name, builder.withShaderDefine("OIT_ADDITIVE"));
    if (key.ignoreDepth) oitBuilder.withoutDepthTest();
    if (key.linear) oitBuilder.withAccumulateModifier(accumulate -> accumulate.withColorTargetState(colorTarget));
    var classic = RenderPipelines.register(builder
            .withLocation(PortalixMod.id("pipeline/" + name))
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withColorTargetState(colorTarget)
            .withDepthStencilState(
                new DepthStencilState(
                    key.ignoreDepth ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL,
                    false))
            .build());
    return new ParticlePipelines(classic, oitBuilder.build());
  }

  public static void clear() {
    sourceTypes.clear();
  }

  private record ParticlePipelines(RenderPipeline classic, OitPipelineSet oit) {}

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
