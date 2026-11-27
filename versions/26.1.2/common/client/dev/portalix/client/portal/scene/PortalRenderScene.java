package dev.portalix.client.portal.scene;

import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.minecraft.MinecraftSceneRenderer;
import dev.portalix.client.mixin.scene.LevelRendererAccessor;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

@SuppressWarnings("try")
final class PortalRenderScene implements AutoCloseable {
  private final ClientLevel level;
  final MinecraftSceneRenderer rendering;
  final LevelRenderer renderer;
  final PortalSceneCamera camera = new PortalSceneCamera();
  final Matrix4f environmentProjection = new Matrix4f();
  List<PortalLink> path;
  boolean renderedThisFrame;
  boolean destinationReady;
  boolean terrainComplete;
  boolean terrainPrepared;
  boolean extracted;
  long lastTick = Long.MIN_VALUE;
  long lastRenderedFrame = Long.MIN_VALUE;
  long lastUsedFrame = Long.MIN_VALUE;
  private int waitingForColumnsAtRadius = -1;
  private long completenessTick = Long.MIN_VALUE;
  PortalScreenVisibility.Bounds visible;
  PortalSceneViewport viewport;
  Vec3 plannedPosition;

  PortalRenderScene(ClientLevel level) {
    this.level = level;
    rendering = new MinecraftSceneRenderer(level);
    renderer = rendering.renderer();
  }

  void bind(List<PortalLink> path) {
    var bound = List.copyOf(path);
    if (bound.equals(this.path)) return;
    boolean retarget =
        this.path != null && !link().exit().endpoint().equals(bound.getLast().exit().endpoint());
    this.path = bound;
    renderedThisFrame = false;
    lastRenderedFrame = Long.MIN_VALUE;
    terrainPrepared = false;
    destinationReady = false;
    terrainComplete = false;
    if (retarget) rendering.retarget();
    else rendering.invalidateHistory();
    lastTick = Long.MIN_VALUE;
    waitingForColumnsAtRadius = -1;
    completenessTick = Long.MIN_VALUE;
  }

  void rebase(List<PortalLink> path) {
    this.path = List.copyOf(path);
    renderedThisFrame = false;
    lastTick = Long.MIN_VALUE;
    waitingForColumnsAtRadius = -1;
    completenessTick = Long.MIN_VALUE;
  }

  PortalLink link() {
    return path.getLast();
  }

  boolean needsTerrainPreparation() {
    if (visible == null && !destinationLoaded(level, link())) return false;
    if (visible != null && !destinationReady) return true;
    int radius = terrainRadius();
    return PortalSceneManager.hasChunkChanges(PortalViewLimits.origin(link().exit().endpoint()), radius)
        || lastTick != level.getGameTime()
            && (camera.frustumChanged() || !terrainComplete && waitingForColumnsAtRadius != radius);
  }

  static boolean destinationLoaded(ClientLevel level, PortalLink link) {
    var center = PortalViewLimits.frontColumn(link.exit().endpoint());
    return level.getChunkSource().getChunk(center.x(), center.z(), ChunkStatus.FULL, false) != null;
  }

  int terrainRadius() {
    return PortalSceneManager.terrainRadius(this, Minecraft.getInstance().options.getEffectiveRenderDistance());
  }

  void consumeChunkEvents(SceneTerrainChanges events) {
    rendering.consumeChunkEvents(events);
    if (PortalTerrainDemand.affectedBy(events, PortalViewLimits.origin(link().exit().endpoint()), terrainRadius())) {
      terrainComplete = false;
      waitingForColumnsAtRadius = -1;
      completenessTick = Long.MIN_VALUE;
    }
  }

  void terrainChanged(int x, int y, int z) {
    var area = ((LevelRendererAccessor) renderer).portalix$viewArea();
    if (area == null) {
      terrainComplete = false;
      return;
    }
    var center = SectionPos.of(PortalViewLimits.origin(link().exit().endpoint()));
    int radius = terrainRadius() + 1;
    // Include boundary neighbours: mesh changes can open an occlusion path into the view.
    if (Math.abs(x - center.x()) <= radius && Math.abs(y - center.y()) <= radius
        && Math.abs(z - center.z()) <= radius) terrainComplete = false;
  }

