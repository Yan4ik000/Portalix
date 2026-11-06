package dev.portalix.client.compat.sodium;

import dev.portalix.client.compat.TerrainIntegration;
import dev.portalix.client.mixin.compat.sodium.LevelRendererAccessor;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

public final class SodiumTerrainIntegration implements TerrainIntegration {
  @Override
  public void attach(LevelRenderer renderer, LevelRenderer owner) {
    ((LevelRendererAccessor) renderer)
        .portalix$renderer(((LevelRendererExtension) owner).sodium$getWorldRenderer());
  }

  @Override
  public void restorePrimary() {
    ((SodiumPrimaryView) SodiumWorldRenderer.instance()).portalix$restorePrimary();
  }

  @Override
  public void exchangePrimary(LevelRenderer renderer) {
    var world = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
    if (world != null) ((SodiumPrimaryView) world).portalix$exchangePrimary(renderer);
    SodiumPortalIndices.exchangePrimary(renderer);
  }

  @Override
  public boolean completeDemand(LevelRenderer renderer, CameraRenderState camera) {
    var world = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
    return world != null && ((SodiumPrimaryView) world).portalix$completeDemand(renderer, camera);
  }

  @Override
  public boolean primaryOccludes(CameraRenderState camera, AABB bounds) {
    var world =
        ((LevelRendererExtension) Minecraft.getInstance().levelRenderer).sodium$getWorldRenderer();
    return world != null && ((SodiumPrimaryView) world).portalix$primaryOccludes(camera, bounds);
  }

  @Override
  public void release(LevelRenderer renderer) {
    SodiumPortalIndices.close(renderer);
    var world = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
    if (world != null) ((SodiumPrimaryView) world).portalix$releaseView(renderer);
  }

  @Override
  public void retarget(LevelRenderer renderer) {
    SodiumPortalIndices.retarget(renderer);
    var world = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
    if (world != null) ((SodiumPrimaryView) world).portalix$retargetView(renderer);
  }

  @Override
  public void invalidateOcclusion(LevelRenderer renderer) {
    var world = ((LevelRendererExtension) renderer).sodium$getWorldRenderer();
    if (world != null) ((SodiumPrimaryView) world).portalix$invalidateOcclusion();
  }

  @Override
  public void loaded(ClientLevel level, int x, int z) {
    ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
  }

  @Override
  public void unloaded(ClientLevel level, int x, int z) {
    ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
  }
}
