package dev.portalix.client.portal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.RenderTargetDescriptor.TextureProperties;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

final class OitPortalRefraction implements AutoCloseable {
  static final RenderPipeline WAVE = RenderPipelines.register(
      RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET, RenderPipelines.OIT_SNIPPET)
          .withLocation(PortalixMod.id("pipeline/portal_oit_refraction"))
          .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
          .withVertexShader(PortalixMod.id("core/portal_particle"))
          .withFragmentShader(PortalixMod.id("core/portal_oit_refraction"))
          .withBindGroupLayout(BindGroupLayouts.OIT_COEFFS_DEPTH_BOUNDS_SAMPLER)
          .withBindGroupLayout(samplers("OpaqueColor", "OpaqueDepth", "CompositedColor"))
          .withColorTargetState(new ColorTargetState(
              Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
          .withDepthStencilState(Optional.empty())
          .withCull(false)
          .build());

  private final GraphicsResourceAllocator resources;
  private final RenderTarget target;
  private final List<Wave> waves;
  private final Target opaque;

  OitPortalRefraction(GraphicsResourceAllocator resources, RenderTarget target, List<Wave> waves) {
    this.resources = resources;
    this.target = target;
    this.waves = waves;
    opaque = acquire(target.getColorTexture().getFormat(),
        new TextureProperties(null, target.getDepthTexture().getFormat()));
  }

  void captureOpaque() {
    opaque.texture.copyColorFrom(target);
    opaque.texture.copyDepthFrom(target);
  }

  record Wave(PreparedRenderType material, StagedVertexBuffer.ExecuteInfo geometry) {}

  void draw(GpuTextureView depthBounds, List<GpuTextureView> transmittance) {
    try (var composited = acquire(target.getColorTexture().getFormat(), null)) {
      draw(depthBounds, transmittance, composited.texture);
    }
  }

  private void draw(GpuTextureView depthBounds, List<GpuTextureView> transmittance,
      RenderTarget composited) {
    composited.copyColorFrom(target);
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    try (var pass = encoder.createRenderPass(
        () -> "Portal refraction", target.getColorTextureView(), Optional.empty())) {
      pass.setUniform("OpaqueColor", opaque.texture.getColorTextureView(), linear);
      pass.setUniform("OpaqueDepth", opaque.texture.getDepthTextureView(), nearest);
      pass.setUniform("CompositedColor", composited.getColorTextureView(), linear);
      pass.setUniform("DepthBoundsSampler", depthBounds, nearest);
      for (int i = 0; i < transmittance.size(); i++)
        pass.setUniform("Coeff" + i, transmittance.get(i), nearest);
      var pipeline = IrisEffectTarget.pipeline(WAVE, target.getColorTexture().getFormat());
      for (var wave : waves) {
        var type = wave.material;
        if (pipeline != WAVE)
          type = new PreparedRenderType(type.name(), pipeline, null, type.dynamicTransforms(),
              type.scissorState(), type.textures());
        type.drawFromBuffer(wave.geometry, pass);
      }
    }
  }

  private Target acquire(GpuFormat color, TextureProperties depth) {
    var descriptor = new RenderTargetDescriptor(target.width, target.height,
        new TextureProperties(null, color), depth);
    return new Target(resources, descriptor, resources.acquire(descriptor));
  }

  private record Target(GraphicsResourceAllocator resources, RenderTargetDescriptor descriptor,
      RenderTarget texture) implements AutoCloseable {
    @Override
    public void close() {
      resources.release(descriptor, texture);
    }
  }

  private static BindGroupLayout samplers(String... names) {
    var builder = BindGroupLayout.builder();
    for (String name : names) builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
    return builder.build();
  }

  @Override
  public void close() {
    opaque.close();
  }
}
