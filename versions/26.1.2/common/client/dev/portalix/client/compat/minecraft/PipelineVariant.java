package dev.portalix.client.compat.minecraft;

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
  private List<RenderPipeline.UniformDescription> uniforms;
  private VertexFormat format;
  private VertexFormat.Mode mode;

  public PipelineVariant(RenderPipeline source, Identifier location) {
    this.source = source;
    this.location = location;
    vertexShader = source.getVertexShader();
    defines = source.getShaderDefines();
    uniforms = source.getUniforms();
    format = source.getVertexFormat();
    mode = source.getVertexFormatMode();
  }

  public PipelineVariant vertexShader(Identifier shader) {
    vertexShader = shader;
    return this;
  }

  public PipelineVariant shaderDefines(ShaderDefines overrides) {
    defines = defines.withOverrides(overrides);
    return this;
  }

  public PipelineVariant uniform(RenderPipeline.UniformDescription uniform) {
    if (!uniforms.contains(uniform)) {
      var extended = new ArrayList<>(uniforms);
      extended.add(uniform);
      uniforms = List.copyOf(extended);
    }
    return this;
  }

  public PipelineVariant geometry(VertexFormat format, VertexFormat.Mode mode) {
    this.format = format;
    this.mode = mode;
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

  public List<RenderPipeline.UniformDescription> uniforms() {
    return uniforms;
  }

  public VertexFormat format() {
    return format;
  }

  public VertexFormat.Mode mode() {
    return mode;
  }
}
