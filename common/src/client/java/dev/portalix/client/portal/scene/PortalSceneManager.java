package dev.portalix.client.portal.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.MinecraftSceneRenderer;
import dev.portalix.client.mixin.terrain.ViewAreaAccessor;
import dev.portalix.client.portal.PortalMousePicking;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.entity.PortalEntityScenes;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.portal.terrain.PortalOcclusionOpenings;
import dev.portalix.client.portal.terrain.PortalFrameTerrainBudget;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import dev.portalix.client.portal.terrain.PortalTerrainDemand;
import dev.portalix.client.portal.terrain.PortalViewIndicesAccess;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

@SuppressWarnings("try")
public final class PortalSceneManager {
  public static final int RECURSION_DEPTH = 2;
  private static final int INITIAL_VIEW_CAPACITY = 2;
  private static final int IDLE_VIEW_CAPACITY = 2 * RECURSION_DEPTH;
  private static final ArrayDeque<PortalRenderScene> PREPARED_SCENES = new ArrayDeque<>();
  private static final Map<List<PortalLink>, PortalRenderScene> SCENES = new HashMap<>();
  private static final List<PortalRenderScene> RENDER_ORDER = new ArrayList<>();
  private static final List<PortalRenderScene> IDLE_SCENES = new ArrayList<>();
  private static final List<PortalScenePlanner.View> PLANNED_SCENES = new ArrayList<>();
  private static ClientLevel level;
  private static volatile ConcurrentLinkedQueue<SectionRenderDispatcher.RenderSection> terrainUpdates;
  private static volatile ConcurrentLinkedQueue<CompiledSectionMesh> terrainReleases;
  private static PortalRenderScene activeScene;
  private static CameraRenderState primaryCameraState;
  private static Camera primaryCamera;
  private static final TerrainWorkCameras NO_TERRAIN_WORK_CAMERAS = new TerrainWorkCameras(new Vec3[0]);
  private static volatile TerrainWorkCameras terrainWorkCameras = NO_TERRAIN_WORK_CAMERAS;
  private static final Matrix4f renderProjection = new Matrix4f();
  private static final ChunkLoadingRenderState CHUNK_EVENTS = new ChunkLoadingRenderState();

  private PortalSceneManager() {}

  public static TerrainWorkCameras terrainWorkCameras() {
    return terrainWorkCameras;
  }

  public static final class TerrainWorkCameras {
    private final Vec3[] positions;

    private TerrainWorkCameras(Vec3[] positions) {
      this.positions = positions;
    }

    public boolean isEmpty() {
      return positions.length == 0;
    }

    public double distanceSquared(BlockPos origin) {
      double distance = Double.MAX_VALUE;
      for (var position : positions) distance = Math.min(distance, origin.distToCenterSqr(position));
      return distance;
    }
  }

  public static Camera camera() {
    return activeScene == null ? null : activeScene.camera;
  }

  public static Vec3 environmentPosition() {
    if (activeScene != null) return activeScene.camera.environmentPosition();
    var view = PortalCameraController.currentView();
    return view == null || view.spacePath().isEmpty() && !PortalCameraController.active()
        ? null
        : view.position();
  }

  public static boolean consumeTerrainPreparationChange() {
    return activeScene.camera.consumeTerrainPreparationChange();
  }

  public static long visibilityRevision() {
    return activeScene.camera.visibilityRevision();
  }

  public static RenderTarget target() {
    return activeScene == null ? null : activeScene.rendering.target();
  }

  public static GpuTextureView lightmap() {
    return activeScene == null ? null : activeScene.rendering.lightmap();
  }

  public static MappableRingBuffer postProcessBuffer(
      PostPass pass, String name, MappableRingBuffer primary) {
    if (activeScene == null) return primary;

    return activeScene.rendering.postProcessBuffer(pass, name, primary);
  }

  public static CameraRenderState cameraState() {
    return activeScene == null ? null : activeScene.rendering.state().cameraRenderState;
  }

  public static boolean isPortalView() {
    return activeScene != null;
  }

  public static List<PortalLink> path() {
    return activeScene == null ? List.of() : activeScene.path;
  }

  public static void terrainChanged(SectionRenderDispatcher.RenderSection section) {
    var updates = terrainUpdates;
    if (updates != null) updates.add(section);
  }

  public static void terrainMeshReleased(CompiledSectionMesh mesh) {
    var releases = terrainReleases;
    if (releases != null) releases.add(mesh);
  }

