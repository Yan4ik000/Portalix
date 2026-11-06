package dev.portalix.client.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

public interface SodiumViewCulling {
  SodiumViewCullingState portalix$cullingState();

  void portalix$prepareView(Viewport viewport, FogParameters fog, boolean culling);

  void portalix$releaseCulling(LevelRenderer renderer);

  void portalix$exchangeCulling(LevelRenderer renderer);

  boolean portalix$completeView(LevelRenderer renderer, CameraRenderState camera, boolean primary);

  boolean portalix$primaryOccludes(CameraRenderState camera, AABB bounds);
}
