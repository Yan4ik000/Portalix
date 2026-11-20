package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.RenderTargetDescriptor.TextureProperties;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisPortalScene;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.mixin.scene.LightmapRenderStateExtractorAccessor;
import dev.portalix.client.mixin.scene.MinecraftAccessor;
import dev.portalix.client.mixin.scene.SkyRendererAccessor;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.scene.PortalSceneCamera;
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
    target = new TextureTarget("Portal", 1, 1, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
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

  public void extract(DeltaTracker delta, PortalSceneCamera camera, boolean visible) {
    try (var dispatcherScope = new SceneDispatcherScope(renderer);
        var outputScope = IrisEffectTarget.override(null);
        var irisScope = iris.extract()) {
      PortalTerrainBackend.attach(renderer, terrainOwner);
      if (!levelAttached) {
        extractor.setLevel(level);
        levelAttached = true;
      }
      ((SceneExtractorAccess) extractor)
          .portalix$shareTerrain(Minecraft.getInstance().levelExtractor);
      if (visible) {
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
      }
      state.cameraRenderState.fogData = camera.extractFog(fog, delta,
          Minecraft.getInstance().gameRenderer.bossOverlayWorldDarkening(delta.getGameTimeDeltaPartialTick(false)));
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
    if (target.width != width || target.height != height) {
      var previous = target;
      target = resources.acquire(targetDescriptor(width, height, previous));
      releaseTarget(previous);
      retargetSky(renderer, target);
    }
    if (outline.width != width || outline.height != height) {
      access.portalix$outlineTarget(resources.acquire(targetDescriptor(width, height, outline)));
      releaseTarget(outline);
    }
    var oldProjection = RenderSystem.getProjectionMatrixBuffer();
    var oldProjectionType = RenderSystem.getProjectionType();
    var oldFog = RenderSystem.getShaderFog();
    var oldGlobals = RenderSystem.getGlobalSettingsUniform();
    var dispatcher = renderer.entityRenderDispatcher();
    try (var dispatcherScope = new SceneDispatcherScope(renderer);
        var irisScope = iris.begin()) {
      CameraRenderState state = this.state.cameraRenderState;
      var options = client.gameRenderer.gameRenderState().optionsRenderState;
      globals.update(
          target.width,
          target.height,
          options.glintStrength,
          level.getGameTime(),
          delta.getGameTimeDeltaPartialTick(false),
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
          ((GameRendererAccessor) client.gameRenderer).portalix$renderBlockOutline(),
          state,
          fog.getBuffer(FogRenderer.FogMode.WORLD),
          state.fogData.color,
          true,
          true);
      renderer.endFrame();
      fog.endFrame();
    } finally {
      PortalClipping.end();
      RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
      RenderSystem.setShaderFog(oldFog);
      RenderSystem.setGlobalSettingsUniform(oldGlobals);
    }
  }

  private void releaseTarget(RenderTarget target) {
    resources.release(targetDescriptor(target.width, target.height, target), target);
  }

  private static RenderTargetDescriptor targetDescriptor(int width, int height, RenderTarget target) {
    var depth = target.getDepthTexture();
    return new RenderTargetDescriptor(
        width, height, new TextureProperties(new Vector4f(), target.getColorTexture().getFormat()),
        depth == null ? null : new TextureProperties(new Vector4f(), depth.getFormat()));
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
