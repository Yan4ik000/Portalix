package dev.portalix.client.compat.minecraft;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.portalix.client.platform.ClientPlatform;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

public final class PipelineVariant {
  private final RenderPipeline source;
  private final Identifier location;
  private final Map<ShaderType, Identifier> shaders;
  private ShaderDefines defines;
  private List<BindGroupLayout> layouts;
  private final ColorTargetState[] colors;
  private final VertexFormat[] bindings;
  private PrimitiveTopology topology;

  public PipelineVariant(RenderPipeline source, Identifier location) {
    this.source = source;
    this.location = location;
    shaders = new EnumMap<>(source.getShaders());
    defines = source.getShaderDefines();
    layouts = source.getBindGroupLayouts();
    colors = source.getColorTargetStates().toArray(ColorTargetState[]::new);
    bindings = source.getVertexFormatBindings().toArray(VertexFormat[]::new);
    topology = source.getPrimitiveTopology();
  }

  public PipelineVariant vertexShader(Identifier shader) {
    shaders.put(ShaderType.VERTEX, shader);
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
    bindings[0] = format;
    topology = primitiveTopology;
    return this;
  }

  public PipelineVariant colorFormat(GpuFormat format) {
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

  public Map<ShaderType, Identifier> shaders() {
    return shaders;
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
