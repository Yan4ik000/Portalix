package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalAperture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalViewSystemScene implements ClientSystemScene {
  private enum Kind { ORIENTATION, OCCLUSION, GLASS, WATER, ENTITY, PARALLAX_PARTICLES, RECURSION }
  private enum Phase { SETUP, READY, ACTION, NEXT, DONE }
  private record Case(String id, Kind kind, Direction entry, Direction exit, float roll) {}
  private record ChromaChange(double meanChange, int increasedPixels, int samples) {
    double increasedFraction() { return (double) increasedPixels / samples; }
  }
  private static final List<Case> CASES = List.of(
      orientation("south-wall", Direction.SOUTH, Direction.SOUTH, 0),
      orientation("floor-rolled-forty-five", Direction.SOUTH, Direction.UP, 45),
      orientation("ceiling-rolled-forty-five", Direction.SOUTH, Direction.DOWN, 45),
      effect("opaque-remote-occlusion", Kind.OCCLUSION), effect("transparent-remote-glass", Kind.GLASS),
      effect("transparent-remote-water", Kind.WATER), effect("entity-front-straddle-and-behind-plane", Kind.ENTITY),
      effect("parallax-and-native-particles", Kind.PARALLAX_PARTICLES),
      effect("second-level-recursive-scene", Kind.RECURSION));

  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final CameraType previousPerspective;
  private final int previousFov;
  private final boolean previousHidden;
  private final List<String> passed = new ArrayList<>();
  private final List<Map<String, Object>> proof = new ArrayList<>();
  private PortalSystemFixture fixture;
  private PortalSystemFixture.Arrangement scene;
  private PortalSystemFixture.Nested nested;
  private PortalSystemFixture.ClipSentinel sentinel;
  private PortalViewParallaxCase parallax;
  private PortalLink sourceLink;
  private CompletableFuture<PortalSystemFixture.Arrangement> setup;
  private CompletableFuture<AABB> blocker;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<Integer> spawn;
  private CompletableFuture<Vec3> actorMove;
  private CompletableFuture<PortalSystemFixture.Nested> nestedSetup;
  private CompletableFuture<Void> diagnostics;
  private CompletableFuture<FrameCapture.Frame> capture;
  private CompletableFuture<FrameCapture.Frame> settlingSample;
  private FrameCapture.Frame settlingAnchor;
  private FrameCapture.Frame baseline;
  private FrameCapture.Frame materialRed;
  private FrameCapture.Frame materialWhite;
  private FrameCapture.Frame materialRestored;
  private FrameCapture.Frame opaqueRed;
  private final Map<String, Object> transmissions = new LinkedHashMap<>();
  private Phase phase = Phase.SETUP;
  private int index;
  private int age;
  private int action;
  private int stableTicks;
  private int actorId = -1;
  private Vec3 actorExpected;
  private AABB actorBounds;
  private AABB obstacle;
  private Vec3 capturedPlayer;
  private long mutationFrame;
  private BlockState material;
  private PortalTerrainUploadProbe uploads;
  private final Map<BlockPos, BlockState> materialBefore = new LinkedHashMap<>();
  private final Map<BlockPos, BlockState> expectedMaterial = new LinkedHashMap<>();
  private Map<String, Object> capturedState;

  PortalViewSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client; this.frames = frames;
    input = new ScenarioInput(client);
    previousPerspective = client.options.getCameraType(); previousFov = client.options.fov().get();
    previousHidden = client.gui.hud.isHidden();
  }

  @Override public List<String> passed() { return List.copyOf(passed); }

  @Override public boolean tick() {
    try {
      return advance();
    } catch (RuntimeException | Error failure) {
      failureDiagnostics(failure);
      throw failure;
    }
  }

  private boolean advance() {
    if (phase == Phase.DONE) return true;
    check(++age < 700, "Portal view case timed out: " + spec().id + "/" + phase + "/" + action);
    client.gui.hud.setOverlayMessage(Component.literal("Portal view system: " + spec().id), false);
    switch (phase) {
      case SETUP -> {
        if (!frames.pause()) return false;
        input.stop();
        if (!client.gui.hud.isHidden()) { input.click(client.options.keyToggleGui); return false; }
        if (setup == null) {
          client.options.setCameraType(CameraType.FIRST_PERSON); client.options.fov().set(70);
          var owner = client.player.getUUID();
          var server = client.getSingleplayerServer();
          setup = server.submit(() -> {
            if (fixture == null) fixture = new PortalSystemFixture(server.getPlayerList().getPlayer(owner), 512, 64);
            var arrangement = fixture.prepare(spec().entry, spec().exit, spec().roll, 2.8, false);
            if (spec().kind == Kind.ORIENTATION) sentinel = fixture.clipSentinel();
            if (spec().kind == Kind.WATER) fixture.prepareMaterialContainer();
            if (spec().kind == Kind.PARALLAX_PARTICLES) fixture.releaseFlight();
            return arrangement;
          });
        }
        if (!setup.isDone()) return false;
        scene = setup.join(); setup = null;
        next(Phase.READY);
      }
      case READY -> {
        sourceLink = PortalTopology.links(client.level).stream().filter(link -> link.gun().equals(fixture.gunId())
            && link.generation() == scene.generation() && link.entrance().endpoint().equals(scene.entrance()))
            .findFirst().orElse(null);
        boolean ready = client.player.position().distanceTo(scene.feet()) < .12
            && client.gui.hud.isHidden() && client.gameRenderer.gameRenderState().guiRenderState.isHudHidden
            && client.levelRenderer.hasRenderedAllSections()
            && PortalSystemFixture.markerLightingReady(client.level, scene)
            && sourceLink != null && PortalSceneManager.hasRenderedView(sourceLink)
            && PortalOpeningReadiness.fullyOpen(sourceLink) && PortalViewTerrainReadiness.complete(sourceLink);
        if (sentinel != null) ready &= sentinel.ready(client.level);
        stableTicks = ready ? stableTicks + 1 : 0;
        if (stableTicks < 8) return false;
        frames.monitor(true); next(Phase.ACTION);
      }
      case ACTION -> {
        switch (spec().kind) {
          case ORIENTATION -> {
            if (image("oriented-and-clipped", () -> true, frame -> {
              PortalSceneAssertions.landmarks(frame, scene);
              capturedState.put("clipping", clippingProof(frame));
            })) next(Phase.NEXT);
          }
          case OCCLUSION, GLASS, WATER -> material();
          case ENTITY -> entity();
          case PARALLAX_PARTICLES -> {
            if (parallax == null) parallax = new PortalViewParallaxCase(client, frames, scene, sourceLink);
            if (parallax.tick()) { proof.addAll(parallax.proof()); next(Phase.NEXT); }
          }
          case RECURSION -> recursion();
        }
      }
      case NEXT -> {
        if (!frames.pause()) return false;
        if (parallax != null) { parallax.close(); parallax = null; }
        if (uploads != null) { uploads.close(); uploads = null; }
        passed.add("portal_views/" + spec().id);
        if (++index == CASES.size()) {
          writeProof(); phase = Phase.DONE; return true;
        }
        action = 0; stableTicks = 0; actorId = -1; actorBounds = null; nested = null; baseline = null; obstacle = null;
        sentinel = null;
        materialRed = null; materialWhite = null; materialRestored = null; opaqueRed = null;
        sourceLink = null; capturedState = null;
        materialBefore.clear(); expectedMaterial.clear();
        next(Phase.SETUP);
      }
      default -> {}
    }
    return phase == Phase.DONE;
  }

  private void material() {
    if (action >= 5) { transmission(); return; }
    if (action == 0) {
      obstacle = scene.materialBounds();
      if (!image("clear", () -> true, frame -> {
        PortalSceneAssertions.landmarks(frame, scene); baseline = frame;
        for (BlockPos position : PortalSystemFixture.materialPositions(obstacle))
          materialBefore.put(position.immutable(), client.level.getBlockState(position));
      })) return;
      material = switch (spec().kind) {
        case GLASS -> Blocks.GLASS.defaultBlockState();
        case WATER -> Blocks.WATER.defaultBlockState();
        default -> Blocks.QUARTZ_BLOCK.defaultBlockState();
      };
      materialBefore.keySet().forEach(position -> expectedMaterial.put(position, material));
      uploads = new PortalTerrainUploadProbe(obstacle);
      blocker = client.getSingleplayerServer().submit(() -> fixture.placeMaterial(material));
      action = 1;
    }
    if (action == 1) {
      if (!blocker.isDone()) return;
      obstacle = blocker.join(); blocker = null; action = 2;
    }
    if (action == 2) {
      if (!image("material", this::materialReady, frame -> {
        sameCamera(frame, baseline);
        if (spec().kind == Kind.OCCLUSION) {
          var red = PortalSceneAssertions.intersection(frame.project(scene.imageBounds(scene.markers().getFirst().bounds())).orElseThrow(),
              PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
          var before = PortalSceneAssertions.pixels(baseline, red, FrameCapture.Material.RED, scene.entrance());
          var after = PortalSceneAssertions.pixels(frame, red, FrameCapture.Material.RED, scene.entrance());
          check(before.matched() >= 8 && after.matched() < before.matched() * .1,
              "A real opaque remote block failed to occlude its authored red landmark");
        } else materialRed = frame;
        var green = PortalSceneAssertions.intersection(frame.project(scene.imageBounds(scene.markers().get(1).bounds())).orElseThrow(),
            PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
        PortalSceneAssertions.sprite(frame, green, FrameCapture.Material.GREEN, 8, scene.entrance());
        var changed = PortalSceneAssertions.intersection(frame.project(scene.imageBounds(obstacle)).orElseThrow(),
            PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
        check(frame.difference(baseline, changed, 8).changedPixels() > 12,
            "The main portal image did not include its real remote material update");
      })) return;
      if (spec().kind != Kind.OCCLUSION) { changeMarker(DyeColor.WHITE, 5); return; }
      uploads.close();
      expectedMaterial.clear(); expectedMaterial.putAll(materialBefore);
      uploads = new PortalTerrainUploadProbe(obstacle);
      mutation = client.getSingleplayerServer().submit(() -> fixture.restoreMaterial());
      action = 3;
    }
    if (action == 3) {
      if (!mutation.isDone()) return;
      mutation.join(); mutation = null; action = 4;
    }
    if (action == 4 && image("restored", this::materialReady, frame -> PortalSceneAssertions.landmarks(frame, scene)))
      next(Phase.NEXT);
  }

  private void transmission() {
    switch (action) {
      case 5, 7, 11 -> {
        if (!mutation.isDone()) return;
        mutation.join(); mutation = null; action++;
      }
      case 6 -> {
        if (!image("marker-white", this::materialReady, frame -> {
          sameCamera(frame, materialRed); materialWhite = frame;
        })) return;
        changeMarker(DyeColor.RED, 7);
      }
      case 8 -> {
        if (!image("marker-restored", this::materialReady, frame -> {
          sameCamera(frame, materialRed); materialRestored = frame;
        })) return;
        uploads.close();
        material = Blocks.QUARTZ_BLOCK.defaultBlockState();
        materialBefore.keySet().forEach(position -> expectedMaterial.put(position, material));
        uploads = new PortalTerrainUploadProbe(obstacle);
        blocker = client.getSingleplayerServer().submit(() -> fixture.placeMaterial(material));
        action = 9;
      }
      case 9 -> {
        if (!blocker.isDone()) return;
        obstacle = blocker.join(); blocker = null; action = 10;
      }
      case 10 -> {
        if (!image("opaque-red", this::materialReady, frame -> {
          sameCamera(frame, materialRed); opaqueRed = frame;
        })) return;
        changeMarker(DyeColor.WHITE, 11);
      }
      case 12 -> {
        if (image("opaque-white", this::materialReady, this::assertTransmission)) next(Phase.NEXT);
      }
      default -> throw new IllegalStateException("Unknown transparent-material action " + action);
    }
  }

  private void changeMarker(DyeColor color, int nextAction) {
    uploads.close();
    var marker = scene.markers().getFirst().bounds();
    var position = BlockPos.containing(marker.getCenter());
    var state = Blocks.CONCRETE.pick(color).defaultBlockState();
    expectedMaterial.put(position, state);
    uploads = new PortalTerrainUploadProbe(marker);
    mutation = client.getSingleplayerServer().submit(() -> fixture.block(position, state));
    action = nextAction;
  }

  private void assertTransmission(FrameCapture.Frame opaqueWhite) {
    sameCamera(opaqueWhite, materialRed);
    var marker = markerInterior(opaqueWhite, 0);
    var control = markerInterior(opaqueWhite, 1);
    var signal = PortalSceneAssertions.difference(materialWhite, materialRed, marker, 8, scene.entrance());
    var repeated = PortalSceneAssertions.difference(materialWhite, materialRestored, marker, 8, scene.entrance());
    var animation = PortalSceneAssertions.difference(materialRestored, materialRed, marker, 8, scene.entrance());
    var materialControl = PortalSceneAssertions.difference(materialWhite, materialRed, control, 8, scene.entrance());
    var restoredControl = PortalSceneAssertions.difference(materialRestored, materialRed, control, 8, scene.entrance());
    var occluded = PortalSceneAssertions.difference(opaqueWhite, opaqueRed, marker, 8, scene.entrance());
    var opaqueControl = PortalSceneAssertions.difference(opaqueWhite, opaqueRed, control, 8, scene.entrance());
    double redControlChroma = chromaChange(materialRed, materialWhite, control, 0).meanChange();
    double restoredControlChroma = chromaChange(materialRestored, materialWhite, control, 0).meanChange();
    double opaqueControlChroma = chromaChange(opaqueRed, opaqueWhite, control, 0).meanChange();
    var redChroma = chromaChange(materialRed, materialWhite, marker, redControlChroma);
    var restoredChroma = chromaChange(materialRestored, materialWhite, marker, restoredControlChroma);
    var opaqueChroma = chromaChange(opaqueRed, opaqueWhite, marker, opaqueControlChroma);
    double contrast = Math.min(redChroma.meanChange(), restoredChroma.meanChange());
    double redRestorationDrift = Math.abs(chromaChange(materialRed, materialRestored, marker,
        redControlChroma - restoredControlChroma).meanChange());
    double controlDrift = Math.max(Math.abs(redControlChroma - restoredControlChroma),
        Math.max(Math.abs(opaqueControlChroma), Math.max(Math.abs(redControlChroma), Math.abs(restoredControlChroma))));
    var details = new LinkedHashMap<String, Object>();
    details.putAll(Map.of("markerRegion", marker, "controlRegion", control,
        "redToWhite", signal, "restoredRedToWhite", repeated, "redRestoration", animation,
        "materialControl", materialControl, "restoredControl", restoredControl,
        "opaqueRedToWhite", occluded, "opaqueControl", opaqueControl,
        "frames", Map.of("materialRed", materialRed.id(), "materialWhite", materialWhite.id(),
            "materialRestored", materialRestored.id(), "opaqueRed", opaqueRed.id(), "opaqueWhite", opaqueWhite.id())));
    details.put("redNeutralChroma", Map.of("redToWhite", redChroma,
        "restoredRedToWhite", restoredChroma, "opaqueRedToWhite", opaqueChroma,
        "redRestorationDrift", redRestorationDrift, "controlDrift", controlDrift,
        "redControl", redControlChroma, "restoredControl", restoredControlChroma,
        "opaqueControl", opaqueControlChroma));
    transmissions.put(spec().id, details);
    check(signal.changedPixels() >= 12 && repeated.changedPixels() >= 12
        && Math.min(signal.changedFraction(), repeated.changedFraction()) > .08
        && redChroma.increasedPixels() >= 12 && restoredChroma.increasedPixels() >= 12
        && Math.min(redChroma.increasedFraction(), restoredChroma.increasedFraction()) > .08
        && contrast > redRestorationDrift && contrast > Math.max(3, controlDrift * 3 + 1),
        "The object behind " + spec().id + " has no repeatable contribution beyond material animation/control noise: " + details);
    check(Math.abs(opaqueChroma.meanChange()) <= Math.max(2, contrast * .15)
        && occluded.meanChannelDifference() <= Math.max(2,
            Math.min(signal.meanChannelDifference(), repeated.meanChannelDifference()) * .15),
        "The same hidden-object mutation leaked through the native opaque control: " + details);
  }

  private ChromaChange chromaChange(FrameCapture.Frame current, FrameCapture.Frame before,
                                    FrameCapture.Region region, double controlChange) {
    var mask = PortalSceneAssertions.coreMask(current, region, scene.entrance());
    double sum = 0;
    int samples = 0, increased = 0;
    for (int y = region.y(); y < region.y() + region.height(); y++) {
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        if (!mask[(y - region.y()) * region.width() + x - region.x()]) continue;
        double change = redNeutralChroma(current.pixel(x, y)) - redNeutralChroma(before.pixel(x, y)) - controlChange;
        sum += change;
        if (change > 3) increased++;
        samples++;
      }
    }
    check(samples > 0, "The transparent-material chromatic observation misses the portal clear core: " + region);
    return new ChromaChange(sum / samples, increased, samples);
  }

  private static double redNeutralChroma(int pixel) {
    int red = pixel >> 16 & 255, green = pixel >> 8 & 255;
    return 255.0 * (red - green) / Math.max(1, red + green);
  }

  private FrameCapture.Region markerInterior(FrameCapture.Frame frame, int index) {
    var bounds = scene.markers().get(index).bounds().deflate(.15);
    var projected = frame.project(scene.imageBounds(bounds)).orElseThrow(() ->
        new AssertionError("The transparent-material control marker projects outside the main window"));
    return PortalSceneAssertions.intersection(projected, PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
  }

  private boolean materialReady() {
    return uploads.complete(sourceLink) && client.levelRenderer.hasRenderedAllSections()
        && PortalViewTerrainReadiness.complete(sourceLink)
        && expectedMaterial.entrySet().stream().allMatch(entry -> client.level.getBlockState(entry.getKey()).equals(entry.getValue()));
  }

  private void entity() {
    if (action == 0) {
      if (!image("empty", () -> true, frame -> baseline = frame)) return;
      spawn = client.getSingleplayerServer().submit(() -> fixture.spawnSliceItem(.4)); action = 1;
    }
    if (action == 1) {
      if (!spawn.isDone()) return;
      actorId = spawn.join(); spawn = null;
      actorExpected = scene.exit().center().add(scene.exit().normal().scale(.4)).add(0, -.125, 0);
      action = 2;
    }
    if (action == 2) {
      if (!image("front", this::actorReady, frame -> {
        var region = actorRegion(frame);
        PortalSceneAssertions.sprite(frame, region, FrameCapture.Material.CYAN, 8, scene.entrance());
        check(frame.difference(baseline, region, 20).changedPixels() > 12,
            "The newly tracked actor is absent from the real portal image");
      })) return;
      actorMove = client.getSingleplayerServer().submit(() -> fixture.moveSliceItem(actorId, 0)); action = 3;
    }
    if (action == 3) {
      if (!actorMove.isDone()) return;
      actorExpected = actorMove.join(); actorMove = null; action = 4;
    }
    if (action == 4) {
      if (!image("straddling", this::actorReady, frame -> {
        var region = actorRegion(frame);
        PortalSceneAssertions.sprite(frame, region, FrameCapture.Material.CYAN, 5, scene.entrance());
      })) return;
      actorMove = client.getSingleplayerServer().submit(() -> fixture.moveSliceItem(actorId, -2)); action = 5;
    }
    if (action == 5) {
      if (!actorMove.isDone()) return;
      actorExpected = actorMove.join(); actorMove = null; action = 6;
    }
    if (action == 6 && image("behind-plane", this::actorReady, frame -> {
      var region = actorRegion(frame);
      check(PortalSceneAssertions.pixels(frame, region, FrameCapture.Material.CYAN, scene.entrance()).matched() <= 2,
          "An entity behind the destination clip plane leaked into the portal aperture");
      PortalSceneAssertions.landmarks(frame, scene);
    })) next(Phase.NEXT);
  }

  private boolean actorReady() {
    Entity actor = client.level.getEntity(actorId);
    if (actor == null || actor.position().distanceTo(actorExpected) > .08) return false;
    // The ground model and maximum item bob extend above the collision box.
    actorBounds = actor.getBoundingBox().inflate(.2).expandTowards(0, 1.0 / 16, 0);
    return true;
  }

  private FrameCapture.Region actorRegion(FrameCapture.Frame frame) {
    return PortalSceneAssertions.intersection(frame.project(scene.imageBounds(actorBounds)).orElseThrow(),
        PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
  }

  private void recursion() {
    if (action == 0) {
      if (!image("single-level", () -> true, frame -> PortalSceneAssertions.landmarks(frame, scene))) return;
      mutationFrame = frames.submittedFrames();
      nestedSetup = client.getSingleplayerServer().submit(() -> fixture.recursivePair()); action = 1;
    }
    if (action == 1) {
      if (!nestedSetup.isDone()) return;
      nested = nestedSetup.join(); nestedSetup = null; action = 2;
    }
    if (action == 2 && image("second-level", () -> frames.submittedFrames() >= mutationFrame + 2
        && client.levelRenderer.hasRenderedAllSections()
        && PortalSystemFixture.markerLightingReady(client.level, nested.arrangement())
        && PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(nested.gun())
            && link.generation() == nested.arrangement().generation() && PortalSceneManager.hasRenderedView(link)
            && PortalOpeningReadiness.fullyOpen(link) && PortalViewTerrainReadiness.complete(link)), frame -> {
      var aperture = PortalSceneAssertions.aperture(frame, scene.entrance(), .05);
      for (var marker : nested.arrangement().markers()) {
        AABB twiceMapped = scene.imageBounds(nested.arrangement().imageBounds(marker.bounds()));
        var region = PortalSceneAssertions.intersection(frame.project(twiceMapped).orElseThrow(), aperture);
        PortalSceneAssertions.sprite(frame, region, marker.material(), 5, scene.entrance(),
            scene.imagePortal(nested.arrangement().entrance()));
      }
    })) next(Phase.NEXT);
  }

  private boolean image(String suffix, BooleanSupplier condition, Consumer<FrameCapture.Frame> assertion) {
    if (capture == null && (spec().kind == Kind.GLASS || spec().kind == Kind.WATER)
        && !settledMaterial(condition)) return false;
    return captureImage(suffix, () -> condition.getAsBoolean() && PortalViewTerrainReadiness.complete(sourceLink), assertion);
  }

  private boolean captureImage(String suffix, BooleanSupplier condition, Consumer<FrameCapture.Frame> assertion) {
    if (capture == null) {
      settlingAnchor = null;
      capture = frames.reservedCheckpoint(name(suffix), () -> {
        if (!client.gui.hud.isHidden() || !client.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return false;
        if (sentinel != null && !sentinel.ready(client.level)) return false;
        if (!condition.getAsBoolean()) return false;
        capturedPlayer = client.player.position(); capturedState = clientState(); return true;
      });
      return false;
    }
    if (!capture.isDone()) return false;
    var frame = capture.join(); capture = null;
    capturedState.put("frame", frame.metadata());
    PortalSceneAssertions.projection(frame);
    check(capturedPlayer.distanceTo(scene.feet()) < .12, "A stationary portal-view fixture moved its observer");
    assertion.accept(frame);
    var entry = new LinkedHashMap<String, Object>(frame.metadata());
    entry.put("case", spec().id); entry.put("checkpoint", name(suffix));
    entry.put("entrance", scene.entrance().toString()); entry.put("exit", scene.exit().toString());
    entry.put("generation", scene.generation());
    entry.put("hudHidden", capturedState.get("hudHidden"));
    entry.put("renderedHudHidden", capturedState.get("renderedHudHidden"));
    if (capturedState.containsKey("clipping")) {
      entry.put("clipping", capturedState.get("clipping"));
      entry.put("sentinelBlocks", capturedState.get("sentinelBlocks"));
      entry.put("sentinelLighting", capturedState.get("sentinelLighting"));
    }
    entry.put("portalTerrain", capturedState.get("portalTerrain")); entry.put("markerBlocks", capturedState.get("markers"));
    if (capturedState.containsKey("materialBlocks")) {
      entry.put("materialBlocks", capturedState.get("materialBlocks"));
      entry.put("terrainUploads", capturedState.get("terrainUploads"));
    }
    if (actorBounds != null) entry.put("actorBounds", actorBounds.toString());
    proof.add(entry);
    return true;
  }

  private boolean settledMaterial(BooleanSupplier ready) {
    if (!ready.getAsBoolean()) {
      settlingAnchor = null;
      return false;
    }
    if (settlingSample == null) {
      if (FrameCapture.canCapture()) settlingSample = FrameCapture.captureSample(client);
      return false;
    }
    if (!settlingSample.isDone()) return false;
    var sample = settlingSample.join();
    settlingSample = null;
    if (settlingAnchor == null) {
      settlingAnchor = sample;
      return false;
    }
    sameCamera(sample, settlingAnchor);
    // Mesh completion does not settle shader history, exposure or translucent reflections.
    // Settle the chromatic measurement, while allowing water texture and brightness animation.
    for (int marker = 0; marker < 2; marker++) {
      var change = chromaChange(sample, settlingAnchor, markerInterior(sample, marker), 0);
      if (Math.abs(change.meanChange()) > 1) {
        settlingAnchor = sample;
        return false;
      }
    }
    return sample.playerTick() - settlingAnchor.playerTick() >= 3;
  }

  private Map<String, Object> clientState() {
    var state = new LinkedHashMap<String, Object>();
    state.put("player", client.player.position().toString());
    state.put("camera", client.gameRenderer.mainCamera().position().toString());
    state.put("hudHidden", client.gui.hud.isHidden());
    state.put("renderedHudHidden", client.gameRenderer.gameRenderState().guiRenderState.isHudHidden);
    state.put("velocity", client.player.getDeltaMovement().toString());
    state.put("flying", client.player.getAbilities().flying);
    state.put("nativeButtons", Map.of("forward", client.options.keyUp.isDown(), "backward", client.options.keyDown.isDown(),
        "left", client.options.keyLeft.isDown(), "right", client.options.keyRight.isDown(),
        "jump", client.options.keyJump.isDown(), "sneak", client.options.keyShift.isDown()));
    state.put("mainTerrainReady", client.levelRenderer.hasRenderedAllSections());
    if (scene != null) {
      state.put("entrance", scene.entrance().toString()); state.put("exit", scene.exit().toString());
      state.put("generation", scene.generation());
      state.put("authoredPlayer", scene.feet().toString());
      state.put("observerPositionError", client.player.position().subtract(scene.feet()).toString());
      state.put("markers", scene.markers().stream().map(marker -> {
        var position = BlockPos.containing(marker.bounds().getCenter());
        return Map.of("material", marker.material().name(), "position", position.toString(),
            "bounds", marker.bounds().toString(), "state", client.level.getBlockState(position).toString());
      }).toList());
    }
    if (sourceLink != null) state.put("portalTerrain", PortalViewTerrainReadiness.diagnostics(sourceLink, scene.markers()));
    if (sentinel != null) {
      state.put("sentinelBlocks", sentinel.blocks().stream().map(position ->
          Map.of("position", position.toString(), "state", client.level.getBlockState(position).toString())).toList());
      state.put("sentinelLighting", sentinel.lightingState(client.level));
    }
    if (obstacle != null) {
      state.put("materialBounds", obstacle.toString());
      state.put("materialBlocks", expectedMaterial.entrySet().stream().map(entry -> Map.of(
          "position", entry.getKey().toString(), "expected", entry.getValue().toString(),
          "actual", client.level.getBlockState(entry.getKey()).toString())).toList());
      if (uploads != null) state.put("terrainUploads", uploads.diagnostics());
    }
    return state;
  }

  private void failureDiagnostics(Throwable failure) {
    if (diagnostics != null) return;
    var state = new LinkedHashMap<String, Object>();
    state.put("failure", failure.toString()); state.put("case", spec().id);
    if (!transmissions.isEmpty()) state.put("transmissions", transmissions);
    state.put("phase", phase.name()); state.put("action", action);
    if (capturedState != null) state.put("beforeBlit", capturedState);
    state.put("currentClient", clientState());
    diagnostics = client.getSingleplayerServer().submit(() -> {
      if (fixture != null) state.put("server", fixture.diagnostics());
      try {
        Files.createDirectories(frames.directory());
        Files.writeString(frames.directory().resolve("portal-views-failure.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(state));
      } catch (IOException writeFailure) { throw new UncheckedIOException(writeFailure); }
    });
  }

  private static void sameCamera(FrameCapture.Frame current, FrameCapture.Frame previous) {
    check(current.cameraPosition().distanceTo(previous.cameraPosition()) < .002
        && Math.abs(current.cameraPitch() - previous.cameraPitch()) < .05
        && Math.abs(current.cameraYaw() - previous.cameraYaw()) < .05, "Remote material comparison changed the actual camera");
  }

  private Map<String, Object> clippingProof(FrameCapture.Frame frame) {
    Vec3 virtualEye = scene.exitPoint(frame.cameraPosition());
    Vec3 normal = scene.exit().normal();
    double eyeDepth = virtualEye.subtract(scene.exit().center()).dot(normal);
    double minimumDepth = Double.POSITIVE_INFINITY, maximumDepth = Double.NEGATIVE_INFINITY;
    AABB bounds = sentinel.bounds();
    for (int corner = 0; corner < 8; corner++) {
      Vec3 point = new Vec3((corner & 1) == 0 ? bounds.minX : bounds.maxX,
          (corner & 2) == 0 ? bounds.minY : bounds.maxY, (corner & 4) == 0 ? bounds.minZ : bounds.maxZ);
      double depth = point.subtract(scene.exit().center()).dot(normal);
      minimumDepth = Math.min(minimumDepth, depth); maximumDepth = Math.max(maximumDepth, depth);
    }
    check(eyeDepth + PortalAperture.PLANE_EPSILON < minimumDepth && maximumDepth < -PortalAperture.PLANE_EPSILON,
        "The clipping sentinel is not wholly between the virtual camera and exit plane: " + eyeDepth
            + " / " + minimumDepth + ".." + maximumDepth);
    var projected = frame.project(scene.imageBounds(bounds)).orElseThrow(
        () -> new AssertionError("The actual clipping sentinel is behind the observing camera"));
    var core = PortalSceneAssertions.aperture(frame, scene.entrance(), .2);
    check(PortalSceneAssertions.intersection(projected, core).equals(core),
        "The real clipping sentinel does not cover the observed portal core without clipping");
    var pixels = PortalSceneAssertions.pixels(frame, core, FrameCapture.Material.MAGENTA, scene.entrance());
    check(pixels.fraction() <= .005, "The exit clip plane leaked native magenta blocks into the world image: " + pixels);
    return Map.of("virtualEye", virtualEye, "eyeDepth", eyeDepth, "sentinelBounds", bounds,
        "minimumDepth", minimumDepth, "maximumDepth", maximumDepth, "projectedRegion", projected,
        "apertureRegion", core, "clearCoreSamples", pixels.samples(), "magentaPixels", pixels.matched(),
        "maximumMagentaFraction", .005);
  }

  private void writeProof() {
    try {
      Files.writeString(frames.directory().resolve("portal-views-proof.json"), new GsonBuilder().setPrettyPrinting()
          .create().toJson(Map.of("cases", passed, "frames", proof, "transmissions", transmissions)));
    } catch (IOException failure) { throw new UncheckedIOException(failure); }
  }

  @Override public CompletableFuture<Void> cleanup() {
    input.stop(); client.options.fov().set(previousFov); client.options.setCameraType(previousPerspective);
    if (client.gui.hud.isHidden() != previousHidden) client.gui.hud.toggle();
    if (parallax != null) parallax.close();
    if (uploads != null) uploads.close();
    CompletableFuture<Void> pending = CompletableFuture.allOf(Stream.of(setup, blocker, mutation, spawn, actorMove, nestedSetup, diagnostics, settlingSample)
        .filter(Objects::nonNull).toArray(CompletableFuture<?>[]::new));
    return pending.handle((ignored, failure) -> null).thenCompose(ignored -> client.getSingleplayerServer().submit(() -> {
      if (fixture != null) fixture.close();
    }));
  }

  private Case spec() { return CASES.get(index); }
  private String name(String suffix) { return "view-" + spec().id + "-" + suffix; }
  private void next(Phase value) { input.stop(); phase = value; age = 0; }
  private static Case orientation(String id, Direction entry, Direction exit, float roll) {
    return new Case(id, Kind.ORIENTATION, entry, exit, roll);
  }
  private static Case effect(String id, Kind kind) {
    return new Case(id, kind, Direction.SOUTH, Direction.SOUTH, 0);
  }
}
