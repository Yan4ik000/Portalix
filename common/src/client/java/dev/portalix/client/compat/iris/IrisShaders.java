package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.ShaderBackend;
import org.joml.Matrix4f;

public final class IrisShaders {
  private IrisShaders() {}

  public static boolean active() {
    return ShaderBackend.INSTANCE != null && ShaderBackend.INSTANCE.active();
  }

  public static boolean shadowPass() {
    return ShaderBackend.INSTANCE != null && ShaderBackend.INSTANCE.shadowPass();
  }

  public static void invalidatePrimaryHistory() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.invalidatePrimaryHistory();
  }

  public static void applyShadowMatrices(Matrix4f projection, Matrix4f rotation) {
    ShaderBackend.INSTANCE.shadowMatrices(projection, rotation);
  }

  public static boolean vanillaEntityShadows() {
    return ShaderBackend.INSTANCE == null || ShaderBackend.INSTANCE.vanillaEntityShadows();
  }

  public static boolean overrides(RenderPipeline pipeline) {
    return ShaderBackend.INSTANCE != null && ShaderBackend.INSTANCE.overrides(pipeline);
  }

  public static boolean overridesGeometry(RenderPipeline pipeline) {
    return ShaderBackend.INSTANCE != null && ShaderBackend.INSTANCE.overridesGeometry(pipeline);
  }

  public static void register(RenderPipeline pipeline) {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.register(pipeline);
  }
}
