package dev.portalix.client.mixin.clip;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackRenderState.class)
abstract class PortalItemClipMixin {
  @Shadow ItemDisplayContext displayContext;

  @WrapMethod(method = "submit")
  private void portalix$itemIntersection(
      PoseStack poses,
      SubmitNodeCollector collector,
      int light,
      int overlay,
      int outline,
      Operation<Void> original) {
    if (displayContext != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        && displayContext != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
      original.call(poses, collector, light, overlay, outline);
      return;
    }
    PortalEntityClipping.withItem(() -> original.call(poses, collector, light, overlay, outline));
  }
}
