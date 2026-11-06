package dev.portalix.test.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

final class ScenarioFrames {
  private enum Kind { TIMELINE, CHECKPOINT, SHOT }
  private record Checkpoint(String name, BooleanSupplier condition, CompletableFuture<FrameCapture.Frame> result) {}
  private record Capture(CompletableFuture<FrameCapture.Frame> result, Kind kind, String caption,
                         boolean monitored, Checkpoint checkpoint, VisualShotProbe shot,
                         VisualShotProbe.FrameTicket ticket) {}

  private final Minecraft client;
  private final Path directory;
  private final ScenarioRecording recording;
  private final PresentedFrameGuard frameGuard;
  private final List<String> checkpoints = new ArrayList<>();
  private final ArrayDeque<Capture> captures = new ArrayDeque<>(FrameCapture.MAX_IN_FLIGHT);
  private Checkpoint checkpoint;
  private VisualShotProbe shot;
  private CompletableFuture<List<String>> shotCompletion;
  private boolean finishShot;
  private boolean shotFinishStarted;
  private boolean monitoring;
  private boolean pausing;
  private String stage = "";
  private int lastSampleTick = -100;
  private int checkedFrames;
  private CompletableFuture<Path> completion;
  private boolean recordingFinished;
  private Throwable renderFailure;

  ScenarioFrames(Minecraft client, Path directory) {
    this.client = client;
    this.directory = directory;
    recording = System.getProperty("portalix.test.diagnostics", "compact").equals("full")
        ? new ScenarioRecording(directory) : null;
    frameGuard = new PresentedFrameGuard(directory);
  }

  Path directory() { return directory; }
  void monitor(boolean value) {
    if (value) pausing = false;
    monitoring = value;
  }

  boolean pause() {
    pausing = true;
    if (checkpoint != null || shot != null || !captures.isEmpty()) return false;
    monitor(false);
    return frameGuard.idle();
  }

  void abortPending() {
    monitor(false);
    pausing = true;
    if (checkpoint != null && captures.stream().noneMatch(capture -> capture.checkpoint == checkpoint)) {
      checkpoint.result.cancel(false);
      checkpoint = null;
    }
    if (shot != null) finishShot = true;
  }

  void worldBoundary() {
    if (monitoring || !pause()) throw new IllegalStateException("Drain scene captures before replacing the world");
    if (recording != null) recording.worldBoundary();
    lastSampleTick = -100;
  }

  CompletableFuture<FrameCapture.Frame> checkpoint(String name) {
    return reservedCheckpoint(name, () -> true);
  }

  CompletableFuture<FrameCapture.Frame> reservedCheckpoint(String name, BooleanSupplier condition) {
    if (checkpoint != null || completion != null) throw new IllegalStateException("Cannot queue a checkpoint now");
    checkpoint = new Checkpoint(name, condition, new CompletableFuture<>());
    return checkpoint.result;
  }

  boolean reservedReady() {
    return completion == null && checkpoint != null && captures.isEmpty() && FrameCapture.canCapture();
  }

  void observeShot(VisualShotProbe probe) {
    if (shot != null) throw new IllegalStateException("A shot is already under observation");
    shot = probe;
    finishShot = false;
    shotFinishStarted = false;
    shotCompletion = new CompletableFuture<>();
  }

  boolean shotReady() {
    return shot != null && !finishShot && captures.isEmpty() && checkpoint == null && FrameCapture.canCapture();
  }

  CompletableFuture<List<String>> finishShot() {
    if (shotCompletion == null) throw new IllegalStateException("No observed shot");
    finishShot = true;
    return shotCompletion;
  }

