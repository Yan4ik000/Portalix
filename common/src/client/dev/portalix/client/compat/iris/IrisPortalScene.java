package dev.portalix.client.compat.iris;

import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;

public final class IrisPortalScene implements AutoCloseable {
  private final ShaderBackend.Scene scene = ShaderBackend.INSTANCE == null ? null : ShaderBackend.INSTANCE.scene();

  public void invalidateHistory() {
    if (scene != null) scene.invalidateHistory();
  }

  public boolean exchangePrimary() {
    return scene != null && scene.exchangePrimary();
  }

  public RenderScope extract() {
    return scene == null ? null : scene.extract();
  }

  public RenderScope begin() {
    return scene == null ? null : scene.begin();
  }

  @Override
  public void close() {
    if (scene != null) scene.close();
  }
}
