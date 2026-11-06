package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.PortalixClient;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import dev.portalix.test.WorldScenarioFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class GunSystemScene extends CourseSceneSupport {
  private enum Step { PREPARE, VARIANTS, AIM, BASELINE, FIRE, OBSERVE,
    RESTORE, BLUE_AIM, BLUE_FIRE, BLUE_RESULT, ORANGE_AIM, ORANGE_FIRE, ORANGE_RESULT, RESET, RESET_RESULT, DONE }
  private final List<String> passed = new ArrayList<>();
  private Step step = Step.PREPARE;
  private GunVariantsScenario variants;
  private CompletableFuture<List<String>> shotResult;
  private FrameCapture.Frame baseline;
  private VisualShotProbe probe;
  private CompletableFuture<FrameCapture.Frame> shotBaseline;
  private int shot;
  private boolean fired;
  private int visibleSince = -1;
  private FrameCapture.Frame paired;
  private PortalLink closingLink;
  private final boolean hidden;
  private final boolean vignette;
  private final int fov;

  GunSystemScene(Minecraft client, ScenarioFrames frames) {
    super(client, frames, WorldScenarioFixture.Area.GUN);
    hidden = client.gui.hud.isHidden();
    vignette = client.options.vignette().get();
    fov = client.options.fov().get();
  }

  public boolean tick() {
    check(++age < 1200, "Gun scene timed out at " + step + "/" + shot);
    var course = WorldScenarioFixture.course();
    switch (step) {
      case PREPARE -> {
        if (!prepare(course.spawn(), course.traversalBlueAim())) return false;
        if (client.player.getInventory().getSelectedSlot() != 0) { input.slot(0); return false; }
        if (!GunEquipReadiness.ready(client, fixture.gunId(), PortalixVariant.STANDARD) || !terrainReady()) return false;
        frames.monitor(true);
        next(Step.VARIANTS);
      }
      case VARIANTS -> {
        if (variants == null) {
          if (client.player.getInventory().getSelectedSlot() != 0
              || !GunEquipReadiness.ready(client, fixture.gunId(), PortalixVariant.STANDARD)) return false;
          // Paired model images need identical lighting, without the HUD vignette's gradual adaptation.
          client.options.vignette().set(false);
          variants = new GunVariantsScenario(client, frames, fixture);
        }
        if (!variants.tick()) return false;
        passed.addAll(variants.passed());
        client.options.vignette().set(vignette);
        client.options.fov().set(40);
        next(Step.AIM);
      }
      case AIM -> {
        if (!input.lookAt(course.missAim())) return false;
        if (!client.gui.hud.isHidden()) { input.click(client.options.keyToggleGui); return false; }
        next(Step.BASELINE);
      }
      case BASELINE -> {
        if (shotBaseline == null) shotBaseline = frames.reservedCheckpoint("shot-" + shot + "-baseline",
            () -> GunEquipReadiness.ready(client, fixture.gunId(), PortalixVariant.STANDARD));
        if (!shotBaseline.isDone()) return false;
        baseline = shotBaseline.join(); shotBaseline = null;
        var hit = course.missAim();
        probe = VisualShotProbe.arm(baseline, new VisualShotProbe.Spec(
            new AABB(course.spawn().x + 5.5, hit.y - 1.5, hit.z - 2.5, hit.x - .5, hit.y + 2.7, hit.z + 2.5),
            new AABB(hit.x - .5, hit.y - 1.5, hit.z - 1.5, hit.x + .5, hit.y + 1.5, hit.z + 1.5),
            new AABB(8, 100, 7, 10, 100.05, 9), hit, shot == 1,
            frames.directory().resolve("shot-" + shot), PortalixVariant.STANDARD));
        frames.observeShot(probe);
        next(Step.FIRE);
      }
      case FIRE -> {
        if (!GunEquipReadiness.ready(client, fixture.gunId(), PortalixVariant.STANDARD) || !frames.shotReady()) return false;
        input.click(shot == 0 ? client.options.keyAttack : client.options.keyUse);
        fired = true;
        next(Step.OBSERVE);
      }
      case OBSERVE -> {
        if (shotResult == null && (probe.done() || age >= 180)) shotResult = frames.finishShot();
        if (shotResult == null || !shotResult.isDone()) return false;
        shotResult.join();
        check(fired && PortalixClient.portals().activeMask(fixture.gunId()) == 0,
            "Failed-surface shot incorrectly created a portal");
        passed.add("gun/standard"
            + (shot == 0 ? " primary" : " secondary") + " native shot palette, flight, impact and expiry");
        shotResult = null;
        fired = false;
        if (++shot == 2) next(Step.RESTORE);
        else next(Step.BASELINE);
      }
      case RESTORE -> {
        if (client.gui.hud.isHidden() != hidden) { input.click(client.options.keyToggleGui); return false; }
        client.options.fov().set(fov);
        if (capture("gun-shots-complete", FrameCapture.Frame::assertNotBlack)) next(Step.BLUE_AIM);
      }
      case BLUE_AIM -> {
        if (!input.lookAt(course.traversalBlueAim())) return false;
        if (!capture("gun-placement-before", FrameCapture.Frame::assertNotBlack)) return false;
        next(Step.BLUE_FIRE);
      }
      case BLUE_FIRE -> {
        input.click(client.options.keyAttack);
        next(Step.BLUE_RESULT);
      }
      case BLUE_RESULT -> {
        if (PortalixClient.portals().activeMask(fixture.gunId()) != 1) return false;
        if (visibleSince < 0) visibleSince = client.player.tickCount;
        if (client.player.tickCount - visibleSince < dev.portalix.client.portal.render.PortalOpeningAnimation.OPEN_SECONDS * 20) return false;
        if (!capture("gun-unpaired-blue", image -> {
          var region = image.project(new AABB(0, 100, -.02, 1, 102, .02)).orElseThrow();
          image.assertMaterial(region, FrameCapture.Material.BLUE, .01, .95);
          check(dev.portalix.portal.PortalTopology.links(client.level).isEmpty(),
              "An unpaired native shot became traversable");
        })) return false;
        visibleSince = -1;
        next(Step.ORANGE_AIM);
      }
      case ORANGE_AIM -> {
        if (input.lookAt(course.traversalOrangeAim())) next(Step.ORANGE_FIRE);
      }
      case ORANGE_FIRE -> {
        input.click(client.options.keyUse);
        next(Step.ORANGE_RESULT);
      }
      case ORANGE_RESULT -> {
        if (PortalixClient.portals().activeMask(fixture.gunId()) != 3
            || dev.portalix.portal.PortalTopology.links(client.level).size() != 2) return false;
        if (visibleSince < 0) visibleSince = client.player.tickCount;
        if (client.player.tickCount - visibleSince < dev.portalix.client.portal.render.PortalOpeningAnimation.LINK_SECONDS * 20) return false;
        if (!capture("gun-paired-orange", image -> {
          var region = image.project(new AABB(16, 100, -.02, 17, 102, .02)).orElseThrow();
          image.assertMaterial(region, FrameCapture.Material.ORANGE, .005, .95);
          paired = image;
        })) return false;
        closingLink = dev.portalix.portal.PortalTopology.links(client.level).stream()
            .filter(link -> link.gun().equals(fixture.gunId())).findFirst().orElseThrow();
        input.resetPortals();
        next(Step.RESET);
      }
      case RESET -> {
        if (PortalixClient.portals().activeMask(fixture.gunId()) != 0
            || !dev.portalix.portal.PortalTopology.links(client.level).isEmpty()
            || !PortalOpeningReadiness.fullyClosed(closingLink)) return false;
        next(Step.RESET_RESULT);
      }
      case RESET_RESULT -> {
        if (!capture("gun-reset-solid-wall", image -> {
          var region = image.project(new AABB(16, 100, -.02, 17, 102, .02)).orElseThrow();
          check(image.difference(paired, region, 24).changedFraction() > .05,
              "Reset removed topology but retained the portal image instead of the solid wall");
        })) return false;
        passed.add("gun/native primary and secondary placement, unpaired aperture, pairing and reset");
        next(Step.DONE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private void next(Step next) { step = next; age = 0; }

  private boolean terrainReady() {
    var course = WorldScenarioFixture.course();
    var floor = BlockPos.containing(course.spawn()).below();
    var camera = FrameCapture.presentation().camera();
    return camera != null && camera.position().distanceTo(client.player.getEyePosition()) < .05
        && client.player.onGround() && client.level.getBlockState(floor).is(Blocks.QUARTZ_BLOCK)
        && client.level.getBlockState(BlockPos.containing(course.missAim())).is(Blocks.IRON_BARS)
        && client.levelRenderer.isSectionCompiledAndVisible(floor);
  }

  public List<String> passed() { return List.copyOf(passed); }
  public CompletableFuture<Void> cleanup() {
    client.options.vignette().set(vignette);
    if (client.gui.hud.isHidden() != hidden) input.click(client.options.keyToggleGui);
    return super.cleanup();
  }
}
