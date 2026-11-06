package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.portal.PortalTopology;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

final class ResourcesSystemScene extends PortalPreparedScene {
  private enum Step { PREPARE, BEFORE, DRAIN, RELOAD, MUTATE, AFTER, DONE }
  private Step step = Step.PREPARE;
  private CompletableFuture<FrameCapture.Frame> screenshot;
  private CompletableFuture<Void> reload;
  private CompletableFuture<Void> mutation;
  private FrameCapture.Frame before;
  private PortalTerrainUploadProbe uploads;
  private int age;

  ResourcesSystemScene(Minecraft client, ScenarioFrames frames) {
    super(client, frames);
  }

  @Override public boolean tick() {
    check(++age < 600, "Resource reload timed out at " + step);
    switch (step) {
      case PREPARE -> {
        if (prepare(224, 512)) next(Step.BEFORE);
      }
      case BEFORE -> {
        if (screenshot == null) screenshot = frames.checkpoint("resources-before");
        if (!screenshot.isDone()) return false;
        before = screenshot.join();
        var marker = arrangement.markers().getFirst();
        before.assertMaterial(before.project(arrangement.imageBounds(marker.bounds())).orElseThrow(),
            FrameCapture.Material.RED, .05, 1);
        screenshot = null;
        next(Step.DRAIN);
      }
      case DRAIN -> {
        if (!frames.pause()) return false;
        reload = client.reloadResourcePacks();
        next(Step.RELOAD);
      }
      case RELOAD -> {
        if (!reload.isDone() || client.gui.overlay() != null) return false;
        reload.join();
        if (IrisShaders.active()) {
          var shaders = java.util.ServiceLoader.load(ClientRegressionRunner.ShaderEnvironment.class)
              .findFirst().orElseThrow();
          shaders.reloadShadersAfterResourceReload();
          shaders.verify();
        }
        uploads = new PortalTerrainUploadProbe(arrangement.markers().getFirst().bounds());
        mutation = originalServer.submit(() -> {
          var box = arrangement.markers().getFirst().bounds();
          fixture.fill(BlockPos.containing(box.minX, box.minY, box.minZ),
              BlockPos.containing(box.maxX - .01, box.maxY - .01, box.maxZ - .01),
              Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState());
        });
        next(Step.MUTATE);
      }
      case MUTATE -> {
        if (!mutation.isDone()) return false;
        mutation.join();
        if (!terrainReady()) return false;
        frames.monitor(true);
        next(Step.AFTER);
      }
      case AFTER -> {
        if (screenshot == null) screenshot = frames.reservedCheckpoint("resources-after", this::terrainReady);
        if (!screenshot.isDone()) return false;
        var image = screenshot.join();
        var box = arrangement.imageBounds(arrangement.markers().getFirst().bounds());
        var region = image.project(box).orElseThrow();
        image.assertMaterial(region, FrameCapture.Material.MAGENTA, .05, 1);
        check(image.difference(before, region, 24).changedFraction() > .1,
            "Reloaded portal retained its old image after a remote block update");
        uploads.close();
        uploads = null;
        next(Step.DONE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private boolean terrainReady() {
    var marker = BlockPos.containing(arrangement.markers().getFirst().bounds().getCenter());
    return client.level.getBlockState(marker).equals(Blocks.CONCRETE.pick(DyeColor.MAGENTA).defaultBlockState())
        && PortalTopology.links(client.level).stream().anyMatch(link -> link.gun().equals(fixture.gunId())
            && link.generation() == arrangement.generation()
            && link.entrance().endpoint().equals(arrangement.entrance())
            && PortalOpeningReadiness.fullyOpen(link) && uploads.complete(link));
  }

  @Override public CompletableFuture<Void> cleanup() {
    if (uploads != null) uploads.close();
    return super.cleanup();
  }

  @Override public List<String> passed() {
    return step == Step.DONE
        ? List.of("resources/native resource reload displays fresh remote world changes") : List.of();
  }

  private void next(Step next) { step = next; age = 0; }
}
