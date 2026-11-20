package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalPassagePose;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.test.mixin.ServerGamePacketListenerImplAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

final class PlayerTraversalSystemScene implements ClientSystemScene {
  private enum Step { PREPARE, WAIT, BASELINE, CROSS, SETTLE, RESULT, BETWEEN, DONE }
  private static final String[] MODES = {
      "walk", "crouch", "jump", "piston", "fall-shot", "slow-floor-wall", "fast-floor-wall-10", "floor-wall-server-motion"
  };
  private record Observation(Vec3 feet, boolean crossed, Pose pose, float scale, boolean sneaking, double height, boolean burning, String pusher, double crossingSpeed, boolean grounded, int teleportId) {}
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final List<String> passed = new ArrayList<>();
  private final java.util.UUID playerId;
  private CompletableFuture<PortalSystemFixture> preparation;
  private CompletableFuture<PortalSystemFixture.Arrangement> setup;
  private CompletableFuture<Observation> observation;
  private CompletableFuture<Boolean> readiness;
  private CompletableFuture<Void> positioning;
  private CompletableFuture<Void> push;
  private CompletableFuture<FrameCapture.Frame> image;
  private PortalSystemFixture fixture;
  private PortalSystemFixture.Arrangement arrangement;
  private Observation serverState;
  private Step step = Step.PREPARE;
  private int mode;
  private int age;
  private boolean fallStarted;
  private boolean floorClientExit;
  private boolean floorServerExit;
  private int floorTeleportId;
  private long warmExitIdentity;
  private final Vec3 feet = new Vec3(64.5, 100, 1.2);
  private final Vec3 fallingFeet = new Vec3(64.5, 108, 514.5);

  PlayerTraversalSystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    input = new ScenarioInput(client);
    playerId = client.player.getUUID();
  }

  public boolean tick() {
    check(++age < 350, "Player portal traversal timed out at " + MODES[mode] + "/" + step
        + ": clientFeet=" + client.player.position() + ", clientPose=" + client.player.getPose() + ", clientScale=" + PortalPassagePose.scale(client.player)
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
          if (mode >= 5) return fixture.prepareFloorExit();
          var current = fixture.prepare(Direction.SOUTH, Direction.SOUTH, 0, 1.2);
          fixture.teleport(feet.add(0, 0, 512), new Vec3(64.5, 101, 512));
          fixture.releaseFlight();
          return current;
        });
        next(Step.WAIT);
      }
      case WAIT -> {
        if (!setup.isDone()) return false;
        arrangement = setup.join();
        if (client.player.position().distanceTo(mode >= 5 ? arrangement.feet() : feet.add(0, 0, 512)) > .15
            || (mode >= 5 ? !client.player.getAbilities().flying
                : !client.player.onGround() || client.player.getAbilities().flying)
            || PortalTopology.links(client.level).stream().noneMatch(link -> link.gun().equals(fixture.gunId())
                && link.generation() == arrangement.generation()
                && link.entrance().endpoint().equals(arrangement.entrance())
                && PortalSceneManager.hasRenderedView(link)
                && PortalTraversalReadiness.ready(client.player, link))) return false;
        if (readiness == null) readiness = server.submit(() -> {
          var owner = server.getPlayerList().getPlayer(playerId);
          return (mode >= 5 ? owner.getAbilities().flying : owner.onGround() && !owner.getAbilities().flying)
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
        if (mode >= 5) {
          if (!observe()) return false;
          floorTeleportId = serverState.teleportId;
        }
        if (mode == 3) {
          Vec3 start = arrangement.entrance().center().add(0, -1, .1);
          if (positioning == null) positioning = server.submit(() ->
              fixture.teleport(start, arrangement.entrance().center()));
          if (!positioning.isDone()) return false;
          positioning.join();
          if (client.player.position().distanceTo(start) > .02 || !client.player.onGround()) return false;
        } else if (mode == 4) {
          if (positioning == null) {
            warmExitIdentity = PortalTopology.links(client.level).stream()
                .filter(link -> link.gun().equals(fixture.gunId())
                    && link.generation() == arrangement.generation()
                    && link.entrance().endpoint().equals(arrangement.entrance()))
                .findFirst().orElseThrow().exit().identity();
            positioning = server.submit(() -> fixture.prepareFallingShot(fallingFeet));
          }
          if (!positioning.isDone()) return false;
          positioning.join();
          if (!client.player.getAbilities().flying || client.player.position().distanceTo(fallingFeet) > .02
              || !GunEquipReadiness.ready(client, fixture.gunId(), PortalixVariant.STANDARD)) return false;
        }
        if (!capture("player-" + MODES[mode] + "-before")) return false;
        serverState = null;
        next(Step.CROSS);
      }
      case CROSS -> {
        check(client.player.getY() > 99 && client.player.getZ() > 511,
            "Native " + MODES[mode] + " movement left the supported portal course without crossing: feet="
                + client.player.position() + ", velocity=" + client.player.getDeltaMovement()
                + ", onGround=" + client.player.onGround() + ", flying=" + client.player.getAbilities().flying
                + ", lastCrossing=" + EntityPortalState.of(client.player).lastCrossing()
                + ", expectedGeneration=" + arrangement.generation());
        if (mode >= 5) {
          observe();
          if (push == null) push = server.submit(() -> fixture.startFloorFall(mode == 7 ? 2.2 : fallSpeed()));
          if (!push.isDone()) return false;
          push.join();
          if (client.player.getAbilities().flying) return false;
          if (!fallStarted) {
            client.player.setDeltaMovement(0, -fallSpeed(), 0);
            fallStarted = true;
          }
          checkFloorExit();
        } else if (mode == 4) {
          check(!fallStarted || client.player.getX() >= 72 || !client.player.onGround(),
              "A late falling shot hit the entrance floor before traversal: velocity="
                  + client.player.getDeltaMovement() + ", server=" + serverState);
          observe();
          if (push == null) push = server.submit(fixture::startFallingShot);
          if (!push.isDone()) return false;
          push.join();
          if (client.player.getAbilities().flying) return false;
          if (!fallStarted) {
            client.player.setDeltaMovement(0, -4, 0);
            input.click(client.options.keyAttack);
            fallStarted = true;
          }
        } else if (mode == 3) {
          observe();
          if (push == null) push = server.submit(fixture::extendPiston);
          if (!push.isDone()) return false;
          push.join();
        } else {
          client.options.keyShift.setDown(mode == 1);
          client.options.keyJump.setDown(mode == 2 && age < 8);
          input.forward(true);
        }
        if (client.player.getX() < 72) return false;
        input.stop();
        check(mode >= 4 ? Math.abs(client.player.getX() - arrangement.exit().center().x) < 2
                : client.player.position().distanceTo(new Vec3(80.5, 100, 513.2)) < 3,
            "Predicted traversal reached an unrelated position");
        next(Step.SETTLE);
      }
      case SETTLE -> {
        if (!observe()) return false;
        if (mode >= 5) checkFloorExit();
        check(mode != 3 || !serverState.burning,
            "Piston crossing applied effects from blocks between the portal endpoints");
        if (mode == 4 && serverState.crossed)
          check(serverState.crossingSpeed >= 3.5,
              "The late falling shot lost its entrance momentum: " + serverState);
        if (!serverState.crossed || serverState.feet.distanceTo(client.player.position()) > .2
            || !client.player.onGround() || PortalPassagePose.scale(client.player) < .999f
            || serverState.scale < .999f || client.player.getPose() != Pose.STANDING
            || serverState.pose != Pose.STANDING || client.player.isShiftKeyDown() || serverState.sneaking) return false;
        check(serverState.height > 1.7 && client.player.getBoundingBox().getYsize() > 1.7,
            "Portal traversal left the standing collision body compressed: clientPose=" + client.player.getPose()
                + ", clientScale=" + PortalPassagePose.scale(client.player)
                + ", clientHeight=" + client.player.getBoundingBox().getYsize() + ", server=" + serverState);
        check(mode != 5 || floorClientExit && floorServerExit,
            "Floor traversal did not observe both sides leaving the wall aperture");
        next(Step.RESULT);
      }
      case RESULT -> {
        if (!capture("player-" + MODES[mode] + "-after")) return false;
        passed.add("player-traversal/" + MODES[mode] + " native movement, authoritative agreement and restored body");
        next(mode == MODES.length - 1 ? Step.DONE : Step.BETWEEN);
      }
      case BETWEEN -> {
        if (!frames.pause()) return false;
        positioning = null;
        push = null;
        fallStarted = false;
        floorClientExit = false;
        floorServerExit = false;
        mode++;
        next(Step.PREPARE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private boolean observe() {
    var server = client.getSingleplayerServer();
    if (observation == null) observation = server.submit(() -> {
      var player = server.getPlayerList().getPlayer(playerId);
      var crossing = EntityPortalState.of(player).lastCrossing();
      boolean crossed = crossing != null && crossing.link().gun().equals(fixture.gunId())
          && (mode == 4
              ? crossing.link().entrance().endpoint().normal().y == 1
                  && crossing.link().exit().identity() == warmExitIdentity
                  && PortalTopology.links(player.level()).contains(crossing.link())
              : crossing.link().generation() == arrangement.generation());
      return new Observation(player.position(), crossed,
          player.getPose(), PortalPassagePose.scale(player), player.isShiftKeyDown(),
          player.getBoundingBox().getYsize(), player.getRemainingFireTicks() > 0, mode == 3 ? fixture.pistonState() : "",
          crossed ? crossing.velocity().length() : 0, player.onGround(),
          ((ServerGamePacketListenerImplAccessor) player.connection).portalix$teleportId());
    });
    if (!observation.isDone()) return false;
    serverState = observation.join();
    observation = null;
    return true;
  }

  private double fallSpeed() { return mode == 5 ? .1 : mode == 6 ? 10 : 3.2; }

  private void checkFloorExit() {
    if (serverState != null && serverState.crossed)
      check(serverState.teleportId == floorTeleportId,
          "The server corrected an ordinary floor traversal during pose recovery: " + serverState);
    var crossing = EntityPortalState.of(client.player).lastCrossing();
    if (crossing != null && crossing.link().generation() == arrangement.generation()
        && crossing.link().gun().equals(fixture.gunId()) && !floorClientExit) {
      check(!client.player.onGround() && !client.player.verticalCollision,
          "Floor traversal caught the wall exit rim: feet=" + client.player.position());
      check(client.player.getDeltaMovement().z > crossing.velocity().length() * .8,
          "Floor traversal lost its outward momentum at the exit");
      floorClientExit = true;
    }
    if (serverState != null && serverState.crossed && !floorServerExit) {
      check(!serverState.grounded, "The server grounded a floor traversal on the exit rim: " + serverState);
      floorServerExit = true;
    }
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
