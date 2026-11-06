package dev.portalix.client.portal.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

public final class ItemModelBounds extends ItemStackRenderState {
  private record Model(ItemDisplayContext context, List<Object> identity) {}

  private final List<Object> identity = new ArrayList<>();
  private final Map<Model, AABB> bounds = new HashMap<>();

  @Override
  public void clear() {
    super.clear();
    identity.clear();
  }

  @Override
  public void appendModelIdentityElement(Object element) {
    identity.add(element);
  }

  AABB bounds(ItemDisplayContext context) {
    if (isAnimated() || identity.isEmpty()) return getModelBoundingBox();
    var model = new Model(context, identity);
    var cached = bounds.get(model);
    if (cached != null) return cached;
    var result = getModelBoundingBox();
    bounds.put(new Model(context, new ArrayList<>(identity)), result);
    return result;
  }

  void reload() {
    clear();
    bounds.clear();
  }
}
