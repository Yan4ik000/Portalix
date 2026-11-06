package dev.portalix.test.client;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

abstract class PortalPreparedScene implements ClientSystemScene {
  protected final Minecraft client;
  protected final ScenarioFrames frames;
  protected PortalSystemFixture fixture;
  protected PortalSystemFixture.Arrangement arrangement;
  protected final java.util.UUID playerId;
  private final CameraType camera;
  private CompletableFuture<PortalSystemFixture> preparation;
  private CompletableFuture<PortalSystemFixture.Arrangement> setup;
  protected final net.minecraft.client.server.IntegratedServer originalServer;

  PortalPreparedScene(Minecraft client, ScenarioFrames frames) {
    this.client = client;
    this.frames = frames;
    playerId = client.player.getUUID();
    camera = client.options.getCameraType();
    originalServer = client.getSingleplayerServer();
  }

  protected boolean prepare(int x, int z) {
    if (preparation == null) {
      client.options.setCameraType(CameraType.FIRST_PERSON);
      preparation = originalServer.submit(() ->
          new PortalSystemFixture(originalServer.getPlayerList().getPlayer(playerId), x, z));
      return false;
    }
    if (!preparation.isDone()) return false;
    fixture = preparation.join();
    if (setup == null) {
      setup = originalServer.submit(() -> fixture.prepare(Direction.SOUTH, Direction.SOUTH, 0, 3, false));
      return false;
    }
    if (!setup.isDone()) return false;
    arrangement = setup.join();
    if (client.player.position().distanceTo(arrangement.feet()) > .1 || !client.levelRenderer.hasRenderedAllSections()) return false;
    var links = PortalTopology.links(client.level).stream().filter(link -> link.gun().equals(fixture.gunId())
        && link.generation() == arrangement.generation()).toList();
    if (links.size() != 2) return false;
    if (links.stream().noneMatch(PortalSceneManager::hasRenderedView)
        || links.stream().anyMatch(link -> !PortalOpeningReadiness.fullyOpen(link))) return false;
    frames.monitor(true);
    return true;
  }

  public CompletableFuture<Void> cleanup() {
    client.options.setCameraType(camera);
    if (preparation == null || originalServer.isShutdown()) return CompletableFuture.completedFuture(null);
    return preparation.handle((owned, failure) -> owned).thenCompose(owned ->
        owned == null ? CompletableFuture.completedFuture(null) : originalServer.submit(owned::close));
  }
}
