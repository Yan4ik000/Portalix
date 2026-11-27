package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.iris.IrisPortalScene;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.mixin.scene.LightmapRenderStateExtractorAccessor;
import dev.portalix.client.mixin.scene.MinecraftAccessor;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.scene.PortalSceneCamera;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.scene.SceneTerrainChanges;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

@SuppressWarnings("try")
public final class MinecraftSceneRenderer implements AutoCloseable {
  private final IrisPortalScene iris = new IrisPortalScene();
  private final ClientLevel level;
  private final LevelRenderState state = new LevelRenderState();
  private final Map<QuadParticleGroup, QuadParticleRenderState> particles = new IdentityHashMap<>();
  private final Map<PostPass, MappableRingBuffer> postProcessBuffers = new IdentityHashMap<>();
  private final LevelRenderer renderer;
  private final LevelRenderer terrainOwner;
  private final FeatureRenderDispatcher features;
  private RenderTarget target;
  private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("Portal projection");
  private final FogRenderer fog;
  private final Lightmap lightmap = new Lightmap();
  private final GlobalSettingsUniform globals = new GlobalSettingsUniform();
  private final LightmapRenderState lightmapState = new LightmapRenderState();
  private final SceneLightmapState uploadedLightmap = new SceneLightmapState();
  private final CrossFrameResourcePool resources;

  public MinecraftSceneRenderer(ClientLevel level) {
    this.level = level;
    var previousFog = RenderSystem.getShaderFog();
    fog = new FogRenderer();
    RenderSystem.setShaderFog(previousFog);
    Minecraft client = Minecraft.getInstance();
    resources = ((GameRendererAccessor) client.gameRenderer).portalix$resources();
    terrainOwner = client.levelRenderer;
    var gameState = client.gameRenderer.getGameRenderState();
    var buffers = client.renderBuffers();
    features = new FeatureRenderDispatcher(new SubmitNodeStorage(), client.getModelManager(),
        buffers.bufferSource(), client.getAtlasManager(), buffers.outlineBufferSource(),
        buffers.crumblingBufferSource(), client.font, gameState);
    renderer = new LevelRenderer(client, client.getEntityRenderDispatcher(),
        client.getBlockEntityRenderDispatcher(), buffers, gameState, features);
    var access = (LevelRendererAccessor) renderer;
    access.portalix$state(state);
    access.portalix$level(level);
    access.portalix$sky(new SkyRenderer(client.getTextureManager(), client.getAtlasManager()));
    access.portalix$destructionProgress(((LevelRendererAccessor) terrainOwner).portalix$destructionProgress());
    access.portalix$outlineTarget(new TextureTarget("Portal outline", 1, 1, true));
    target = new TextureTarget("Portal", 1, 1, true);
  }

  public LevelRenderState state() {
    return state;
  }

  public LevelRenderer renderer() {
    return renderer;
  }

  public RenderTarget target() {
    return target;
  }

  public GpuTextureView lightmap() {
    return lightmap.getTextureView();
  }

  public QuadParticleRenderState particleState(QuadParticleGroup group) {
    return particles.computeIfAbsent(group, ignored -> new QuadParticleRenderState());
  }

  public MappableRingBuffer postProcessBuffer(PostPass pass, String name, MappableRingBuffer primary) {
    return postProcessBuffers.computeIfAbsent(pass, ignored -> new MappableRingBuffer(
        () -> name + " Portal SamplerInfo",
        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, primary.size()));
  }

  public void retarget() {
    iris.invalidateHistory();
    PortalTerrainBackend.retargetView(renderer);
  }

  public void releaseShaderView() {
    iris.close();
  }

  public void invalidateHistory() {
    iris.invalidateHistory();
  }

