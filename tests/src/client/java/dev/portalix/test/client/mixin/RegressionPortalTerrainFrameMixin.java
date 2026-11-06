package dev.portalix.test.client.mixin;

import dev.portalix.portal.PortalLink;
import dev.portalix.test.client.PortalTerrainUploadProbe;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.portalix.client.portal.scene.PortalRenderScene", remap = false)
abstract class RegressionPortalTerrainFrameMixin {
  @Shadow List<PortalLink> path;
  @Shadow boolean renderedThisFrame;
  @Unique private long portalix$extractionNanos;

  @Inject(method = "extract", at = @At("HEAD"))
  private void portalix$extracting(DeltaTracker delta, CallbackInfo callback) {
    portalix$extractionNanos = PortalTerrainUploadProbe.extractionStarted();
  }

  @Inject(method = "render", at = @At("RETURN"))
  private void portalix$rendered(DeltaTracker delta, CallbackInfo callback) {
    if (renderedThisFrame) PortalTerrainUploadProbe.rendered(path, portalix$extractionNanos);
  }
}
