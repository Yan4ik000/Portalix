package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.test.WorldScenarioFixture;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalTopology;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

final class CombatSystemScene extends CourseSceneSupport {
  private enum Step { PREPARE, PAIR, READY, TARGET, WEAPON, ARM, HIT, RECOVER, DONE }
  private Step step = Step.PREPARE;
  private CompletableFuture<Void> pairing;
  private CompletableFuture<WorldScenarioFixture.ServerObservation> observation;
  private WorldScenarioFixture.ServerObservation state;
  private CompletableFuture<FrameCapture.Frame> hurtFrame;
  private FrameCapture.Frame baseline;
  private AABB hurtBounds;
  private float health;
  private boolean attacked;

  CombatSystemScene(Minecraft client, ScenarioFrames frames) { super(client, frames, WorldScenarioFixture.Area.COMBAT); }

  public boolean tick() {
    check(++age < 350, "Portal melee timed out at " + step);
    var course = WorldScenarioFixture.course();
    if (fixture != null) {
      if (observation != null && observation.isDone()) { state = observation.join(); observation = null; }
      if (observation == null) observation = client.getSingleplayerServer().submit(fixture::observe);
    }
    switch (step) {
      case PREPARE -> {
        if (!prepare(course.combatApproach(), course.combatBlueAim())) return false;
        pairing = pair(course.combatBlueAim(), course.combatOrangeAim(), net.minecraft.core.Direction.NORTH);
        next(Step.PAIR);
      }
      case PAIR -> {
        if (!pairing.isDone()) return false;
        pairing.join();
        next(Step.READY);
      }
      case READY -> {
        if (!input.lookAt(course.combatBlueAim()) || state == null || state.links().size() != 2) return false;
        var links = PortalTopology.links(client.level).stream()
            .filter(link -> link.gun().equals(fixture.gunId())).toList();
        if (links.size() != 2) return false;
        if (links.stream().noneMatch(PortalSceneManager::hasRenderedView)
            || links.stream().anyMatch(link -> !PortalOpeningReadiness.fullyOpen(link))) return false;
        if (client.level.getEntity(fixture.combatTarget().id()) == null) return false;
        var target = target();
        var chestplate = target.getItemBySlot(EquipmentSlot.CHEST);
        var dye = chestplate.get(DataComponents.DYED_COLOR);
        if (!chestplate.is(Items.LEATHER_CHESTPLATE) || dye == null || dye.rgb() != 0x00ff00
            || state.targetBlockLight() < 14
            || client.level.getBrightness(LightLayer.BLOCK, BlockPos.containing(target.getLightProbePosition(1))) < 14)
          return false;
        if (!(client.hitResult instanceof EntityHitResult hit) || hit.getEntity() != target()) return false;
        frames.monitor(true);
        next(Step.TARGET);
      }
      case TARGET -> {
        if (!capture("combat-target-visible", image -> {
          var region = image.project(mapped(target().getBoundingBox().inflate(.1))).orElseThrow();
          image.assertSprite(region, FrameCapture.Material.GREEN, 12, region.area(), 3, 3);
          check(client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target(),
              "Actual crosshair did not pick the visible target through the portal");
        })) return false;
        input.slot(1);
        next(Step.WEAPON);
      }
      case WEAPON -> {
        if (client.player.getInventory().getSelectedSlot() != 1 || age < 20) return false;
        if (!capture("combat-before-hit", image -> baseline = image)) return false;
        health = state.targetHealth();
        next(Step.ARM);
      }
      case ARM -> {
        hurtFrame = frames.reservedCheckpoint("combat-hurt", () -> {
          if (!attacked || target().hurtTime <= 0) return false;
          hurtBounds = target().getBoundingBox().inflate(.1);
          return true;
        });
        next(Step.HIT);
      }
      case HIT -> {
        if (!attacked) {
          if (!frames.reservedReady()) return false;
          input.click(client.options.keyAttack);
          attacked = true;
        }
        if (!hurtFrame.isDone() || state.targetHealth() >= health) return false;
        var image = hurtFrame.join();
        var region = image.project(mapped(hurtBounds)).orElseThrow();
        check(image.difference(baseline, region, 24).changedPixels() > 20,
            "Authoritative damage did not produce a visible hurt response");
        image.assertSprite(region, FrameCapture.Material.RED, 8, region.area(), 2, 2);
        check(state.targetPosition().distanceTo(course.combatTargetFeet()) > .01
            || state.targetVelocity().lengthSqr() > .001, "Portal damage lost physical knockback");
        next(Step.RECOVER);
      }
      case RECOVER -> {
        if (target().hurtTime != 0) return false;
        check(target().isAlive() && target().getHealth() < health, "Client did not retain the confirmed nonfatal damage");
        if (capture("combat-recovered", FrameCapture.Frame::assertNotBlack)) next(Step.DONE);
      }
      case DONE -> { return true; }
    }
    return step == Step.DONE;
  }

  private LivingEntity target() {
    var entity = client.level.getEntity(fixture.combatTarget().id());
    check(entity instanceof LivingEntity && entity.getUUID().equals(fixture.combatTarget().uuid()), "Combat actor lost identity");
    return (LivingEntity) entity;
  }
  private static AABB mapped(AABB box) {
    return new AABB(57 - box.maxX, box.minY, 32 - box.maxZ, 57 - box.minX, box.maxY, 32 - box.minZ);
  }
  private void next(Step next) { step = next; age = 0; }
  public List<String> passed() {
    return step == Step.DONE ? List.of("combat/native crosshair, melee input, authoritative damage and knockback, visible hurt and recovery") : List.of();
  }
}
