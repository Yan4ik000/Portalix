package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.sodium.SharedSodiumTerrain;
import dev.portalix.client.compat.sodium.SodiumPrimaryView;
import dev.portalix.client.compat.sodium.SodiumRegionViews;
import dev.portalix.client.compat.sodium.SodiumViewCulling;
import dev.portalix.client.compat.sodium.SodiumViewSortTask;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.scene.SceneChunkEventAccess;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
abstract class SodiumSceneChunksMixin implements SodiumPrimaryView {
  @Shadow private ClientLevel level;
  @Shadow private RenderSectionManager renderSectionManager;
  @Shadow private int renderDistance;

  @Shadow private UniformBufferManager uniformBufferManager;

  @Unique private boolean portalix$initializedLevel;
  @Unique private FogParameters portalix$primaryFog;
  @Unique private LevelRenderer portalix$primaryRenderer;
  @Shadow private FogParameters lastFogParameters;

  @Inject(method = "setLevel", at = @At("HEAD"))
  private void portalix$levelChange(ClientLevel next, CallbackInfo ci) {
    if (next != level) {
      portalix$initializedLevel = next != null;
      portalix$primaryFog = null;
    }
  }

  @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
  private void portalix$initialTerrain(CallbackInfo ci) {
    boolean initialized = portalix$initializedLevel;
    portalix$initializedLevel = false;

    if (initialized
        && renderDistance == Minecraft.getInstance().options.getEffectiveRenderDistance())
      ci.cancel();
  }

  @WrapOperation(
      method = "initRenderer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker;forEachChunk(Lit/unimi/dsi/fastutil/longs/LongCollection;Lnet/caffeinemc/mods/sodium/client/render/chunk/map/ChunkTracker$ChunkEventHandler;)V"))
  private void portalix$initialChunks(
      LongCollection ready, ChunkTracker.ChunkEventHandler handler, Operation<Void> original) {
    var tracker = ChunkTrackerHolder.get(level);
    ((SceneChunkEventAccess) tracker).portalix$chunkEvents().clear();
    renderSectionManager.beforeSectionUpdates();
    original.call(ready, handler);
  }

  @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
  private void portalix$sceneCamera(
      Camera camera,
      Viewport viewport,
      FogParameters fog,
      boolean culling,
      boolean immediate,
      Matrix4f matrix,
      CallbackInfo ci) {
    if (IrisShaders.shadowPass()) return;
    if (PortalSceneManager.isPortalView()) {
      lastFogParameters = fog;
      uniformBufferManager.prepareFrame();
      ((SharedSodiumTerrain) renderSectionManager)
          .portalix$portalView(camera, viewport, fog, culling);
      PortalFrameTerrainBudget.claim(Minecraft.getInstance().levelRenderer).finish();
      ci.cancel();
    } else {
      portalix$primaryFog = fog;
      portalix$primaryRenderer = Minecraft.getInstance().levelRenderer;
    }
  }

  @Unique
  private void portalix$refreshChunks() {
    var tracker = ChunkTrackerHolder.get(level);
    renderSectionManager.beforeSectionUpdates();
    ((SceneChunkEventAccess) tracker)
        .portalix$chunkEvents()
        .drain(
            chunk -> renderSectionManager.onChunkAdded(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)),
            ((SharedSodiumTerrain) renderSectionManager)::portalix$unloadChunks);
  }

  @Inject(method = "setupTerrain", at = @At("RETURN"))
  private void portalix$shadowDemand(
      Camera camera, Viewport viewport, FogParameters fog, boolean culling, boolean immediate,
      Matrix4f matrix, CallbackInfo ci) {
    if (IrisShaders.shadowPass())
      ((SharedSodiumTerrain) renderSectionManager).portalix$queueShadowView();
    else PortalFrameTerrainBudget.claim(Minecraft.getInstance().levelRenderer).finish();
  }

  @Override
  public void portalix$invalidateOcclusion() {
    if (renderSectionManager != null) renderSectionManager.markGraphDirty();
  }

  @Override
  public void portalix$releaseView(LevelRenderer renderer) {
    portalix$retargetView(renderer);
    if (renderSectionManager != null)
      for (var region : renderSectionManager.regions.getLoadedRegions())
        ((SodiumRegionViews) region).portalix$releaseView(renderer);
  }

  @Override
  public void portalix$retargetView(LevelRenderer renderer) {
    if (renderSectionManager != null)
      ((SodiumViewCulling) renderSectionManager).portalix$releaseCulling(renderer);
  }

  @Override
  public void portalix$queueSort(SodiumViewSortTask task) {
    ((SharedSodiumTerrain) renderSectionManager).portalix$queueSort(task);
  }

  @Override
  public void portalix$restorePrimary() {
    if (portalix$primaryFog == null) return;
    lastFogParameters = portalix$primaryFog;
    uniformBufferManager.prepareFrame();
    ((SharedSodiumTerrain) renderSectionManager).portalix$restorePrimary();
  }

  @Override
  public void portalix$exchangePrimary(LevelRenderer renderer) {
    if (renderSectionManager != null)
      ((SharedSodiumTerrain) renderSectionManager).portalix$exchangePrimary(renderer);
  }

  @Override
  public boolean portalix$completeDemand(LevelRenderer renderer, CameraRenderState camera) {
    return renderSectionManager != null
        && ((SodiumViewCulling) renderSectionManager)
            .portalix$completeView(renderer, camera, renderer == portalix$primaryRenderer);
  }

  @Override
  public boolean portalix$primaryOccludes(CameraRenderState camera, AABB bounds) {
    return renderSectionManager != null
        && ((SodiumViewCulling) renderSectionManager).portalix$primaryOccludes(camera, bounds);
  }

  @Inject(method = "endFrame", at = @At("HEAD"), cancellable = true)
  private void portalix$finishFrameOnce(CallbackInfo ci) {
    if (PortalSceneManager.isPortalView()) ci.cancel();
  }

  @Inject(method = "processChunkEvents", at = @At("HEAD"), cancellable = true)
  private void portalix$chunks(CallbackInfo ci) {
    var tracker = ChunkTrackerHolder.get(level);
    portalix$refreshChunks();

    if (!PortalSceneManager.isPortalView()) tracker.forEachEvent((x, z) -> {}, (x, z) -> {});
    ci.cancel();
  }
}
