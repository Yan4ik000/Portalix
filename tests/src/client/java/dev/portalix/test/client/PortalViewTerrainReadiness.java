package dev.portalix.test.client;

import dev.portalix.portal.PortalLink;
import dev.portalix.test.client.mixin.PortalRenderSceneAccessor;
import dev.portalix.test.client.mixin.PortalSceneManagerAccessor;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

final class PortalViewTerrainReadiness {
  private PortalViewTerrainReadiness() {}

  static boolean complete(PortalLink link) {
    return PortalSceneManagerAccessor.portalix$scenes().entrySet().stream()
        .filter(entry -> entry.getKey().getLast().equals(link))
        .map(entry -> (PortalRenderSceneAccessor) entry.getValue())
        .anyMatch(scene -> scene.portalix$rendered() && scene.portalix$destinationReady()
            && scene.portalix$renderer().hasRenderedAllSections());
  }

  static List<Map<String, Object>> diagnostics(PortalLink link, List<PortalSystemFixture.Marker> markers) {
    return PortalSceneManagerAccessor.portalix$scenes().entrySet().stream()
        .filter(entry -> entry.getKey().getLast().gun().equals(link.gun())).map(entry -> {
          var scene = (PortalRenderSceneAccessor) entry.getValue();
          return Map.<String, Object>of("path", entry.getKey().toString(),
              "rendered", scene.portalix$rendered(), "destinationReady", scene.portalix$destinationReady(),
              "allSectionsReady", scene.portalix$renderer().hasRenderedAllSections(),
              "markers", markers.stream().map(marker -> Map.of("material", marker.material().name(),
                  "position", BlockPos.containing(marker.bounds().getCenter()).toString(),
                  "compiledAndVisible", scene.portalix$renderer().isSectionCompiledAndVisible(
                      BlockPos.containing(marker.bounds().getCenter())))).toList());
        }).toList();
  }
}
