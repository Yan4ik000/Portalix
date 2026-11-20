package dev.portalix.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.item.PortalixVariant;
import dev.portalix.resource.ModAssetLoader;
import java.io.IOException;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParticleRecordingAssetsTest {
  @ParameterizedTest
  @ValueSource(floats = {0.05F, 0.1F, 0.15F, 0.2F, 0.5F})
  void projectileControlPointAndChildrenRespectPhysicalArrival(float flightTime) {
    var start = new Vec3(2, 3, 4);
    var end = new Vec3(11, 5, -7);
    var projectile = new PortalShotParticleSystem(
        start, end, new Vec3(1, 0, 0), false, flightTime, PortalixVariant.STANDARD);
    assertEquals(flightTime, projectile.stopTime);
    assertEquals(start, projectile.controlPoint(0));
    assertEquals(end, projectile.controlPoint(flightTime));
    if (flightTime < 0.2F) {
      float midpoint = flightTime * 0.5F;
      assertNotEquals(start, projectile.controlPoint(midpoint), "A short shot stays at the muzzle through mid-flight");
      projectile.advance(Math.nextDown(midpoint));
      for (var child : projectile.collections) {
        assertTrue(child.delay < midpoint, "A short-shot child starts after mid-flight");
        assertTrue(child.started, "A short-shot child did not start before mid-flight");
      }
    }
    projectile.advance(Math.nextDown(flightTime));
    assertFalse(projectile.collections.isEmpty());
    for (var child : projectile.collections) {
      assertTrue(child.delay < flightTime, "A projectile child starts after impact");
      assertTrue(child.started, "A projectile child did not start before impact");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"holding_fp", "holding_tp"})
  void bundledRecordingUsesAvailableParticleSystemsAndTextures(String name) throws IOException {
    String path = "/assets/portalix/source_particles/" + name;
    var recording = new ParticleRecording(ModAssetLoader.open(path + ".pgp"));
    var materials = new ParticleMaterialLibrary();
    float loopStart = ModAssetLoader.json(path + ".playback.json").get("loopStart").getAsFloat();
    var player = new ParticleRecordingPlayer(recording, materials.ropes(), loopStart);
    assertFalse(player.systems().isEmpty());
    for (String system : player.systems()) {
      var texture = materials.system(system).material().texture();
      try (var resource = ModAssetLoader.open("/assets/" + texture.getNamespace() + "/" + texture.getPath())) {
        assertTrue(resource.read() >= 0, "Empty particle texture: " + texture);
      }
    }
  }
}
