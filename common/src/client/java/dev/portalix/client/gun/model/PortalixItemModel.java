package dev.portalix.client.gun.model;

import dev.portalix.PortalixMod;
import dev.portalix.client.gun.render.PortalixItemRenderer;
import dev.portalix.item.PortalixVariant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PortalixItemModel implements ItemModel {
  public static final Set<Identifier> MODELS = Arrays.stream(PortalixVariant.values())
      .map(PortalixVariant::itemId)
      .collect(Collectors.toUnmodifiableSet());

  private final ItemModel original;

  public PortalixItemModel(ItemModel original) {
    this.original = original;
  }

  @Override
  public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
      ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
    if (!PortalixMod.isPortalix(stack)) {
      original.update(state, stack, resolver, context, level, owner, seed);
      return;
    }
    var model = GunModels.world();
    PortalixVariant variant = PortalixVariant.of(stack);
    state.appendModelIdentityElement(model);
    state.appendModelIdentityElement(variant);
    var layer = state.newLayer();
    layer.setupSpecialModel(
        PortalixItemRenderer.INSTANCE,
        new PortalixItemRenderer.Argument(
            context, owner instanceof Entity entity ? entity.getId() : -1, variant, stack.getItem()));
    layer.setUsesBlockLight(context != ItemDisplayContext.GUI);
    layer.setExtents(model::extents);
    if (context != ItemDisplayContext.GUI) state.setAnimated();
  }
}
