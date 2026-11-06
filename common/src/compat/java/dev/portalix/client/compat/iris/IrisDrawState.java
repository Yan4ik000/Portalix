package dev.portalix.client.compat.iris;

import dev.portalix.client.compat.RenderScope;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class IrisDrawState {
  public static IrisDrawContext capture() {
    if (!IrisShaders.active()) return null;
    return current();
  }

  private static IrisDrawContext current() {
    var state = CapturedRenderingState.INSTANCE;
    return new IrisDrawContext(
        state.getCurrentRenderedEntity(),
        state.getCurrentRenderedBlockEntity(),
        state.getCurrentRenderedItem());
  }

  public static Scope forItem(Item item) {
    if (!IrisShaders.active()) return null;
    var context = current();
    var ids = WorldRenderingSettings.INSTANCE.getItemIds();
    if (ids == null) return begin(context);
    var id = BuiltInRegistries.ITEM.getKey(item);
    return begin(new IrisDrawContext(
        context.entity(), context.blockEntity(),
        ids.applyAsInt(new NamespacedId(id.getNamespace(), id.getPath()))));
  }

  public static long vertexKey() {
    if (!IrisShaders.active() || !ImmediateState.isRenderingLevel) return 0;
    var state = CapturedRenderingState.INSTANCE;
    return (long) (state.getCurrentRenderedEntity() & 65535) << 32
        | (long) (state.getCurrentRenderedBlockEntity() & 65535) << 16
        | state.getCurrentRenderedItem() & 65535L;
  }

  private static void apply(IrisDrawContext context) {
    var state = CapturedRenderingState.INSTANCE;
    state.setCurrentEntity(context.entity());
    state.setCurrentBlockEntity(context.blockEntity());
    state.setCurrentRenderedItem(context.item());
  }

  public static Scope begin(IrisDrawContext context) {
    return new Scope(context);
  }

  public static final class Scope implements RenderScope {
    private final IrisDrawContext previous = current();

    Scope(IrisDrawContext context) {
      apply(context);
    }

    @Override
    public void close() {
      apply(previous);
    }
  }
}
