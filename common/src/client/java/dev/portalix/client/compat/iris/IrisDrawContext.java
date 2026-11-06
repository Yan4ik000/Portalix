package dev.portalix.client.compat.iris;

import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import net.minecraft.world.item.Item;

public record IrisDrawContext(int entity, int blockEntity, int item) {
  public static IrisDrawContext capture() {
    return ShaderBackend.INSTANCE == null ? null : ShaderBackend.INSTANCE.captureDrawContext();
  }

  public static RenderScope forItem(Item item) {
    return ShaderBackend.INSTANCE == null ? null : ShaderBackend.INSTANCE.itemContext(item);
  }

  public static long vertexKey() {
    return ShaderBackend.INSTANCE == null ? 0 : ShaderBackend.INSTANCE.vertexKey();
  }

  public RenderScope begin() {
    return ShaderBackend.INSTANCE.drawContext(this);
  }
}