  public static boolean hasPendingTerrainChanges() {
    var updates = terrainUpdates;
    return updates != null && !updates.isEmpty();
  }

  public static boolean hasChunkChanges(Vec3 position, int viewDistance) {
    return PortalTerrainDemand.affectedBy(CHUNK_EVENTS, position, viewDistance);
  }

  public static void invalidateTerrainPreparation() {
    for (var scene : SCENES.values()) scene.terrainComplete = false;
    for (var scene : IDLE_SCENES) scene.terrainComplete = false;
  }

  public static void invalidateTerrainPreparation(LevelRenderer renderer) {
    for (var scene : SCENES.values())
      if (scene.renderer == renderer) scene.terrainComplete = false;
    for (var scene : IDLE_SCENES)
      if (scene.renderer == renderer) scene.terrainComplete = false;
  }

  public static void invalidateTerrainPreparation(int x, int y, int z) {
    for (var scene : SCENES.values()) scene.terrainChanged(x, y, z);
    for (var scene : IDLE_SCENES) scene.terrainChanged(x, y, z);
  }

  public static void publishTerrainChanges() {
    var primary = Minecraft.getInstance().levelRenderer;
    var updates = terrainUpdates;
    var area = primary.viewArea();
    var sections = area == null ? null
        : (PortalRenderSections<?>) ((ViewAreaAccessor) area).portalix$sections();
    var releases = terrainReleases;
    CompiledSectionMesh mesh;
    while (releases != null && (mesh = releases.poll()) != null) {
      ((PortalViewIndicesAccess) primary).portalix$indices().release(mesh);
      for (var scene : SCENES.values())
        ((PortalViewIndicesAccess) scene.renderer).portalix$indices().release(mesh);
      for (var scene : IDLE_SCENES)
        ((PortalViewIndicesAccess) scene.renderer).portalix$indices().release(mesh);
    }
    SectionRenderDispatcher.RenderSection section;
    while (updates != null && (section = updates.poll()) != null) {
      if (sections == null || sections.getExisting(section.getSectionNode()) != section) continue;
      long node = section.getSectionNode();
      invalidateTerrainPreparation(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
      primary.sectionOcclusionGraph().schedulePropagationFrom(section);
      for (var scene : SCENES.values()) scene.rendering.terrainChanged(section);
      for (var scene : IDLE_SCENES) scene.rendering.terrainChanged(section);
    }
  }

  public static QuadParticleRenderState particleState(QuadParticleGroup group) {
    return activeScene.rendering.particleState(group);
  }

  public static boolean terrainOnly() {
    return activeScene != null && activeScene.visible == null;
  }

  public static boolean arrivalView() {
    return activeScene != null && activeScene.camera.arrival();
  }

  public static Frustum entityFrustum(Frustum ordinary) {
    return activeScene == null || IrisShaders.shadowPass()
        ? ordinary
        : activeScene.camera.renderFrustum();
  }

  public static Frustum renderFrustum() {
    return activeScene == null ? null : activeScene.camera.renderFrustum();
  }

  public static boolean isActiveView(LevelRenderer renderer) {
    for (var scene : SCENES.values()) if (scene.renderer == renderer) return true;
    return false;
  }

  public static double particleDistance(double distance, double x, double y, double z) {
    var position = new Vec3(x, y, z);
    for (PortalRenderScene scene : SCENES.values()) {
      if (PortalAperture.signedDistance(scene.link().exit().endpoint(), position)
          >= -PortalAperture.PLANE_EPSILON)
        distance = Math.min(distance, scene.camera.position().distanceToSqr(x, y, z));
    }
    return distance;
  }

  public static boolean hasRenderedView(PortalLink link) {
    return SCENES.values().stream()
        .anyMatch(
            scene ->
                scene.destinationReady
                    && (scene.link().equals(link) || scene.link().equals(link.inverse())));
  }

  public static boolean hidesCameraSource() {
    var source = new ArrayList<PortalLink>();
    if (activeScene != null && activeScene.camera.entity() != null)
      for (var change : PortalEntityScenes.frame(activeScene.camera.entity()))
        PortalRoutes.append(source, change);
    return (activeScene == null
            || activeScene.camera.entity() != null && cameraPath().equals(source))
        && Minecraft.getInstance().options.getCameraType().isFirstPerson();
  }

  public static List<PortalLink> cameraPath() {
    List<PortalLink> path = new ArrayList<>();
    var main = PortalCameraController.currentView();
    if (main != null) for (PortalLink link : main.spacePath()) PortalRoutes.append(path, link);
    if (activeScene != null) for (PortalLink link : activeScene.path) PortalRoutes.append(path, link);
    return path;
  }

  public record Scene(GpuTextureView texture, Vector4f uvTransform) {}

  public static Scene portalImage(PortalEndpoint entrance) {
    List<PortalLink> parent = activeScene == null ? List.of() : activeScene.path;
    for (PortalRenderScene scene : RENDER_ORDER) {
      if (scene.renderedThisFrame
          && scene.destinationReady
          && scene.path.size() == parent.size() + 1
          && scene.path.subList(0, parent.size()).equals(parent)
          && scene.link().entrance().endpoint().equals(entrance)) {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var parentViewport =
            activeScene == null
                ? new PortalSceneViewport(0, 0, main.width, main.height, main.width, main.height)
                : activeScene.viewport;
        return new Scene(scene.rendering.target().getColorTextureView(), scene.viewport.from(parentViewport));
      }
    }
    return null;
  }

  public static void extract(DeltaTracker delta, Camera camera, CameraRenderState root) {
    Minecraft client = Minecraft.getInstance();
    if (level != client.level) {
      clear();
      level = client.level;
      if (level != null) {
        terrainUpdates = new ConcurrentLinkedQueue<>();
        terrainReleases = new ConcurrentLinkedQueue<>();
        for (int view = 0; view < INITIAL_VIEW_CAPACITY; view++)
          PREPARED_SCENES.addLast(new PortalRenderScene(level));
      }
    }
    RENDER_ORDER.clear();
    primaryCameraState = root;
    primaryCamera = camera;
    if (level == null) return;
    for (var crossing : PortalCameraController.consumeCameraTransitions()) changeCameraSpace(crossing);
    var links = PortalTopology.links(level);
    publishTerrainChanges();
    if (PortalTerrainBackend.usesVanillaChunkEvents()) {
      var dispatcher = client.levelRenderer.sectionRenderDispatcher();
      ((PortalViewIndicesAccess) client.levelRenderer).portalix$indices().reclaim(dispatcher);
      for (var scene : SCENES.values())
        ((PortalViewIndicesAccess) scene.renderer).portalix$indices().reclaim(dispatcher);
      for (var scene : IDLE_SCENES)
        ((PortalViewIndicesAccess) scene.renderer).portalix$indices().reclaim(dispatcher);
    }
    var chunks = level.getChunkSource();
    PortalTerrainBackend.captureChanges(chunks, CHUNK_EVENTS);
    if (!CHUNK_EVENTS.removedLoadedChunks.isEmpty()) {
      PortalTerrainBackend.unloadColumns(client.levelRenderer, CHUNK_EVENTS.removedLoadedChunks);
      for (var scene : SCENES.values()) scene.unloaded(CHUNK_EVENTS.removedLoadedChunks);
      for (var scene : IDLE_SCENES) scene.unloaded(CHUNK_EVENTS.removedLoadedChunks);
    }
    PortalEntityScenes.beginFrame(level, delta);

    if (PortalOcclusionOpenings.update(level.dimension(), links)) {
      PortalTerrainBackend.invalidateOcclusion(client.levelRenderer);
      if (PortalTerrainBackend.usesVanillaChunkEvents()) {
        for (var scene : SCENES.values()) PortalTerrainBackend.invalidateOcclusion(scene.renderer);
        for (var scene : IDLE_SCENES) PortalTerrainBackend.invalidateOcclusion(scene.renderer);
      }
    }
    PLANNED_SCENES.clear();
    PLANNED_SCENES.addAll(PortalScenePlanner.plan(
        level.dimension(), root, root.projectionMatrix, root.viewRotationMatrix, links,
        client.options.getEffectiveRenderDistance() * 16.0, entrance -> true));
    Set<List<PortalLink>> used = new HashSet<>();
    for (var plan : PLANNED_SCENES) used.add(plan.path());
    var iterator = SCENES.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (!used.contains(entry.getKey())) {
        IDLE_SCENES.add(entry.getValue());
        iterator.remove();
      }
    }

    for (var scene : SCENES.values()) scene.consumeChunkEvents(CHUNK_EVENTS);
    for (var scene : IDLE_SCENES) scene.consumeChunkEvents(CHUNK_EVENTS);
    Set<PortalLink> liveLinks = new HashSet<>(links);
    for (var plan : PLANNED_SCENES.reversed()) {
      var scene = SCENES.get(plan.path());
      if (scene == null) continue;
      scene.visible = plan.bounds();
      scene.renderedThisFrame = false;
      scene.configure(
          plan.position(), plan.orientation(), root.projectionMatrix, root.depthFar, camera, null);
      scene.extracted = plan.bounds() != null || scene.needsTerrainPreparation();
    }
    // Reserve preparation time before the primary view consumes this frame's budget.
    var terrainPlans = new ArrayList<PortalScenePlanner.View>();
    for (var plan : PLANNED_SCENES)
      if (plan.bounds() != null) terrainPlans.add(plan);
    PLANNED_SCENES.stream()
        .filter(plan -> plan.bounds() == null && plan.path().size() == 1)
        .sorted(Comparator.comparingDouble(plan ->
            root.pos.distanceToSqr(plan.path().getFirst().entrance().endpoint().center())))
        .limit(IDLE_VIEW_CAPACITY)
        .forEach(terrainPlans::add);
    var positions = new ArrayList<Vec3>();
    var renderers = new ArrayList<LevelRenderer>();
    for (var plan : terrainPlans) {
      var scene = SCENES.get(plan.path());
      if (scene == null || scene.extracted) {
        positions.add(plan.position());
        // Primary occlusion is resolved later; completed hidden views must not reserve a turn.
        if (scene != null && (scene.needsTerrainPreparation()
            || scene.lastRenderedFrame == PortalFrameTerrainBudget.frame() - 1))
          renderers.add(scene.renderer);
      }
    }
    PortalFrameTerrainBudget.planViews(positions.size(), renderers);
    if (PortalTerrainBackend.usesVanillaChunkEvents()) {
      if (positions.isEmpty()) terrainWorkCameras = NO_TERRAIN_WORK_CAMERAS;
      else {
        positions.addFirst(root.pos);
        terrainWorkCameras = new TerrainWorkCameras(positions.toArray(Vec3[]::new));
      }
    }
    var idle = IDLE_SCENES.iterator();
    while (idle.hasNext()) {
      var scene = idle.next();
      if (!liveLinks.containsAll(scene.path)) {
        idle.remove();
        scene.close();
      }
    }
  }

