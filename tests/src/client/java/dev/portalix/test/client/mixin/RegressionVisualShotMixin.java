package dev.portalix.test.client.mixin;

import dev.portalix.client.particle.GunShotEffects;
import dev.portalix.client.render.ParticleView;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.PortalImpactPayload;
import dev.portalix.test.client.VisualShotProbe;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunShotEffects.class)
abstract class RegressionVisualShotMixin {
  @Inject(method = "onShotFired", at = @At("RETURN"))
  private void observeShot(GunShotPayload data, CallbackInfo callback) {
    VisualShotProbe.shotFired(data);
  }

  @Inject(method = "onPortalImpact", at = @At("RETURN"))
  private void observeImpact(PortalImpactPayload data, CallbackInfo callback) {
    VisualShotProbe.portalImpact(data);
  }

  @Inject(method = "submit", at = @At("RETURN"))
  private void observeMainSubmission(ParticleView view, OrderedSubmitNodeCollector collector, CallbackInfo callback) {
    VisualShotProbe.submitted(view);
  }
}
