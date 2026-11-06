package dev.portalix.client.portal.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.entity.EntityRendererAccessor;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalItemRotation;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalProjectileRotation;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.DisplayEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PortalEntityScenes {
  private static final Map<Entity, List<PortalEntitySlice>> SLICES_BY_ENTITY = new IdentityHashMap<>();
  private static final Set<Entity> VIEW_ENTITIES = new HashSet<>();
  private static volatile Set<Entity> visibleEntities = Set.of();

  private PortalEntityScenes() {}

  public static void clear() {
    SLICES_BY_ENTITY.clear();
    VIEW_ENTITIES.clear();
    visibleEntities = Set.of();
    PortalEntityClipping.clear();
    PortalEntityModelBounds.reload();
  }

  public static List<PortalLink> frame(Entity entity) {
    var slices = SLICES_BY_ENTITY.get(entity);
    return slices == null ? List.of() : slices.getFirst().frame();
  }

  public static void beginFrame(ClientLevel level, DeltaTracker delta) {
    SLICES_BY_ENTITY.clear();
    VIEW_ENTITIES.clear();
    PortalEntityClipping.clear();
    var links = PortalTopology.links(level);
    var client = Minecraft.getInstance();
    var dispatcher = client.levelRenderer.entityRenderDispatcher();
    var camera = client.getCameraEntity();
    boolean cameraIncluded = false;
    for (var entity : level.entitiesForRendering()) {
      collectFrame(entity, level, delta, dispatcher, links);
      cameraIncluded |= entity == camera;
    }
    if (!cameraIncluded && camera != null && camera.level() == level)
      collectFrame(camera, level, delta, dispatcher, links);
  }

  private static void collectFrame(
      Entity entity,
      ClientLevel level,
      DeltaTracker delta,
      EntityRenderDispatcher dispatcher,
      List<PortalLink> links) {
    var motion = EntityPortalState.of(entity);
    if (links.isEmpty() && !motion.cameraHistory().hasRenderPosition(entity.tickCount)) return;
    float partial =
        delta.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
    var position = entity.position();
    var rendered = PortalEyePose.renderedFeet(entity, partial);
    double dx = rendered.x - position.x;
    double dy = rendered.y - position.y;
    double dz = rendered.z - position.z;
    var bounds =
        ((EntityRendererAccessor) dispatcher.getRenderer(entity)).portalix$renderBounds(entity);
    var body = entity.getBoundingBox();
    var carried = PortalixClient.carryController().renderCenter(entity, partial);
    if (carried != null) {
      var shift = carried.subtract(body.getCenter());
      dx = shift.x;
      dy = shift.y;
      dz = shift.z;
    }
    double padding = .5 + PortalEntityModelBounds.itemPadding(entity);
    var history =
        carried == null
            ? motion.cameraHistory().rewind(entity.tickCount - 1 + partial)
            : List.<PortalLink>of();
    if (history.isEmpty() && !nearPortal(links, body, bounds, dx, dy, dz, padding + .5)) return;
    bounds =
        bounds.minmax(PortalPassagePose.interactionBounds(entity)).inflate(padding).move(dx, dy, dz);
    var interpolatedBounds = bounds;
    body = body.move(dx, dy, dz);
    var interpolatedAnchor = body.getCenter();
    var last = motion.lastCrossing();
    PortalLink preferred =
        !history.isEmpty()
            ? history.getLast().inverse()
            : last == null ? null : last.link().inverse();
    for (var link : history) {
      bounds = link.transform().bounds(bounds);
      body = link.transform().bounds(body);
    }
    var anchor = body.getCenter();
    links =
        links.stream()
            .filter(link -> PortalAperture.signedDistance(link.entrance().endpoint(), anchor) >= 0)
            .toList();
    var selected = PortalAperture.selectIntersectingPortal(links, body, anchor, preferred);
    if (selected == null)
      selected = PortalAperture.selectIntersectingPortal(links, bounds, anchor, preferred);
    if (selected != null || !history.isEmpty())
      SLICES_BY_ENTITY.put(
          entity,
          PortalEntitySlice.split(history, selected, interpolatedBounds, interpolatedAnchor));
  }

  static boolean nearPortal(
      List<PortalLink> links, AABB body, AABB bounds, double dx, double dy, double dz) {
    return nearPortal(links, body, bounds, dx, dy, dz, .5);
  }

  static boolean nearPortal(
      List<PortalLink> links,
      AABB body,
      AABB bounds,
      double dx,
      double dy,
      double dz,
      double padding) {
    double minX = Math.min(body.minX, bounds.minX) - padding + dx;
    double minY = Math.min(body.minY, bounds.minY) - padding + dy;
    double minZ = Math.min(body.minZ, bounds.minZ) - padding + dz;
    double maxX = Math.max(body.maxX, bounds.maxX) + padding + dx;
    double maxY = Math.max(body.maxY, bounds.maxY) + padding + dy;
    double maxZ = Math.max(body.maxZ, bounds.maxZ) + padding + dz;
    for (int i = 0; i < links.size(); i++) {
      var portal = links.get(i).entrance().endpoint();
      var center = portal.center();
      double extent = (portal.width() + portal.height()) / 2 + PortalAperture.PLANE_EPSILON;
      if (maxX >= center.x - extent
          && minX <= center.x + extent
          && maxY >= center.y - extent
          && minY <= center.y + extent
          && maxZ >= center.z - extent
          && minZ <= center.z + extent) return true;
    }
    return false;
  }

  public static void onEntityExtracted(Entity entity, EntityRenderState state, float partial) {
    var history = EntityPortalState.of(entity).cameraHistory();
    if (history.hasRenderPosition(entity.tickCount)
        && !PortalixClient.carryController().predicted(entity)) {
      var position = PortalEyePose.renderedFeet(entity, partial);
      state.x = position.x;
      state.y = position.y;
      state.z = position.z;
    }
    if (PortalSceneManager.isPortalView() || isManaged(entity)) VIEW_ENTITIES.add(entity);
    var presentation = (PortalEntityRenderStateAccess) state;
    var modelRotation =
        entity instanceof ItemEntity item
            ? PortalItemRotation.rotation(item)
            : PortalProjectileRotation.modelRotation(entity, partial);
    if (modelRotation != null && modelRotation.equals(new Quaternionf(), 1e-6f))
      modelRotation = null;
    presentation.portalix$modelRotation(modelRotation);
    presentation.portalix$carryRotation(
        PortalixClient.carryController().renderRotation(entity, partial));
    if (PortalPassagePose.compact(entity)) {
      var dimensions = PortalPassagePose.interactionDimensions(entity);
      state.boundingBoxWidth = dimensions.width();
      state.boundingBoxHeight = dimensions.height();
      state.eyeHeight = dimensions.eyeHeight();
    }
    if (!isManaged(entity)
        && presentation.portalix$carryRotation() == null
        && !isFirstPersonEntity(entity)) {
      presentation.portalix$slices(null);
      return;
    }
    var access =
        (EntityRendererAccessor)
            Minecraft.getInstance().levelRenderer.entityRenderDispatcher().getRenderer(entity);
    var probe =
        entity.getLightProbePosition(1).subtract(entity.position()).add(state.x, state.y, state.z);
    var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
    boolean projectShadow = !IrisShaders.shadowPass() && IrisShaders.vanillaEntityShadows();
    presentation.portalix$slices(
        slices(entity).stream()
            .map(
                slice -> {
                  if (projectShadow && (!slice.frame().isEmpty() || slice.portal() != null))
                    slice =
                        slice.withShadow(
                            PortalEntityShadows.extract(
                                access,
                                state,
                                entity.level(),
                                slice.position(new Vec3(state.x, state.y, state.z)),
                                camera));
                  if (slice.portal() == null) return slice;
                  var point = slice.position(probe);
                  double distance = PortalAperture.signedDistance(slice.portal(), point);

                  point = point.add(slice.portal().normal().scale(Math.max(0, .001 - distance)));
                  var position = BlockPos.containing(point);
                  return slice.withLight(
                      LightCoordsUtil.pack(
                          access.portalix$blockLight(entity, position),
                          access.portalix$skyLight(entity, position)));
                })
            .toList());
  }

  public static boolean isManaged(Entity entity) {
    return SLICES_BY_ENTITY.containsKey(entity);
  }

  public static boolean visibleThroughPortal(Entity entity) {
    return visibleEntities.contains(entity);
  }

  public static void endFrame() {
    visibleEntities = Set.copyOf(VIEW_ENTITIES);
  }

  private static List<PortalEntitySlice> slices(Entity entity) {
    if (IrisShaders.shadowPass()) {
      var slices = SLICES_BY_ENTITY.get(entity);
      if (slices != null) return slices;
    }
    boolean firstPerson = isFirstPersonEntity(entity) && !IrisShaders.shadowPass();
    var cameraFrame = PortalSceneManager.cameraPath();
    var root = PortalCameraController.currentView();
    var rootFrame = root == null ? List.<PortalLink>of() : root.spacePath();
    var camera = PortalSceneManager.cameraState();
    var cameraPosition =
        camera == null ? Minecraft.getInstance().gameRenderer.mainCamera().position() : camera.pos;
    var slices = SLICES_BY_ENTITY.get(entity);
    if (slices == null)
      slices =
          List.of(
              new PortalEntitySlice(
                  List.of(),
                  null,
                  entity.getBoundingBox(),
                  false,
                  entity.getBoundingBox().getCenter(),
                  -1));
    return slices.stream()
        .filter(
            slice ->
                !firstPerson || !slice.bodyBounds(entity.getBoundingBox()).contains(cameraPosition))
        .filter(
            slice ->
                slice.visibleTo(
                    firstPerson, rootFrame, cameraFrame, PortalSceneManager.path(), cameraPosition))
        .toList();
  }

  private static boolean isFirstPersonEntity(Entity entity) {
    var client = Minecraft.getInstance();
    return entity == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
  }

  public static void extractParts(Frustum frustum, DeltaTracker delta, LevelRenderState output) {
    var client = Minecraft.getInstance();
    var dispatcher = client.levelRenderer.entityRenderDispatcher();
    for (var entry : SLICES_BY_ENTITY.entrySet()) {
      var entity = entry.getKey();
      if (IrisShaders.shadowPass() && entity.isSpectator()) continue;
      if (slices(entity).stream().noneMatch(slice -> frustum.isVisible(slice.bounds()))) continue;
      float partial =
          delta.getGameTimeDeltaPartialTick(!client.level.tickRateManager().isEntityFrozen(entity));
      var state = dispatcher.extractEntity(entity, partial);
      var presentation = (PortalEntityRenderStateAccess) state;
      presentation.portalix$slices(
          presentation.portalix$slices().stream()
              .filter(slice -> frustum.isVisible(slice.bounds()))
              .toList());
      output.entityRenderStates.add(state);
    }
    output.lastEntityRenderStateCount = output.entityRenderStates.size();
  }

  public static void submit(
      EntityRenderState state,
      CameraRenderState camera,
      PoseStack poses,
      SubmitNodeCollector collector,
      Consumer<CameraRenderState> submit) {
    for (var slice : ((PortalEntityRenderStateAccess) state).portalix$slices()) {
      var position = slice.position(new Vec3(state.x, state.y, state.z)).subtract(camera.pos);
      var rotation = slice.rotation();
      var carriedRotation = ((PortalEntityRenderStateAccess) state).portalix$carryRotation();
      if (carriedRotation != null) rotation.mul(carriedRotation);
      var display = state instanceof DisplayEntityRenderState value ? value : null;
      float displayX = display == null ? 0 : display.cameraXRot;
      float displayY = display == null ? 0 : display.cameraYRot;
      int originalLight = state.lightCoords;
      var originalShadow = slice.shadow() == null ? null : List.copyOf(state.shadowPieces);
      poses.pushPose();
      try {
        poses.translate(position.x, position.y, position.z);
        poses.mulPose(rotation);

        var localCamera = camera;
        if (!slice.frame().isEmpty() || carriedRotation != null) {
          localCamera = new CameraRenderState();
          localCamera.orientation.set(rotation).conjugate().mul(camera.orientation);
          var localPosition = camera.pos;
          for (var link : slice.frame().reversed())
            localPosition = link.transform().inverse().point(localPosition);
          if (carriedRotation != null) {
            var anchor = new Vec3(state.x, state.y, state.z);
            var relative =
                carriedRotation
                    .conjugate(new Quaternionf())
                    .transform(localPosition.subtract(anchor).toVector3f());
            localPosition = anchor.add(relative.x, relative.y, relative.z);
          }
          localCamera.pos = localPosition;
          var forward = localCamera.orientation.transform(new Vector3f(0, 0, -1));
          localCamera.xRot =
              (float) Math.toDegrees(Math.atan2(-forward.y, Math.hypot(forward.x, forward.z)));
          localCamera.yRot = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        }

        if (display != null) {
          display.cameraXRot = localCamera.xRot;
          display.cameraYRot = localCamera.yRot;
        }
        PortalEntityClipping.begin(slice);
        if (slice.light() >= 0) state.lightCoords = slice.light();
        if (slice.shadow() != null) state.shadowPieces.clear();
        submit.accept(localCamera);
      } finally {
        state.lightCoords = originalLight;
        if (originalShadow != null) {
          state.shadowPieces.clear();
          state.shadowPieces.addAll(originalShadow);
        }
        if (display != null) {
          display.cameraXRot = displayX;
          display.cameraYRot = displayY;
        }
        PortalEntityClipping.end();
        poses.popPose();
      }
      try {
        if (slice.shadow() != null && !slice.shadow().pieces().isEmpty()) {
          PortalEntityClipping.beginShadow(slice);
          poses.pushPose();
          try {
            poses.translate(position.x, position.y, position.z);
            collector.submitShadow(poses, slice.shadow().radius(), slice.shadow().pieces());
          } finally {
            poses.popPose();
          }
        }
      } finally {
        PortalEntityClipping.end();
      }
    }
  }
}
