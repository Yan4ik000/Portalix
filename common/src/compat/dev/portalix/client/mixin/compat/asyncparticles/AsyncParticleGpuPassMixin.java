package dev.portalix.client.mixin.compat.asyncparticles;

import static dev.portalix.client.compat.asyncparticles.AsyncParticlesCompatibility.BEGIN_FRAME;
import static dev.portalix.client.compat.asyncparticles.AsyncParticlesCompatibility.COMPUTE;
import static dev.portalix.client.compat.asyncparticles.AsyncParticlesCompatibility.ENSURE_COMPUTE_READY;

import dev.portalix.client.portal.scene.PortalSceneManager;
import fun.qu_an.minecraft.asyncparticles.client.core.particle.gpu_acceleration.ComputeResult;
import fun.qu_an.minecraft.asyncparticles.client.core.particle.gpu_acceleration.GpuParticleBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GpuParticleBehavior.class, remap = false)
abstract class AsyncParticleGpuPassMixin {
  @Inject(method = {BEGIN_FRAME, COMPUTE}, at = @At("HEAD"), cancellable = true, require = 2)
  private void portalix$keepPrimaryGpuState(CallbackInfo callback) {
    if (PortalSceneManager.isPortalView()) callback.cancel();
  }

  @Inject(method = ENSURE_COMPUTE_READY, at = @At("HEAD"), cancellable = true)
  private void portalix$skipPrimaryGpuResult(CallbackInfoReturnable<ComputeResult> callback) {
    if (PortalSceneManager.isPortalView()) callback.setReturnValue(null);
  }
}
