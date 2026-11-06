package dev.portalix.client.compat.iris;

import dev.portalix.client.portal.clip.PortalShaderInterface;
import dev.portalix.client.render.GlslSource;
import java.util.Map;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

final class IrisShaderAbi {
  enum VertexPosition {
    ATTRIBUTE,
    CLOUDS
  }

  private IrisShaderAbi() {}

  static VertexPosition position(ShaderKey key, GlslSource source) {
    if (key != ShaderKey.CLOUDS) return VertexPosition.ATTRIBUTE;
    // Only the core transformer injects/renames UV1; compatibility clouds generate vertices.
    if (source.hasIdentifier("iris_UV1")) {
      if (source.hasIdentifier("iris_cloudPos") || source.hasIdentifier("iris_cloudsMain"))
        throw new IllegalStateException("Ambiguous Iris cloud shader interface");
      return VertexPosition.ATTRIBUTE;
    }
    return VertexPosition.CLOUDS;
  }

  static PortalShaderInterface vertex(
      GlslSource source, boolean projected, boolean shadow, boolean terrain, boolean fallback,
      VertexPosition vertexPosition) {
    if (!projected || fallback)
      return new PortalShaderInterface(projected, terrain, "", shadow);
    String position;
    if (terrain) {
      if (!source.hasUniformBlock("u_Globals", null,
              Map.of("u_ProjectionMatrix", "mat4", "u_ModelViewMatrix", "mat4"))
          || source.function("vec4", "getVertexPosition") < 0)
        throw new IllegalStateException("Unsupported Iris terrain shader interface");
      position = "u_ProjectionMatrix * u_ModelViewMatrix * getVertexPosition()";
    } else {
      if (!source.hasUniformBlock("iris_Projection", null, Map.of("iris_ProjMat", "mat4"))
          || !source.hasUniformBlock("iris_DynamicTransforms", "iris_transforms",
              Map.of("ModelViewMat", "mat4", "ModelOffset", "vec3")))
        throw new IllegalStateException("Unsupported Iris entity shader interface");
      String vertex;
      if (vertexPosition == VertexPosition.CLOUDS) {
        if (!source.hasUniformBlock("iris_CloudInfo", "iris_Clouds",
                Map.of("CloudColor", "vec4", "CloudOffset", "vec3", "CellSize", "vec3"))
            || !source.hasDeclaration("vec3", "iris_cloudPos")
            || source.function("void", "iris_cloudsMain") < 0)
          throw new IllegalStateException("Unsupported Iris generated cloud shader interface");
        vertex = "iris_cloudPos";
      } else {
        if (!source.hasDeclaration("vec3", "iris_Position"))
          throw new IllegalStateException("Unsupported Iris vertex position interface");
        vertex = "iris_Position";
      }
      position = "iris_ProjMat * iris_transforms.ModelViewMat * vec4("
          + vertex + " + iris_transforms.ModelOffset, 1.0)";
    }
    return new PortalShaderInterface(projected, terrain,
        "vec4 portalix_UnclippedPosition = " + position + "; ", shadow);
  }
}
