package dev.portalix.client.compat.sodium;

import dev.portalix.client.portal.clip.PortalShaderInterface;
import dev.portalix.client.render.GlslSource;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class SodiumShaderAbi {
  private SodiumShaderAbi() {}

  public static boolean terrain(Identifier shader) {
    return shader != null && shader.getNamespace().equals("sodium")
        && shader.getPath().equals("blocks/block_layer_opaque");
  }

  public static PortalShaderInterface vertex(GlslSource source) {
    if (!source.hasUniformBlock("u_Globals", null,
            Map.of("u_ProjectionMatrix", "mat4", "u_ModelViewMatrix", "mat4"))
        || !source.hasDeclaration("vec3", "u_RegionOffset")
        || !source.hasDeclaration("vec3", "_vert_position")
        || !source.hasDeclaration("uint", "_draw_id")
        || source.function("vec3", "_get_draw_translation", "uint") < 0)
      throw new IllegalStateException("Unsupported Sodium terrain shader interface");
    return new PortalShaderInterface(true, true,
        "vec3 section = round(u_RegionOffset + _get_draw_translation(_draw_id) - portalix_CameraFraction.xyz); "
            + "vec3 position = (_vert_position + section) + portalix_CameraFraction.xyz; "
            + "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0); ");
  }
}
