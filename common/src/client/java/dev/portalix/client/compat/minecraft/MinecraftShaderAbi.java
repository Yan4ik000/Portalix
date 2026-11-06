package dev.portalix.client.compat.minecraft;

import dev.portalix.client.portal.clip.PortalShaderInterface;
import dev.portalix.client.render.GlslSource;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class MinecraftShaderAbi {
  private MinecraftShaderAbi() {}

  public static PortalShaderInterface vertex(Identifier shader, GlslSource source) {
    boolean projected = source.hasUniformBlock("Projection", null, Map.of("ProjMat", "mat4"));
    boolean terrain = source.hasUniformBlock("ChunkSection", null,
        Map.of("ModelViewMat", "mat4", "ChunkPosition", "ivec3"));
    if (shader != null && (shader.getNamespace().equals("minecraft")
        || shader.getNamespace().equals("portalix"))) {
      boolean core = shader.getPath().startsWith("core/");
      boolean screen = shader.getNamespace().equals("minecraft")
          && (shader.getPath().equals("core/screenquad")
              || shader.getPath().equals("core/animate_sprite"));
      if ((core && !screen || source.hasIdentifier("ProjMat")) && !projected)
        throw new IllegalStateException("Unsupported projection shader interface: " + shader);
      if ((shader.equals(Identifier.withDefaultNamespace("core/terrain"))
          || source.hasIdentifier("ChunkPosition")) && !terrain)
        throw new IllegalStateException("Unsupported terrain shader interface: " + shader);
    }
    return new PortalShaderInterface(projected, terrain, "");
  }
}
