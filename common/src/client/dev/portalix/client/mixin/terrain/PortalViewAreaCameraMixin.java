package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ViewArea.class)
abstract class PortalViewAreaCameraMixin {
  @ModifyReturnValue(method = "getCameraSectionPos", at = @At("RETURN"))
  private SectionPos portalix$buildCamera(SectionPos position) {
    return PortalOcclusionContext.cameraSection(position);
  }
}
