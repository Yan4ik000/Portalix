package dev.portalix.client.portal.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.compat.minecraft.MinecraftRenderAccess;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.camera.PortalCameraCollision;
import dev.portalix.portal.camera.PortalCameraCorrections;
import dev.portalix.portal.camera.PortalCameraDistance;
import dev.portalix.portal.camera.PortalCameraOrbit;
import dev.portalix.portal.camera.PortalCameraReorientation;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.camera.PortalThirdPersonCamera;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.scene.PortalViewHorizon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PortalCameraController {
  private static final double MIN_CAMERA_CLEARANCE = 1e-4;
  private static final PortalCameraReorientation ORIENTATION = new PortalCameraReorientation();
  private static final PortalCameraOrbit ORBIT = new PortalCameraOrbit();
  private static final CameraRenderState BOB_STATE = new CameraRenderState();
  private static final PoseStack BOB_POSE = new PoseStack();
  private static Supplier<List<PortalLink>> links = List::of;
  private static boolean tracking;
  private static LocalPlayer trackedPlayer;
  private static float appliedYaw;
  private static float appliedPitch;
  private static long lastCrossing = Long.MIN_VALUE;
  private static boolean mirrored;
  private static float frameSeconds;
  private static View view;
  private static List<PortalLink> waitingPath = List.of();
  private static int requestedBoom;
  private static final List<PortalLink> pendingBodyCrossings = new ArrayList<>();
  private static List<PortalLink> previousEyePath = List.of();
  private static Entity cameraSpaceOwner;

  private PortalCameraController() {}

  public static void setPortalSupplier(Supplier<List<PortalLink>> supplier) {
    links = supplier;
  }

  public static void reset() {
    PortalViewHorizon.reset();
    resetPose();
    pendingBodyCrossings.clear();
    previousEyePath = List.of();
    cameraSpaceOwner = null;
  }

  public static void resetPose() {
    PortalSceneManager.invalidateViewDemand();
    var player = Minecraft.getInstance().player;
    if (player != null) EntityPortalState.of(player).resetCamera();
    clearTracking();
  }

  public static void correctSceneRoute(List<PortalLink> route) {
    if (route.isEmpty()) return;
    PortalViewHorizon.reset();
    if (cameraSpaceOwner != Minecraft.getInstance().player) return;
    for (var link : route) PortalRoutes.append(pendingBodyCrossings, link);
  }

  private static void clearTracking() {
    tracking = false;
    trackedPlayer = null;
    lastCrossing = Long.MIN_VALUE;
    ORBIT.clear();
    view = null;
    waitingPath = List.of();
    requestedBoom = 0;
  }

  public static void bodyCrossed(
      long eventId, PortalLink link, float previousYaw, float previousPitch) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;
    var camera = MinecraftRenderAccess.camera(Minecraft.getInstance());
    trackCameraSpaceOwner(camera.entity());
    if (trackedPlayer != null && trackedPlayer != player) clearTracking();
    if (eventId <= lastCrossing) return;
    PortalViewHorizon.bodyCrossed(link, player.level().getGameTime());
    if (camera.entity() == player) {
      var history = EntityPortalState.of(player).cameraHistory();
      float partial = (float) (history.lastCrossingTime() - (player.tickCount - 1));
      float offset = ((CameraOrientationAccess) camera).portalix$takeEyeOffset(partial);
      history.shifted(history.lastCrossingTime(), link.transform().vector(new Vec3(0, offset, 0)));
    }
    var transform = link.transform();
    rebaseHands(
        player, Mth.wrapDegrees(player.getYRot() - previousYaw), player.getXRot() - previousPitch);
    if (ORBIT.active()) {
      if (Mth.wrapDegrees(previousYaw - appliedYaw) != 0 || previousPitch != appliedPitch)
        ORBIT.lookBody(previousYaw, previousPitch);
      ORBIT.bodyCrossed(link);
    } else {
      if (!tracking) {
        ORIENTATION.resetView(previousYaw, previousPitch);
        tracking = true;
        trackedPlayer = player;
      } else if (Mth.wrapDegrees(previousYaw - appliedYaw) != 0 || previousPitch != appliedPitch) {
        ORIENTATION.look(previousYaw, previousPitch);
      }
      ORIENTATION.teleport(
          new Matrix3f(
              transform.vector(new Vec3(1, 0, 0)).toVector3f(),
              transform.vector(new Vec3(0, 1, 0)).toVector3f(),
              transform.vector(new Vec3(0, 0, 1)).toVector3f()));
    }
    synchronizeAngles(player);
    lastCrossing = eventId;
    if (camera.entity() == player) {
      PortalRoutes.append(pendingBodyCrossings, link);
      PortalSceneManager.invalidateViewDemand();
    }
  }

  public static PortalLookAngles mouse(LocalPlayer player, double horizontal, double vertical) {
    if ((!tracking && !ORBIT.active()) || trackedPlayer != player) return null;
    if (MinecraftRenderAccess.camera(Minecraft.getInstance()).entity() != player) return null;
    followExternalLook(player);
    if (ORBIT.active()) {
      ORBIT.mouse(horizontal * .15, vertical * .15);
      return ORBIT.bodyAngles();
    }
    ORIENTATION.mouse(horizontal * .15, vertical * .15);
    return ORIENTATION.angles();
  }

  public static void turned(LocalPlayer player, float expectedYaw, float expectedPitch) {
    // Respect passenger turn limits without feeding Entity.turn's float rounding back into the view.
    if (player.getYRot() != expectedYaw || player.getXRot() != expectedPitch) {
      if (ORBIT.active()) ORBIT.lookBody(player.getYRot(), player.getXRot());
      else ORIENTATION.look(player.getYRot(), player.getXRot());
    }
    synchronizeAngles(player);
  }

  public static void prepare(Camera camera, float seconds) {
    Minecraft minecraft = Minecraft.getInstance();
    waitingPath = List.of();
    requestedBoom = 0;
    trackCameraSpaceOwner(camera.entity());
    if (trackedPlayer != null && trackedPlayer != minecraft.player) clearTracking();
    if (camera.entity() != minecraft.player || minecraft.player == null) {
      clearTracking();
      return;
    }
    var player = minecraft.player;
    if (player.isSleeping()) {
      ORBIT.clear();
      tracking = false;
      view = null;
      return;
    }
    if (tracking || ORBIT.active()) followExternalLook(player);
    var cameraType = minecraft.options.getCameraType();
    if (!cameraType.isFirstPerson()) {
      frameSeconds = seconds;
      if (ORBIT.active() || tracking) {
        prepareOrbit(player);
        ORBIT.advance(seconds);
        synchronizeAngles(player);
      }
      return;
    }
    if (ORBIT.active()) {
      ORIENTATION.resetView(ORBIT.bodyOrientation());
      ORBIT.clear();
      tracking = ORIENTATION.active();
      synchronizeAngles(player);
    }
    if (!tracking) return;
    ORIENTATION.update(seconds);
    synchronizeAngles(player);
    if (!ORIENTATION.active()) tracking = false;
  }

  private static void followExternalLook(LocalPlayer player) {
    if (Mth.wrapDegrees(player.getYRot() - appliedYaw) != 0 || player.getXRot() != appliedPitch) {
      if (ORBIT.active()) ORBIT.lookBody(player.getYRot(), player.getXRot());
      else ORIENTATION.look(player.getYRot(), player.getXRot());
    }
  }

  public static void update(Camera camera, float partialTick) {
    Minecraft minecraft = Minecraft.getInstance();
    if (camera.entity() != minecraft.player) {
      if (camera == MinecraftRenderAccess.camera(minecraft)) view = null;
      return;
    }
    if (camera.isDetached()) return;
    Quaternionf rotation = cameraRotation(camera);
    Vec3 position = camera.position().add(bobOffset(partialTick, rotation));
    apply(camera, project(minecraft.player, partialTick, position, rotation));
  }

  public static View currentView() {
    return view;
  }

  public static List<PortalLink> waitingPath() {
    return waitingPath;
  }

  public static int requestedBoom() {
    return requestedBoom;
  }

  /** Consumed once after resolving the main camera, before extracting its render view. */
  public static List<PortalLink> consumeCameraTransitions() {
    var minecraft = Minecraft.getInstance();
    trackCameraSpaceOwner(MinecraftRenderAccess.camera(minecraft).entity());
    var path =
        minecraft.player != null && cameraSpaceOwner == minecraft.player && view != null
            ? view.spacePath()
            : List.<PortalLink>of();
    var transitions = new ArrayList<PortalLink>();
    for (var link : previousEyePath.reversed()) PortalRoutes.append(transitions, link.inverse());
    for (var link : pendingBodyCrossings) PortalRoutes.append(transitions, link);
    for (var link : path) PortalRoutes.append(transitions, link);
    pendingBodyCrossings.clear();
    previousEyePath = path;
    if (transitions.isEmpty()) return List.of();
    return List.copyOf(transitions);
  }

  private static void trackCameraSpaceOwner(Entity owner) {
    if (cameraSpaceOwner == owner) return;
    pendingBodyCrossings.clear();
    previousEyePath = List.of();
    cameraSpaceOwner = owner;
  }

  private static void prepareOrbit(LocalPlayer player) {
    boolean front = Minecraft.getInstance().options.getCameraType().isMirrored();
    if (ORBIT.active() && mirrored == front) return;
    var rotation = ORBIT.active() ? ORBIT.bodyOrientation()
        : tracking ? ORIENTATION.orientation()
        : PortalLookAngles.rotation(player.getYRot(), player.getXRot());
    ORBIT.initialize(rotation, null, List.of());
    tracking = false;
    mirrored = front;
    trackedPlayer = player;
    appliedYaw = player.getYRot();
    appliedPitch = player.getXRot();
  }

  public static @Nullable Boom portalBoom(Camera camera, float partialTick, float distance) {
    var player = (LocalPlayer) camera.entity();
    if (!Float.isFinite(distance)) return null;
    requestedBoom = Mth.ceil(Math.min(Math.abs(distance), PortalCameraDistance.MAX_MODDED_LOADING_DISTANCE));
    if (!PortalEntityMovement.vehicleAllowed(player)) return null;
    var history = EntityPortalState.of(player).cameraHistory();
    double time = player.tickCount - 1 + partialTick;
    boolean transitioning = ORBIT.transitioning() || history.hasPosition(player.tickCount)
        || history.eyeCorrection(time).lengthSqr() >= 1e-10;
    if (!transitioning && links.get().isEmpty()) return null;

    var target = new PortalThirdPersonCamera.Target(
        PortalEyePose.interpolatedCenter(player, partialTick), camera.position());
    var rotation = cameraRotation(camera);
    var bob = bobOffset(partialTick, rotation);
    if (!transitioning) {
      Vec3 focus = target.eye().add(bob);
      Vec3 end = focus.add(vector(rotation.transform(new Vector3f(0, 0, distance))));
      var pivotVolume = new AABB(target.center(), focus).inflate(PortalCameraCollision.RADIUS);
      var boomVolume = new AABB(focus, end).inflate(PortalCameraCollision.RADIUS);
      boolean touchesPortal = false;
      for (var link : PortalTopology.intersecting(player.level(), pivotVolume.minmax(boomVolume))) {
        var portal = link.entrance().endpoint();
        if (PortalCameraCollision.touchesOpening(portal, target.center(), pivotVolume)
            || PortalCameraCollision.touchesOpening(portal, focus, boomVolume)) {
          touchesPortal = true;
          break;
        }
      }
      if (!touchesPortal) return null;
    }
    return new Boom(target, rotation, bob, history, time, distance);
  }

  public static void nativeThirdPerson() {
    ORBIT.clear();
    view = null;
  }

  public static void thirdPerson(Camera camera, Boom boom) {
    var player = (LocalPlayer) camera.entity();
    prepareOrbit(player);
    var candidates = links.get();
    var ready = new HashSet<PortalLink>();
    for (var link : candidates) if (PortalEntityMovement.canTraverse(player, link)) ready.add(link);
    var pose =
        ORBIT.resolve(
            boom.target(),
            boom.rotation(),
            boom.bob(),
            boom.history(),
            boom.time(),
            boom.distance(),
            candidates,
            ready::contains,
            (body, movement, opening) -> PortalCameraWorld.query(player, body, movement, opening, ready.contains(opening)),
            frameSeconds);
    waitingPath = pose.waitingPath();
    synchronizeAngles(player);
    apply(
        camera,
        new View(
            pose.position(),
            pose.rotation(),
            vector(pose.rotation().transform(new Vector3f(0, 0, -1))),
            pose.path().isEmpty() ? null : pose.path().getLast(),
            pose.path()));
  }

  private static void apply(Camera camera, View resolved) {
    view = resolved;
    ((CameraOrientationAccess) camera).portalix$setPose(resolved.position(), resolved.rotation());
  }

  private static Vec3 bobOffset(float partialTick, Quaternionf rotation) {
    var minecraft = Minecraft.getInstance();
    if (!minecraft.options.bobView().get()) return Vec3.ZERO;
    var bob = BOB_STATE;
    bob.entityRenderState.isPlayer = true;
    bob.entityRenderState.backwardsInterpolatedWalkDistance =
        minecraft.player.avatarState().getBackwardsInterpolatedWalkDistance(partialTick);
    bob.entityRenderState.bob = minecraft.player.avatarState().getInterpolatedBob(partialTick);
    BOB_POSE.setIdentity();
    ((GameRendererAccessor) minecraft.gameRenderer).portalix$bobView(bob, BOB_POSE);
    var offset = BOB_POSE.last().pose().invert().getTranslation(new Vector3f());
    return vector(rotation.transform(offset));
  }

  private static Quaternionf cameraRotation(Camera camera) {
    if (!tracking && !ORBIT.active()) return new Quaternionf(camera.rotation());
    var rotation = ORBIT.active() ? ORBIT.bodyOrientation() : ORIENTATION.orientation();
    var entity = camera.entity();
    var nativeRotation = PortalLookAngles.rotation(entity.getYRot(), entity.getXRot());
    if (camera.isDetached() && Minecraft.getInstance().options.getCameraType().isMirrored()) {
      rotation.rotateY((float) Math.PI);
      nativeRotation.rotateY((float) Math.PI);
    }
    return rotation.mul(nativeRotation.conjugate().mul(camera.rotation()));
  }

  public static View raycast(float partialTick) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return null;
    Vec3 body = PortalEyePose.interpolatedFeet(player, partialTick);
    Vec3 position = body.add(0, player.getEyeHeight(), 0);
    var rotation =
        PortalLookAngles.rotation(player.getViewYRot(partialTick), player.getViewXRot(partialTick));
    if (ORBIT.active() || tracking) {
      var command = PortalLookAngles.rotation(player.getYRot(), player.getXRot());
      var corrected = ORBIT.active() ? ORBIT.bodyOrientation() : ORIENTATION.orientation();
      rotation = corrected.mul(command.conjugate()).mul(rotation);
    }
    return project(player, partialTick, position, rotation);
  }

  private static View project(
      LocalPlayer player, float partialTick, Vec3 position, Quaternionf rotation) {
    double time = player.tickCount - 1 + partialTick;
    var state = EntityPortalState.of(player);
    position = position.add(state.eyeCorrection(time));
    Vec3 center = PortalEyePose.interpolatedCenter(player, partialTick);
    var path = new ArrayList<PortalLink>();
    for (var link : state.cameraHistory().rewind(time)) {
      if (!PortalViewLimits.canCross(path, link)) break;
      position = link.transform().point(position);
      center = link.transform().point(center);
      rotation.premul(link.transform().rotation());
      PortalRoutes.append(path, link);
    }
    var eyePath = PortalEyePose.eyePath(player, center, position);
    for (var eyeLink : eyePath) {
      if (!PortalViewLimits.canCross(path, eyeLink)) {
        var portal = eyeLink.entrance().endpoint();
        var ray = position.subtract(center);
        double t = PortalAperture.signedDistance(portal, center) / -ray.dot(portal.normal());
        position = center.add(ray.scale(t)).add(portal.normal().scale(MIN_CAMERA_CLEARANCE));
        break;
      }
      center = eyeLink.transform().point(center);
      position = eyeLink.transform().point(position);
      rotation.premul(eyeLink.transform().rotation());
      PortalRoutes.append(path, eyeLink);
    }
    return new View(
        clearance(player, position),
        new Quaternionf(rotation),
        vector(rotation.transform(new Vector3f(0, 0, -1))),
        path.isEmpty() ? null : path.getLast(),
        List.copyOf(path));
  }

  private static Vec3 clearance(LocalPlayer player, Vec3 position) {
    for (var link : links.get()) {
      var portal = link.entrance().endpoint();
      Vec3 relative = position.subtract(portal.center());
      double distance = relative.dot(portal.normal());
      if (distance >= -PortalAperture.PLANE_EPSILON
          && distance < MIN_CAMERA_CLEARANCE
          && PortalAperture.containsPassage(portal, position)
          && PortalEntityMovement.canTraverse(player, link)) {
        position = position.add(portal.normal().scale(MIN_CAMERA_CLEARANCE - distance));
      }
    }
    return position;
  }

  public static boolean active() {
    if (tracking || ORBIT.reorienting() || view != null && view.eyeLink() != null) return true;
    var player = Minecraft.getInstance().player;
    return player != null
        && EntityPortalState.of(player).eyeCorrection(player.tickCount).lengthSqr() >= 1e-10;
  }

  private static void synchronizeAngles(LocalPlayer player) {
    var angles = ORBIT.active() ? ORBIT.bodyAngles() : ORIENTATION.angles();
    float yaw = angles.yaw();
    float pitch = angles.pitch();
    float previousYaw = Mth.wrapDegrees(player.getYRot());
    float yawDelta = Mth.wrapDegrees(yaw - previousYaw);
    float pitchDelta = pitch - player.getXRot();
    player.yBodyRot = Mth.wrapDegrees(player.yBodyRot);
    player.yBodyRotO = player.yBodyRot + Mth.wrapDegrees(player.yBodyRotO - player.yBodyRot);
    rebaseHands(player, yawDelta, pitchDelta);
    yaw = Mth.wrapDegrees(yaw);
    player.yBob = yaw + Mth.wrapDegrees(player.yBob - yaw);
    player.yBobO = yaw + Mth.wrapDegrees(player.yBobO - yaw);
    float head = player.getYHeadRot() + yawDelta;
    player.yHeadRotO = head + Mth.wrapDegrees(player.yHeadRotO + yawDelta - head);
    player.setYHeadRot(head);
    player.yRotO = yaw + Mth.wrapDegrees(player.yRotO - previousYaw);
    player.xRotO += pitchDelta;
    player.setYRot(yaw);
    player.setXRot(pitch);
    appliedYaw = yaw;
    appliedPitch = pitch;
  }

  private static void rebaseHands(LocalPlayer player, float yaw, float pitch) {
    player.yBob += yaw;
    player.yBobO += yaw;
    player.xBob += pitch;
    player.xBobO += pitch;
  }

  public static float nearPlane(
      Camera camera, float ordinary, float far, float fov, float aspect) {
    var player = Minecraft.getInstance().player;
    if (view == null || player == null || camera.entity() != player) return ordinary;
    double tangent = Math.tan(Math.toRadians(fov) / 2);
    // Fit the current frame's near plane inside the volume checked for camera collisions.
    float near =
        camera.isDetached()
            ? Math.min(
                ordinary,
                (float)
                    (PortalCameraCollision.RADIUS
                        / Math.sqrt(1 + tangent * tangent * (1 + aspect * aspect))))
            : ordinary;
    Vec3 position = camera.position();
    double reach = PortalAperture.MAX_APERTURE_EXTENT
        + 4 * ordinary * (1 + Math.abs(tangent) * (1 + Math.abs(aspect)));
    for (var link : PortalTopology.entrances(
        player.level(), position.x - reach, position.z - reach, position.x + reach, position.z + reach)) {
      var portal = link.entrance().endpoint();
      Vec3 relative = position.subtract(portal.center());
      if (Math.abs(relative.dot(portal.right()))
              <= portal.width() / 2
                  + ordinary * projectionExtent(camera, portal.right(), tangent, aspect)
          && Math.abs(relative.dot(portal.up()))
              <= portal.height() / 2
                  + ordinary * projectionExtent(camera, portal.up(), tangent, aspect)) {
        double extent = projectionExtent(camera, portal.normal(), tangent, aspect);
        double distance =
            Math.max(PortalAperture.PLANE_EPSILON, Math.abs(relative.dot(portal.normal())));
        float clipped = (float) (distance / (2 * extent));
        if (clipped < near && PortalEntityMovement.canTraverse(player, link)) near = clipped;
      }
    }
    // Iris reconstructs its far plane from projection coefficients; keep it representable.
    return IrisShaders.active()
        ? Math.min(ordinary, Math.max(near, far * Math.ulp(1.0F)))
        : near;
  }

  private static double projectionExtent(Camera camera, Vec3 axis, double tangent, float aspect) {
    var normal = axis.toVector3f();
    return Math.abs(camera.forwardVector().dot(normal))
        + tangent
            * (Math.abs(camera.upVector().dot(normal))
                + aspect * Math.abs(camera.leftVector().dot(normal)));
  }

  private static Vec3 vector(Vector3f vector) {
    return new Vec3(vector.x, vector.y, vector.z);
  }

  public record Boom(
      PortalThirdPersonCamera.Target target, Quaternionf rotation, Vec3 bob,
      PortalCameraCorrections history, double time, float distance) {}

  public record View(
      Vec3 position,
      Quaternionf rotation,
      Vec3 forward,
      PortalLink eyeLink,
      List<PortalLink> spacePath) {}
}