  void tick(String stage) {
    this.stage = stage;
    if (renderFailure != null) {
      var failure = renderFailure;
      renderFailure = null;
      throw new AssertionError("Visual frame processing failed", failure);
    }
    frameGuard.check();
    drainCapture();
    finishObservedShot();
    if (!captures.isEmpty()) return;
    if (completion != null) {
      if (!recordingFinished) {
        recordingFinished = true;
        var finished = recording == null
            ? CompletableFuture.<Path>completedFuture(null) : recording.finish();
        var all = shotCompletion == null ? CompletableFuture.allOf(finished, frameGuard.finish())
            : CompletableFuture.allOf(finished, frameGuard.finish(), shotCompletion);
        all.whenComplete((ignored, failure) -> {
          if (failure == null) completion.complete(finished.join());
          else completion.completeExceptionally(failure);
        });
      }
      return;
    }
    if (recording != null && checkpoint == null && shot == null && monitoring && !pausing
        && client.player != null && client.player.tickCount - lastSampleTick >= 4) {
      lastSampleTick = client.player.tickCount;
      captures.addLast(new Capture(FrameCapture.captureSample(client), Kind.TIMELINE, stage, true, null, null, null));
    }
  }

  private void drainCapture() {
    while (!captures.isEmpty() && captures.getFirst().result.isDone()) {
      var finished = captures.removeFirst();
      try {
        var frame = finished.result.join();
        // Keep the offending frame even when its visual assertion fails.
        if (recording != null) recording.accept(frame, finished.caption, finished.monitored);
        if (finished.monitored) {
          frame.assertNotBlack(new FrameCapture.Region(frame.width() / 20, frame.height() / 12,
              frame.width() * 9 / 10, frame.height() * 2 / 3));
          checkedFrames++;
        }
        if (finished.shot != null) finished.shot.accept(frame, finished.ticket);
        if (finished.checkpoint != null) {
          checkpoints.add(finished.checkpoint.name);
          checkpoint = null;
          finished.checkpoint.result.complete(frame);
        }
      } catch (RuntimeException | Error failure) {
        if (finished.checkpoint != null) {
          checkpoint = null;
          finished.checkpoint.result.completeExceptionally(failure);
        }
        throw failure;
      }
    }
  }

  private void finishObservedShot() {
    if (!finishShot || shotFinishStarted || !captures.isEmpty()) return;
    shotFinishStarted = true;
    shot.finish().whenComplete((groups, failure) -> {
      if (failure == null) shotCompletion.complete(groups);
      else shotCompletion.completeExceptionally(failure);
    });
    shot = null;
  }

  void onFrame(RenderTarget target) {
    try {
      drainCapture();
      finishObservedShot();
      if (monitoring) frameGuard.presented(target);
      else frameGuard.release();
      if (reservedReady() && checkpoint.condition.getAsBoolean())
        captures.addLast(new Capture(FrameCapture.capture(client, checkpoint.name, directory), Kind.CHECKPOINT,
            stage, monitoring, checkpoint, null, null));
      if (completion == null && !finishShot && shot != null) {
        String blocker = checkpoint != null ? "CHECKPOINT_PENDING"
            : !captures.isEmpty() && captures.getFirst().kind != Kind.SHOT ? captures.getFirst().kind.name() + "_IN_FLIGHT"
            : captures.size() == FrameCapture.MAX_IN_FLIGHT || !FrameCapture.canCapture() ? "READBACK_CAPACITY" : "";
        var ticket = shot.presentedFrame(blocker);
        if (ticket != null)
          captures.addLast(new Capture(FrameCapture.captureSample(client), Kind.SHOT, shot.caption(ticket),
              monitoring, null, shot, ticket));
      }
    } catch (Throwable failure) {
      renderFailure = failure;
    }
  }

  int checkedFrames() { return checkedFrames; }
  long presentedFrames() { return frameGuard.checked(); }
  long submittedFrames() { return frameGuard.presented(); }
  List<String> checkpoints() { return List.copyOf(checkpoints); }

  CompletableFuture<Path> finish() {
    monitor(false);
    frameGuard.finish();
    if (checkpoint != null && captures.stream().noneMatch(capture -> capture.checkpoint == checkpoint)) {
      checkpoint.result.cancel(false);
      checkpoint = null;
    }
    if (shot != null) finishShot = true;
    if (completion == null) completion = new CompletableFuture<>();
    return completion;
  }
}
