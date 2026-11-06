package dev.portalix.test.client;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

final class LifecycleSystemScene extends PortalPreparedScene {
  private enum Step { PREPARE, BEFORE, DRAIN, REPLACE, AFTER, DONE }
  private Step step = Step.PREPARE;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private int age;

  LifecycleSystemScene(Minecraft client, ScenarioFrames frames) {
    super(client, frames);
  }

  @Override public boolean tick() {
    ClientAssertions.check(++age < 600, "World replacement timed out at " + step);
    switch (step) {
      case PREPARE -> {
        if (prepare(256, 512)) next(Step.BEFORE);
      }
      case BEFORE -> {
        if (screenshot == null) screenshot = frames.checkpoint("lifecycle-before-disconnect");
        if (!screenshot.isDone()) return false;
        PortalSceneAssertions.landmarks(screenshot.join(), arrangement);
        screenshot = null;
        next(Step.DRAIN);
      }
      case DRAIN -> {
        if (!frames.pause()) return false;
        next(Step.REPLACE);
      }
      case REPLACE -> {
        if (!ClientSessionRegression.tick(client)) return false;
        frames.worldBoundary();
        frames.monitor(true);
        next(Step.AFTER);
      }
      case AFTER -> {
        if (screenshot == null) screenshot = frames.checkpoint("lifecycle-after-rejoin");
        if (!screenshot.isDone()) return false;
        screenshot.join().assertNotBlack();
        next(Step.DONE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  @Override public List<String> passed() {
    return step == Step.DONE ? ClientSessionRegression.passedGroups() : List.of();
  }

  private void next(Step next) { step = next; age = 0; }
}