  private static void changeCameraSpace(PortalLink crossing) {
    var scenes = new ArrayList<>(SCENES.values());
    scenes.addAll(IDLE_SCENES);
    PortalRenderScene destination = null;
    for (var scene : scenes)
      if (scene.path.equals(List.of(crossing))) {
        destination = scene;
        break;
      }
    if (destination != null && destination.lastSection != Long.MIN_VALUE) {
      var viewport = destination.viewport;
      boolean compatibleHistory =
          destination.lastRenderedFrame == PortalFrameTerrainBudget.frame() - 1
              && viewport != null
              && viewport.isFullScreen()
              && destination.camera.fullScreenRender()
              && destination.environmentProjection.equals(renderProjection);
      boolean exchangedImage = destination.rendering.exchangePrimary(compatibleHistory);
      destination.lastUsedFrame = PortalFrameTerrainBudget.frame();
      destination.rebase(List.of(crossing.inverse()));
      destination.destinationReady = false;
      if (exchangedImage) {
        var target = destination.rendering.target();
        destination.viewport =
            new PortalSceneViewport(0, 0, target.width, target.height, target.width, target.height);
        destination.lastRenderedFrame = PortalFrameTerrainBudget.frame() - 1;
      }
    } else destination = null;
    SCENES.clear();
    IDLE_SCENES.clear();
    for (var scene : scenes) {
      if (scene != destination) {
        var path = new ArrayList<PortalLink>();
        PortalRoutes.append(path, crossing.inverse());
        for (var link : scene.path) PortalRoutes.append(path, link);
        if (path.isEmpty() || path.size() > RECURSION_DEPTH) {
          scene.close();
          continue;
        }
        scene.rebase(path);
      }
      IDLE_SCENES.add(scene);
    }
  }

