package dev.portalix.client.compat.iris;

import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.mixin.compat.iris.PipelineManagerAccessor;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ImmediateState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

public final class IrisScene implements ShaderBackend.Scene {
  private static final int PREPARED_VIEW_CAPACITY = 2 * PortalSceneManager.RECURSION_DEPTH;
  private static WorldRenderingPipeline current;
  private static final Map<WorldRenderingPipeline, Views> views = new IdentityHashMap<>();
  private static boolean preparing;
  private PipelineManager manager;
  private WorldRenderingPipeline pipeline;
  private Views owner;
  private boolean invalidated = true;
  private Environment environment;

  public void invalidateHistory() {
    invalidated = true;
  }

  @Override
  public boolean exchangePrimary() {
    if (!IrisShaders.active() || pipeline == null || owner.closed) return false;
    var activeManager = Iris.getPipelineManager();
    var primary = activeManager.getPipelineNullable();
    if (manager != activeManager || views.get(primary) != owner) return false;
    if (current != null) throw new IllegalStateException("Cannot exchange an active Iris view");

    var promoted = pipeline;
    var access = (PipelineManagerAccessor) manager;
    access.portalix$dimensions().put(owner.dimension, promoted);
    access.portalix$pipeline(promoted);
    views.remove(primary);
    views.put(promoted, owner);
    owner.pipelines.set(owner.pipelines.indexOf(promoted), primary);
    pipeline = primary;
    return true;
  }

  public static boolean preparing() {
    return preparing;
  }

  public static void prepare(
      PipelineManager manager, NamespacedId dimension, WorldRenderingPipeline primary) {
    if (!(primary instanceof IrisRenderingPipeline)
        || current != null
        || views.containsKey(primary)) return;
    for (var previous : List.copyOf(views.keySet())) release(previous);
    var pool = new Views(manager, dimension);
    views.put(primary, pool);
    for (int i = 0; i < PREPARED_VIEW_CAPACITY; i++)
      pool.available.addLast(pool.create());
  }

  public static void release(WorldRenderingPipeline primary) {
    var pool = views.remove(primary);
    if (pool == null) return;
    pool.closed = true;
    for (var pipeline : pool.pipelines) {
      pipeline.destroy();
    }
    pool.pipelines.clear();
    pool.available.clear();
  }

  private static final class Views {
    final PipelineManager manager;
    final NamespacedId dimension;
    final List<WorldRenderingPipeline> pipelines = new ArrayList<>();
    final ArrayDeque<WorldRenderingPipeline> available = new ArrayDeque<>();
    boolean closed;

    Views(PipelineManager manager, NamespacedId dimension) {
      this.manager = manager;
      this.dimension = dimension;
    }

    WorldRenderingPipeline create() {
      preparing = true;
      try {
        var pipeline = ((PipelineManagerAccessor) manager).portalix$factory().apply(dimension);
        ((IrisRenderingPipelineAccessor) pipeline).portalix$blockIdsInitialized(true);
        pipelines.add(pipeline);
        return pipeline;
      } finally {
        preparing = false;
      }
    }
  }

  public static WorldRenderingPipeline current() {
    return current;
  }

  public ExtractionScope extract() {
    return IrisShaders.active() ? new ExtractionScope() : null;
  }

  public final class ExtractionScope implements RenderScope {
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

  public Scope begin() {
    if (!IrisShaders.active()) return null;
    var activeManager = Iris.getPipelineManager();
    var activePrimary = activeManager.getPipelineNullable();
    var activeOwner = views.get(activePrimary);
    if (manager != activeManager || owner != activeOwner) {
      close();
      manager = activeManager;
      owner = activeOwner;
    }
    if (pipeline == null) {
      pipeline = owner.available.pollFirst();
      if (pipeline == null) pipeline = owner.create();
      invalidated = true;
    }
    if (invalidated) {
      ((IrisTargetHistory) ((IrisRenderingPipelineAccessor) pipeline).portalix$targets())
          .portalix$invalidateHistory();
      invalidated = false;
    }
    return new Scope();
  }

  @Override
  public void close() {
    if (pipeline != null && !owner.closed) {
      owner.available.addLast(pipeline);
      while (owner.available.size() > PREPARED_VIEW_CAPACITY) {
        var retired = owner.available.removeFirst();
        owner.pipelines.remove(retired);
        retired.destroy();
      }
    }
    pipeline = null;
    manager = null;
    owner = null;
    invalidated = true;
  }

  public final class Scope implements RenderScope {
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
      environment.apply();
    }

    @Override
    public void close() {
      ((PipelineManagerAccessor) manager).portalix$pipeline(previousPipeline);
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
