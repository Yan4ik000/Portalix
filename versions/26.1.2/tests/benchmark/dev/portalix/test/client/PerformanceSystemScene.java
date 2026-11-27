package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.test.client.mixin.PortalRenderSceneAccessor;
import dev.portalix.test.client.mixin.PortalSceneManagerAccessor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

final class PerformanceSystemScene implements ClientSystemScene {
  private enum Step { INITIAL, BASELINE_BEFORE, BASELINE_DRAIN, BASELINE, PREPARE, EQUIP, BEFORE, FIRE, OPEN, WALK, SETTLE, STEADY, AFTER, WARM_LOOK, WARM_RETURN, WARM_FORWARD, WARM_SETTLE, WARM_STEADY, DONE }
  private record Sample(long nanos, double milliseconds, int hop, String phase, Vec3 position,
                        boolean remoteImage, boolean terrainBuildQueuesDrained, boolean destinationCoreReady,
                        boolean destinationCamera, boolean bodyCrossed, long renderFrame, int clientTick) {}
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final List<String> passed = new ArrayList<>();
  private final List<Sample> samples = new ArrayList<>();
  private final List<Map<String, Object>> legs = new ArrayList<>();
  private final List<Map<String, Object>> warmPasses = new ArrayList<>();
  private final java.util.UUID playerId;
  private CompletableFuture<PerformanceWorldFixture> creation;
  private CompletableFuture<PerformanceWorldFixture.Leg> preparation;
  private CompletableFuture<Boolean> agreement;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private PerformanceWorldFixture fixture;
  private PerformanceWorldFixture.Leg leg;
  private Step step = Step.INITIAL;
  private int age;
  private int hop;
  private long previousFrame;
  private long firedNanos;
  private long openedNanos;
  private long crossedNanos;
  private long settledNanos;
  private long firstRemoteImageNanos;
  private long firstTerrainQueuesDrainedNanos;
  private long firstDestinationCoreReadyNanos;
  private long firstEyePathNanos;
  private long firstDestinationCameraNanos;
  private long firstBodyPresentedNanos;
  private int firedTick, settledTick;
  private PortalLink openedLink;
  private long preparationStarted;
  private double preparationMilliseconds;
  private boolean measuring;
  private PortalLink warmLink;
  private boolean warmReturning;
  private CompletableFuture<Long> warmServerBefore;
  private CompletableFuture<Map<String, Object>> warmServerCrossing;
  private long warmClientBefore;
  private long warmMovementNanos;
  private long warmCrossedNanos;
  private Map<String, Object> warmArrivalPresentation;
  private boolean warmAligned;

  PerformanceSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    input = new ScenarioInput(client);
    playerId = client.player.getUUID();
  }

  @Override
  public boolean tick() {
    check(++age < 2400, "Performance route timed out at hop " + hop + "/" + step
        + ", position=" + client.player.position());
    var server = client.getSingleplayerServer();
    check(PortalTopology.links(client.level).size() <= 2,
        "The performance route opened more than one portal pair");
    if (warmLink != null)
      check(PortalTopology.links(client.level).contains(warmLink)
              && PortalTopology.links(client.level).contains(warmLink.inverse()),
          "The measured same-pair return replaced or closed its retained portal pair");
    switch (step) {
      case INITIAL -> {
        if (creation == null) creation = server.submit(() ->
            new PerformanceWorldFixture(server.getPlayerList().getPlayer(playerId)));
        if (!creation.isDone()) return false;
        fixture = creation.join();
        if (client.player.getX() < 4000 || !client.player.onGround()) return false;
        next(Step.BASELINE_BEFORE);
      }
      case BASELINE_BEFORE -> {
        if (!image("performance-baseline")) return false;
        next(Step.BASELINE_DRAIN);
      }
      case BASELINE_DRAIN -> {
        if (!frames.pause()) return false;
        measure(true);
        next(Step.BASELINE);
      }
      case BASELINE -> {
        if (age < 160) return false;
        measure(false);
        check(samples.stream().anyMatch(sample -> sample.hop == -1),
            "performance/baseline produced no measured frames");
        passed.add("performance/normal generated world and high graphics baseline");
        next(Step.PREPARE);
      }
      case PREPARE -> {
        if (!frames.pause()) return false;
        if (preparation == null) {
          preparationStarted = FrameCapture.monotonicNanos();
          preparation = server.submit(() -> fixture.prepare(hop));
        }
        if (!preparation.isDone()) return false;
        leg = preparation.join(); preparation = null;
        preparationMilliseconds = (FrameCapture.monotonicNanos() - preparationStarted) / 1e6;
        next(Step.EQUIP);
      }
      case EQUIP -> {
        if (!GunEquipReadiness.ready(client, leg.gun(), PortalixVariant.STANDARD)) return false;
        if (!input.lookAt(leg.source().aim())) return false;
        next(Step.BEFORE);
      }
      case BEFORE -> {
        if (!image(String.format(java.util.Locale.ROOT, "performance-hop-%02d-before", hop))) return false;
        next(Step.FIRE);
      }
      case FIRE -> {
        if (!frames.pause()) return false;
        measure(true);
        firedNanos = FrameCapture.monotonicNanos();
        firedTick = client.player.tickCount;
        openedNanos = crossedNanos = settledNanos = 0;
        firstRemoteImageNanos = firstTerrainQueuesDrainedNanos = firstDestinationCoreReadyNanos = 0;
        firstEyePathNanos = firstDestinationCameraNanos = firstBodyPresentedNanos = 0;
        openedLink = null;
        input.click(client.options.keyAttack);
        next(Step.OPEN);
      }
      case OPEN -> {
        observeOpening();
        if (age < (stationaryOpening() ? 60 : 10)) return false;
        input.forward(true);
        next(Step.WALK);
      }
      case WALK -> {
        observeOpening();
        var crossing = EntityPortalState.of(client.player).lastCrossing();
        if (crossing == null || !crossing.link().gun().equals(leg.gun())
            || crossing.link().entrance().endpoint().center().distanceToSqr(leg.source().aim()) >= 1)
          return false;
        crossedNanos = FrameCapture.monotonicNanos();
        input.stop();
        next(Step.SETTLE);
      }
      case SETTLE -> {
        if (age >= 10) input.lookAt(client.player.getEyePosition().add(20, 0, 0));
        if (agreement == null) agreement = server.submit(() -> {
          var player = server.getPlayerList().getPlayer(playerId);
          var crossing = EntityPortalState.of(player).lastCrossing();
          return crossing != null && crossing.link().gun().equals(leg.gun())
              && crossing.link().entrance().endpoint().center().distanceToSqr(leg.source().aim()) < 1
              && player.position().distanceTo(leg.destination().feet()) < 6;
        });
        if (!agreement.isDone()) return false;
        boolean confirmed = agreement.join(); agreement = null;
        if (!confirmed || !client.player.onGround()
            || client.player.position().distanceTo(leg.destination().feet()) > 6 || age < 40) return false;
        settledNanos = FrameCapture.monotonicNanos();
        settledTick = client.player.tickCount;
        next(Step.STEADY);
      }
      case STEADY -> {
        if (age < 60) return false;
        measure(false);
        recordLeg();
        next(Step.AFTER);
      }
      case AFTER -> {
        if (!image(String.format(java.util.Locale.ROOT, "performance-hop-%02d-after", hop))) return false;
        passed.add(String.format(java.util.Locale.ROOT,
            "performance/hop-%02d native shot and authoritative distant traversal", hop));
        if (hop + 1 == PerformanceWorldFixture.LOCATIONS * 2) next(Step.WARM_LOOK);
        else { hop++; next(Step.PREPARE); }
      }
      case WARM_LOOK -> {
        if (warmLink == null) {
          if (!frames.pause()) return false;
          beginWarmPass(true);
          age = 0;
        }
        warmAligned = input.lookAt(warmLink.entrance().endpoint().center());
        if (age < 80) return false;
        check(warmAligned && warmServerBefore.isDone(), "Same-pair return did not aim at its live entrance in the fixed look interval");
        warmMovementNanos = FrameCapture.monotonicNanos();
        input.forward(true);
        next(warmReturning ? Step.WARM_RETURN : Step.WARM_FORWARD);
      }
      case WARM_RETURN, WARM_FORWARD -> {
        var crossing = EntityPortalState.of(client.player).lastCrossing();
        if (warmCrossedNanos == 0 && crossing != null && crossing.eventId() > warmClientBefore
            && crossing.link().equals(warmLink)) {
          warmCrossedNanos = FrameCapture.monotonicNanos();
          input.stop();
        }
        if (age < 60) return false;
        input.stop();
        check(warmCrossedNanos != 0, "The retained same-pair native movement did not cross within its fixed interval");
        long previousServerEvent = warmServerBefore.join();
        var expected = warmLink;
        warmServerCrossing = server.submit(() -> {
          var player = server.getPlayerList().getPlayer(playerId);
          var event = EntityPortalState.of(player).lastCrossing();
          boolean confirmed = event != null && event.eventId() > previousServerEvent
              && event.link().equals(expected) && player.position().distanceTo(expected.exit().endpoint().center()) < 6;
          return Map.<String, Object>of("confirmed", confirmed, "eventId", event == null ? -1L : event.eventId(),
              "previousEventId", previousServerEvent, "nanos", FrameCapture.monotonicNanos(),
              "position", player.position(), "dimension", player.level().dimension().toString(),
              "entranceIdentity", event == null ? -1L : event.link().entrance().identity(),
              "exitIdentity", event == null ? -1L : event.link().exit().identity());
        });
        next(Step.WARM_SETTLE);
      }
      case WARM_SETTLE -> {
        if (age < 40) return false;
        check(warmServerCrossing.isDone() && (boolean) warmServerCrossing.join().get("confirmed"),
            "The retained same-pair crossing was not server-authoritative within the fixed settle interval");
        check(client.player.onGround() && client.player.position().distanceTo(warmLink.exit().endpoint().center()) < 6,
            "The retained same-pair observer did not settle at the real exit");
        next(Step.WARM_STEADY);
      }
      case WARM_STEADY -> {
        if (age < 60) return false;
        recordWarmPass();
        if (warmReturning) {
          beginWarmPass(false);
          next(Step.WARM_LOOK);
        } else {
          measure(false);
          passed.add("performance/retained same-pair inverse and forward authoritative traversals");
          writeReport();
          next(Step.DONE);
        }
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  @Override
  public void presentedFrame(long nanos) {
    if (!measuring) return;
    if (warmLink != null) { presentedWarmFrame(nanos); return; }
    String phase = step == Step.OPEN && stationaryOpening() && age >= 40 ? "PORTAL_STEADY" : step.name();
    if (leg != null && step != Step.BASELINE) observeOpening();
    boolean remoteImage = leg != null && openedLink != null
        && PortalSceneManager.portalImage(openedLink.entrance().endpoint()) != null;
    boolean queuesDrained = false;
    boolean coreReady = false;
    if (openedLink != null) {
      for (var entry : PortalSceneManagerAccessor.portalix$scenes().entrySet()) {
        if (!entry.getKey().equals(List.of(openedLink))) continue;
        var scene = (PortalRenderSceneAccessor) entry.getValue();
        if (!scene.portalix$rendered()) continue;
        queuesDrained = scene.portalix$renderer().hasRenderedAllSections();
        coreReady = queuesDrained && leg.destination().terrainProbes().stream().allMatch(position -> {
          var chunk = client.level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4,
              ChunkStatus.FULL, false);
          return chunk != null && (chunk.getSection(chunk.getSectionIndex(position.getY())).hasOnlyAir()
              || scene.portalix$renderer().isSectionCompiledAndVisible(position));
        });
      }
      if (remoteImage && firstRemoteImageNanos == 0) firstRemoteImageNanos = nanos;
      if (queuesDrained && firstTerrainQueuesDrainedNanos == 0) firstTerrainQueuesDrainedNanos = nanos;
      if (coreReady && firstDestinationCoreReadyNanos == 0) firstDestinationCoreReadyNanos = nanos;
    }
    var view = PortalCameraController.currentView();
    if (openedLink != null && view != null && view.spacePath().contains(openedLink) && firstEyePathNanos == 0)
      firstEyePathNanos = nanos;
    var camera = FrameCapture.worldCamera();
    boolean destinationCamera = leg != null && camera != null
        && camera.position().distanceToSqr(leg.destination().feet()) < 128 * 128;
    if (destinationCamera && firstDestinationCameraNanos == 0) firstDestinationCameraNanos = nanos;
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    boolean bodyCrossed = openedLink != null && crossing != null && crossing.link().equals(openedLink);
    if (bodyCrossed && firstBodyPresentedNanos == 0) firstBodyPresentedNanos = nanos;
    if (previousFrame != 0) samples.add(new Sample(nanos, (nanos - previousFrame) / 1_000_000.0,
        step == Step.BASELINE ? -1 : hop,
        phase,
        client.player.position(), remoteImage, queuesDrained, coreReady, destinationCamera, bodyCrossed, FrameCapture.renderFrame(), client.player.tickCount));
    previousFrame = nanos;
  }

  private boolean stationaryOpening() { return hop % PerformanceWorldFixture.LOCATIONS % 2 == 1; }

  private String warmPrefix() { return warmReturning ? "WARM_RETURN" : "WARM_FORWARD"; }

  private void beginWarmPass(boolean returning) {
    input.stop();
    warmReturning = returning;
    warmLink = returning ? openedLink.inverse() : openedLink;
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    warmClientBefore = crossing == null ? -1 : crossing.eventId();
    var server = client.getSingleplayerServer();
    warmServerBefore = server.submit(() -> {
      var event = EntityPortalState.of(server.getPlayerList().getPlayer(playerId)).lastCrossing();
      return event == null ? -1L : event.eventId();
    });
    warmMovementNanos = warmCrossedNanos = 0;
    warmArrivalPresentation = null;
    warmServerCrossing = null;
    if (!measuring) measure(true);
  }

  private void presentedWarmFrame(long nanos) {
    int sampleHop = PerformanceWorldFixture.LOCATIONS * 2 + warmPasses.size();
    String prefix = warmPrefix();
    String phase = switch (step) {
      case WARM_LOOK -> prefix + (age >= 60 ? "_PORTAL_STEADY" : "_LOOK");
      case WARM_SETTLE -> prefix + "_SETTLE";
      case WARM_STEADY -> prefix + "_STEADY";
      default -> prefix;
    };
    var camera = FrameCapture.worldCamera();
    boolean arrival = camera != null
        && camera.position().distanceToSqr(warmLink.exit().endpoint().center()) < 128 * 128;
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    boolean body = crossing != null && crossing.eventId() > warmClientBefore && crossing.link().equals(warmLink);
    boolean remoteImage = PortalSceneManager.portalImage(warmLink.entrance().endpoint()) != null;
    if (body && arrival && warmArrivalPresentation == null)
      warmArrivalPresentation = Map.of("nanos", nanos, "renderFrame", FrameCapture.renderFrame(),
          "clientEventId", crossing.eventId(), "position", camera.position());
    if (previousFrame != 0) samples.add(new Sample(nanos, (nanos - previousFrame) / 1e6, sampleHop,
        phase, client.player.position(), remoteImage, false, false, arrival, body, FrameCapture.renderFrame(), client.player.tickCount));
    previousFrame = nanos;
  }

  private void recordWarmPass() {
    check(warmArrivalPresentation != null, "Same-pair traversal did not present its crossed body and destination camera");
    int sampleHop = PerformanceWorldFixture.LOCATIONS * 2 + warmPasses.size();
    String prefix = warmPrefix();
    var recorded = samples.stream().filter(sample -> sample.hop == sampleHop).toList();
    var phases = new LinkedHashMap<String, Object>();
    for (String phase : List.of(prefix + "_LOOK", prefix + "_PORTAL_STEADY", prefix, prefix + "_SETTLE", prefix + "_STEADY"))
      phases.put(phase, statistics(recorded.stream().filter(sample -> sample.phase.equals(phase)).toList()));
    var result = new LinkedHashMap<String, Object>();
    result.put("direction", prefix);
    result.put("entrance", warmLink.entrance().endpoint().center());
    result.put("exit", warmLink.exit().endpoint().center());
    result.put("movementStartedNanos", warmMovementNanos);
    result.put("crossedNanos", warmCrossedNanos);
    result.put("serverCrossing", warmServerCrossing.join());
    result.put("arrivalPresentation", warmArrivalPresentation);
    check(!recorded.isEmpty(), "performance/same-pair traversal produced no measured frames");
    result.put("frameTimes", statistics(recorded));
    result.put("phases", phases);
    warmPasses.add(result);
  }

  private void observeOpening() {
    if (openedLink != null) return;
    openedLink = PortalTopology.links(client.level).stream()
        .filter(link -> link.gun().equals(leg.gun())
            && link.entrance().endpoint().center().distanceToSqr(leg.source().aim()) < 1)
        .findFirst().orElse(null);
    if (openedLink != null) openedNanos = FrameCapture.monotonicNanos();
  }

  private void measure(boolean enabled) {
    measuring = enabled;
    previousFrame = enabled ? FrameCapture.presentation().nanos() : 0;
  }

  private boolean image(String name) {
    frames.monitor(true);
    if (screenshot == null) { screenshot = frames.checkpoint(name); return false; }
    if (!screenshot.isDone()) return false;
    screenshot.join().assertNotBlack(); screenshot = null;
    return true;
  }

  private void recordLeg() {
    check(openedNanos > firedNanos && crossedNanos >= openedNanos,
        "Performance traversal lacks a real shot, opened pair or crossing");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("hop", hop);
    result.put("gun", leg.gun());
    result.put("firstVisit", hop < PerformanceWorldFixture.LOCATIONS - 1);
    result.put("destinationVisit", hop < PerformanceWorldFixture.LOCATIONS - 1 ? "first-remote-view" : "revisit");
    result.put("openingMode", stationaryOpening() ? "stationary-opening" : "immediate-crossing");
    result.put("stationaryOpeningTicks", stationaryOpening() ? 60 : 10);
    result.put("source", leg.source()); result.put("destination", leg.destination());
    result.put("distanceBlocks", leg.source().feet().distanceTo(leg.destination().feet()));
    result.put("livePairsBeforeShot", leg.livePairs());
    result.put("destinationPreparationMs", preparationMilliseconds);
    result.put("firedTick", firedTick); result.put("settledTick", settledTick);
    result.put("finishedTick", client.player.tickCount);
    result.put("firedNanos", firedNanos); result.put("openedNanos", openedNanos);
    result.put("crossedNanos", crossedNanos); result.put("settledNanos", settledNanos);
    result.put("serverCrossingConfirmed", true);
    result.put("shotToOpenMs", (openedNanos - firedNanos) / 1e6);
    result.put("openToCrossMs", (crossedNanos - openedNanos) / 1e6);
    result.put("shotToSettledMs", (settledNanos - firedNanos) / 1e6);
    var milestones = new LinkedHashMap<String, Object>();
    milestone(milestones, "firstRemoteImagePresented", firstRemoteImageNanos);
    milestone(milestones, "firstTerrainBuildQueuesDrained", firstTerrainQueuesDrainedNanos);
    milestone(milestones, "firstAuthoredDestinationCoreReady", firstDestinationCoreReadyNanos);
    milestone(milestones, "firstRemoteEyePathPresented", firstEyePathNanos);
    milestone(milestones, "firstDestinationCameraPresented", firstDestinationCameraNanos);
    milestone(milestones, "firstBodyCrossingPresented", firstBodyPresentedNanos);
    result.put("milestones", milestones);
    var recordedFrames = samples.stream().filter(sample -> sample.hop == hop).toList();
    check(!recordedFrames.isEmpty(), "performance/traversal produced no measured frames");
    result.put("frameTimes", statistics(recordedFrames));
    var phases = new LinkedHashMap<String, Object>();
    for (String phase : List.of("OPEN", "PORTAL_STEADY", "WALK", "SETTLE", "STEADY")) {
      var recorded = samples.stream().filter(sample -> sample.hop == hop && sample.phase.equals(phase)).toList();
      phases.put(phase, recorded.isEmpty() ? Map.of("frames", 0) : statistics(recorded));
    }
    result.put("phases", phases);
    result.put("heapUsedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    legs.add(result);
  }

  private void milestone(Map<String, Object> result, String name, long nanos) {
    result.put(name, nanos == 0 ? Map.of("observed", false)
        : Map.of("observed", true, "nanos", nanos, "shotToMs", (nanos - firedNanos) / 1e6));
  }

  private static Map<String, Object> statistics(List<Sample> samples) {
    if (samples.isEmpty()) return Map.of("frames", 0);
    double[] values = samples.stream().mapToDouble(Sample::milliseconds).sorted().toArray();
    return Map.of("frames", values.length, "meanMs", Arrays.stream(values).average().orElseThrow(),
        "p50Ms", percentile(values, .5), "p95Ms", percentile(values, .95),
        "p99Ms", percentile(values, .99), "maxMs", values[values.length - 1],
        "over50Ms", Arrays.stream(values).filter(value -> value > 50).count(),
        "over100Ms", Arrays.stream(values).filter(value -> value > 100).count());
  }

  private static double percentile(double[] sorted, double quantile) {
    return sorted[Math.max(0, (int) Math.ceil(sorted.length * quantile) - 1)];
  }

  private void writeReport() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("clock", "completed main-window image before blit, monotonic nanoseconds");
    report.put("fixture", ClientRegressionRunner.performanceFixture());
    report.put("seed", 1);
    report.put("preset", "normal");
    report.put("locations", PerformanceWorldFixture.LOCATIONS);
    report.put("renderDistance", client.options.renderDistance().get());
    report.put("simulationDistance", client.options.simulationDistance().get());
    report.put("completedHops", legs.size());
    report.put("baseline", statistics(samples.stream().filter(sample -> sample.hop == -1).toList()));
    report.put("legs", legs);
    report.put("samePairRoundtrip", warmPasses);
    report.put("samples", samples);
    report.put("readbackDuringMeasurements", false);
    try {
      Files.writeString(frames.directory().resolve("performance.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(report));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private void next(Step next) {
    step = next;
    age = 0;
  }

  @Override public List<String> passed() { return List.copyOf(passed); }
  @Override public CompletableFuture<Void> cleanup() {
    measure(false);
    input.stop();
    if (samples.stream().anyMatch(sample -> sample.hop == -1)) writeReport();
    return CompletableFuture.completedFuture(null);
  }
}
