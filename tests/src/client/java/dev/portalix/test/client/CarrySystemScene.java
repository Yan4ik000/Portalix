package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.PortalixClient;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.camera.PortalEyePose;
import dev.portalix.portal.traversal.PortalItemRotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class CarrySystemScene implements ClientSystemScene {
  private enum Step { START, PREPARE, EMPTY, SPAWN, TRACKED, BEFORE, GRAB, HELD, ENABLE_PHYSICS,
    TURN, FOLLOW, DROP, FALL, DROP_VIEW, DROPPED, AIM, REGRAB, REHELD, RETURN_TO_OBSERVER, THROW_AIM, THROW, FLIGHT, THROWN, REMOVE, REMOVED, ABSENT, DONE }
  private static final EntitySystemFixture.Kind[] KINDS = {EntitySystemFixture.Kind.ITEM, EntitySystemFixture.Kind.PIG};
  private static final AABB INITIAL_QUARTZ_CONTROL = new AABB(159, 99.99, 508, 160, 100.01, 510);
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final ScenarioInput input;
  private final UUID playerId;
  private final CameraType camera;
  private final EntitySceneView view;
  private final List<String> passed = new ArrayList<>();
  private Step step = Step.START;
  private int age;
  private int index;
  private boolean clicked;
  private CompletableFuture<EntitySystemFixture> preparation;
  private CompletableFuture<EntitySystemFixture.Ref> spawn;
  private CompletableFuture<Void> mutation;
  private CompletableFuture<EntitySystemFixture.Observation> observation;
  private CompletableFuture<FrameCapture.Frame> capture;
  private EntitySystemFixture fixture;
  private EntitySystemFixture.Ref actor;
  private EntitySystemFixture.Observation observed;
  private FrameCapture.Frame empty;
  private FrameCapture.Frame before;
  private AABB captureBounds;
  private Vec3 heldPosition;
  private Vec3 throwStart;
  private FrameCapture.Frame thrown;
  private AABB thrownBounds;
  private AABB thrownControl;
  private final Map<String, Object> visibility = new LinkedHashMap<>();
  private Map<String, Object> capturePose;

  CarrySystemScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    input = new ScenarioInput(client);
    playerId = client.player.getUUID();
    camera = client.options.getCameraType();
    view = new EntitySceneView(client, frames);
  }

  @Override public boolean tick() {
    if (++age >= 400) {
      view.record("timeout");
      throw new AssertionError("Native carry client scene timed out at " + step + "/" + index + ": " + view.diagnostics()
          + ", yaw=" + client.player.getYRot() + ", pitch=" + client.player.getXRot()
          + ", clientTarget=" + PortalixClient.carryState().localTarget() + ", observed=" + observed);
    }
    var server = client.getSingleplayerServer();
    if (actor != null && step != Step.REMOVE && step != Step.REMOVED && step != Step.ABSENT) poll();
    switch (step) {
      case START -> {
        client.options.setCameraType(CameraType.FIRST_PERSON);
        preparation = server.submit(() -> new EntitySystemFixture(server.getPlayerList().getPlayer(playerId), false));
        next(Step.PREPARE);
      }
      case PREPARE -> {
        if (!preparation.isDone()) return false;
        fixture = preparation.join();
        if (client.player.position().distanceTo(EntitySystemFixture.OBSERVER) > .1
            || !view.ready()
            || !GunEquipReadiness.ready(client, fixture.gunId, PortalixVariant.STANDARD)) return false;
        if (!input.lookAt(client.player.getEyePosition().add(0, 0, 10))) return false;
        frames.monitor(true);
        next(Step.EMPTY);
      }
      case EMPTY -> {
        if (!input.lookAt(client.player.getEyePosition().add(0, 0, 10))) return false;
        if (capture == null) capture = frames.checkpoint("carry-" + name() + "-empty");
        if (!capture.isDone()) return false;
        empty = capture.join();
        capture = null;
        spawn = server.submit(() -> fixture.spawn(KINDS[index], fixture.owner.getEyePosition().add(0, 0, 1.5)));
        next(Step.SPAWN);
      }
      case SPAWN -> {
        if (!spawn.isDone()) return false;
        actor = spawn.join();
        next(Step.TRACKED);
      }
      case TRACKED -> {
        if (entity() == null || age < 10 || observed == null) return false;
        next(Step.BEFORE);
      }
      case BEFORE -> {
        if (!capture("carry-" + name() + "-before", true)) return false;
        next(Step.GRAB);
      }
      case GRAB -> {
        if (!clicked) { input.grab(); clicked = true; }
        if (!held() || age < 15) return false;
        next(Step.HELD);
      }
      case HELD -> {
        if (!capture("carry-" + name() + "-held", false)) return false;
        heldPosition = entity().position();
        mutation = server.submit(() -> {
          var entity = fixture.entity(actor);
          check(dev.portalix.carry.CarryBindings.holderId(entity) == fixture.owner.getId(),
              "Native carry binding disappeared before enabling the target's normal physics");
          entity.setNoGravity(false);
          if (entity instanceof Mob mob) mob.setNoAi(false);
        });
        next(Step.ENABLE_PHYSICS);
      }
      case ENABLE_PHYSICS -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        next(Step.TURN);
      }
      case TURN -> {
        if (!input.lookAt(client.player.getEyePosition().add(2, .3, 4)) || age < 20) return false;
        if (!held() || entity().position().distanceTo(heldPosition) < .35) return false;
        next(Step.FOLLOW);
      }
      case FOLLOW -> {
        if (!capture("carry-" + name() + "-follows-camera", false)) return false;
        heldPosition = entity().position();
        next(Step.DROP);
      }
      case DROP -> {
        if (!clicked) { input.click(client.options.keyUse); clicked = true; }
        if (!released()) return false;
        next(Step.FALL);
      }
      case FALL -> {
        if (age < 15 || entity() == null || observed == null) return false;
        check(entity().getY() < heldPosition.y - .2 && observed.position().y < heldPosition.y - .2,
            "Native carry drop did not resume target gravity on the client and server: " + name());
        next(Step.DROP_VIEW);
      }
      case DROP_VIEW -> {
        check(entity() != null, "Dropped carry actor disappeared before observation");
        if (!input.lookAt(entity().getBoundingBox().getCenter())) return false;
        var camera = FrameCapture.presentation().camera();
        if (camera == null || Math.abs(Mth.wrapDegrees(camera.yaw() - client.player.getYRot())) > .2
            || Math.abs(camera.pitch() - client.player.getXRot()) > .2) return false;
        next(Step.DROPPED);
      }
      case DROPPED -> {
        if (!capture("carry-" + name() + "-dropped", false)) return false;
        next(Step.AIM);
      }
      case AIM -> {
        check(entity() != null, "Carry target disappeared before native regrab");
        if (client.player.getEyePosition().distanceTo(entity().getBoundingBox().getCenter()) > 1.8) {
          Vec3 offset = entity().position().subtract(client.player.position()).multiply(1, 0, 1);
          Vec3 approach = client.player.position().add(offset.normalize().scale(.35));
          input.walkTo(approach);
          return false;
        }
        input.stop();
        if (!input.lookAt(entity().getBoundingBox().getCenter())) return false;
        next(Step.REGRAB);
      }
      case REGRAB -> {
        if (!clicked) { input.grab(); clicked = true; }
        if (!held() || age < 15) return false;
        next(Step.REHELD);
      }
      case REHELD -> {
        if (!capture("carry-" + name() + "-regrabbed", false)) return false;
        next(Step.RETURN_TO_OBSERVER);
      }
      case RETURN_TO_OBSERVER -> {
        check(held(), "Regrabbed target was released while walking: clientTarget="
            + PortalixClient.carryState().localTarget() + ", actor=" + actor + ", observed=" + observed);
        if (!input.walkTo(EntitySystemFixture.OBSERVER)) return false;
        next(Step.THROW_AIM);
      }
      case THROW_AIM -> {
        check(held(), "Regrabbed target was released while aiming: clientTarget="
            + PortalixClient.carryState().localTarget() + ", actor=" + actor + ", observed=" + observed);
        if (!input.lookAt(client.player.getEyePosition().add(0, .5, 10)) || age < 20) return false;
        throwStart = entity().position();
        next(Step.THROW);
      }
      case THROW -> {
        if (!clicked) { input.click(client.options.keyAttack); clicked = true; }
        if (!released()) return false;
        next(Step.FLIGHT);
      }
      case FLIGHT -> {
        if (age < 6 || observed == null || entity() == null) return false;
        check(entity().position().distanceTo(throwStart) > 1 && observed.position().distanceTo(throwStart) > 1,
            "Native throw input did not physically launch the tracked target: " + name());
        next(Step.THROWN);
      }
      case THROWN -> {
        if (!capture("carry-" + name() + "-thrown", false)) return false;
        mutation = server.submit(() -> fixture.remove(actor));
        next(Step.REMOVE);
      }
      case REMOVE -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        next(Step.REMOVED);
      }
      case REMOVED -> {
        if (client.level.getEntity(actor.id()) != null) return false;
        next(Step.ABSENT);
      }
      case ABSENT -> {
        if (capture == null) capture = frames.checkpoint("carry-" + name() + "-removed-empty");
        if (!capture.isDone()) return false;
        var removed = capture.join();
        capture = null;
        visibility.put("thrownNativeAbsence", EntityScenePixels.visible(thrown, thrownBounds, actor.kind(), true,
            thrownControl, removed));
        visibility.put("nativeRemovalAbsence", removed.metadata());
        visibility.put("actorUuid", actor.uuid());
        EntityScenePixels.save(frames.directory().resolve("carry-" + name() + "-visibility-proof.json"), visibility);
        visibility.clear();
        passed.add("carry/" + name() + " actual input, ownership, camera following, drop gravity, regrab and throw");
        actor = null;
        observed = null;
        observation = null;
        if (++index == KINDS.length) next(Step.DONE);
        else next(Step.EMPTY);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private void poll() {
    if (observation == null) observation = client.getSingleplayerServer().submit(() -> fixture.observe(actor));
    if (observation.isDone()) { observed = observation.join(); observation = null; }
  }

  private boolean held() {
    return PortalixClient.carryState().localTarget() == actor.id() && observed != null
        && observed.heldBy() == client.player.getId();
  }

  private boolean released() {
    return PortalixClient.carryState().localTarget() < 0 && observed != null && observed.heldBy() < 0;
  }

  private boolean capture(String name, boolean initial) {
    if (capture == null) capture = frames.reservedCheckpoint(name, () -> {
      var entity = entity();
      check(entity != null, "Native carry target disappeared before frame copy: " + actor);
      float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(
          !client.level.tickRateManager().isEntityFrozen(entity));
      var carry = PortalixClient.carryController();
      Vec3 center = carry.renderCenter(entity, partial);
      Vec3 feet = center == null ? PortalEyePose.renderedFeet(entity, partial)
          : center.add(0, -entity.getBbHeight() / 2, 0);
      Quaternionf carryRotation = carry.renderRotation(entity, partial);
      Quaternionf rotation = carryRotation == null ? new Quaternionf() : new Quaternionf(carryRotation);
      if (entity instanceof ItemEntity item) rotation.mul(PortalItemRotation.rotation(item));
      var body = entity.getBoundingBox().move(entity.position().scale(-1));
      double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
      double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
      for (int corner = 0; corner < 8; corner++) {
        var point = rotation.transform(new Vector3f((float) ((corner & 1) == 0 ? body.minX : body.maxX),
            (float) ((corner & 2) == 0 ? body.minY : body.maxY),
            (float) ((corner & 4) == 0 ? body.minZ : body.maxZ)));
        minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
        maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
      }
      captureBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(feet).inflate(.1);
      capturePose = Map.of("partialTick", partial, "physicalPosition", entity.position(),
          "physicalVelocity", entity.getDeltaMovement(), "physicalBounds", entity.getBoundingBox(),
          "renderFeet", feet, "renderRotation", rotation, "renderBounds", captureBounds,
          "predicted", carry.predicted(entity));
      return true;
    });
    if (!capture.isDone()) return false;
    var frame = capture.join();
    capture = null;
    var pixels = initial
        ? EntityScenePixels.visible(frame, captureBounds, actor.kind(), true, INITIAL_QUARTZ_CONTROL, empty)
        : EntityScenePixels.visible(frame, captureBounds, actor.kind(), true);
    pixels.put("renderPose", capturePose);
    visibility.put(step.name().toLowerCase(java.util.Locale.ROOT), pixels);
    if (initial) {
      before = frame;
    } else if (step == Step.HELD) {
      var hand = new FrameCapture.Region(frame.width() / 2, frame.height() * 3 / 5,
          frame.width() / 2, frame.height() * 2 / 5);
      check(frame.difference(before, hand, 24).changedPixels() > 50,
          "Native grab did not alter the rendered gun/holding posture");
    }
    if (step == Step.THROWN) {
      thrown = frame; thrownBounds = captureBounds;
      Vec3 observer = frame.cameraPosition();
      thrownControl = new AABB(observer.x + 3, 99.99, observer.z + 8,
          observer.x + 4, 100.01, observer.z + 10);
    }
    return true;
  }

  private Entity entity() {
    Entity entity = client.level.getEntity(actor.id());
    if (entity != null) check(entity.getUUID().equals(actor.uuid()), "Native carry tracking changed target UUID");
    return entity;
  }

  private String name() { return KINDS[index].name().toLowerCase(java.util.Locale.ROOT); }
  private void next(Step step) { this.step = step; age = 0; clicked = false; }
  @Override public List<String> passed() { return List.copyOf(passed); }
  @Override public CompletableFuture<Void> cleanup() {
    input.stop();
    client.options.setCameraType(camera);
    if (preparation == null) return CompletableFuture.completedFuture(null);
    var server = client.getSingleplayerServer();
    return preparation.handle((owned, failure) -> owned).thenCompose(owned -> server.submit(() -> {
      if (owned != null) owned.close();
    }));
  }
}
