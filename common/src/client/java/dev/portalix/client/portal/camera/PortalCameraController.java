package dev.portalix.client.portal.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.camera.PortalCameraCollision;
import dev.portalix.portal.camera.PortalCameraOrbit;
import dev.portalix.portal.camera.PortalCameraReorientation;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.camera.PortalThirdPersonCamera;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityMovement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
  private static final List<PortalLink> pendingBodyCrossings = new ArrayList<>();
  private static List<PortalLink> previousEyePath = List.of();
  private static Entity cameraSpaceOwner;

  private PortalCameraController() {}

  public static void setPortalSupplier(Supplier<List<PortalLink>> supplier) {
    links = supplier;
  }

  public static void reset() {
    var player = Minecraft.getInstance().player;
    if (player != null) EntityPortalState.of(player).resetCamera();
    clearTracking();
    pendingBodyCrossings.clear();
    previousEyePath = List.of();
    cameraSpaceOwner = null;
  }

  private static void clearTracking() {
    tracking = false;
    trackedPlayer = null;
    lastCrossing = Long.MIN_VALUE;
    ORBIT.clear();
    view = null;
  }

  public static void bodyCrossed(
      long eventId, PortalLink link, float previousYaw, float previousPitch) {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;
    var camera = Minecraft.getInstance().gameRenderer.mainCamera();
    trackCameraSpaceOwner(camera.entity());
    if (trackedPlayer != null && trackedPlayer != player) clearTracking();
    if (eventId <= lastCrossing) return;
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
    if (camera.entity() == player) PortalRoutes.append(pendingBodyCrossings, link);
  }

  public static PortalLookAngles mouse(LocalPlayer player, double horizontal, double vertical) {
    if ((!tracking && !ORBIT.active()) || trackedPlayer != player) return null;
    if (Minecraft.getInstance().gameRenderer.mainCamera().entity() != player) return null;
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
      if (!ORBIT.active() || mirrored != cameraType.isMirrored()) {
        var rotation =
            ORBIT.active()
                ? ORBIT.bodyOrientation()
                : tracking
                    ? ORIENTATION.orientation()
                    : PortalLookAngles.rotation(player.getYRot(), player.getXRot());
        ORBIT.initialize(rotation, null, List.of());
        tracking = false;
        mirrored = cameraType.isMirrored();
        trackedPlayer = player;
        appliedYaw = player.getYRot();
        appliedPitch = player.getXRot();
      }
      ORBIT.advance(seconds);
      frameSeconds = seconds;
      synchronizeAngles(player);
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
      if (camera == minecraft.gameRenderer.mainCamera()) view = null;
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

  /** Consumed once after resolving the main camera, before extracting its render view. */
  public static List<PortalLink> consumeCameraTransitions() {
    var minecraft = Minecraft.getInstance();
    trackCameraSpaceOwner(minecraft.gameRenderer.mainCamera().entity());
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

  public static void thirdPerson(Camera camera, float partialTick, float distance) {
    var player = (LocalPlayer) camera.entity();
    var rotation = cameraRotation(camera);
    var pose =
        ORBIT.resolve(
            new PortalThirdPersonCamera.Target(
                PortalEyePose.interpolatedCenter(player, partialTick),
                camera.position()),
            rotation,
            bobOffset(partialTick, rotation),
            EntityPortalState.of(player).cameraHistory(),
            player.tickCount - 1 + partialTick,
            distance,
            readyLinks(player),
            (body, movement, opening) -> PortalCameraWorld.query(player, body, movement, opening),
            frameSeconds);
    synchronizeAngles(player);
    apply(
        camera,
        new View(
            pose.position(),
            pose.rotation(),
            vector(pose.rotation().transform(new Vector3f(0, 0, -1))),
            vector(pose.rotation().transform(new Vector3f(0, 1, 0))),
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
      position = link.transform().point(position);
      center = link.transform().point(center);
      rotation.premul(link.transform().rotation());
      PortalRoutes.append(path, link);
    }
    var ready = readyLinks(player);
    var eyePath = PortalEyePose.eyePath(center, position, ready);
    for (var eyeLink : eyePath) {
      position = eyeLink.transform().point(position);
      rotation.premul(eyeLink.transform().rotation());
      PortalRoutes.append(path, eyeLink);
    }
    return new View(
        clearance(position, ready),
        new Quaternionf(rotation),
        vector(rotation.transform(new Vector3f(0, 0, -1))),
        vector(rotation.transform(new Vector3f(0, 1, 0))),
        path.isEmpty() ? null : path.getLast(),
        List.copyOf(path));
  }

  private static Vec3 clearance(Vec3 position, List<PortalLink> ready) {
    for (var link : ready) {
      var portal = link.entrance().endpoint();
      Vec3 relative = position.subtract(portal.center());
      double distance = relative.dot(portal.normal());
      if (distance >= -PortalAperture.PLANE_EPSILON
          && distance < MIN_CAMERA_CLEARANCE
          && PortalAperture.containsPassage(portal, position)) {
        position = position.add(portal.normal().scale(MIN_CAMERA_CLEARANCE - distance));
      }
    }
    return position;
  }

  private static List<PortalLink> readyLinks(LocalPlayer player) {
    return links.get().stream()
        .filter(link -> PortalEntityMovement.canTraverse(player, link))
        .toList();
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
    if (view == null || camera.entity() != Minecraft.getInstance().player) return ordinary;
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
    for (var link : readyLinks(Minecraft.getInstance().player)) {
      var portal = link.entrance().endpoint();
      Vec3 relative = camera.position().subtract(portal.center());
      if (Math.abs(relative.dot(portal.right()))
              <= portal.width() / 2
                  + ordinary * projectionExtent(camera, portal.right(), tangent, aspect)
          && Math.abs(relative.dot(portal.up()))
              <= portal.height() / 2
                  + ordinary * projectionExtent(camera, portal.up(), tangent, aspect)) {
        double extent = projectionExtent(camera, portal.normal(), tangent, aspect);
        double distance =
            Math.max(PortalAperture.PLANE_EPSILON, Math.abs(relative.dot(portal.normal())));
        near = Math.min(near, (float) (distance / (2 * extent)));
      }
    }
    // Iris converts reversed depth to a legacy projection; keep its far plane representable.
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

  public record View(
      Vec3 position,
      Quaternionf rotation,
      Vec3 forward,
      Vec3 up,
      PortalLink eyeLink,
      List<PortalLink> spacePath) {}
}
