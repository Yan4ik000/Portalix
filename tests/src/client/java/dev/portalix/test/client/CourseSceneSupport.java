package dev.portalix.test.client;

import dev.portalix.test.WorldScenarioFixture;
import dev.portalix.test.client.mixin.PortalServerSessionAccessor;
import dev.portalix.test.client.mixin.PortalServerEventsAccessor;
import dev.portalix.test.client.mixin.ServerChunkCacheAccessor;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;

abstract class CourseSceneSupport implements ClientSystemScene {
  protected final Minecraft client;
  protected final ScenarioFrames frames;
  protected final ScenarioInput input;
  protected WorldScenarioFixture fixture;
  protected int age;
  private final CameraType camera;
  private final int fov;
  private final Vec3 position;
  private final float yaw;
  private final float pitch;
  protected final java.util.UUID playerId;
  private final WorldScenarioFixture.Area area;
  private CompletableFuture<WorldScenarioFixture> preparation;
  private CompletableFuture<FrameCapture.Frame> screenshot;

  CourseSceneSupport(Minecraft client, ScenarioFrames frames, WorldScenarioFixture.Area area) {
    this.client = client;
    this.frames = frames;
    input = new ScenarioInput(client);
    camera = client.options.getCameraType();
    fov = client.options.fov().get();
    position = client.player.position();
    yaw = client.player.getYRot();
    pitch = client.player.getXRot();
    playerId = client.player.getUUID();
    this.area = area;
  }

  protected CompletableFuture<Void> pair(Vec3 blue, Vec3 orange, net.minecraft.core.Direction face) {
    var server = client.getSingleplayerServer();
    return server.submit(() -> {
      var owner = server.getPlayerList().getPlayer(playerId);
      var session = PortalServerEventsAccessor.portalix$session();
      var registry = ((PortalServerSessionAccessor) (Object) session).portalix$registry();
      for (var color : dev.portalix.api.PortalColor.values()) {
        var center = color == dev.portalix.api.PortalColor.BLUE ? blue : orange;
        var normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        var endpoint = new dev.portalix.api.PortalEndpoint(owner.level().dimension(), center,
            normal, new Vec3(0, 1, 0), 1, 2);
        ClientAssertions.check(registry.apply(owner, fixture.gunId(), color, endpoint,
            dev.portalix.item.PortalixVariant.STANDARD), "Native scene portal placement failed");
      }
    });
  }

  protected boolean prepare(Vec3 feet, Vec3 aim) {
    if (preparation == null) {
      var server = client.getSingleplayerServer();
      var uuid = client.player.getUUID();
      client.options.setCameraType(CameraType.FIRST_PERSON);
      preparation = server.submit(() -> {
        var owner = server.getPlayerList().getPlayer(uuid);
        var owned = WorldScenarioFixture.build(owner.level(), area);
        try {
          owned.bind(owner);
          Vec3 direction = aim.subtract(feet.add(0, owner.getEyeHeight(), 0));
          float lookYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
          float lookPitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
          // Apply native fixture blocks before the position packet puts the observer into the new scene.
          ((ServerChunkCacheAccessor) (Object) owner.level().getChunkSource()).portalix$broadcastChangedChunks(Profiler.get());
          ClientAssertions.check(owner.teleportTo(owner.level(), feet.x, feet.y, feet.z,
              Set.of(), lookYaw, lookPitch, true), "Scene setup teleport failed");
          return owned;
        } catch (RuntimeException | Error failure) {
          try { owned.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
          throw failure;
        }
      });
      return false;
    }
    if (!preparation.isDone()) return false;
    fixture = preparation.join();
    if (client.player.position().distanceTo(feet) > .1 || client.gui.overlay() != null
        || !client.levelRenderer.hasRenderedAllSections()) return false;
    return true;
  }

  protected boolean capture(String name, Consumer<FrameCapture.Frame> assertion) {
    if (screenshot == null) { screenshot = frames.checkpoint(name); return false; }
    if (!screenshot.isDone()) return false;
    var image = screenshot.join();
    screenshot = null;
    assertion.accept(image);
    return true;
  }

  @Override
  public CompletableFuture<Void> cleanup() {
    input.stop();
    client.options.setCameraType(camera);
    client.options.fov().set(fov);
    var server = client.getSingleplayerServer();
    if (preparation == null || server == null) return CompletableFuture.completedFuture(null);
    return preparation.handle((owned, failure) -> owned).thenCompose(owned -> server.submit(() -> {
      if (owned == null) return;
      var session = PortalServerEventsAccessor.portalix$session();
      if (session != null) ((PortalServerSessionAccessor) (Object) session).portalix$registry().clear(owned.gunId());
      owned.close();
      var owner = server.getPlayerList().getPlayer(playerId);
      if (owner != null) owner.teleportTo(owner.level(), position.x, position.y, position.z, Set.of(), yaw, pitch, true);
    }));
  }
}
