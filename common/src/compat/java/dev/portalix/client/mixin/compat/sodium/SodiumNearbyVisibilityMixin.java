package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = OcclusionCuller.class, remap = false)
abstract class SodiumNearbyVisibilityMixin {
  @Shadow private int token;
  @Shadow private OcclusionCuller.GraphOcclusionVisitor visitorWide;
  @Shadow private OcclusionCuller.GraphOcclusionVisitor visitorRegular;

  @WrapOperation(
      method = "addNearbySections",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller;isWithinNearbySectionFrustum(Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;)Z"))
  private boolean portalix$completeReusableVisibility(
      Viewport viewport, RenderSection section, Operation<Boolean> original) {
    boolean visible = original.call(viewport, section);
    if (!visible) {
      section.resetOnFirstVisit(token);
      visitorWide.visit(section, false);
      visitorRegular.visit(section, false);
    }
    return visible;
  }
}
