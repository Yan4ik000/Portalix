package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.portal.clip.PortalFrustumState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class PortalBlockEntityRangeMixin {
  @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
  private void portalix$viewColumn(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
      CameraRenderState camera, CallbackInfo ci) {
    var window = ((PortalFrustumState) camera.cullFrustum).portalix$terrainWindow();
    if (window == null) return;
    // Global block entities such as beacons bypass terrain lists and can span the whole column.
    var pos = state.blockPos;
    if (!window.containsOffset(SectionPos.blockToSectionCoord(pos.getX()) - window.center().x(), 0,
        SectionPos.blockToSectionCoord(pos.getZ()) - window.center().z())) ci.cancel();
  }
}
