package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.portal.clip.PortalClipShaders;
import dev.portalix.client.portal.clip.PortalShaderInterface;
import dev.portalix.client.render.GlslSource;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.transform.Patch;

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
      IrisShaderAbi.VertexPosition position,
      boolean zeroToOne) {}

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
    boolean terrain = !fallback && key.patch == Patch.SODIUM;
    boolean advanced = geometry != null || evaluation != null;
    var position = !projected || terrain || fallback || advanced
        ? IrisShaderAbi.VertexPosition.ATTRIBUTE : IrisShaderAbi.position(key, new GlslSource(vertex));
    return new Program(
        advanced
            ? vertex
            : irisShader(vertex, ShaderType.VERTEX, projected, shadow, terrain, fallback, position),
        irisRasterShader(geometry, true, projected, shadow, terrain),
        control,
        geometry == null
            ? irisRasterShader(evaluation, false, projected, shadow, terrain)
            : evaluation,
        irisShader(fragment, ShaderType.FRAGMENT, projected, shadow, terrain, fallback, position));
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
            position,
            false),
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
            IrisShaderAbi.VertexPosition.ATTRIBUTE,
            RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
    return irisShaders.computeIfAbsent(
        key,
        shader -> {
          String position =
              shader.shadow && shader.zeroToOne
                  ? "vec4(gl_Position.xy, 2.0 * gl_Position.z - gl_Position.w, gl_Position.w)"
                  : "gl_Position";
          return PortalClipShaders.patch(
                  shader.source,
                  ShaderType.VERTEX,
                  true,
                  new PortalShaderInterface(
                      shader.projected,
                      shader.terrain,
                      "vec4 portalix_UnclippedPosition = " + position + "; ",
                      shader.shadow),
                  shader.stage == IrisStage.GEOMETRY)
              .replace("#ifdef PORTALIX_CLIP", "#if 1")
              .replace("uniform PortalClip", "uniform iris_PortalClip");
        });
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
    String patched = PortalClipShaders.patch(
            shader.source,
            type,
            true,
            shaderInterface,
            false)
        .replace("#ifdef PORTALIX_CLIP", "#if 1");
    return shader.fallback
        ? patched : patched.replace("uniform PortalClip", "uniform iris_PortalClip");
  }
}
