package dev.portalix.test.client;

import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;

enum ClientSystemScenes {
  CAMERA("camera", CameraSystemScene::new),
  PORTAL_VIEWS("portal_views", PortalViewSystemScene::new),
  GUN("gun", GunSystemScene::new),
  PLAYER_TRAVERSAL("player_traversal", PlayerTraversalSystemScene::new),
  CARRY("carry", CarrySystemScene::new),
  COMBAT("combat", CombatSystemScene::new),
  ENTITY_TRAVERSAL("entity_traversal", EntityTraversalSystemScene::new),
  VEHICLES("vehicles", VehiclesSystemScene::new),
  WORLD_UPDATES("world_updates", WorldUpdatesSystemScene::new),
  AUDIO("audio", AudioSystemScene::new),
  RESOURCES("resources", ResourcesSystemScene::new),
  LIFECYCLE("lifecycle", LifecycleSystemScene::new),
  PERFORMANCE("performance", PerformanceSystemScene::new);

  private final String id;
  private final BiFunction<Minecraft, ScenarioFrames, ClientSystemScene> factory;

  ClientSystemScenes(String id, BiFunction<Minecraft, ScenarioFrames, ClientSystemScene> factory) {
    this.id = id;
    this.factory = factory;
  }

  String id() { return id; }
  ClientSystemScene create(Minecraft client, ScenarioFrames frames) { return factory.apply(client, frames); }

  static List<ClientSystemScenes> select(String selection) {
    var all = List.of(values());
    if (selection.equals("all")) return all.stream().filter(scene -> scene != PERFORMANCE).toList();
    return List.of(all.stream().filter(scene -> scene.id.equals(selection)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown clientTest: " + selection)));
  }
}
