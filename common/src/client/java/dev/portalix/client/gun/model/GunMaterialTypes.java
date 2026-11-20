package dev.portalix.client.gun.model;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.minecraft.GunVertexAbi;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class GunMaterialTypes {
  private static final RenderPipeline ICON =
      RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] {RenderPipelines.ENTITY_EMISSIVE_SNIPPET})
              .withLocation(PortalixMod.id("pipeline/icon"))
              .withFragmentShader(PortalixMod.id("core/gun_icon"))
              .withShaderDefine("NO_OVERLAY")
              .withShaderDefine("NO_CARDINAL_LIGHTING")
              .withColorTargetState(ColorTargetState.DEFAULT)
              .withCull(false)
              .build());
  private static final Map<Identifier, RenderType> icons = new HashMap<>();
  private static final RenderPipeline WORLD =
      RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] {RenderPipelines.ENTITY_SNIPPET})
              .withLocation(PortalixMod.id("pipeline/world"))
              .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
              .withColorTargetState(ColorTargetState.DEFAULT)
              .withCull(false)
              .build());
  private static final Map<Identifier, RenderType> worlds = new HashMap<>();
  private static final Map<Identifier, RenderType> emissiveTypes = new HashMap<>();
  private static final RenderPipeline ADDITIVE = RenderPipelines.register(additiveEntity()
      .withLocation(PortalixMod.id("pipeline/additive"))
      .withColorTargetState(
          new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)))
      .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
      .build());
  private static final OitPipelineSet OIT_ADDITIVE = OitPipelineSet.builder(
      "portalix_gun_additive", additiveEntity().withShaderDefine("OIT_ADDITIVE")).build();
  private static final Map<Identifier, RenderType> additiveTypes = new HashMap<>();
  private static final RenderPipeline INDICATOR =
      RenderPipelines.register(
          RenderPipeline.builder()
              .withLocation(PortalixMod.id("pipeline/gun_indicator"))
              .withVertexShader(PortalixMod.id("core/gun_indicator"))
              .withFragmentShader(PortalixMod.id("core/gun_indicator"))
              .withBindGroupLayout(BindGroupLayouts.PROJECTION)
              .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
              .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
              .withVertexBinding(0, GunVertexAbi.FORMAT)
              .withPrimitiveTopology(PrimitiveTopology.QUADS)
              .withColorTargetState(new ColorTargetState(
                  Optional.of(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)),
                  GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
              .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
              .withCull(false)
              .build());
  private static final Map<Identifier, RenderType> indicators = new HashMap<>();

  private GunMaterialTypes() {}

  static RenderType world(Identifier texture) {
    return worlds.computeIfAbsent(
        texture,
        t ->
            RenderType.create(
                "portalix_world",
                entitySetup(WORLD, t).createRenderSetup()));
  }

  static RenderType icon(Identifier texture) {
    return icons.computeIfAbsent(
        texture,
        t ->
            RenderType.create(
                "portalix_icon",
                entitySetup(ICON, t).createRenderSetup()));
  }

  static RenderType emissive(Identifier texture) {
    return emissiveTypes.computeIfAbsent(
        texture,
        t ->
            RenderType.create(
                "portalix_emissive",
                entitySetup(RenderPipelines.EYES, t).setOitPipelines(RenderPipelines.OIT_EYES)
                    .sortOnUpload().createRenderSetup()));
  }

  static RenderType additive(Identifier texture) {
    return additiveTypes.computeIfAbsent(
        texture,
        t -> RenderType.create(
            "portalix_gun_additive",
            entitySetup(ADDITIVE, t).setOitPipelines(OIT_ADDITIVE).createRenderSetup()));
  }

  static RenderType indicator(Identifier texture) {
    return indicators.computeIfAbsent(texture, t -> RenderType.create(
        "portalix_gun_indicator",
        RenderSetup.builder(INDICATOR).withTexture("Sampler0", t).createRenderSetup()));
  }

  private static RenderSetup.RenderSetupBuilder entitySetup(
      RenderPipeline pipeline, Identifier texture) {
    // Iris shadow programs consume the complete entity sampler ABI, including emissive materials.
    return RenderSetup.builder(pipeline)
        .withTexture("Sampler0", texture)
        .useOverlay()
        .useLightmap();
  }

  private static RenderPipeline.Builder additiveEntity() {
    return RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
        .withShaderDefine("NO_OVERLAY")
        .withShaderDefine("NO_CARDINAL_LIGHTING")
        .withCull(false);
  }

  public static void clear() {
    icons.clear();
    worlds.clear();
    emissiveTypes.clear();
    additiveTypes.clear();
    indicators.clear();
  }
}
