package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.google.gson.GsonBuilder;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.portal.PortalTopology;
import dev.portalix.test.WorldScenarioFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

final class VehicleTraversalScenario {
  private enum Step {
    BLUE_AIM, BLUE_FIRE, ORANGE_AIM, ORANGE_FIRE, READY_VIEW,
    BOARD_APPROACH, BOARD_TARGET, SPAWN_CART, CART_VISIBLE, CART_PROOF, BOARD, MOUNTED_VIEW, LEVER,
    RIDE_AIM, RIDING, EXIT_SETTLE, DISMOUNT, FINAL_FRAME, DONE
  }

  private static final Component STATION_CAPTION = Component.literal(
      "Passenger transport: station before and after native minecart spawn");
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final WorldScenarioFixture fixture;
  private final WorldScenarioFixture.VehicleCourse course = WorldScenarioFixture.vehicleCourse();
  private final ScenarioInput input;
  private final List<String> passed = new ArrayList<>();
  private Step step = Step.BLUE_AIM;
  private int age = -1;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private CompletableFuture<WorldScenarioFixture.VehicleObservation> observation;
  private CompletableFuture<Boolean> readiness;
  private CompletableFuture<Void> spawn;
  private CompletableFuture<Void> cartProof;
  private CompletableFuture<Void> boardingSetup;
  private FrameCapture.Frame emptyStation;
  private Vec3 stationObserver;
  private boolean sourceReady;
  private WorldScenarioFixture.VehicleObservation server;

  VehicleTraversalScenario(Minecraft client, ScenarioFrames frames, WorldScenarioFixture fixture) {
    this.client = client;
    this.frames = frames;
    this.fixture = fixture;
    input = new ScenarioInput(client);
    check(client.options.getCameraType() == CameraType.FIRST_PERSON,
        "Vehicle traversal must begin in the normal first-person camera");
  }

  String title() {
    return "Passenger transport: " + step.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
  }

  List<String> passed() { return List.copyOf(passed); }

