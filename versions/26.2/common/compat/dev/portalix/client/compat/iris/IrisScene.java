package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import dev.portalix.client.mixin.compat.iris.PipelineManagerAccessor;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

public final class IrisScene implements ShaderBackend.Scene {
  private enum Creation { PRIMARY, PREWARM, PORTAL }
  private static WorldRenderingPipeline current;
  private static final Map<WorldRenderingPipeline, PipelinePool> pipelinePools = new IdentityHashMap<>();
  private static Creation creation = Creation.PRIMARY;
  private static RenderTarget prewarmTarget;
  private static IrisPipelineCreation pendingCreation;
  private WorldRenderingPipeline pipeline;
  private PipelinePool pool;
  private boolean needsHistoryReset = true;
  private Environment environment;

  @Override
  public void invalidateHistory() {
    needsHistoryReset = true;
  }

  @Override
  public boolean exchangePrimary() {
    if (!IrisShaders.active() || !(pipeline instanceof IrisRenderingPipeline)
        || needsHistoryReset || pool.closed) return false;
    var activeManager = Iris.getPipelineManager();
    var primary = activeManager.getPipelineNullable();
    if (pool.manager != activeManager || pipelinePools.get(primary) != pool) return false;
    if (current != null) throw new IllegalStateException("Cannot exchange an active Iris view");

    var promoted = pipeline;
    var access = (PipelineManagerAccessor) activeManager;
    access.portalix$dimensions().put(pool.dimension, promoted);
    access.portalix$pipeline(promoted);
    bindStorage(promoted);
    pipelinePools.remove(primary);
    pipelinePools.put(promoted, pool);
    pool.pipelines.set(pool.pipelines.indexOf(promoted), primary);
    pipeline = primary;
    return true;
  }

  public static boolean preparing() {
    return creation != Creation.PRIMARY;
  }

  public static boolean prewarming() {
    return creation == Creation.PREWARM;
  }

  public static RenderTarget mainTarget() {
    return Minecraft.getInstance().gameRenderer.mainRenderTarget();
  }