  public static void copyChunkEvents(ChunkLoadingRenderState target) {
    target.addedLoadedChunks = CHUNK_EVENTS.addedLoadedChunks;
    target.removedLoadedChunks = CHUNK_EVENTS.removedLoadedChunks;
    target.addedEmptySections = CHUNK_EVENTS.addedEmptySections;
    target.removedEmptySections = CHUNK_EVENTS.removedEmptySections;
  }

  public static void renderProjection(Matrix4fc projection) {
    renderProjection.set(projection);
  }

  public static void render(DeltaTracker delta, Matrix4fc modelView) {
    if (PLANNED_SCENES.isEmpty()) {
      trimIdleScenes();
      return;
    }
    Matrix4fc parentProjection = renderProjection;
    var viewEffects = new Matrix4f(modelView).rotate(primaryCameraState.orientation);
    var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    var links = PortalTopology.links(level);
    PLANNED_SCENES.clear();
    PLANNED_SCENES.addAll(PortalScenePlanner.plan(
        level.dimension(), primaryCameraState, parentProjection, modelView, links,
        Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0,
        entrance -> !PortalTerrainBackend.primaryOccludes(primaryCameraState, entrance)));
    Set<PortalLink> liveLinks = new HashSet<>(links);
    var hidden = new ArrayList<PortalScenePlanner.View>();
    for (var scene : SCENES.values()) scene.visible = null;
    for (var plan : PLANNED_SCENES.reversed()) {
      PortalRenderScene parent =
          plan.path().size() == 1 ? null : SCENES.get(plan.path().subList(0, plan.path().size() - 1));
      if (plan.path().size() > 1 && (parent == null || parent.visible == null)) continue;
      var bounds = plan.bounds();
      if (bounds == null && plan.path().size() == 1) hidden.add(plan);
      var scene = SCENES.get(plan.path());
      if (scene == null && bounds == null) continue;
      if (bounds != null) scene = acquireScene(plan.path(), liveLinks);
      scene.visible = bounds;
      if (bounds == null) scene.extracted = scene.needsTerrainPreparation();
      if (scene.visible != null) {
        scene.extracted = true;
        scene.viewport =
            PortalSceneViewport.of(scene.visible, main.width, main.height)
                .retainSize(scene.rendering.target().width, scene.rendering.target().height);
        scene.configure(
            parent == null ? primaryCameraState : parent.rendering.state().cameraRenderState,
            parentProjection,
            primaryCamera,
            viewEffects);
      }
    }
    hidden.sort(Comparator.comparingDouble(plan ->
        primaryCameraState.pos.distanceToSqr(plan.path().getFirst().entrance().endpoint().center())));
    var preparing = new HashSet<List<PortalLink>>();
    for (int index = 0; index < Math.min(IDLE_VIEW_CAPACITY, hidden.size()); index++) {
      var plan = hidden.get(index);
      preparing.add(plan.path());
      var scene = acquireScene(plan.path(), liveLinks);
      scene.visible = null;
      scene.configure(plan.position(), plan.orientation(), primaryCameraState.projectionMatrix,
          primaryCameraState.depthFar, primaryCamera, null);
      scene.extracted = scene.needsTerrainPreparation();
    }
    var inactive = SCENES.entrySet().iterator();
    while (inactive.hasNext()) {
      var entry = inactive.next();
      var scene = entry.getValue();
      if (scene.visible == null && !preparing.contains(entry.getKey())) {
        scene.renderedThisFrame = false;
        IDLE_SCENES.add(scene);
        inactive.remove();
      }
    }
    RENDER_ORDER.clear();
    for (var plan : PLANNED_SCENES) {
      var scene = SCENES.get(plan.path());
      if (scene != null) RENDER_ORDER.add(scene);
    }
    trimIdleScenes();
    updateTerrainWorkCameras();
    PLANNED_SCENES.clear();
    var preparingRenderers = RENDER_ORDER.stream().filter(scene -> scene.extracted)
        .map(scene -> scene.renderer).toList();
    PortalFrameTerrainBudget.planViews(preparingRenderers.size(), preparingRenderers);
    try {
      for (PortalRenderScene scene : RENDER_ORDER) {
        if (scene.visible == null) {
          if (scene.extracted) {
            scene.extract(delta);
            scene.maintain();
            scene.lastTick = level.getGameTime();
            scene.lastSection = SectionPos.asLong(scene.camera.blockPosition());
          }
          continue;
        }
        scene.extract(delta);
        scene.lastTick = level.getGameTime();
        scene.lastSection = SectionPos.asLong(scene.camera.blockPosition());
        scene.environmentProjection.set(scene.viewport.projection(parentProjection));
        var camera = scene.rendering.state().cameraRenderState;
        camera.projectionMatrix.set(scene.environmentProjection);
        scene.render(delta);
      }
    } finally {
      PortalTerrainBackend.restorePrimary();
    }
  }