  boolean tick() {
    if (step == Step.DONE) return true;
    check(++age < 700, "Passenger transport timed out during " + step + ", player="
        + client.player.position() + ", observed=" + server);
    observe();
    if (step == Step.BOARD_TARGET || step == Step.SPAWN_CART || step == Step.CART_VISIBLE || step == Step.CART_PROOF)
      client.gui.hud.setOverlayMessage(STATION_CAPTION, false);
    switch (step) {
      case BLUE_AIM -> {
        if (age == 0) input.slot(0);
        if (input.lookAt(course.sourceAim()) && age >= 35 && gunReady()) next(Step.BLUE_FIRE);
      }
      case BLUE_FIRE -> {
        if (age == 0) input.click(client.options.keyAttack);
        if (age >= 40 && PortalixClient.portals().activeMask(fixture.gunId()) == 1) {
          check(PortalTopology.links(client.level).stream().noneMatch(link -> link.gun().equals(fixture.gunId())),
              "The first vehicle portal became traversable before the second native shot");
          next(Step.ORANGE_AIM);
        }
      }
      case ORANGE_AIM -> {
        if (input.lookAt(course.exitAim()) && age >= 25) next(Step.ORANGE_FIRE);
      }
      case ORANGE_FIRE -> {
        if (age == 0) input.click(client.options.keyUse);
        if (age >= 40 && hasEntrance(course.exitAim())) next(Step.READY_VIEW);
      }
      case READY_VIEW -> {
        if (!input.lookAt(course.sourceAim()) || age < 35 || !sourceReady) return false;
        check(renderedSource(), "The mounted route must be visible before activating its real powered rails");
        next(Step.BOARD_APPROACH);
      }
      case BOARD_APPROACH -> {
        if (boardingSetup == null) {
          if (!frames.pause()) return false;
          var server = client.getSingleplayerServer();
          var uuid = client.player.getUUID();
          boardingSetup = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var feet = course.boardingFeet();
            check(player.teleportTo(player.level(), feet.x, feet.y, feet.z, java.util.Set.of(), 90, 0, true),
                "Boarding setup teleport failed");
          });
          return false;
        }
        if (!boardingSetup.isDone()) return false;
        boardingSetup.join();
        if (client.player.position().distanceTo(course.boardingFeet()) > .1) return false;
        next(Step.BOARD_TARGET);
      }
      case BOARD_TARGET -> {
        if (age == 0) input.slot(2);
        if (!input.lookAt(course.cartStart().add(0, .35, 0)) || age < 35
            || !PortalixClient.displayedGun().isEmpty()) return false;
        var camera = FrameCapture.presentation().camera();
        if (!client.player.onGround() || client.level.getLightEngine().hasLightWork() || camera == null
            || camera.position().distanceTo(client.player.getEyePosition()) > .05
            || Math.abs(Mth.wrapDegrees(camera.yaw() - client.player.getYRot())) > .05
            || Math.abs(camera.pitch() - client.player.getXRot()) > .05) return false;
        frames.monitor(true);
        check(client.level.getEntity(fixture.vehicleCart().id()) == null,
            "The empty station baseline already contains its minecart");
        for (var entity : client.level.entitiesForRendering())
          check(!entity.getUUID().equals(fixture.vehicleCart().uuid()),
              "The empty station baseline already tracks its minecart UUID");
        if (capture("vehicle-empty-station", frame -> {
          frame.assertNotBlack();
          emptyStation = frame;
          stationObserver = client.player.position();
        })) next(Step.SPAWN_CART);
      }
      case SPAWN_CART -> {
        if (age == 0) spawn = client.getSingleplayerServer().submit(fixture::spawnVehicleCart);
        if (!spawn.isDone()) return false;
        spawn.join();
        var entity = client.level.getEntity(fixture.vehicleCart().id());
        if (entity == null || server == null || age < 35) return false;
        check(entity.getUUID().equals(fixture.vehicleCart().uuid()), "The station cart has the wrong tracked identity");
        check(entity.position().distanceTo(course.cartStart()) < .2
            && server.cartPosition().distanceTo(course.cartStart()) < .2,
            "The native spawned minecart moved away from its unpowered station");
        next(Step.CART_VISIBLE);
      }
      case CART_VISIBLE -> {
        check(client.player.position().distanceTo(stationObserver) < .002,
            "The observer moved between empty and occupied station images");
        check(client.hitResult instanceof EntityHitResult hit && hit.getEntity() == cart(),
            "The station cart cannot be targeted with ordinary empty-hand picking");
        if (capture("vehicle-cart-before-boarding", frame -> {
          frame.assertNotBlack();
          var roi = frame.project(cart().getBoundingBox().inflate(.05)).orElseThrow();
          double distance = frame.cameraPosition().distanceTo(emptyStation.cameraPosition());
          double pitch = Math.abs(frame.cameraPitch() - emptyStation.cameraPitch());
          double yaw = Math.abs(Mth.wrapDegrees(frame.cameraYaw() - emptyStation.cameraYaw()));
          check(distance < .002 && pitch < .05 && yaw < .05,
              "Empty and occupied station images used different actual camera poses");
          var control = new FrameCapture.Region(frame.width() / 12, frame.height() / 3,
              frame.width() / 8, frame.height() / 5);
          var background = frame.difference(emptyStation, control, 24);
          check(background.changedFraction() < .03 && background.meanChannelDifference() < 5,
              "The station background changed independently of the spawned minecart: " + background);
          var difference = frame.difference(emptyStation, roi, 24);
          // The upper body excludes the floor shadow and the thin rail selection outline.
          var upperBody = new FrameCapture.Region(roi.x(), roi.y(), roi.width(), Math.max(1, roi.height() * 2 / 3));
          var upperDifference = frame.difference(emptyStation, upperBody, 24);
          check(upperDifference.changedFraction() > .05 && upperDifference.changedPixels() > 100,
              "Spawning the real minecart did not add its visible body above the empty station: " + upperDifference);
          var proof = new LinkedHashMap<String, Object>();
          proof.put("emptyFrame", emptyStation.metadata());
          proof.put("visibleFrame", frame.metadata());
          proof.put("cartUuid", cart().getUUID().toString());
          proof.put("cameraDistance", distance); proof.put("pitchDelta", pitch); proof.put("yawDelta", yaw);
          proof.put("controlRegion", control); proof.put("controlDifference", background);
          proof.put("cartRegion", roi); proof.put("cartDifference", difference);
          proof.put("upperBodyRegion", upperBody); proof.put("upperBodyDifference", upperDifference);
          cartProof = CompletableFuture.runAsync(() -> {
            try {
              Files.writeString(frames.directory().resolve("vehicle-cart-proof.json"),
                  new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
          }, Util.ioPool());
        })) next(Step.CART_PROOF);
      }
      case CART_PROOF -> {
        if (!cartProof.isDone()) return false;
        cartProof.join();
        next(Step.BOARD);
      }
      case BOARD -> {
        if (age == 0) input.click(client.options.keyUse);
        if (mountedBothSides()) next(Step.MOUNTED_VIEW);
      }
      case MOUNTED_VIEW -> {
        check(mountedBothSides(), "Mounting lost its vanilla passenger relationship before rail activation");
        if (!input.lookAt(course.sourceAim()) || age < 35 || !server.sourceReady()) return false;
        if (capture("vehicle-mounted-before-power", frame -> {
          frame.assertNotBlack();
          check(cart().position().distanceTo(course.cartStart()) < .2,
              "The unpowered cart moved before the player activated the lever");
        })) next(Step.LEVER);
      }
      case LEVER -> {
        check(mountedBothSides(), "The player dismounted before pressing the actual station lever");
        var leverAim = Vec3.atCenterOf(course.lever()).add(0, -.375, 0);
        if (!input.lookAt(leverAim)) return false;
        check(client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(course.lever()),
            "The real station lever cannot be reached from the mounted passenger seat");
        input.click(client.options.keyUse);
        next(Step.RIDE_AIM);
      }
      case RIDE_AIM -> {
        check(mountedBothSides(), "The passenger detached while looking down the approach track");
        if (!input.lookAt(course.sourceAim())) return false;
        next(Step.RIDING);
      }
      case RIDING -> {
        check(mountedBothSides(), "Crossing packets detached the player from the real minecart");
        boolean exited = cart().getX() > 48;
        if (server == null || !server.leverPowered() || !exited || server.cartPosition().x < 48) return false;
        check(server.cartLastExit().distanceTo(course.exitAim()) < .1
            && server.playerLastExit().distanceTo(course.exitAim()) < .1,
            "Cart and passenger did not publish the same actual portal crossing");
        if (capture("vehicle-riding-at-exit", frame -> {
          frame.assertNotBlack();
          check(mountedBothSides(), "The final image lost the vehicle/passenger relationship");
          check(Math.abs(cart().getZ() - course.exitAim().z) < .25
              && Math.abs(server.cartPosition().z - course.exitAim().z) < .25,
              "Vehicle transport reached the wrong authored exit lane");
        })) next(Step.EXIT_SETTLE);
      }
      case EXIT_SETTLE -> {
        check(mountedBothSides(), "The player was detached while the exit braking rails slowed the cart");
        if (age < 30 || server.cartVelocity().horizontalDistance() > .03
            || cart().position().distanceTo(server.cartPosition()) > .3) return false;
        check(server.cartPosition().x > 49 && server.cartPosition().x < 56,
            "The real unpowered braking rails failed to retain the vehicle on the exit platform");
        next(Step.DISMOUNT);
      }
      case DISMOUNT -> {
        client.options.keyShift.setDown(true);
        if (client.player.getVehicle() == null && server != null && server.playerVehicleId() < 0) {
          client.options.keyShift.setDown(false);
          next(Step.FINAL_FRAME);
        }
      }
      case FINAL_FRAME -> {
        if (age == 0) input.slot(0);
        if (!input.lookAt(course.exitAim()) || age < 40 || !gunReady() || server == null) return false;
        check(client.player.getVehicle() == null && server.playerVehicleId() < 0
            && server.passengerUuids().isEmpty(), "Ordinary sneak dismount left a stale passenger binding");
        check(client.player.getBoundingBox().getYsize() > 1.7 && server.playerHeight() > 1.7,
            "Passenger traversal did not restore the full standing player body");
        check(client.options.getCameraType() == CameraType.FIRST_PERSON,
            "Passenger travel changed the selected normal camera");
        if (capture("vehicle-dismounted-body-restored", FrameCapture.Frame::assertNotBlack)) {
          passed.add("gameplay/real rail-powered minecart and player cross together, retain riding state and dismount normally");
          next(Step.DONE);
        }
      }
      default -> throw new AssertionError("Unexpected vehicle step " + step);
    }
    return step == Step.DONE;
  }

