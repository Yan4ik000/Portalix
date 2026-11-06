package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumPortalFrustum;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.portal.clip.PortalFrustumState;
import dev.portalix.client.portal.clip.PortalFrustumPlanes;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
abstract class SodiumFrustumMixin {
  @Shadow @Final private Matrix4f matrix;
  @Shadow private double camX;
  @Shadow private double camY;
  @Shadow private double camZ;

  @Dynamic("Sodium ViewportProvider")
  @Inject(method = "sodium$createViewport", at = @At("HEAD"), cancellable = true, remap = false)
  private void portalix$exitViewport(CallbackInfoReturnable<Viewport> cir) {
    if (IrisShaders.shadowPass()) return;
    var state = (PortalFrustumState) this;
    var plane = state.portalix$exitPlane();
    var connectivity = plane;
    if (state.portalix$isAperture() && PortalSceneManager.isPortalView())
      connectivity = PortalCullPlane.of(PortalSceneManager.path().getLast().exit().endpoint());
    if (plane == null && connectivity == null) return;
    cir.setReturnValue(
        new Viewport(
            new SodiumPortalFrustum(
                new SimpleFrustum(PortalFrustumPlanes.set(new FrustumIntersection(), matrix)),
                plane == null ? null : plane.relativeTo(new Vec3(camX, camY, camZ)),
                connectivity),
            new Vector3d(camX, camY, camZ)));
  }
}
