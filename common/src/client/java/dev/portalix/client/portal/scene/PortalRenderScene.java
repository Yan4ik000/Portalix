package dev.portalix.client.portal.scene;

import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.minecraft.MinecraftSceneRenderer;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.portal.PortalLink;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
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
  boolean extracted;
  long lastTick = Long.MIN_VALUE;
  long lastSection = Long.MIN_VALUE;
  long lastRenderedFrame = Long.MIN_VALUE;
  long lastUsedFrame = Long.MIN_VALUE;
  private long completenessTick = Long.MIN_VALUE;
  PortalScreenVisibility.Bounds visible;
  PortalSceneViewport viewport;

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
    lastRenderedFrame = Long.MIN_VALUE;
    if (retarget) {
      rendering.retarget();
      destinationReady = false;
      terrainComplete = false;
    } else rendering.invalidateHistory();
    lastTick = Long.MIN_VALUE;
    completenessTick = Long.MIN_VALUE;
  }

  void rebase(List<PortalLink> path) {
    this.path = List.copyOf(path);
    renderedThisFrame = false;
    lastTick = Long.MIN_VALUE;
    completenessTick = Long.MIN_VALUE;
  }

  PortalLink link() {
    return path.getLast();
  }

  boolean needsTerrainPreparation() {
    return !destinationReady || PortalSceneManager.hasChunkChanges(
        camera.position(), Minecraft.getInstance().options.getEffectiveRenderDistance())
        || lastTick != level.getGameTime() && (camera.frustumChanged() || !terrainComplete);
  }

  void consumeChunkEvents(ChunkLoadingRenderState events) {
    rendering.consumeChunkEvents(events);
    var area = renderer.viewArea();
    int radius = area == null ? Minecraft.getInstance().options.getEffectiveRenderDistance()
        : area.getViewDistance();
    if (PortalTerrainDemand.affectedBy(events, camera.position(), radius)) {
      terrainComplete = false;
      completenessTick = Long.MIN_VALUE;
    }
  }

  void terrainChanged(int x, int y, int z) {
    var area = renderer.viewArea();
    if (area == null) {
      terrainComplete = false;
      return;
    }
    var center = SectionPos.of(camera.position());
    int radius = area.getViewDistance() + 1;
    // Include boundary neighbours: mesh changes can open an occlusion path into the view.
    if (Math.abs(x - center.x()) <= radius && Math.abs(y - center.y()) <= radius
        && Math.abs(z - center.z()) <= radius) terrainComplete = false;
  }

  void configure(
      CameraRenderState parent, Matrix4fc baseProjection, Camera root, Matrix4fc viewEffects) {
    var transform = link().transform();
    Vec3 position = transform.point(parent.pos);
    Quaternionf orientation = transform.rotation().mul(parent.orientation, new Quaternionf());
    configure(position, orientation, baseProjection, parent.depthFar, root, viewEffects);
  }

  void configure(
      Vec3 position, Quaternionf orientation, Matrix4fc baseProjection, float far, Camera root,
      Matrix4fc viewEffects) {
    var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
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
    var position = BlockPos.containing(portal.center().add(portal.normal().scale(.1)));
    if (removedChunks.contains(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4)))
      destinationReady = false;
  }

  private void updateCompleteness() {
    if (visible != null) terrainComplete = false;
    else if (completenessTick != level.getGameTime()) {
      terrainComplete = PortalTerrainBackend.completeDemand(renderer, rendering.state().cameraRenderState);
      completenessTick = level.getGameTime();
    }
    Vec3 exit = link().exit().endpoint().center().add(link().exit().endpoint().normal().scale(.1));
    var block = BlockPos.containing(exit);
    var chunk =
        level
            .getChunkSource()
            .getChunk(block.getX() >> 4, block.getZ() >> 4, ChunkStatus.FULL, false);
    boolean empty =
        chunk != null
            && level.isInsideBuildHeight(block.getY())
            && chunk.getSection(chunk.getSectionIndex(block.getY())).hasOnlyAir();
    destinationReady |= empty || renderer.isSectionCompiledAndVisible(block);
  }

  @Override
  public void close() {
    try (var ignored = new PortalSceneManager.SceneScope(this)) {
      rendering.close();
    }
  }
}
