package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalViewParallaxCase implements AutoCloseable {
  private final Minecraft client;
  private final ScenarioFrames frames;
  private final PortalSystemFixture.Arrangement scene;
  private final PortalLink link;
  private final ScenarioInput input;
  private final List<Particle> particles = new ArrayList<>();
  private final List<Map<String, Object>> proof = new ArrayList<>();
  private final AABB particleVolume;
  private CompletableFuture<FrameCapture.Frame> capture;
  private FrameCapture.Frame baseline;
  private FrameCapture.Frame moved;
  private AABB capturedParticles;
  private PortalSceneAssertions.Pixels absentParticles;
  private PortalSceneAssertions.Pixels visibleParticles;
  private int step;
  private int age;

  PortalViewParallaxCase(Minecraft client, ScenarioFrames frames, PortalSystemFixture.Arrangement scene,
                         PortalLink link) {
    this.client = client;
    this.frames = frames;
    this.scene = scene;
    this.link = link;
    input = new ScenarioInput(client);
    Vec3 center = scene.exit().center().add(scene.exit().normal().scale(2.3)).add(scene.exit().up().scale(.1));
    particleVolume = new AABB(center, center).inflate(.6);
  }

  boolean tick() {
    check(++age < 300, "Portal parallax/particle observation timed out at step " + step);
    switch (step) {
      case 0 -> {
        if (!image("before-strafe", frame -> {
          var marker = scene.markers().getLast();
          PortalSceneAssertions.projectedMarker(frame, scene.imageBounds(marker.bounds()), marker.material(), scene.entrance());
          baseline = frame;
        })) return false;
        client.options.keyRight.setDown(true);
        step = 1;
      }
      case 1 -> {
        Vec3 offset = client.player.getEyePosition().subtract(baseline.cameraPosition());
        check(Math.abs(offset.dot(scene.entrance().normal())) < .08 && Math.abs(offset.y) < .08,
            "Native strafe changed the authored viewing depth or height: " + offset);
        if (Math.abs(offset.dot(scene.entrance().right())) < .05) return false;
        input.stop();
        step = 2;
      }
      case 2 -> {
        if (!client.player.onGround() || client.player.getDeltaMovement().horizontalDistance() > .002) return false;
        if (!image("after-strafe", frame -> {
          assertParallax(frame);
          absentParticles = PortalSceneAssertions.pixels(frame, particleRegion(frame), FrameCapture.Material.CYAN, scene.entrance());
          moved = frame;
        })) return false;
        Vec3 center = particleVolume.getCenter();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
          Vec3 point = center.add(scene.exit().right().scale(x * .12)).add(scene.exit().up().scale(y * .12));
          Particle particle = client.particleEngine.createParticle(new DustParticleOptions(0x00ffff, 2),
              point.x, point.y, point.z, 0, 0, 0);
          check(particle != null, "Native particle engine rejected the authored dust particle");
          particle.setParticleSpeed(0, 0, 0);
          particle.setLifetime(80);
          particles.add(particle);
        }
        step = 3;
      }
      case 3 -> {
        if (!image("remote-particles", frame -> {
          sameCamera(frame);
          var region = particleRegion(frame);
          var colored = PortalSceneAssertions.sprite(frame, region, FrameCapture.Material.CYAN, 12, scene.entrance());
          var difference = PortalSceneAssertions.difference(frame, moved, region, 12, scene.entrance());
          check(colored.matched() - absentParticles.matched() >= 12 && difference.changedPixels() >= 12,
              "Native particles made no visible contribution beyond their empty-scene control");
          visibleParticles = colored;
          proof.getLast().put("nativeParticles", Map.of("count", particles.size(),
              "renderBounds", capturedParticles.toString(), "region", region,
              "baselineColoredPixels", absentParticles.matched(), "coloredPixels", colored.matched(), "difference", difference));
        })) return false;
        particles.forEach(Particle::remove);
        step = 4;
      }
      case 4 -> {
        if (!image("particles-removed", frame -> {
          sameCamera(frame);
          check(particles.stream().noneMatch(Particle::isAlive), "Native removed particles remained alive");
          var removed = PortalSceneAssertions.pixels(frame, particleRegion(frame), FrameCapture.Material.CYAN, scene.entrance());
          check(removed.matched() - absentParticles.matched()
                  <= (visibleParticles.matched() - absentParticles.matched()) * .1,
              "Native particle contribution did not disappear after removal: before=" + absentParticles
                  + ", present=" + visibleParticles + ", removed=" + removed);
          proof.getLast().put("removedParticlePixels", removed.matched());
          var marker = scene.markers().getLast();
          PortalSceneAssertions.projectedMarker(frame, scene.imageBounds(marker.bounds()), marker.material(), scene.entrance());
        })) return false;
        return true;
      }
      default -> throw new AssertionError("Unknown portal parallax step " + step);
    }
    return false;
  }

  List<Map<String, Object>> proof() { return List.copyOf(proof); }

  private void assertParallax(FrameCapture.Frame frame) {
    Vec3 movement = frame.cameraPosition().subtract(baseline.cameraPosition());
    check(Math.abs(movement.dot(scene.entrance().right())) > .15 && movement.length() < .5,
        "Parallax must follow a short native lateral movement: " + movement);
    check(Math.abs(frame.cameraPitch() - baseline.cameraPitch()) < .05
        && Math.abs(frame.cameraYaw() - baseline.cameraYaw()) < .05,
        "Parallax observation rotated the camera instead of translating it");
    var marker = scene.markers().stream().filter(value -> value.material() == FrameCapture.Material.BLUE).findFirst().orElseThrow();
    var bounds = scene.imageBounds(marker.bounds());
    var beforeSample = PortalSceneAssertions.projectedMarker(baseline, bounds, marker.material(), scene.entrance());
    var afterSample = PortalSceneAssertions.projectedMarker(frame, bounds, marker.material(), scene.entrance());
    var before = beforeSample.observed();
    var after = afterSample.observed();
    var expectedBefore = beforeSample.expected();
    var expectedAfter = afterSample.expected();
    double expectedX = expectedAfter.x() - expectedBefore.x();
    double expectedY = expectedAfter.y() - expectedBefore.y();
    double actualX = after.x() - before.x(), actualY = after.y() - before.y();
    double expectedDistance = Math.hypot(expectedX, expectedY);
    check(expectedDistance >= 12, "Authored camera movement produces too little remote parallax to discriminate a frozen view");
    check(Math.hypot(actualX - expectedX, actualY - expectedY) < Math.max(5, expectedDistance * .2),
        "Remote " + marker.material() + " did not follow the actual camera projection: expected="
            + expectedX + "," + expectedY + "; actual=" + actualX + "," + actualY);
    proof.getLast().put("parallax", Map.of("nativeCameraMovement", movement.toString(), "material", marker.material().name(),
        "before", beforeSample, "after", afterSample,
        "expectedShift", List.of(expectedX, expectedY), "actualShift", List.of(actualX, actualY)));
  }

  private FrameCapture.Region particleRegion(FrameCapture.Frame frame) {
    return PortalSceneAssertions.intersection(frame.project(scene.imageBounds(particleVolume)).orElseThrow(),
        PortalSceneAssertions.aperture(frame, scene.entrance(), .05));
  }

  private void sameCamera(FrameCapture.Frame frame) {
    check(frame.cameraPosition().distanceTo(moved.cameraPosition()) < .02
        && Math.abs(frame.cameraPitch() - moved.cameraPitch()) < .05
        && Math.abs(frame.cameraYaw() - moved.cameraYaw()) < .05,
        "The particle presence/removal control changed the camera");
  }

  private boolean image(String suffix, Consumer<FrameCapture.Frame> assertion) {
    String name = "view-parallax-and-native-particles-" + suffix;
    if (capture == null) {
      capture = frames.reservedCheckpoint(name, () -> {
        if (!client.levelRenderer.hasRenderedAllSections() || !PortalViewTerrainReadiness.complete(link)
            || !PortalSceneManager.hasRenderedView(link)) return false;
        if (step == 3) {
          if (particles.stream().anyMatch(particle -> !particle.isAlive()))
            throw new AssertionError("Native particles expired before their first presented observation");
          if (particles.stream().anyMatch(particle -> !(particle instanceof SingleQuadParticle sprite)
              || sprite.getQuadSize(0) <= 0 || sprite.getQuadSize(0) != sprite.getQuadSize(1))) return false;
          capturedParticles = particles.stream().map(particle -> particle.getBoundingBox().inflate(.25))
              .reduce(AABB::minmax).orElseThrow();
          check(particleVolume.contains(capturedParticles.getMinPosition())
              && particleVolume.contains(capturedParticles.getMaxPosition()),
              "Native dust moved outside its authored portal image control volume: " + capturedParticles);
        }
        return true;
      });
      return false;
    }
    if (!capture.isDone()) return false;
    var frame = capture.join(); capture = null;
    PortalSceneAssertions.projection(frame);
    var observation = new LinkedHashMap<String, Object>(frame.metadata());
    observation.put("case", "parallax-and-native-particles");
    observation.put("checkpoint", name);
    observation.put("generation", scene.generation());
    proof.add(observation);
    assertion.accept(frame);
    return true;
  }

  @Override public void close() {
    input.stop();
    particles.forEach(Particle::remove);
  }
}
