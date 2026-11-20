package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SodiumPortalFrustum;
import dev.portalix.client.compat.sodium.SodiumTerrainWindow;
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
    var window = state.portalix$terrainWindow();
    if (plane == null && window == null) return;
    var eye = new Vec3(camX, camY, camZ);
    var origin = PortalSceneManager.terrainOrigin(eye);
    boolean rebased = !origin.equals(eye);
    var culling = rebased ? new Matrix4f(matrix).translate(
        (float) (origin.x - camX), (float) (origin.y - camY), (float) (origin.z - camZ)) : matrix;
    cir.setReturnValue(
        new Viewport(
            new SodiumPortalFrustum(
                new SimpleFrustum(PortalFrustumPlanes.set(new FrustumIntersection(), culling)),
                plane == null ? null : plane.relativeTo(origin),
                window == null ? null : new SodiumTerrainWindow(origin, window),
                rebased ? new Viewport(null, new Vector3d(camX, camY, camZ)) : null),
            new Vector3d(origin.x, origin.y, origin.z)));
  }
}
