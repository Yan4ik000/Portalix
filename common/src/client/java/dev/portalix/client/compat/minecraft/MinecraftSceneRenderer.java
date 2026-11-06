package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.GpuFormat;
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
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.compat.iris.IrisPortalScene;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.mixin.scene.LightmapRenderStateExtractorAccessor;
import dev.portalix.client.mixin.scene.MinecraftAccessor;
import dev.portalix.client.mixin.scene.SkyRendererAccessor;
import dev.portalix.client.portal.clip.PortalClipping;
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
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Vector4f;

@SuppressWarnings("try")
public final class MinecraftSceneRenderer implements AutoCloseable {
  private final IrisPortalScene iris = new IrisPortalScene();
  private final ClientLevel level;
  private final LevelRenderState state = new LevelRenderState();
  private final Map<QuadParticleGroup, QuadParticleRenderState> particles = new IdentityHashMap<>();
  private final Map<PostPass, MappableRingBuffer> postProcessBuffers = new IdentityHashMap<>();
  private final LevelRenderer renderer;
  private final LevelRenderer terrainOwner;
  private final LevelExtractor extractor;
  private RenderTarget target;
  private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("Portal projection");
  private final FogRenderer fog;
  private final Lightmap lightmap = new Lightmap();
  private final GlobalSettingsUniform globals = new GlobalSettingsUniform();
  private final LightmapRenderState lightmapState = new LightmapRenderState();
  private final SceneLightmapState uploadedLightmap = new SceneLightmapState();
  // Sequential views borrow the main frame's pool; GameRenderer owns its lifetime.
  private final CrossFrameResourcePool resources;
  private boolean levelAttached;

