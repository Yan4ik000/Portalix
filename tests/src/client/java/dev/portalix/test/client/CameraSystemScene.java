package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Camera arrangements start independently; only the movement under assertion is performed by input. */
final class CameraSystemScene implements ClientSystemScene {
  private enum Kind { STILL, BOOM, CROSS, RESET }
  private enum Phase { SETUP, READY, ACTION, NEXT, DONE }
  private record Case(String id, Kind kind, Direction entry, Direction exit, float roll,
                      double distance, int fov, CameraType perspective) {}
  private record Pose(Vec3 player, AABB body, float scale, float eyeHeight, Vec3 camera,
                      Quaternionf rotation, List<String> path) {}

  private static final List<Case> CASES = List.of(
      still("stationary-portal-view", .25, 70),
      boom("third-person-back-through-aperture", CameraType.THIRD_PERSON_BACK),
      boom("third-person-front-through-aperture", CameraType.THIRD_PERSON_FRONT),
      cross("wall-to-wall", Direction.SOUTH, Direction.SOUTH, 0),
      cross("wall-to-floor-rolled", Direction.SOUTH, Direction.UP, 45),
      cross("floor-to-wall", Direction.UP, Direction.SOUTH, 0),
      new Case("unlink-restores-the-local-camera", Kind.RESET, Direction.SOUTH,
          Direction.SOUTH, 0, 1.2, 70, CameraType.FIRST_PERSON));

  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final int originalFov;
  private final CameraType originalPerspective;
  private final boolean originalHidden;
  private final List<String> passed = new ArrayList<>();
  private final List<PortalLink> bodyCameraPath = new ArrayList<>();
  private long observedBodyEvent = Long.MIN_VALUE;
  private long previousPresentation;
  private Vec3 previousUnfoldedCamera;
  private Quaternionf previousUnfoldedRotation;
  private CameraType previousPerspective;
  private AssertionError continuityFailure;
  private PortalSystemFixture fixture;
  private PortalSystemFixture.Arrangement scene;
  private PortalLink closingLink;
  private CompletableFuture<PortalSystemFixture.Arrangement> setup;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<Boolean> readiness;
  private CompletableFuture<Boolean> authoritative;
  private CompletableFuture<FrameCapture.Frame> capture;
  private Phase phase = Phase.SETUP;
  private int index;
  private int age;
  private int action;
  private int actionTick = -1;
  private int exitMotionTick = -1;
  private boolean crossingPresented;
  private int readyTicks;
  private CameraType requestedPerspective;
  private AABB obstacle;
  private Pose pose;
  private FrameCapture.Frame baseline;
  private Vec3 mouseDirection;
  private Vec3 mouseBefore;

  CameraSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client; this.frames = frames;
    input = new ScenarioInput(client);
    originalFov = client.options.fov().get();
    originalPerspective = client.options.getCameraType();
    originalHidden = client.gui.hud.isHidden();
  }

  @Override public List<String> passed() { return List.copyOf(passed); }

  @Override public boolean tick() {
    if (continuityFailure != null) throw continuityFailure;
    return advance();
  }

  @Override public void presentedFrame(long nanos) {
    if (phase != Phase.ACTION
        || spec().kind != Kind.CROSS && spec().kind != Kind.BOOM) return;
    var camera = FrameCapture.worldCamera();
    if (camera == null || continuityFailure != null) return;
    observeBodyCrossing();
    var view = PortalCameraController.currentView();
    var path = new ArrayList<>(bodyCameraPath);
    if (view != null) path.addAll(view.spacePath());
    Vec3 position = camera.position();
    var rotation = new Quaternionf(client.gameRenderer.mainCamera().rotation());
    for (var link : path.reversed()) {
      position = link.inverse().transform().point(position);
      rotation.premul(link.inverse().transform().rotation());
    }
    double displacement = 0;
    double angle = 0;
    var perspective = client.options.getCameraType();
    if (previousUnfoldedCamera != null && perspective == previousPerspective) {
      double seconds = (nanos - previousPresentation) / 1e9;
      displacement = position.distanceTo(previousUnfoldedCamera);
      angle = 2 * Math.acos(Math.clamp(Math.abs(rotation.dot(previousUnfoldedRotation)), 0, 1));
      if (displacement > 1 + seconds * 32 || angle > .25 + seconds * 16)
        continuityFailure = new AssertionError("Presented camera discontinuity in " + spec().id
            + ": unfolded displacement=" + displacement + ", rotationRadians=" + angle + ", seconds=" + seconds);
    }
    previousUnfoldedCamera = position;
    previousUnfoldedRotation = rotation;
    previousPresentation = nanos;
    previousPerspective = perspective;
  }

  private void observeBodyCrossing() {
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    if (crossing != null && crossing.link().gun().equals(fixture.gunId())
        && crossing.link().generation() == scene.generation() && crossing.eventId() > observedBodyEvent) {
      bodyCameraPath.add(crossing.link());
      observedBodyEvent = crossing.eventId();
    }
  }

  private boolean advance() {
    if (phase == Phase.DONE) return true;
    if (phase == Phase.ACTION) observeBodyCrossing();
    if (++age >= 700) throw timeout();
    client.gui.hud.setOverlayMessage(Component.literal("Camera system: " + spec().id), false);
    switch (phase) {
      case SETUP -> {
        if (!frames.pause()) return false;
        input.stop();
        if (setup == null) {
          client.options.setCameraType(CameraType.FIRST_PERSON);
          client.options.fov().set(spec().fov);
          if (client.gui.hud.isHidden()) client.gui.hud.toggle();
          var player = client.player.getUUID();
          var server = client.getSingleplayerServer();
          setup = server.submit(() -> {
            if (fixture == null) fixture = new PortalSystemFixture(server.getPlayerList().getPlayer(player), 512, 0);
            var arrangement = fixture.prepare(spec().entry, spec().exit, spec().roll, spec().distance, false);
            if (spec().kind == Kind.BOOM) obstacle = fixture.cameraObstacle();
            return arrangement;
          });
        }
        if (!setup.isDone()) return false;
        scene = setup.join();
        setup = null;
        next(Phase.READY);
      }
      case READY -> {
        boolean ready = client.player.position().distanceTo(scene.feet()) < .12
            && PortalSystemFixture.markerLightingReady(client.level, scene)
            && PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
                && link.generation() == scene.generation() && PortalSceneManager.hasRenderedView(link)
                && PortalOpeningReadiness.fullyOpen(link) && PortalTraversalReadiness.ready(client.player, link));
        readyTicks = ready ? readyTicks + 1 : 0;
        if (readyTicks < 8) return false;
        if (readiness == null) readiness = client.getSingleplayerServer().submit(() -> fixture.traversalReady());
        if (!readiness.isDone()) return false;
        boolean serverReady = readiness.join(); readiness = null;
        if (!serverReady) return false;
        frames.monitor(true);
        next(Phase.ACTION);
      }
      case ACTION -> {
        switch (spec().kind) {
          case STILL -> {
            if (image("view", () -> true, frame -> {
              PortalSceneAssertions.landmarks(frame, scene);
              check(pose.player.distanceTo(scene.feet()) < .12, "A stationary near-plane observation moved the player");
            })) next(Phase.NEXT);
          }
          case BOOM -> boom();
          case CROSS -> crossing();
          case RESET -> reset();
        }
      }
      case NEXT -> {
        if (!frames.pause()) return false;
        passed.add("camera/" + spec().id);
        if (++index == CASES.size()) {
          phase = Phase.DONE;
          return true;
        }
        baseline = null; obstacle = null; requestedPerspective = null; closingLink = null;
        action = 0; actionTick = -1; exitMotionTick = -1; crossingPresented = false;
        readyTicks = 0; mouseDirection = null;
        bodyCameraPath.clear(); observedBodyEvent = Long.MIN_VALUE;
        previousUnfoldedCamera = null; previousUnfoldedRotation = null; previousPerspective = null;
        next(Phase.SETUP);
      }
      default -> {}
    }
    return phase == Phase.DONE;
  }

  private void boom() {
    if (action == 0) {
      if (spec().perspective == CameraType.THIRD_PERSON_BACK
          && !input.lookAt(client.player.getEyePosition().add(scene.entrance().normal().scale(5)))) return;
      action = 1;
    }
    if (action == 1) {
      if (!perspective(spec().perspective)) return;
      if (!image("collision", this::cameraThroughPortal, frame -> {
        check(pose.player.distanceTo(scene.feet()) < .12, "The third-person camera moved its body through the portal");
        check(!obstacle.inflate(.099).contains(pose.camera), "The native camera sphere entered the authored opaque blocker");
        double depth = pose.camera.subtract(scene.exit().center()).dot(scene.exit().normal());
        check(depth > 1.4 && depth < 2.1, "The camera boom did not stop against the real remote obstacle: " + depth);
        var actor = frame.project(scene.exitBounds(pose.body)).orElseThrow();
        frame.assertSprite(actor, FrameCapture.Material.CYAN, 8, actor.area(), 2, 2);
      })) return;
      action = 2;
    }
    if (action == 2) {
      if (!perspective(CameraType.FIRST_PERSON)) return;
      if (image("restored", () -> client.options.getCameraType() == CameraType.FIRST_PERSON, frame -> {
        check(pose.path.isEmpty(), "Returning through F5 retained a detached portal-space camera");
        check(pose.camera.distanceTo(pose.player.add(0, pose.eyeHeight, 0)) < .02,
            "Returning through F5 did not restore the local eye position");
        check(pose.player.distanceTo(scene.feet()) < .12, "F5 changed the stationary body position");
      })) next(Phase.NEXT);
    }
  }

  private void crossing() {
    if (action == 0) {
      if (!image("before", () -> true, frame -> PortalSceneAssertions.landmarks(frame, scene))) return;
      capture = frames.reservedCheckpoint(name("crossing"), () -> {
        if (!crossed() || FrameCapture.worldCamera() == null
            || FrameCapture.worldCamera().position().distanceTo(scene.exit().center()) > 5) return false;
        pose = snapshot(); crossingPresented = true; return true;
      });
      action = 1;
    }
    if (action == 1) {
      if (!frames.reservedReady()) return;
      mutation = client.getSingleplayerServer().submit(() -> fixture.releaseFlight());
      action = 2;
    }
    if (action == 2) {
      if (!mutation.isDone() || client.player.getAbilities().flying) return;
      if (spec().entry == Direction.DOWN && !client.player.onGround()) return;
      mutation.join(); mutation = null;
      actionTick = client.player.tickCount;
      if (spec().entry == Direction.DOWN) client.options.keyJump.setDown(true);
      else if (spec().entry.getAxis() != Direction.Axis.Y) input.forward(true);
      action = 3;
    }
    if (action == 3) {
      if (client.player.tickCount > actionTick) client.options.keyJump.setDown(false);
      if (crossingPresented) {
        continueFromExit();
      }
      if (!capture.isDone()) return;
      FrameCapture.Frame frame = capture.join(); capture = null;
      PortalSceneAssertions.projection(frame);
      check(crossingPresented && crossed(), "The presented transition has no real portal movement event");
      action = 4;
    }
    if (action == 4) {
      continueFromExit();
      Vec3 after = Vec3.directionFromRotation(client.player.getXRot(), client.player.getYRot());
      if (client.player.tickCount - exitMotionTick < 12) return;
      input.stop();
      check(after.dot(mouseBefore) < .9995, "Native mouse input did not affect the post-portal look direction");
      if (!upright(client.gameRenderer.mainCamera().rotation()) || PortalPassagePose.scale(client.player) < .999) return;
      authoritative = client.getSingleplayerServer().submit(fixture::crossedEntrance);
      action = 5;
    }
    if (action == 5) {
      if (!authoritative.isDone()) return;
      check(authoritative.join(), "The client camera transition has no authoritative server crossing");
      authoritative = null;
      action = 6;
    }
    if (action == 6) {
      if (image("settled", this::crossed, frame -> {
        check(pose.player.distanceTo(scene.exit().center()) < 5, "The settled body did not remain at the authored exit");
        check(pose.scale >= .999 && pose.body.getYsize() > 1.7, "Passage compression did not restore the standing body");
        check(upright(pose.rotation), "The rendered camera did not recover upright");
      })) next(Phase.NEXT);
    }
  }

  private void continueFromExit() {
    if (exitMotionTick < 0) {
      exitMotionTick = client.player.tickCount;
      mouseBefore = Vec3.directionFromRotation(client.player.getXRot(), client.player.getYRot());
      mouseDirection = Vec3.directionFromRotation(
          Math.clamp(client.player.getXRot() - 8, -82, 82), client.player.getYRot() + 8);
    }
    input.lookAt(client.player.getEyePosition().add(mouseDirection.scale(8)));
    moveFromExit(client.player.tickCount - exitMotionTick < 12);
  }

  private void moveFromExit(boolean moving) {
    input.stop();
    if (!moving) return;
    if (spec().exit != Direction.UP) {
      input.forward(true);
      return;
    }
    Vec3 shortAxis = scene.exit().right();
    double forward = Vec3.directionFromRotation(0, client.player.getYRot()).dot(shortAxis);
    double right = Vec3.directionFromRotation(0, client.player.getYRot() + 90).dot(shortAxis);
    if (Math.abs(right) > Math.abs(forward)) {
      client.options.keyRight.setDown(right > 0);
      client.options.keyLeft.setDown(right < 0);
    } else {
      client.options.keyUp.setDown(forward > 0);
      client.options.keyDown.setDown(forward < 0);
    }
  }

  private void reset() {
    if (action == 0) {
      if (!image("linked", () -> true, frame -> {
        PortalSceneAssertions.landmarks(frame, scene); baseline = frame;
      })) return;
      closingLink = PortalTopology.links(client.level).stream().filter(link -> link.gun().equals(fixture.gunId())
          && link.generation() == scene.generation() && link.entrance().endpoint().equals(scene.entrance()))
          .findFirst().orElseThrow();
      mutation = client.getSingleplayerServer().submit(() -> fixture.clearPortals()); action = 1;
    }
    if (action == 1) {
      if (!mutation.isDone()) return;
      mutation.join(); mutation = null; action = 2;
    }
    if (action == 2 && image("unlinked", () -> PortalTopology.links(client.level).stream()
        .noneMatch(link -> link.gun().equals(fixture.gunId())) && PortalOpeningReadiness.fullyClosed(closingLink), frame -> {
      var aperture = PortalSceneAssertions.aperture(frame, scene.entrance(), .15);
      for (var marker : scene.markers()) {
        var region = PortalSceneAssertions.intersection(frame.project(scene.imageBounds(marker.bounds())).orElseThrow(), aperture);
        var before = PortalSceneAssertions.pixels(baseline, region, marker.material(), scene.entrance());
        var after = PortalSceneAssertions.pixels(frame, region, marker.material(), scene.entrance());
        check(after.matched() <= Math.max(2, before.matched() * .01),
            "Unlink retained its authored remote " + marker.material() + " landmark");
        check(PortalSceneAssertions.difference(frame, baseline, region, 8, scene.entrance()).changedFraction() > .05,
            "Unlink did not replace the remote landmark with the real source wall");
      }
      check(pose.path.isEmpty(), "Unlink left a stale eye-space camera path");
      check(PortalSceneManager.portalImage(scene.entrance()) == null, "Unlinked portal retained a live scene image");
    })) {
      setup = client.getSingleplayerServer().submit(() -> fixture.prepare(Direction.SOUTH, Direction.SOUTH, 0, 1.2, false));
      action = 3;
    }
    if (action == 3) {
      if (setup != null) {
        if (!setup.isDone()) return;
        scene = setup.join(); setup = null;
      }
      if (readiness == null) readiness = client.getSingleplayerServer().submit(() -> fixture.traversalReady());
      if (!readiness.isDone()) return;
      boolean ready = readiness.join(); readiness = null;
      if (!ready || PortalTopology.links(client.level).stream().noneMatch(link -> link.gun().equals(fixture.gunId())
          && link.generation() == scene.generation() && PortalOpeningReadiness.fullyOpen(link))) return;
      action = 4;
    }
    if (action == 4) {
      if (!input.lookAt(client.player.getEyePosition().add(scene.entrance().normal().scale(5)))
          || !perspective(CameraType.THIRD_PERSON_BACK)) return;
      if (!image("detached-before-unlink", this::cameraThroughPortal, frame -> {
        check(pose.player.distanceTo(scene.feet()) < .12 && !pose.path.isEmpty(),
            "Detached unlink preparation did not leave the real body at its source");
      })) return;
      closingLink = PortalTopology.links(client.level).stream().filter(link -> link.gun().equals(fixture.gunId())
          && link.generation() == scene.generation() && link.entrance().endpoint().equals(scene.entrance()))
          .findFirst().orElseThrow();
      mutation = client.getSingleplayerServer().submit(() -> fixture.clearPortals());
      action = 5;
    }
    if (action == 5) {
      if (!mutation.isDone()) return;
      mutation.join(); mutation = null;
      action = 6;
    }
    if (action == 6 && image("detached-after-unlink", () -> PortalTopology.links(client.level).stream()
        .noneMatch(link -> link.gun().equals(fixture.gunId())) && PortalOpeningReadiness.fullyClosed(closingLink)
        && !cameraThroughPortal(), frame -> {
      check(pose.path.isEmpty() && pose.camera.distanceTo(scene.exit().center()) > 8,
          "Closing the portal retained its detached remote camera space");
      check(pose.player.distanceTo(scene.feet()) < .12, "Detached unlink moved the source body");
      check(PortalSceneManager.portalImage(scene.entrance()) == null, "Detached unlink retained a portal image");
    })) next(Phase.NEXT);
  }

  private boolean perspective(CameraType desired) {
    var current = client.options.getCameraType();
    if (current == desired) return true;
    if (current != requestedPerspective) {
      input.click(client.options.keyTogglePerspective);
      requestedPerspective = current;
    }
    return false;
  }

  private boolean cameraThroughPortal() {
    var view = PortalCameraController.currentView();
    return view != null && view.spacePath().stream().anyMatch(link -> link.gun().equals(fixture.gunId()));
  }

  private boolean crossed() {
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    return crossing != null && crossing.link().gun().equals(fixture.gunId())
        && crossing.link().generation() == scene.generation()
        && crossing.link().entrance().endpoint().equals(scene.entrance());
  }

  private AssertionError timeout() {
    return new AssertionError("Camera case timed out: " + spec().id + "/" + phase + "/" + action);
  }

  private boolean image(String suffix, BooleanSupplier condition, Consumer<FrameCapture.Frame> assertion) {
    if (capture == null) {
      capture = frames.reservedCheckpoint(name(suffix), () -> {
        if (!condition.getAsBoolean()) return false;
        pose = snapshot(); return true;
      });
      return false;
    }
    if (!capture.isDone()) return false;
    var frame = capture.join(); capture = null;
    PortalSceneAssertions.projection(frame);
    check(frame.cameraPosition().distanceTo(pose.camera) < .002, "Camera evidence crossed a presentation boundary");
    check(pose.rotation.isFinite() && Math.abs(pose.rotation.lengthSquared() - 1) < 1e-4,
        "The native rendered camera lost its orthonormal orientation");
    assertion.accept(frame);
    return true;
  }

  private Pose snapshot() {
    var camera = client.gameRenderer.mainCamera();
    var view = PortalCameraController.currentView();
    return new Pose(client.player.position(), client.player.getBoundingBox(), PortalPassagePose.scale(client.player),
        client.player.getEyeHeight(), camera.position(), new Quaternionf(camera.rotation()),
        view == null ? List.of() : view.spacePath().stream()
            .map(link -> link.gun() + ":" + link.generation()).toList());
  }

  private static boolean upright(Quaternionf rotation) {
    Vec3 forward = new Vec3(rotation.transform(new Vector3f(0, 0, -1)));
    Vec3 up = new Vec3(rotation.transform(new Vector3f(0, 1, 0)));
    Vec3 vertical = new Vec3(0, 1, 0).subtract(forward.scale(forward.y));
    return vertical.lengthSqr() > 1e-6 && up.dot(vertical.normalize()) > .995;
  }

  @Override public CompletableFuture<Void> cleanup() {
    input.stop();
    client.options.fov().set(originalFov);
    client.options.setCameraType(originalPerspective);
    if (client.gui.hud.isHidden() != originalHidden) client.gui.hud.toggle();
    CompletableFuture<Void> pending = CompletableFuture.allOf(Stream.of(setup, mutation, readiness, authoritative)
        .filter(Objects::nonNull).toArray(CompletableFuture<?>[]::new));
    return pending.handle((ignored, failure) -> null).thenCompose(ignored -> client.getSingleplayerServer().submit(() -> {
      if (fixture != null) fixture.close();
    }));
  }

  private Case spec() { return CASES.get(index); }
  private String name(String suffix) { return "camera-" + spec().id + "-" + suffix; }
  private void next(Phase value) { input.stop(); phase = value; age = 0; }
  private static Case still(String id, double distance, int fov) {
    return new Case(id, Kind.STILL, Direction.SOUTH, Direction.SOUTH, 0, distance, fov, CameraType.FIRST_PERSON);
  }
  private static Case boom(String id, CameraType perspective) {
    return new Case(id, Kind.BOOM, Direction.SOUTH, Direction.SOUTH, 0, 1.2, 70, perspective);
  }
  private static Case cross(String id, Direction entry, Direction exit, float roll) {
    return new Case(id, Kind.CROSS, entry, exit, roll, entry == Direction.DOWN ? .38 : entry == Direction.UP ? 2.8 : 1.2,
        70, CameraType.FIRST_PERSON);
  }
}
