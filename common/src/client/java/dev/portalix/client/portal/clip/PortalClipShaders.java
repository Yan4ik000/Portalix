package dev.portalix.client.portal.clip;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.compat.minecraft.MinecraftShaderAbi;
import dev.portalix.client.compat.sodium.SodiumShaderAbi;
import dev.portalix.client.render.GlslSource;
import net.minecraft.resources.Identifier;

public final class PortalClipShaders {
  private PortalClipShaders() {}

  public static String shader(Identifier shader, String source, ShaderType type) {
    boolean hardwareClipping =
        RenderSystem.getDevice().getDeviceInfo().backendName().equals("OpenGL");
    return patch(source, type, hardwareClipping, null, false, shader);
  }

  public static String patch(
      String source,
      ShaderType type,
      boolean hardwareClipping,
      PortalShaderInterface shaderInterface,
      boolean geometryStage) {
    return patch(source, type, hardwareClipping, shaderInterface, geometryStage, null);
  }

  static String patch(
      String source,
      ShaderType type,
      boolean hardwareClipping,
      PortalShaderInterface shaderInterface,
      boolean geometryStage,
      Identifier shader) {
    if (source == null) return null;
    var parsed = new GlslSource(source);
    int main = parsed.function("void", "main");
    if (main < 0) return source;
    if (type == ShaderType.VERTEX && hardwareClipping) {
      source = PortalShaderSource.clipDistances(source);
      parsed = new GlslSource(source);
      main = parsed.function("void", "main");
    }
    String originalMain = parsed.uniqueIdentifier("portalix_main");
    String renamed = source.substring(0, main) + originalMain + source.substring(main + 4);
    if (type == ShaderType.VERTEX) {
      var layout =
          shaderInterface != null
              ? shaderInterface
              : SodiumShaderAbi.terrain(shader)
                  ? SodiumShaderAbi.vertex(parsed) : MinecraftShaderAbi.vertex(shader, parsed);
      boolean projected = layout.projected();
      boolean unwarped =
          parsed.hasIdentifier("portalix_UnclippedPosition")
              || layout.vertexCorrection().contains("portalix_UnclippedPosition");
      String position = unwarped ? "portalix_UnclippedPosition" : "gl_Position";
      if (!layout.homogeneous()) position = "vec4(" + position + ".xy, " + position + ".w, 1.0)";
      boolean correction = !layout.vertexCorrection().isEmpty();
      String declarations =
          "\n"
              + (correction ? "" : "#ifdef PORTALIX_CLIP\n")
              + "layout(std140) uniform PortalClip { vec4 portalix_Plane; vec4 portalix_EntityPlane;"
              + " vec4 portalix_Edges[4]; vec4 portalix_Mode; vec4 portalix_CameraFraction; };\n"
              + (correction ? "#ifdef PORTALIX_CLIP\n" : "")
              + "out float portalix_EntityDistance;\nout vec4 portalix_Aperture;\n"
              + "flat out float portalix_EntityMode;\n"
              + "flat out float portalix_Oval;\n"
              + "out vec3 portalix_ProjectedPosition;\nflat out vec4 portalix_ScenePlane;\n"
              + "flat out float portalix_Terrain;\n"
              + (hardwareClipping ? "" : "out float portalix_Distance;\n")
              + "#endif\n";
      String assignments =
          layout.vertexCorrection()
              + "\n#ifdef PORTALIX_CLIP\n"
              + (hardwareClipping ? "gl_ClipDistance[0]" : "portalix_Distance")
              + " = "
              + (projected ? "dot(portalix_Plane, " + position + ")" : "1.0")
              + "; "
              + "portalix_EntityDistance = "
              + (projected ? "dot(portalix_EntityPlane, " + position + ")" : "1.0")
              + "; "
              + "portalix_EntityMode = "
              + (projected ? "portalix_Mode.x" : "0.0")
              + "; "
              + "portalix_Oval = portalix_Mode.y; "
              + "portalix_ProjectedPosition = "
              + position
              + ".xyz; "
              + "portalix_ScenePlane = portalix_Plane; "
              + "portalix_Terrain = "
              + (layout.terrain() ? "1.0" : "0.0")
              + "; "
              + "portalix_Aperture = "
              + (projected
                  ? "vec4(dot(portalix_Edges[0], "
                      + position
                      + "),"
                      + "dot(portalix_Edges[1], "
                      + position
                      + "),"
                      + "dot(portalix_Edges[2], "
                      + position
                      + "),"
                      + "dot(portalix_Edges[3], "
                      + position
                      + "))"
                  : "vec4(1.0)")
              + "; "
              + (hardwareClipping
                  ? "gl_ClipDistance[1] = portalix_EntityMode < 0.0 ? 1.0 : portalix_EntityDistance; "
                  : "")
              + "\n#endif\n";
      if (geometryStage) {
        String clip = parsed.uniqueIdentifier("portalix_clip");
        return PortalShaderSource.geometry(source, clip)
            + declarations
            + "void " + clip + "() { "
            + assignments
            + "}\n";
      }
      return renamed + declarations + "void main() { " + originalMain + "(); " + assignments + "}\n";
    }
    return renamed
        + "\n#ifdef PORTALIX_CLIP\nin float portalix_EntityDistance;\nin vec4 portalix_Aperture;\n"
        + "flat in float portalix_EntityMode;\n"
        + "flat in float portalix_Oval;\n"
        + "in vec3 portalix_ProjectedPosition;\nflat in vec4 portalix_ScenePlane;\n"
        + "flat in float portalix_Terrain;\n"
        + (hardwareClipping ? "" : "in float portalix_Distance;\n")
        + "void main() { "
        + "if (portalix_Terrain > 0.0 && dot(portalix_ScenePlane.xyz, portalix_ScenePlane.xyz) > 0.0) { "
        + "vec3 planeNormal = portalix_ScenePlane.xyz; "
        + "vec3 faceNormal = cross(dFdx(portalix_ProjectedPosition), dFdy(portalix_ProjectedPosition)); "
        + "float faceLength = dot(faceNormal, faceNormal); "
        + "float parallel = dot(faceNormal, planeNormal); "
        + "float distance = dot(portalix_ScenePlane, vec4(portalix_ProjectedPosition, 1.0)); "
        + "float error = 9.5367431640625e-7 * dot(abs(portalix_ScenePlane), vec4(abs(portalix_ProjectedPosition), 1.0)); "
        + "if (faceLength > 0.0 && abs(distance) <= error "
        + "&& parallel * parallel >= (1.0 - 9.5367431640625e-7) * faceLength * dot(planeNormal, planeNormal)) discard; }\n"
        + (hardwareClipping
            ? ""
            : "if (portalix_Distance < 0.0 || (portalix_EntityMode >= 0.0 && portalix_EntityDistance < 0.0)) discard;\n")
        + "bool inside = all(greaterThanEqual(portalix_Aperture, vec4(0.0)));\n"
        + "if (portalix_Oval > 0.0) inside = portalix_Aperture.z >= 0.0 && dot(portalix_Aperture.xy, portalix_Aperture.xy) <= portalix_Aperture.z * portalix_Aperture.z;\n"
        + "if (portalix_EntityMode < 0.0 && portalix_EntityDistance < 0.0 && inside) discard;\n"
        + "if (portalix_EntityMode > 0.0 && !inside) discard;\n"
        + originalMain + "(); }\n#else\nvoid main() { " + originalMain + "(); }\n#endif\n";
  }
}