  void configure(
      Vec3 position, Quaternionf orientation, Matrix4fc baseProjection, float far, Camera root,
      Matrix4fc viewEffects) {
    plannedPosition = position;
    var main = Minecraft.getInstance().getMainRenderTarget();
    var entrance = link().entrance().endpoint();
    var entity = root.entity();
    Vec3 velocity = entity == null ? Vec3.ZERO
        : entity.position().subtract(entity.xo, entity.yo, entity.zo);
    var view = PortalCameraController.currentView();
    if (view != null)
      for (var crossing : view.spacePath()) velocity = crossing.transform().vector(velocity);
    double approachSpeed = Math.max(0, -velocity.dot(entrance.normal())) * 20;
    double arrivalDistance = Math.max(entrance.width(), entrance.height()) * 2 + approachSpeed;
    var relative = link().inverse().transform().point(position).subtract(entrance.center());
    double normal = relative.dot(entrance.normal());
    double right = Math.max(0, Math.abs(relative.dot(entrance.right())) - entrance.width() / 2);
    double up = Math.max(0, Math.abs(relative.dot(entrance.up())) - entrance.height() / 2);
    boolean arrival =
        path.size() == 1
            && (visible != null || approachSpeed > 0)
            && normal * normal + right * right + up * up <= arrivalDistance * arrivalDistance;
    camera.configure(
        level,
        root,
        position,
        orientation,
        baseProjection,
        far,
        visible == null ? null : PortalSceneViewport.of(visible, main.width, main.height),
        link().exit().endpoint(),
        arrival,
        terrainRadius(),
        viewEffects);
    camera.extractRenderState(rendering.state().cameraRenderState, 0);
    renderedThisFrame = false;
  }

  void extract(DeltaTracker delta) {
    try (var ignored = new PortalSceneManager.SceneScope(this)) {
      rendering.extract(delta, camera, visible != null);
    }
  }

  void maintain() {
    try (var ignored = new PortalSceneManager.SceneScope(this)) {
      rendering.maintain();
      updateCompleteness();
    }
  }

  void render(DeltaTracker delta) {
    try (var ignored = new PortalSceneManager.SceneScope(this)) {
      if (lastRenderedFrame != PortalFrameTerrainBudget.frame() - 1) rendering.invalidateHistory();
      rendering.render(delta, camera, link().exit().endpoint(), viewport.width(), viewport.height());
      renderedThisFrame = true;
      lastRenderedFrame = PortalFrameTerrainBudget.frame();
      updateCompleteness();
    }
  }

  void unloaded(LongSet removedChunks) {
    var portal = link().exit().endpoint();
    var position = BlockPos.containing(PortalViewLimits.origin(portal));
    if (removedChunks.contains(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4)))
      destinationReady = false;
  }

  private void updateCompleteness() {
    if (visible != null) {
      terrainComplete = false;
      waitingForColumnsAtRadius = -1;
    } else if (completenessTick != level.getGameTime()) {
      var state = rendering.state().cameraRenderState;
      waitingForColumnsAtRadius = PortalTerrainDemand.resident(state) ? -1 : terrainRadius();
      terrainComplete = waitingForColumnsAtRadius < 0 && PortalTerrainBackend.meshesReady(renderer, state);
      completenessTick = level.getGameTime();
    }
    if (destinationReady) return;
    Vec3 exit = PortalViewLimits.origin(link().exit().endpoint());
    var block = BlockPos.containing(exit);
    var chunk =
        level
            .getChunkSource()
            .getChunk(block.getX() >> 4, block.getZ() >> 4, ChunkStatus.FULL, false);
    boolean empty =
        chunk != null
            && level.isInsideBuildHeight(block.getY())
            && chunk.getSection(chunk.getSectionIndex(block.getY())).hasOnlyAir();
    destinationReady = empty || renderer.isSectionCompiledAndVisible(block);
  }

  @Override
  public void close() {
    try (var ignored = new PortalSceneManager.SceneScope(this)) {
      rendering.close();
    }
  }
}