  private static void updateTerrainWorkCameras() {
    if (PortalTerrainBackend.usesVanillaChunkEvents()) {
      int count = 1;
      for (var scene : RENDER_ORDER) if (scene.extracted) count++;
      if (count == 1) terrainWorkCameras = NO_TERRAIN_WORK_CAMERAS;
      else {
        var positions = new Vec3[count];
        positions[0] = primaryCameraState.pos;
        int index = 1;
        for (var scene : RENDER_ORDER)
          if (scene.extracted) positions[index++] = scene.camera.position();
        terrainWorkCameras = new TerrainWorkCameras(positions);
      }
    }
  }

  public static Matrix4f surfaceProjection() {
    return new Matrix4f(activeScene == null ? renderProjection : activeScene.environmentProjection);
  }

  private static PortalRenderScene acquireScene(List<PortalLink> path, Set<PortalLink> liveLinks) {
    PortalRenderScene existing = SCENES.get(path);
    if (existing != null) {
      existing.lastUsedFrame = PortalFrameTerrainBudget.frame();
      return existing;
    }
    PortalRenderScene reusable = null;
    double nearest = Double.POSITIVE_INFINITY;
    for (PortalRenderScene candidate : IDLE_SCENES) {
      if (candidate.path.equals(path)) {
        reusable = candidate;
        break;
      }
      // A live path keeps its view caches for the next trip through the pair.
      if (liveLinks.containsAll(candidate.path)) continue;
      double distance =
          candidate
              .link()
              .exit()
              .endpoint()
              .center()
              .distanceToSqr(path.getLast().exit().endpoint().center());
      if (distance < nearest) {
        nearest = distance;
        reusable = candidate;
      }
    }
    if (reusable == null) {
      reusable = PREPARED_SCENES.pollFirst();
      if (reusable == null) reusable = new PortalRenderScene(level);
    } else {
      IDLE_SCENES.remove(reusable);
      SCENES.remove(reusable.path, reusable);
    }
    reusable.bind(path);
    reusable.lastUsedFrame = PortalFrameTerrainBudget.frame();
    SCENES.put(reusable.path, reusable);
    return reusable;
  }

