package dev.portalix.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;

public final class PortalTextures {
  private PortalTextures() {}

  public static GpuTexture allocateLike(String name, int usage, GpuTextureView source) {
    return color(name, usage, source.getWidth(0), source.getHeight(0), storageFormat(source));
  }

  public static int storageFormat(GpuTextureView source) {
    RenderSystem.assertOnRenderThread();
    int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    GlStateManager._bindTexture(((GlTexture) source.texture()).glId());
    try {
      return GL11.glGetTexLevelParameteri(
          GL11.GL_TEXTURE_2D, source.baseMipLevel(), GL11.GL_TEXTURE_INTERNAL_FORMAT);
    } finally {
      GlStateManager._bindTexture(binding);
    }
  }

  public static GpuTexture color(String name, int usage, int width, int height, int storageFormat) {
    RenderSystem.assertOnRenderThread();
    int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    var texture = RenderSystem.getDevice().createTexture(name, usage, TextureFormat.RGBA8, width, height, 1, 1);
    try {
      // Blaze3D 26.1.2 only describes RGBA8 color textures. OpenGL derives the attachment format from its storage.
      if (storageFormat != GL11.GL_RGBA8) {
        GlStateManager._bindTexture(((GlTexture) texture).glId());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, storageFormat, width, height,
            0, GL11.GL_RGBA, GL11.GL_FLOAT, 0L);
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR)
          throw new IllegalStateException("OpenGL error " + error + " allocating " + name);
      }
      return texture;
    } catch (RuntimeException | Error exception) {
      texture.close();
      throw exception;
    } finally {
      GlStateManager._bindTexture(binding);
    }
  }
}
