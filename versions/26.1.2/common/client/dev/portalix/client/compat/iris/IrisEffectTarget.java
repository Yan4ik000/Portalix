package dev.portalix.client.compat.iris;

import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;

public final class IrisEffectTarget {
  private IrisEffectTarget() {}

  public static boolean supportsColorEffects() {
    return ShaderBackend.INSTANCE == null || ShaderBackend.INSTANCE.supportsColorEffects();
  }

  public static void deferDraw(Runnable draw) {
    ShaderBackend.INSTANCE.submit(draw);
  }

  public static RenderScope begin() {
    return ShaderBackend.INSTANCE == null ? null : ShaderBackend.INSTANCE.beginEffect();
  }

  public static void clear() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.clearEffects();
  }

  public static void endFrame() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.endFrame();
  }
}