  public static RenderTarget primaryTarget() {
    return ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).portalix$mainTarget();
  }

  public static void clearImage(GlImage image) {
    image.clear();
  }

  public static RenderTarget prewarmTarget() {
    return prewarmTarget;
  }

  public static int trackProgram(int program) {
    if (pendingCreation != null) pendingCreation.programs.add(program);
    return program;
  }

  public static int trackShader(int shader) {
    if (pendingCreation != null) pendingCreation.shaders.add(shader);
    return shader;
  }

  public static int trackTexture(int texture) {
    if (pendingCreation != null)
      pendingCreation.resources.add(() -> GlStateManager._deleteTexture(texture));
    return texture;
  }

  public static void trackResource(Runnable release) {
    if (pendingCreation != null) pendingCreation.resources.add(release);
  }

  private static void bindStorage(WorldRenderingPipeline pipeline) {
    if (pipeline instanceof IrisRenderingPipeline) {
      var storage = ((IrisRenderingPipelineAccessor) pipeline).portalix$storage();
      if (storage != null) storage.setupBuffers();
    }
  }

  public static void prepare(
      PipelineManager manager, NamespacedId dimension, WorldRenderingPipeline primary) {
    if (!(primary instanceof IrisRenderingPipeline)
        || current != null
        || pipelinePools.containsKey(primary)) return;
    for (var previous : List.copyOf(pipelinePools.keySet())) release(previous);
    var pool = new PipelinePool(manager, dimension);
    pipelinePools.put(primary, pool);
    var access = (IrisRenderingPipelineAccessor) primary;
    // Shadow compute can read screen-sized storage before Iris resizes it on the first frame.
    boolean compact = Arrays.stream(access.portalix$shadowComputes()).noneMatch(Objects::nonNull);
    try {
      if (compact) {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        pool.prewarmTarget = new TextureTarget(
            "Portal shader preparation", 1, 1, true, main.getColorTexture().getFormat());
      }
      for (int i = 0; i < IrisShaders.MAX_PORTAL_VIEWS; i++) {
        var pipeline = pool.create(compact ? Creation.PREWARM : Creation.PORTAL);
        if (compact && !(pipeline instanceof IrisRenderingPipeline)) {
          pipeline.destroy();
          compact = false;
          pipeline = pool.create(Creation.PORTAL);
        }
        if (pipeline == pool.fallback) break;
        pool.available.addLast(new SparePipeline(pipeline, !compact));
      }
    } catch (RuntimeException | Error failure) {
      release(primary);
      throw failure;
    }
  }

  public static void release(WorldRenderingPipeline primary) {
    var pool = pipelinePools.remove(primary);
    if (pool != null) pool.close();
  }

  private record SparePipeline(WorldRenderingPipeline pipeline, boolean resetHistory) {}

  private static final class PipelinePool {
    final PipelineManager manager;
    final NamespacedId dimension;
    final List<WorldRenderingPipeline> pipelines = new ArrayList<>();
    final ArrayDeque<SparePipeline> available = new ArrayDeque<>();
    WorldRenderingPipeline fallback;
    RenderTarget prewarmTarget;
    boolean closed;

    PipelinePool(PipelineManager manager, NamespacedId dimension) {
      this.manager = manager;
      this.dimension = dimension;
    }

    WorldRenderingPipeline create(Creation mode) {
      var resources = new IrisPipelineCreation();
      var previous = creation;
      var previousTarget = IrisScene.prewarmTarget;
      var previousCreation = pendingCreation;
      creation = mode;
      IrisScene.prewarmTarget = mode == Creation.PREWARM ? prewarmTarget : null;
      pendingCreation = resources;
      boolean completed = false;
      try {
        var pipeline = ((PipelineManagerAccessor) manager).portalix$factory().apply(dimension);
        if (pipeline instanceof IrisRenderingPipeline) {
          ((IrisRenderingPipelineAccessor) pipeline).portalix$blockIdsInitialized(true);
          pipelines.add(pipeline);
          completed = true;
        } else if (mode == Creation.PORTAL) {
          fallback = pipeline;
          pipelines.add(pipeline);
        }
        return pipeline;
      } finally {
        pendingCreation = previousCreation;
        IrisScene.prewarmTarget = previousTarget;
        creation = previous;
        try {
          if (!completed) resources.rollback();
        } finally {
          bindStorage(manager.getPipelineNullable());
        }
      }
    }

    void recycle(WorldRenderingPipeline pipeline) {
      if (closed || pipeline == fallback) return;
      available.addFirst(new SparePipeline(pipeline, true));
    }

    void releasePrewarmTarget() {
      var target = prewarmTarget;
      prewarmTarget = null;
      if (target != null) target.destroyBuffers();
    }

    void close() {
      closed = true;
      available.clear();
      fallback = null;
      RuntimeException failure = null;
      try {
        for (var pipeline : pipelines) {
          try {
            pipeline.destroy();
          } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
            else failure.addSuppressed(exception);
          }
        }
      } finally {
        pipelines.clear();
        try {
          releasePrewarmTarget();
        } finally {
          bindStorage(manager.getPipelineNullable());
        }
      }
      if (failure != null) throw failure;
    }
  }

  public static WorldRenderingPipeline current() {
    return current;
  }

  @Override
  public RenderScope extract() {
    return IrisShaders.active() ? new ExtractionScope() : null;
  }

  private final class ExtractionScope implements RenderScope {
    private final Environment previous = Environment.capture();

    @Override
    public void close() {
      environment = Environment.capture();
      previous.apply();
    }
  }

  private record Environment(Vector3d color, float density, float darkness) {
    static Environment capture() {
      var state = CapturedRenderingState.INSTANCE;
      return new Environment(
          state.getFogColor(), state.getFogDensity(), state.getDarknessLightFactor());
    }

    void apply() {
      var state = CapturedRenderingState.INSTANCE;
      state.setFogColor((float) color.x, (float) color.y, (float) color.z);
      state.setFogDensity(density);
      state.setDarknessLightFactor(darkness);
    }
  }

  @Override
  public RenderScope begin() {
    if (!IrisShaders.active()) return null;
    var activeManager = Iris.getPipelineManager();
    var activePrimary = activeManager.getPipelineNullable();
    var activePool = pipelinePools.get(activePrimary);
    if (pool != activePool) {
      close();
      pool = activePool;
    }
    if (pool == null) return null;
    if (pipeline == null) {
      var spare = pool.available.pollFirst();
      pipeline = spare != null ? spare.pipeline()
          : Objects.requireNonNull(pool.fallback, "More rendered portal views than prepared Iris pipelines");
      needsHistoryReset = spare != null && spare.resetHistory();
    }
    if (needsHistoryReset) {
      if (pipeline instanceof IrisPipelineHistory history) history.portalix$invalidateHistory();
      needsHistoryReset = false;
    }
    return new Scope();
  }

  @Override
  public void close() {
    var released = pipeline;
    var owner = pool;
    pipeline = null;
    pool = null;
    needsHistoryReset = true;
    if (released != null) owner.recycle(released);
  }

  private final class Scope implements RenderScope {
    private final PipelineManager manager = pool.manager;
    private final WorldRenderingPipeline previous = current;
    private final WorldRenderingPipeline previousPipeline = manager.getPipelineNullable();
    private final Matrix4fc modelView = CapturedRenderingState.INSTANCE.getGbufferModelView();
    private final Matrix4f projection =
        new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
    private final Environment previousEnvironment = Environment.capture();
    private final float cloudTime = CapturedRenderingState.INSTANCE.getCloudTime();
    private final float tickDelta = CapturedRenderingState.INSTANCE.getTickDelta();
    private final float alphaTest = CapturedRenderingState.INSTANCE.getCurrentAlphaTest();
    private final int entity = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
    private final int blockEntity = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
    private final int item = CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
    private final boolean rendering = ImmediateState.isRenderingLevel;
    private final boolean extended = ImmediateState.renderWithExtendedVertexFormat;
    private final boolean multiply = ImmediateState.safeToMultiply;
    private final boolean bypass = ImmediateState.bypass;
    private final boolean tessellation = ImmediateState.usingTessellation;

    Scope() {
      current = pipeline;
      ((PipelineManagerAccessor) manager).portalix$pipeline(pipeline);
      bindStorage(pipeline);
      if (environment != null) environment.apply();
    }

    @Override
    public void close() {
      ((PipelineManagerAccessor) manager).portalix$pipeline(previousPipeline);
      bindStorage(previousPipeline);
      current = previous;
      CapturedRenderingState.INSTANCE.setGbufferModelView(modelView);
      CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
      var state = CapturedRenderingState.INSTANCE;
      previousEnvironment.apply();
      state.setCloudTime(cloudTime);
      state.setTickDelta(tickDelta);
      state.setCurrentAlphaTest(alphaTest);
      state.setCurrentEntity(entity);
      state.setCurrentBlockEntity(blockEntity);
      state.setCurrentRenderedItem(item);
      ImmediateState.isRenderingLevel = rendering;
      ImmediateState.renderWithExtendedVertexFormat = extended;
      ImmediateState.safeToMultiply = multiply;
      ImmediateState.bypass = bypass;
      ImmediateState.usingTessellation = tessellation;
    }
  }
}
