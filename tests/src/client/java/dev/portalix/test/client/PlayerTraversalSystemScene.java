package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import dev.portalix.portal.query.PlayerPortalVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

final class PlayerTraversalSystemScene implements ClientSystemScene {
  private enum Step { PREPARE, WAIT, BASELINE, CROSS, SETTLE, RESULT, BETWEEN, DONE }
  private static final String[] MODES = { "walk", "crouch", "jump" };
  private record Observation(Vec3 feet, boolean crossed, Pose pose, float scale, boolean sneaking, double height) {}
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final List<String> passed = new ArrayList<>();
  private final java.util.UUID playerId;
  private CompletableFuture<PortalSystemFixture> preparation;
  private CompletableFuture<PortalSystemFixture.Arrangement> setup;
  private CompletableFuture<Observation> observation;
  private CompletableFuture<Boolean> readiness;
  private CompletableFuture<FrameCapture.Frame> image;
  private PortalSystemFixture fixture;
  private PortalSystemFixture.Arrangement arrangement;
  private Observation serverState;
  private Step step = Step.PREPARE;
  private int mode;
  private int age;
  private final Vec3 feet = new Vec3(64.5, 100, 1.2);

  PlayerTraversalSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    input = new ScenarioInput(client);
    playerId = client.player.getUUID();
  }

  public boolean tick() {
    check(++age < 350, "Player portal traversal timed out at " + MODES[mode] + "/" + step
        + ": clientPose=" + client.player.getPose() + ", clientScale=" + PortalPassagePose.scale(client.player)
        + ", clientHeight=" + client.player.getBoundingBox().getYsize()
        + ", clientSneaking=" + client.player.isShiftKeyDown() + ", server=" + serverState);
    var server = client.getSingleplayerServer();
    switch (step) {
      case PREPARE -> {
        if (preparation == null) preparation = server.submit(() ->
            new PortalSystemFixture(server.getPlayerList().getPlayer(playerId), 64, 512));
        if (!preparation.isDone()) return false;
        fixture = preparation.join();
        setup = server.submit(() -> {
          var current = fixture.prepare(Direction.SOUTH, Direction.SOUTH, 0, 1.2, false);
          fixture.teleport(feet.add(0, 0, 512), new Vec3(64.5, 101, 512));
          fixture.releaseFlight();
          return current;
        });
        next(Step.WAIT);
      }
      case WAIT -> {
        if (!setup.isDone()) return false;
        arrangement = setup.join();
        if (client.player.position().distanceTo(feet.add(0, 0, 512)) > .15
            || !client.player.onGround() || client.player.getAbilities().flying
            || PortalTopology.links(client.level).stream().noneMatch(link -> link.gun().equals(fixture.gunId())
                && link.generation() == arrangement.generation()
                && link.entrance().endpoint().equals(arrangement.entrance())
                && PortalSceneManager.hasRenderedView(link)
                && PortalTraversalReadiness.ready(client.player, link))) return false;
        if (readiness == null) readiness = server.submit(() -> {
          var owner = server.getPlayerList().getPlayer(playerId);
          return owner.onGround() && !owner.getAbilities().flying
              && PortalTopology.links(owner.level()).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
                  && link.generation() == arrangement.generation()
                  && link.entrance().endpoint().equals(arrangement.entrance())
                  && PlayerPortalVisibility.of(owner).isReady(link));
        });
        if (!readiness.isDone()) return false;
        boolean ready = readiness.join();
        readiness = null;
        if (!ready) return false;
        frames.monitor(true);
        next(Step.BASELINE);
      }
      case BASELINE -> {
        if (!capture("player-" + MODES[mode] + "-before")) return false;
        serverState = null;
        next(Step.CROSS);
      }
      case CROSS -> {
        check(client.player.getY() > 99 && client.player.getZ() > 511.5,
            "Native " + MODES[mode] + " movement left the supported portal course without crossing: feet="
                + client.player.position() + ", velocity=" + client.player.getDeltaMovement()
                + ", onGround=" + client.player.onGround() + ", flying=" + client.player.getAbilities().flying
                + ", lastCrossing=" + EntityPortalState.of(client.player).lastCrossing()
                + ", expectedGeneration=" + arrangement.generation());
        client.options.keyShift.setDown(mode == 1);
        client.options.keyJump.setDown(mode == 2 && age < 8);
        input.forward(true);
        if (client.player.getX() < 72) return false;
        input.stop();
        check(client.player.position().distanceTo(new Vec3(80.5, 100, 513.2)) < 3,
            "Predicted traversal reached an unrelated position");
        next(Step.SETTLE);
      }
      case SETTLE -> {
        if (observation == null) observation = server.submit(() -> {
          var player = server.getPlayerList().getPlayer(playerId);
          var crossing = EntityPortalState.of(player).lastCrossing();
          return new Observation(player.position(), crossing != null && crossing.link().gun().equals(fixture.gunId())
                  && crossing.link().generation() == arrangement.generation(),
              player.getPose(), PortalPassagePose.scale(player), player.isShiftKeyDown(),
              player.getBoundingBox().getYsize());
        });
        if (!observation.isDone()) return false;
        serverState = observation.join();
        observation = null;
        if (!serverState.crossed || serverState.feet.distanceTo(client.player.position()) > .2
            || !client.player.onGround() || PortalPassagePose.scale(client.player) < .999f
            || serverState.scale < .999f || client.player.getPose() != Pose.STANDING
            || serverState.pose != Pose.STANDING || client.player.isShiftKeyDown() || serverState.sneaking) return false;
        check(serverState.height > 1.7 && client.player.getBoundingBox().getYsize() > 1.7,
            "Portal traversal left the standing collision body compressed: clientPose=" + client.player.getPose()
                + ", clientScale=" + PortalPassagePose.scale(client.player)
                + ", clientHeight=" + client.player.getBoundingBox().getYsize() + ", server=" + serverState);
        next(Step.RESULT);
      }
      case RESULT -> {
        if (!capture("player-" + MODES[mode] + "-after")) return false;
        passed.add("player-traversal/" + MODES[mode] + " native input, predicted crossing, authoritative agreement and restored body");
        next(mode == MODES.length - 1 ? Step.DONE : Step.BETWEEN);
      }
      case BETWEEN -> {
        if (!frames.pause()) return false;
        mode++;
        next(Step.PREPARE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private boolean capture(String name) {
    if (image == null) { image = frames.checkpoint(name); return false; }
    if (!image.isDone()) return false;
    image.join().assertNotBlack();
    image = null;
    return true;
  }

  private void next(Step next) { step = next; age = 0; }
  public List<String> passed() { return List.copyOf(passed); }
  public CompletableFuture<Void> cleanup() {
    input.stop();
    if (preparation == null) return CompletableFuture.completedFuture(null);
    var server = client.getSingleplayerServer();
    return preparation.handle((owned, failure) -> owned).thenCompose(owned ->
        owned == null ? CompletableFuture.completedFuture(null) : server.submit(owned::close));
  }
}