  private Entity cart() {
    var entity = client.level.getEntity(fixture.vehicleCart().id());
    check(entity != null && entity.getUUID().equals(fixture.vehicleCart().uuid()), "The tracked minecart is missing");
    return entity;
  }

  private boolean mountedBothSides() {
    var cart = cart();
    return client.player.getVehicle() == cart && cart.getPassengers().contains(client.player)
        && server != null && server.playerVehicleId() == cart.getId()
        && server.playerVehicleUuid().equals(cart.getUUID())
        && server.passengerUuids().contains(client.player.getUUID());
  }

  private boolean hasEntrance(Vec3 point) {
    return PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
        && link.entrance().endpoint().center().distanceTo(point) < .1);
  }

  private boolean renderedSource() {
    return PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
        && link.entrance().endpoint().center().distanceTo(course.sourceAim()) < .1
        && PortalSceneManager.hasRenderedView(link));
  }

  private boolean gunReady() {
    return fixture.gunId().equals(PortalixIdentity.get(client.player.getMainHandItem()))
        && !PortalixClient.localAnimation().isDrawing() && !PortalixClient.localAnimation().isHolstering();
  }

  private void observe() {
    if (readiness != null && readiness.isDone()) {
      sourceReady = readiness.join();
      readiness = null;
    }
    if (spawn == null) {
      if (readiness == null && age % 2 == 0)
        readiness = client.getSingleplayerServer().submit(fixture::vehicleSourceReady);
      return;
    }
    if (!spawn.isDone()) return;
    spawn.join();
    if (observation != null && observation.isDone()) {
      server = observation.join();
      observation = null;
    }
    if (observation == null && age % 2 == 0)
      observation = client.getSingleplayerServer().submit(fixture::observeVehicle);
  }

  private boolean capture(String name, Consumer<FrameCapture.Frame> assertion) {
    if (screenshot == null) {
      screenshot = frames.checkpoint(name);
      return false;
    }
    if (!screenshot.isDone()) return false;
    var frame = screenshot.join();
    screenshot = null;
    assertion.accept(frame);
    return true;
  }

  private void next(Step next) {
    check(screenshot == null, "Vehicle transition has an unfinished frame capture");
    input.stop();
    step = next;
    age = -1;
  }
}
