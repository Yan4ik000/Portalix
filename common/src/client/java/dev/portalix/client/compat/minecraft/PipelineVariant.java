package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.client.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

public final class PipelineVariant {
  private final RenderPipeline source;
  private final Identifier location;
  private Identifier vertexShader;
  private ShaderDefines defines;
  private List<BindGroupLayout> layouts;
  private ColorTargetState[] colors;
  private VertexFormat[] bindings;
  private PrimitiveTopology topology;

  public PipelineVariant(RenderPipeline source, Identifier location) {
    this.source = source;
    this.location = location;
    vertexShader = source.getVertexShader();
    defines = source.getShaderDefines();
    layouts = source.getBindGroupLayouts();
    colors = source.getColorTargetStates();
    bindings = source.getVertexFormatBindings();
    topology = source.getPrimitiveTopology();
  }

  public PipelineVariant vertexShader(Identifier shader) {
    vertexShader = shader;
    return this;
  }

  public PipelineVariant shaderDefines(ShaderDefines overrides) {
    defines = defines.withOverrides(overrides);
    return this;
  }

  public PipelineVariant bindGroupLayout(BindGroupLayout layout) {
    if (!layouts.contains(layout)) {
      var extended = new ArrayList<>(layouts);
      extended.add(layout);
      layouts = List.copyOf(extended);
    }
    return this;
  }

  public PipelineVariant geometry(VertexFormat format, PrimitiveTopology primitiveTopology) {
    bindings =
        RenderPipeline.builder().withVertexBinding(0, format).buildSnippet().vertexFormatPerBuffer();
    topology = primitiveTopology;
    return this;
  }

  public PipelineVariant colorFormat(GpuFormat format) {
    colors = colors.clone();
    var color = colors[0];
    colors[0] = new ColorTargetState(color.blendFunction(), format, color.writeMask());
    return this;
  }

  public RenderPipeline build() {
    return ClientPlatform.INSTANCE.pipeline(this);
  }

  public RenderPipeline source() {
    return source;
  }

  public Identifier location() {
    return location;
  }

  public Identifier vertexShader() {
    return vertexShader;
  }

  public ShaderDefines defines() {
    return defines;
  }

  public List<BindGroupLayout> layouts() {
    return layouts;
  }

  public ColorTargetState[] colors() {
    return colors;
  }

  public VertexFormat[] bindings() {
    return bindings;
  }

  public PrimitiveTopology topology() {
    return topology;
  }
}