  public MinecraftSceneRenderer(ClientLevel level) {
    this.level = level;
    var previousFog = RenderSystem.getShaderFog();
    fog = new FogRenderer();
    RenderSystem.setShaderFog(previousFog);
    Minecraft client = Minecraft.getInstance();
    resources = ((GameRendererAccessor) client.gameRenderer).portalix$resources();
    terrainOwner = client.levelRenderer;
    renderer =
        new LevelRenderer(
            client.levelRenderer.entityRenderDispatcher(),
            client.levelRenderer.blockEntityRenderDispatcher(),
            client.getModelManager(),
            client.getTextureManager(),
            client.getAtlasManager(),
            client.getShaderManager(),
            client.gameRenderer,
            1,
            1);
    ((LevelRendererAccessor) renderer).portalix$state(state);
    extractor = new LevelExtractor(client, state, renderer);
    target = new TextureTarget("Portal", 1, 1, true, GpuFormat.RGBA8_UNORM);
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

  public void invalidateHistory() {
    iris.invalidateHistory();
  }

  /** Must run before either view is extracted or rendered in the current frame. */
  public boolean exchangePrimary(boolean imageHistoryCompatible) {
    var client = Minecraft.getInstance();
    PortalTerrainBackend.exchangePrimary(renderer);
    ((SceneExtractorAccess) client.levelExtractor).portalix$invalidateView();
    ((SceneExtractorAccess) extractor).portalix$invalidateView();

    var primary = client.gameRenderer.mainRenderTarget();
    boolean exchangeImage =
        imageHistoryCompatible
            && target.width == primary.width
            && target.height == primary.height
            && target.getColorTexture().getFormat() == primary.getColorTexture().getFormat()
            && target.getDepthTexture().getFormat() == primary.getDepthTexture().getFormat()
            && (!IrisShaders.active() || iris.exchangePrimary());
    if (exchangeImage) {
      ((GameRendererAccessor) client.gameRenderer).portalix$mainTarget(target);
      target = primary;
      retargetSky(client.levelRenderer, client.gameRenderer.mainRenderTarget());
      retargetSky(renderer, target);
    } else {
      iris.invalidateHistory();
      IrisShaders.invalidatePrimaryHistory();
    }
    return exchangeImage;
  }

  public void terrainChanged(SectionRenderDispatcher.RenderSection section) {
    renderer.sectionOcclusionGraph().schedulePropagationFrom(section);
  }

  public void consumeChunkEvents(ChunkLoadingRenderState events) {
    if (!PortalTerrainBackend.usesVanillaChunkEvents()) return;
    var graph = renderer.sectionOcclusionGraph();
    graph.updateLoadedChunks(events.addedLoadedChunks, events.removedLoadedChunks);
    graph.updateEmptySections(events.addedEmptySections, events.removedEmptySections);
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

  public void extract(DeltaTracker delta, Camera camera, boolean visible) {
    try (var dispatcherScope = new SceneDispatcherScope(renderer);
        var irisScope = iris.extract()) {
      PortalTerrainBackend.attach(renderer, terrainOwner);
      if (!levelAttached) {
        extractor.setLevel(level);
        levelAttached = true;
      }
      ((SceneExtractorAccess) extractor)
          .portalix$shareTerrain(Minecraft.getInstance().levelExtractor);
      var lightExtractor =
          ((GameRendererAccessor) Minecraft.getInstance().gameRenderer)
              .portalix$lightmapExtractor();
      var lightAccess = (LightmapRenderStateExtractorAccessor) lightExtractor;
      boolean lightDirty = lightAccess.portalix$needsUpdate();
      lightAccess.portalix$needsUpdate(true);
      try {
        lightExtractor.extract(lightmapState, delta.getGameTimeDeltaPartialTick(false));
      } finally {
        lightAccess.portalix$needsUpdate(lightDirty);
      }
      state.cameraRenderState.fogData =
          fog.setupFog(
              camera,
              Minecraft.getInstance().options.getEffectiveRenderDistance(),
              delta,
              Minecraft.getInstance()
                  .gameRenderer
                  .bossOverlayWorldDarkening(delta.getGameTimeDeltaPartialTick(false)),
              level);
      extractor.extract(delta, camera, delta.getGameTimeDeltaPartialTick(false));
      if (visible)
        particles.values().removeIf(group -> !state.particlesRenderState.particles.contains(group));

      state.render3dCrosshair = false;
      state.playerCompiledSectionCallback = null;
    }
  }

  public void maintain() {
    PortalTerrainBackend.maintain(renderer, state.cameraRenderState, state.chunkLoadingRenderState);
    state.entityRenderStates.clear();
    state.blockEntityRenderStates.clear();
    state.blockBreakingRenderStates.clear();
    renderer.endFrame();
  }

  public void render(DeltaTracker delta, Camera camera, PortalEndpoint exit, int width, int height) {
    Minecraft client = Minecraft.getInstance();
    var access = (LevelRendererAccessor) renderer;
    var outline = access.portalix$outlineTarget();
    var descriptor =
        new RenderTargetDescriptor(
            width, height, true, new Vector4f(), GpuFormat.RGBA8_UNORM);
    if (target.width != width || target.height != height) {
      var previous = target;
      target = resources.acquire(descriptor);
      releaseTarget(previous);
      retargetSky(renderer, target);
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
    var dispatcher = renderer.entityRenderDispatcher();
    try (var dispatcherScope = new SceneDispatcherScope(renderer);
        var irisScope = iris.begin()) {
      RenderSystem.outputColorTextureOverride = null;
      RenderSystem.outputDepthTextureOverride = null;
      CameraRenderState state = this.state.cameraRenderState;
      var options = client.gameRenderer.gameRenderState().optionsRenderState;
      globals.update(
          target.width,
          target.height,
          options.glintStrength,
          level.getGameTime(),
          delta,
          options.menuBackgroundBlurriness,
          camera.position(),
          options.textureFiltering == TextureFilteringMethod.RGSS);
      if (uploadedLightmap.update(lightmapState)) lightmap.render(lightmapState);
      dispatcher.prepare(camera, null);
      renderer.blockEntityRenderDispatcher().prepare(camera.position());
      RenderSystem.setProjectionMatrix(
          projection.getBuffer(state.projectionMatrix), ProjectionType.PERSPECTIVE);
      PortalClipping.begin(
          state.projectionMatrix, state.viewRotationMatrix, state.pos, exit);
      fog.updateBuffer(state.fogData);
      renderer.render(
          resources,
          delta,
          ((GameRendererAccessor) client.gameRenderer).portalix$renderBlockOutline(),
          state,
          state.viewRotationMatrix,
          fog.getBuffer(FogRenderer.FogMode.WORLD),
          state.fogData.color,
          true);
      renderer.endFrame();
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
    resources.release(
        new RenderTargetDescriptor(
            target.width,
            target.height,
            true,
            new Vector4f(),
            target.getColorTexture().getFormat()),
        target);
  }

  @Override
  public void close() {
    PortalFrameTerrainBudget.release(renderer);
    iris.close();
    PortalTerrainBackend.detach(renderer);
    renderer.close();
    target.destroyBuffers();
    projection.close();
    fog.close();
    lightmap.close();
    globals.close();
    postProcessBuffers.values().forEach(MappableRingBuffer::close);
    postProcessBuffers.clear();
  }

  static void retargetSky(LevelRenderer renderer, RenderTarget target) {
    if (renderer.skyRenderer() != null)
      ((SkyRendererAccessor) renderer.skyRenderer()).portalix$target(target);
  }
}
