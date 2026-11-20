package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.portal.clip.PortalTerrainWindow;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalTerrainGraphAccess;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.test.client.mixin.PortalRenderSceneAccessor;
import dev.portalix.test.client.mixin.PortalSceneManagerAccessor;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

final class PortalViewRangeCase {
  private enum Step { REFERENCE, POSITION, OPEN, ENTER, RESTORE, RETURN, REENTER, HOT_HOLD, LEAVE, CONTRACT, DRAIN, DONE }

  private final Minecraft client;
  private final ScenarioFrames frames;
  private final PortalSystemFixture fixture;
  private final PortalSystemFixture.RangeCourse course;
  private Step step = Step.REFERENCE;
  private CompletableFuture<FrameCapture.Frame> capture;
  private CompletableFuture<Void> mutation;
  private final ScenarioInput input;
  private PortalLink route;
  private long crossingId;
  private int crossingCount;
  private int enteredAt;
  private int restoredFrames;
  private int expandingFrames;
  private final double sectionFade;
  private int coldFrames;
  private int hotFrames;
  private int departureFrames;
  private int contractingFrames;
  private int contractedFrames;
  private int latestColdTick;
  private int latestHotTick;
  private int reversedAt;
  private int returnInterval;
  private double referenceFar;
  private double referenceEdge;
  private double observedEdge;
  private int presented;
  private int openFrames;
  private boolean armed;
  private int referenceTicks;
  private final TreeSet<Integer> vanillaGraphRadii = new TreeSet<>();

  PortalViewRangeCase(Minecraft client, ScenarioFrames frames, PortalSystemFixture fixture,
                        PortalSystemFixture.RangeCourse course) {
    this.client = client;
    sectionFade = client.options.chunkSectionFadeInTime().get();
    input = new ScenarioInput(client);
    this.frames = frames;
    this.fixture = fixture;
    this.course = course;
  }

