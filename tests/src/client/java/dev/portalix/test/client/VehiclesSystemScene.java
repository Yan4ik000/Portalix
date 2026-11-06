package dev.portalix.test.client;

import dev.portalix.test.WorldScenarioFixture;
import java.util.List;
import net.minecraft.client.Minecraft;

final class VehiclesSystemScene extends CourseSceneSupport {
  private VehicleTraversalScenario scenario;
  private boolean prepared;

  VehiclesSystemScene(Minecraft client, ScenarioFrames frames) { super(client, frames, WorldScenarioFixture.Area.VEHICLE); }

  public boolean tick() {
    if (!prepared) {
      var course = WorldScenarioFixture.vehicleCourse();
      if (!prepare(course.setupFeet(), course.sourceAim())) return false;
      prepared = true;
      scenario = new VehicleTraversalScenario(client, frames, fixture);
    }
    return scenario.tick();
  }

  public List<String> passed() { return scenario == null ? List.of() : scenario.passed(); }
}