  public boolean exchangePrimary(boolean imageHistoryCompatible) {
    var client = Minecraft.getInstance();
    PortalTerrainBackend.exchangePrimary(renderer);
    ((SceneExtractorAccess) client.levelRenderer).portalix$invalidateView();
    ((SceneExtractorAccess) renderer).portalix$invalidateView();
    var primary = client.getMainRenderTarget();
    boolean exchangeImage = imageHistoryCompatible
        && target.width == primary.width && target.height == primary.height
        && target.getColorTexture().getFormat() == primary.getColorTexture().getFormat()
        && target.getDepthTexture().getFormat() == primary.getDepthTexture().getFormat()
        && (!IrisShaders.active() || iris.exchangePrimary());
    if (exchangeImage) {
      ((MinecraftAccessor) client).portalix$mainTarget(target);
      target = primary;
    } else {
      iris.invalidateHistory();
      IrisShaders.invalidatePrimaryHistory();
    }
    return exchangeImage;
  }

  public void terrainChanged(SectionRenderDispatcher.RenderSection section) {
    if (((LevelRendererAccessor) renderer).portalix$viewArea() != null)
      renderer.getSectionOcclusionGraph().schedulePropagationFrom(section);
  }

  public void consumeChunkEvents(SceneTerrainChanges events) {
    if (!PortalTerrainBackend.usesVanillaChunkEvents()) return;
    var area = ((LevelRendererAccessor) renderer).portalix$viewArea();
    if (area == null) return;
    var graph = renderer.getSectionOcclusionGraph();
    for (long added : events.addedLoadedChunks) graph.onChunkReadyToRender(ChunkPos.unpack(added));
    var center = area.getCameraSectionPos();
    int radius = area.getViewDistance();
    for (long removed : events.removedLoadedChunks) {
      if (ChunkTrackingView.isInViewDistance(center.x(), center.z(), radius,
          ChunkPos.getX(removed), ChunkPos.getZ(removed))) {
        graph.invalidate();
        break;
      }
    }
    for (long added : events.addedEmptySections) {
      var section = area.getRenderSection(added);
      if (section != null) graph.schedulePropagationFrom(section);
    }
    for (long removed : events.removedEmptySections) renderer.onSectionBecomingNonEmpty(removed);
  }

  public Binding bind() {
    return new Binding(renderer);
  }

  public static final class Binding implements AutoCloseable {
    private final LevelRenderer previous;

    private Binding(LevelRenderer renderer) {
      var client = Minecraft.getInstance();
      previous = client.levelRenderer;
      ((MinecraftAccessor) client).portalix$levelRenderer(renderer);
    }

    @Override
    public void close() {
      ((MinecraftAccessor) Minecraft.getInstance()).portalix$levelRenderer(previous);
    }
  }

  public void extract(DeltaTracker delta, PortalSceneCamera camera, boolean visible) {
    try (var dispatcherScope = new SceneDispatcherScope(); var irisScope = iris.extract()) {
      var client = Minecraft.getInstance();
      PortalTerrainBackend.attach(renderer, terrainOwner);
      if (PortalSceneManager.consumeTerrainPreparationChange())
        ((SceneExtractorAccess) renderer).portalix$invalidateView();
      var access = (LevelRendererAccessor) renderer;
      access.portalix$viewDistance(client.options.getEffectiveRenderDistance());
      access.portalix$ticks(((LevelRendererAccessor) terrainOwner).portalix$ticks());
      var game = client.gameRenderer;
      float partialTick = delta.getGameTimeDeltaPartialTick(false);
      if (visible) camera.extractRenderState(state.cameraRenderState, camera.getCameraEntityPartialTicks(delta));
      if (visible || !PortalTerrainBackend.usesVanillaChunkEvents())
        state.cameraRenderState.fogData = camera.extractFog(fog, delta, game.getBossOverlayWorldDarkening(partialTick));
      // Iris moves the terrain cull to renderLevel, which hidden views omit.
      if (!visible && IrisShaders.active())
        access.portalix$cullTerrain(camera, camera.getCullFrustum(), client.player.isSpectator());
      renderer.update(camera);
      if (!visible) return;
      var lightExtractor = ((GameRendererAccessor) game).portalix$lightmapExtractor();
      var lightAccess = (LightmapRenderStateExtractorAccessor) lightExtractor;
      boolean lightDirty = lightAccess.portalix$needsUpdate();
      lightAccess.portalix$needsUpdate(true);
      try {
        lightExtractor.extract(lightmapState, partialTick);
      } finally {
        lightAccess.portalix$needsUpdate(lightDirty);
      }
      renderer.extractLevel(delta, camera, partialTick);
      particles.values().removeIf(group -> !state.particlesRenderState.particles.contains(group));
    }
  }