  boolean tick() {
    var view = course.view();
    switch (step) {
      case REFERENCE -> {
        if (client.player.position().distanceTo(course.referenceFeet()) > .1
            || !resident() || !client.levelRenderer.hasRenderedAllSections()
            || !client.levelRenderer.isSectionCompiledAndVisible(BlockPos.containing(course.edge().getCenter()))
            || !client.levelRenderer.isSectionCompiledAndVisible(BlockPos.containing(course.far().getCenter()))) {
          referenceTicks = 0;
          return false;
        }
        if (++referenceTicks < 2 + Math.ceil(client.options.chunkSectionFadeInTime().get() * 20)) return false;
        if (capture == null) {
          capture = frames.checkpoint("range-native-reference");
          return false;
        }
        if (!capture.isDone()) return false;
        var reference = capture.join();
        var far = region(reference, course.far());
        var edge = region(reference, course.edge());
        check(chroma(reference, far) > 20 && chroma(reference, edge) > 20,
            "Native reference cannot resolve both magenta panels: far=" + chroma(reference, far)
                + ", edge=" + chroma(reference, edge));
        referenceFar = chroma(reference, far);
        referenceEdge = edgeContrast(reference, false);
        capture = null;
        mutation = client.getSingleplayerServer().submit(() -> fixture.teleport(view.feet(), view.aim()));
        step = Step.POSITION;
      }
      case POSITION -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        if (client.player.position().distanceTo(view.feet()) > .1 || !resident()
            || PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId()))) return false;
        client.options.chunkSectionFadeInTime().set(0.0);
        frames.setTerrainCheck(this::terrainCheck);
        frames.monitor(true);
        mutation = client.getSingleplayerServer().submit(fixture::openRangeCourse);
        step = Step.OPEN;
      }
      case OPEN -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        if (capture == null) {
          capture = frames.reservedCheckpoint("range-open-preview", () -> {
            var candidate = entrance();
            return candidate != null && PortalOpeningReadiness.fullyOpen(candidate) && PortalViewTerrainReadiness.complete(candidate);
          });
          return false;
        }
        if (!capture.isDone() || openFrames < 24) return false;
        capture.join();
        route = entrance();
        crossingId = EntityPortalState.of(client.player).crossingSequence();
        input.forward(true);
        step = Step.ENTER;
      }
      case ENTER -> {
        if (crossed(route)) { input.stop(); step = Step.RESTORE; }
      }
      case RESTORE -> {
        if (restoredFrames < 10 || latestColdTick < PortalViewTransition.LOAD_DELAY_TICKS + 20) return false;
        client.options.keyDown.setDown(true);
        step = Step.RETURN;
      }
      case RETURN -> {
        if (!crossed(route.inverse())) return false;
        reversedAt = client.player.tickCount;
        input.stop();
        input.forward(true);
        step = Step.REENTER;
      }
      case REENTER -> {
        if (!crossed(route)) return false;
        input.stop();
        returnInterval = client.player.tickCount - reversedAt;
        check(returnInterval < 20, "The return to recently visible outer terrain was not rapid: " + returnInterval);
        step = Step.HOT_HOLD;
      }
      case HOT_HOLD -> {
        if (latestHotTick < PortalViewTransition.LOAD_DELAY_TICKS) return false;
        client.options.keyDown.setDown(true);
        step = Step.LEAVE;
      }
      case LEAVE -> {
        if (!crossed(route.inverse())) return false;
        input.stop();
        step = Step.CONTRACT;
      }
      case CONTRACT -> {
        if (contractedFrames < 10) return false;
        frames.setTerrainCheck(null);
        client.options.chunkSectionFadeInTime().set(sectionFade);
        step = Step.DRAIN;
      }
      case DRAIN -> {
        if (!frames.pause()) return false;
        step = Step.DONE;
      }
      case DONE -> {}
    }
    return step == Step.DONE;
  }

  private boolean crossed(PortalLink expected) {
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    if (crossing == null || crossing.eventId() == crossingId) return false;
    check(crossing.eventId() == crossingId + 1 && expected.equals(crossing.link()), "Unexpected route or extra passage");
    crossingId = crossing.eventId();
    crossingCount++;
    enteredAt = client.player.tickCount;
    return true;
  }

  private PortalLink entrance() {
    return PortalTopology.links(client.level).stream().filter(candidate -> candidate.gun().equals(fixture.gunId())
        && candidate.entrance().endpoint().equals(course.view().entrance())).findFirst().orElse(null);
  }

  private Consumer<FrameCapture.Frame> terrainCheck() {
    var view = course.view();
    PortalLink link = entrance();
    boolean open = link != null && PortalOpeningReadiness.fullyOpen(link) && PortalSceneManager.hasRenderedView(link);
    boolean previewCaptured = step == Step.OPEN && capture != null && capture.isDone();
    boolean walking = step != Step.OPEN && step != Step.POSITION;
    int count = crossingCount, elapsed = client.player.tickCount - enteredAt;
    if (PortalTerrainBackend.usesVanillaChunkEvents()) {
      if (open && !walking) {
        var scene = (PortalRenderSceneAccessor) PortalSceneManagerAccessor.portalix$scenes().get(List.of(link));
        if (scene != null) checkGraphBounds(scene.portalix$renderer(), PortalViewLimits.preview(
            client.options.getEffectiveRenderDistance()));
      } else if (count == 1 && elapsed >= PortalViewTransition.LOAD_DELAY_TICKS) {
        checkGraphBounds(client.levelRenderer, client.options.getEffectiveRenderDistance());
      }
    }
    check(resident(), "The range test lost the far panel from native chunk storage");
    return frame -> {
      boolean destination = frame.cameraPosition().distanceToSqr(view.exit().center())
          < frame.cameraPosition().distanceToSqr(view.entrance().center());
      var far = region(frame, destination ? course.far() : view.imageBounds(course.far()));
      double distantChroma = chroma(frame, far);
      boolean held = elapsed < PortalViewTransition.LOAD_DELAY_TICKS;
      if (count == 0 || count == 1 && destination && held)
        check(distantChroma < referenceFar * .28, "Cold preview leaked far terrain in frame " + frame.id());
      if (count == 2 || count == 3 || count == 4 && held)
        check(distantChroma > referenceFar * .55, "Recently visible far terrain vanished in frame "
            + frame.id() + ", destination=" + destination + ", crossing=" + count + ", elapsed=" + elapsed
            + ", chroma=" + distantChroma + "/" + referenceFar);
      presented++;
      if (!walking && !open) return;
      var near = region(frame, destination ? course.near() : view.imageBounds(course.near()));
      boolean visible = frame.statistics(near, FrameCapture.Material.GREEN).fraction() > .5;
      if (!armed && !visible) {
        check(!previewCaptured, "Compiled portal terrain lacks the near green panel at " + near
            + " in presented frame " + frame.id() + ", center pixel "
            + Integer.toHexString(frame.pixel(near.x() + near.width() / 2, near.y() + near.height() / 2)));
        return;
      }
      armed = true;
      check(visible, "The near terrain vanished in frame " + frame.id() + ", destination=" + destination);
      if (!walking) {
        observedEdge = edgeContrast(frame, true);
        check(observedEdge > referenceEdge * .025 && observedEdge < referenceEdge * .75,
            "The fog must retain an attenuated edge marker: "
            + observedEdge + " versus " + referenceEdge + ", frame " + frame.id());
        openFrames++;
      } else if (count == 1 && destination) {
        latestColdTick = Math.max(latestColdTick, elapsed);
        if (held) coldFrames++;
        else if (distantChroma > referenceFar * .75) restoredFrames++;
        else if (distantChroma > referenceFar * .28) expandingFrames++;
      } else if (count == 3 && destination) {
        latestHotTick = Math.max(latestHotTick, elapsed);
        if (held) hotFrames++;
      } else if (count == 4 && !destination) {
        if (held) departureFrames++;
        else if (elapsed >= PortalViewTransition.LOAD_DELAY_TICKS + 20) {
          check(distantChroma < referenceFar * .28, "Far terrain remained visible after contraction");
          contractedFrames++;
        } else if (distantChroma >= referenceFar * .28 && distantChroma <= referenceFar * .55) contractingFrames++;
      }
    };
  }

  private void checkGraphBounds(LevelRenderer renderer, int radius) {
    if (vanillaGraphRadii.contains(radius)) return;
    var graph = renderer.sectionOcclusionGraph();
    var access = (PortalTerrainGraphAccess) graph;
    var snapshot = access.portalix$visibility();
    if (snapshot == null || snapshot.radius() != radius) return;
    var tree = graph.getOctree();
    if (snapshot != access.portalix$visibility()) return;

    var window = new PortalTerrainWindow(snapshot.center(), snapshot.radius());
    var frustum = new Frustum(new Matrix4f(), new Matrix4f().ortho(-2048, 2048, -2048, 2048, -2048, 2048));
    var center = snapshot.center().center();
    frustum.prepare(center.getX(), center.getY(), center.getZ());
    tree.visitNodes((node, fullyVisible, depth, close) -> {
      var section = node.getSection();
      if (section == null) return;
      var position = SectionPos.of(section.getSectionNode());
      check(window.containsOffset(position.x() - snapshot.center().x(), position.y() - snapshot.center().y(),
          position.z() - snapshot.center().z()), "Vanilla graph radius " + radius + " contains " + position
          + " outside its bounds around " + snapshot.center());
    }, frustum, 32);
    vanillaGraphRadii.add(radius);
  }

  Map<String, Object> state() {
    return Map.of("step", step, "frames", presented, "openFrames", openFrames, "crossings", crossingCount,
        "coldFrames", coldFrames, "restoredFrames", restoredFrames, "hotFrames", hotFrames);
  }

  Map<String, Object> verifyAndReport() {
    check(armed && openFrames >= 24 && presented >= openFrames, "The portal was never positively observed");
    check(crossingCount == 4 && coldFrames > 0 && latestColdTick >= PortalViewTransition.LOAD_DELAY_TICKS && restoredFrames >= 10
        && hotFrames > 0 && latestHotTick >= PortalViewTransition.LOAD_DELAY_TICKS, "Missing cold, restored or hot observations");
    check(departureFrames > 0 && contractingFrames > 0 && contractedFrames >= 10,
        "Missing held, fading or fully fogged far terrain after departure");
    check(expandingFrames > 0, "The resident far panel reappeared without any partially fogged frames");
    if (PortalTerrainBackend.usesVanillaChunkEvents()) {
      int distance = client.options.getEffectiveRenderDistance();
      check(vanillaGraphRadii.containsAll(List.of(PortalViewLimits.preview(distance), distance)),
          "Missing published vanilla graph radii for the transition to " + distance + ": " + vanillaGraphRadii);
    }
    return Map.ofEntries(Map.entry("expandingFrames", expandingFrames), Map.entry("case", "resident-terrain-range"),
        Map.entry("departureFrames", departureFrames), Map.entry("contractingFrames", contractingFrames),
        Map.entry("contractedFrames", contractedFrames), Map.entry("auditedFrames", presented), Map.entry("openFrames", openFrames), Map.entry("coldHeldFrames", coldFrames),
        Map.entry("restoredFrames", restoredFrames), Map.entry("hotHeldFrames", hotFrames),
        Map.entry("hotReturnTicks", returnInterval), Map.entry("nativeEdgeContrast", referenceEdge),
        Map.entry("portalEdgeContrast", observedEdge), Map.entry("vanillaGraphRadii", List.copyOf(vanillaGraphRadii)));
  }

  private boolean resident() {
    return client.level.getBlockState(BlockPos.containing(course.far().getCenter())).is(Blocks.CONCRETE.pick(DyeColor.MAGENTA))
        && client.level.getBlockState(BlockPos.containing(course.edge().getCenter())).is(Blocks.CONCRETE.pick(DyeColor.MAGENTA))
        && client.level.getBlockState(BlockPos.containing(course.near().getCenter())).is(Blocks.CONCRETE.pick(DyeColor.LIME));
  }

  static FrameCapture.Region region(FrameCapture.Frame frame, AABB panel) {
    var area = frame.project(panel).orElseThrow(() -> new AssertionError("Range panel outside the camera: " + panel));
    int dx = area.width() / 4, dy = area.height() / 4;
    var interior = new FrameCapture.Region(area.x() + dx, area.y() + dy, area.width() - 2 * dx, area.height() - 2 * dy);
    check(interior.area() >= 4, "Too few screen pixels to distinguish the range panel: " + interior);
    return interior;
  }

  private double edgeContrast(FrameCapture.Frame frame, boolean throughPortal) {
    var panel = course.edge();
    var sky = new AABB(panel.maxX + .5, panel.minY, panel.minZ,
        panel.maxX + 3.5, panel.maxY, panel.maxZ);
    var edge = region(frame, throughPortal ? course.view().imageBounds(panel) : panel);
    var background = region(frame, throughPortal ? course.view().imageBounds(sky) : sky);
    return redMinusGreen(frame, edge) - redMinusGreen(frame, background);
  }

  private static double redMinusGreen(FrameCapture.Frame frame, FrameCapture.Region region) {
    long sum = 0;
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int c = frame.pixel(x, y);
        sum += (c >>> 16 & 255) - (c >>> 8 & 255);
      }
    return (double) sum / region.area();
  }

  private static double chroma(FrameCapture.Frame frame, FrameCapture.Region region) {
    long sum = 0;
    for (int y = region.y(); y < region.y() + region.height(); y++)
      for (int x = region.x(); x < region.x() + region.width(); x++) {
        int c = frame.pixel(x, y);
        sum += Math.max(0, Math.min(c >>> 16 & 255, c & 255) - (c >>> 8 & 255));
      }
    return (double) sum / region.area();
  }
}
