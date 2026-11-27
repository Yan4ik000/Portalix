package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.portalix.client.portal.clip.PortalClipShaders;
import dev.portalix.client.portal.clip.PortalClipShaders.ClipAbi;
import dev.portalix.client.portal.clip.PortalShaderInterface;
import dev.portalix.client.render.GlslSource;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;

public final class IrisPortalShaders {
  private static final Map<IrisShader, String> irisShaders = new HashMap<>();

  private enum IrisStage {
    VERTEX,
    FRAGMENT,
    GEOMETRY,
    EVALUATION
  }

  private record IrisShader(
      String source,
      IrisStage stage,
      boolean projected,
      boolean shadow,
      boolean terrain,
      boolean fallback,
      IrisShaderAbi.VertexPosition position) {}

  private IrisPortalShaders() {}

  public record Program(
      String vertex, String geometry, String control, String evaluation, String fragment) {}

  public static Program program(
      String vertex,
      String geometry,
      String control,
      String evaluation,
      String fragment,
      ShaderKey key,
      boolean fallback) {
    boolean projected =
        switch (key.getProgram()) {
          case SkyBasic, SkyTextured -> false;
          default -> true;
        };
    boolean shadow = key.isShadow();
    boolean advanced = geometry != null || evaluation != null;
    var position = !projected || fallback || advanced
        ? IrisShaderAbi.VertexPosition.ATTRIBUTE : IrisShaderAbi.position(key, new GlslSource(vertex));
    return new Program(
        advanced
            ? vertex
            : irisShader(vertex, ShaderType.VERTEX, projected, shadow, false, fallback, position),
        irisRasterShader(geometry, true, projected, shadow, false),
        control,
        geometry == null
            ? irisRasterShader(evaluation, false, projected, shadow, false)
            : evaluation,
        irisShader(fragment, ShaderType.FRAGMENT, projected, shadow, false, fallback, position));
  }

  private static String irisShader(
      String source,
      ShaderType type,
      boolean projected,
      boolean shadow,
      boolean terrain,
      boolean fallback,
      IrisShaderAbi.VertexPosition position) {
    if (source == null) return null;
    return irisShaders.computeIfAbsent(
        new IrisShader(
            source,
            type == ShaderType.VERTEX ? IrisStage.VERTEX : IrisStage.FRAGMENT,
            projected,
            shadow,
            terrain,
            fallback,
            position),
        IrisPortalShaders::patchIrisShader);
  }

  private static String irisRasterShader(
      String source, boolean geometry, boolean projected, boolean shadow, boolean terrain) {
    if (source == null) return null;
    var key =
        new IrisShader(
            source,
            geometry ? IrisStage.GEOMETRY : IrisStage.EVALUATION,
            projected,
            shadow,
            terrain,
            false,
            IrisShaderAbi.VertexPosition.ATTRIBUTE);
    return irisShaders.computeIfAbsent(key, shader -> PortalClipShaders.patch(
        shader.source,
        ShaderType.VERTEX,
        true,
        new PortalShaderInterface(shader.projected, shader.terrain,
            "vec4 portalix_UnclippedPosition = gl_Position; ", shader.shadow),
        shader.stage == IrisStage.GEOMETRY,
        ClipAbi.IRIS));
  }

  public static Map<PatchShaderType, String> sodium(Map<PatchShaderType, String> program, boolean shadow) {
    var patched = new EnumMap<PatchShaderType, String>(PatchShaderType.class);
    patched.putAll(program);
    boolean hasGeometry = program.get(PatchShaderType.GEOMETRY) != null;
    if (!hasGeometry && program.get(PatchShaderType.TESS_EVAL) == null) {
      patched.computeIfPresent(PatchShaderType.VERTEX, (stage, source) -> irisShader(
          source, ShaderType.VERTEX, true, shadow, true, false, IrisShaderAbi.VertexPosition.ATTRIBUTE));
    }
    patched.computeIfPresent(PatchShaderType.GEOMETRY,
        (stage, source) -> irisRasterShader(source, true, true, shadow, true));
    if (!hasGeometry) {
      patched.computeIfPresent(PatchShaderType.TESS_EVAL,
          (stage, source) -> irisRasterShader(source, false, true, shadow, true));
    }
    patched.computeIfPresent(PatchShaderType.FRAGMENT, (stage, source) -> irisShader(
        source, ShaderType.FRAGMENT, true, shadow, true, false, IrisShaderAbi.VertexPosition.ATTRIBUTE));
    return patched;
  }

  public static void clear() {
    irisShaders.clear();
  }

  private static String patchIrisShader(IrisShader shader) {
    var type = shader.stage == IrisStage.VERTEX ? ShaderType.VERTEX : ShaderType.FRAGMENT;
    var shaderInterface = type == ShaderType.VERTEX
        ? IrisShaderAbi.vertex(
            new GlslSource(shader.source), shader.projected, shader.shadow, shader.terrain,
            shader.fallback, shader.position)
        : new PortalShaderInterface(shader.projected, shader.terrain, "", shader.shadow);
    boolean needsRegionOffset = type == ShaderType.VERTEX && shader.terrain
        && !new GlslSource(shader.source).hasDeclaration("vec3", "u_RegionOffset");
    String source = needsRegionOffset
        ? shader.source + "\nuniform vec3 u_RegionOffset;\n" : shader.source;
    return PortalClipShaders.patch(
        source,
        type,
        true,
        shaderInterface,
        false,
        shader.fallback ? ClipAbi.IRIS_FALLBACK : ClipAbi.IRIS);
  }
}
