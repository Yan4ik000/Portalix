package dev.portalix.client.mixin.entity;

import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.entity.ItemModelBounds;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CuboidItemModelWrapper.class)
abstract class PortalItemBoundsMixin {
  @Shadow @Final private Supplier<Vector3fc[]> extents;
  @Shadow @Final private ModelRenderProperties properties;
  @Shadow @Final private Matrix4fc transformation;
  @Shadow @Final private QuadCollection quads;
  @Unique private Supplier<Vector3fc[]> portalix$extents;

  @Inject(method = "update", at = @At("HEAD"), cancellable = true)
  private void portalix$boundsOnly(
      ItemStackRenderState state,
      ItemStack item,
      ItemModelResolver resolver,
      ItemDisplayContext context,
      ClientLevel level,
      ItemOwner owner,
      int seed,
      CallbackInfo ci) {
    if (!(state instanceof ItemModelBounds)) return;
    state.appendModelIdentityElement(this);
    var layer = state.newLayer();
    if (portalix$extents == null) portalix$extents = ClientPlatform.INSTANCE.itemExtents(quads, extents);
    layer.setExtents(portalix$extents);
    layer.setLocalTransform(transformation);
    properties.applyToLayer(layer, context);
    ci.cancel();
  }

}
