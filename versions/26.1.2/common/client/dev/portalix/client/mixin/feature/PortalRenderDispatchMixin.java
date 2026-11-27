package dev.portalix.client.mixin.feature;

import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.render.PortalRenderQueue;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
abstract class PortalRenderDispatchMixin {
  @Shadow @Final private SubmitNodeStorage submitNodeStorage;
  @Shadow @Final private MultiBufferSource.BufferSource bufferSource;

  @Inject(method = "renderSolidFeatures", at = @At("TAIL"))
  private void portalix$drawOpaque(CallbackInfo ci) {
    PortalRenderQueue.drawPhase(submitNodeStorage, bufferSource, Phase.SOLID);
  }

  @Inject(method = "renderTranslucentFeatures", at = @At("TAIL"))
  private void portalix$drawTranslucent(CallbackInfo ci) {
    PortalRenderQueue.drawPhase(submitNodeStorage, bufferSource, Phase.TRANSLUCENT_CUSTOM_GEOMETRY);
  }
}
