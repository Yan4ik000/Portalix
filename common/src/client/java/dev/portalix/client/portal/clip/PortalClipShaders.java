package dev.portalix.client.portal.clip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.portalix.client.compat.minecraft.MinecraftShaderAbi;
import dev.portalix.client.compat.sodium.SodiumShaderAbi;
import dev.portalix.client.render.GlslSource;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

public final class PortalClipShaders {
  private static final Pattern LOCATION = Pattern.compile("\\blocation\\s*=\\s*(\\d+)");

  public enum ClipAbi {
    VANILLA(PortalClipping.UNIFORM_NAME, "#ifdef PORTALIX_CLIP\n"),
    IRIS("iris_" + PortalClipping.UNIFORM_NAME, "#if 1\n"),
    IRIS_FALLBACK(PortalClipping.UNIFORM_NAME, "#if 1\n");

    private final String uniformName;
    private final String guard;

    ClipAbi(String uniformName, String guard) {
      this.uniformName = uniformName;
      this.guard = guard;
    }
  }

  private PortalClipShaders() {}

  public static int firstVaryingLocation(String vertexSource) {
    var locations = LOCATION.matcher(vertexSource);
    int next = 0;
    while (locations.find()) next = Math.max(next, 1 + Integer.parseInt(locations.group(1)));
    return next;
  }

  public static String shader(Identifier shader, String source, ShaderType type, int location) {
    boolean hardwareClipping =
        RenderSystem.getDevice().getDeviceInfo().backendName().equals("OpenGL");
    return patch(source, type, hardwareClipping, null, false, ClipAbi.VANILLA, shader, location);
  }

  public static String patch(
      String source,
      ShaderType type,
      boolean hardwareClipping,
      PortalShaderInterface shaderInterface,
      boolean geometryStage,
      ClipAbi abi) {
    return patch(source, type, hardwareClipping, shaderInterface, geometryStage, abi, null, -1);
  }

  private static String patch(
      String source,
      ShaderType type,
      boolean hardwareClipping,
      PortalShaderInterface shaderInterface,
      boolean geometryStage,
      ClipAbi abi,
      Identifier shader,
      int location) {
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
                  ? SodiumShaderAbi.vertex(parsed) : MinecraftShaderAbi.vertex(shader, parsed, source);
      boolean projected = layout.projected();
      boolean unwarped =
          parsed.hasIdentifier("portalix_UnclippedPosition")
              || layout.vertexCorrection().contains("portalix_UnclippedPosition");
      String position = unwarped ? "portalix_UnclippedPosition" : "gl_Position";
      if (!layout.homogeneous()) position = "vec4(" + position + ".xy, " + position + ".w, 1.0)";
      boolean correction = !layout.vertexCorrection().isEmpty();
      String declarations =
          "\n"
              + (correction ? "" : abi.guard)
              + "layout(std140) uniform " + abi.uniformName
              + " { vec4 portalix_Plane; vec4 portalix_EntityPlane;"
              + " vec4 portalix_Edges[4]; vec4 portalix_Mode; vec4 portalix_CameraFraction; };\n"
              + (correction ? abi.guard : "")
              + varyings("out", location, hardwareClipping)
              + "#endif\n";
      String assignments =
          layout.vertexCorrection()
              + "\n" + abi.guard
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
        + "\n" + abi.guard
        + varyings("in", location, hardwareClipping)
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

  private static String varyings(String direction, int location, boolean hardwareClipping) {
    return varying(direction, location, 0, "", "float portalix_EntityDistance")
        + varying(direction, location, 1, "", "vec4 portalix_Aperture")
        + varying(direction, location, 2, "flat ", "float portalix_EntityMode")
        + varying(direction, location, 3, "flat ", "float portalix_Oval")
        + varying(direction, location, 4, "", "vec3 portalix_ProjectedPosition")
        + varying(direction, location, 5, "flat ", "vec4 portalix_ScenePlane")
        + varying(direction, location, 6, "flat ", "float portalix_Terrain")
        + (hardwareClipping ? "" : varying(direction, location, 7, "", "float portalix_Distance"));
  }

  private static String varying(String direction, int location, int offset, String qualifier, String declaration) {
    String layout = location < 0 ? "" : "layout(location = " + (location + offset) + ") ";
    return layout + qualifier + direction + " " + declaration + ";\n";
  }
}
