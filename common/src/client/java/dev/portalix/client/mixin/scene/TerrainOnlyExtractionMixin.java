package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
abstract class TerrainOnlyExtractionMixin {
  // Let extract finish its own scopes and lifecycle while warming an invisible terrain view.
  @Inject(
      method = {"extractVisibleEntities", "extractBlockOutline",
          "extractBlockDestroyAnimation", "extractGizmos"},
      at = @At("HEAD"), cancellable = true, require = 4, allow = 4)
  private void portalix$terrainOnly(CallbackInfo callback) {
    if (PortalSceneManager.terrainOnly()) callback.cancel();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/WeatherEffectRenderer;extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V"), require = 1, allow = 1)
  private boolean portalix$weather(WeatherEffectRenderer renderer, ClientLevel level,
      float partial, Vec3 position, WeatherRenderState state) {
    return !PortalSceneManager.terrainOnly();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/SkyRenderer;extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V"), require = 1, allow = 1)
  private boolean portalix$sky(SkyRenderer renderer, ClientLevel level, float partial,
      Camera camera, SkyRenderState state) {
    return !PortalSceneManager.terrainOnly();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/WorldBorderRenderer;extract(Lnet/minecraft/world/level/border/WorldBorder;FLnet/minecraft/world/phys/Vec3;DLnet/minecraft/client/renderer/state/level/WorldBorderRenderState;)V"), require = 1, allow = 1)
  private boolean portalix$border(WorldBorderRenderer renderer, WorldBorder border,
      float partial, Vec3 position, double distance, WorldBorderRenderState state) {
    return !PortalSceneManager.terrainOnly();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"), require = 1, allow = 1)
  private boolean portalix$particles(ParticleEngine engine, ParticlesRenderState state,
      Frustum frustum, Camera camera, float partial) {
    return !PortalSceneManager.terrainOnly();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"), require = 1, allow = 1)
  private boolean portalix$debug(DebugRenderer renderer, Frustum frustum,
      double x, double y, double z, float partial) {
    return !PortalSceneManager.terrainOnly();
  }

  @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/debug/GameTestBlockHighlightRenderer;emitGizmos()V"), require = 1, allow = 1)
  private boolean portalix$highlights(GameTestBlockHighlightRenderer renderer) {
    return !PortalSceneManager.terrainOnly();
  }
}
