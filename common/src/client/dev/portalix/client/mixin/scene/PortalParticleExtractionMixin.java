package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleViewDistance;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(QuadParticleGroup.class)
abstract class PortalParticleExtractionMixin {
  @Shadow @Final @Mutable private QuadParticleRenderState particleTypeRenderState;

  @WrapOperation(
      method = "extractRenderState",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
  private boolean portalix$viewDistance(
      Frustum frustum,
      double x,
      double y,
      double z,
      Operation<Boolean> original,
      @Local(argsOnly = true) Camera camera,
      @Local SingleQuadParticle particle) {
    return camera.position().distanceToSqr(x, y, z)
            <= ((ParticleViewDistance) particle).portalix$viewDistanceSquared()
        && original.call(frustum, x, y, z);
  }

  @WrapMethod(method = "extractRenderState")
  private ParticleGroupRenderState portalix$viewState(
      Frustum frustum, Camera camera, float partial, Operation<ParticleGroupRenderState> original) {
    if (!PortalSceneManager.isPortalView()) return original.call(frustum, camera, partial);
    var primary = particleTypeRenderState;
    particleTypeRenderState = PortalSceneManager.particleState((QuadParticleGroup) (Object) this);
    particleTypeRenderState.clear();
    try {
      return original.call(frustum, camera, partial);
    } finally {
      particleTypeRenderState = primary;
    }
  }
}