  public void maintain() {
    state.reset();
  }

  public void render(DeltaTracker delta, Camera camera, PortalEndpoint exit, int width, int height) {
    Minecraft client = Minecraft.getInstance();
    var access = (LevelRendererAccessor) renderer;
    var outline = access.portalix$outlineTarget();
    var descriptor = new RenderTargetDescriptor(width, height, true, 0);
    if (target.width != width || target.height != height) {
      var previous = target;
      target = resources.acquire(descriptor);
      releaseTarget(previous);
    }
    if (outline.width != width || outline.height != height) {
      access.portalix$outlineTarget(resources.acquire(descriptor));
      releaseTarget(outline);
    }
    var oldProjection = RenderSystem.getProjectionMatrixBuffer();
    var oldProjectionType = RenderSystem.getProjectionType();
    var oldFog = RenderSystem.getShaderFog();
    var oldGlobals = RenderSystem.getGlobalSettingsUniform();
    var oldColor = RenderSystem.outputColorTextureOverride;
    var oldDepth = RenderSystem.outputDepthTextureOverride;
    try (var dispatcherScope = new SceneDispatcherScope(); var irisScope = iris.begin()) {
      RenderSystem.outputColorTextureOverride = null;
      RenderSystem.outputDepthTextureOverride = null;
      var cameraState = state.cameraRenderState;
      var options = client.gameRenderer.getGameRenderState().optionsRenderState;
      globals.update(target.width, target.height, options.glintStrength, level.getGameTime(), delta,
          options.menuBackgroundBlurriness, camera.position(), options.textureFiltering == TextureFilteringMethod.RGSS);
      if (uploadedLightmap.update(lightmapState)) lightmap.render(lightmapState);
      client.getEntityRenderDispatcher().prepare(camera, null);
      client.getBlockEntityRenderDispatcher().prepare(camera.position());
      RenderSystem.setProjectionMatrix(projection.getBuffer(cameraState.projectionMatrix), ProjectionType.PERSPECTIVE);
      PortalClipping.begin(cameraState.projectionMatrix, cameraState.viewRotationMatrix, cameraState.pos, exit);
      fog.updateBuffer(cameraState.fogData);
      renderer.renderLevel(resources, delta, ((GameRendererAccessor) client.gameRenderer).portalix$renderBlockOutline(),
          cameraState, cameraState.viewRotationMatrix, fog.getBuffer(FogRenderer.FogMode.WORLD),
          cameraState.fogData.color, true, state.chunkSectionsToRender);
      renderer.doEntityOutline();
      renderer.endFrame();
      features.getSubmitNodeStorage().endFrame();
      features.endFrame();
      fog.endFrame();
    } finally {
      PortalClipping.end();
      RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
      RenderSystem.setShaderFog(oldFog);
      RenderSystem.setGlobalSettingsUniform(oldGlobals);
      RenderSystem.outputColorTextureOverride = oldColor;
      RenderSystem.outputDepthTextureOverride = oldDepth;
    }
  }

  private void releaseTarget(RenderTarget target) {
    resources.release(new RenderTargetDescriptor(target.width, target.height, true, 0), target);
  }

  @Override
  public void close() {
    PortalFrameTerrainBudget.release(renderer);
    iris.close();
    PortalTerrainBackend.detach(renderer);
    renderer.close();
    features.close();
    target.destroyBuffers();
    projection.close();
    fog.close();
    lightmap.close();
    globals.close();
    postProcessBuffers.values().forEach(MappableRingBuffer::close);
    postProcessBuffers.clear();
  }
}