  private static void trimIdleScenes() {
    int available = IDLE_VIEW_CAPACITY;
    for (var scene : SCENES.values()) if (scene.visible == null) available--;
    if (IDLE_SCENES.size() <= available) return;
    IDLE_SCENES.sort(Comparator.comparingLong((PortalRenderScene scene) -> scene.lastUsedFrame).reversed());
    while (IDLE_SCENES.size() > available) IDLE_SCENES.removeLast().close();
  }

  public static void clear() {
    RenderSystem.assertOnRenderThread();
    level = null;
    terrainWorkCameras = NO_TERRAIN_WORK_CAMERAS;
    terrainUpdates = null;
    terrainReleases = null;
    IrisShaders.invalidatePrimaryHistory();
    if (PortalTerrainBackend.usesVanillaChunkEvents())
      ((PortalViewIndicesAccess) Minecraft.getInstance().levelRenderer).portalix$clearIndices();
    for (PortalRenderScene scene : SCENES.values()) scene.close();
    for (PortalRenderScene scene : IDLE_SCENES) scene.close();
    for (PortalRenderScene scene : PREPARED_SCENES) scene.close();
    PREPARED_SCENES.clear();
    IDLE_SCENES.clear();
    SCENES.clear();
    RENDER_ORDER.clear();
    PLANNED_SCENES.clear();
    PortalOcclusionOpenings.update(null, List.of());
    primaryCameraState = null;
    primaryCamera = null;
    PortalMousePicking.select(null);
    PortalEntityScenes.clear();
    PortalSurfaceRenderer.clearResources();
  }

  static final class SceneScope implements AutoCloseable {
    private final PortalRenderScene previous;
    private final MinecraftSceneRenderer.Binding binding;

    SceneScope(PortalRenderScene scene) {
      previous = activeScene;
      binding = scene.rendering.bind();
      activeScene = scene;
    }

    @Override
    public void close() {
      try {
        binding.close();
      } finally {
        activeScene = previous;
      }
    }
  }
}
