package dev.portalix.test.client;

import dev.portalix.client.PortalixClient;
import dev.portalix.portal.PortalLink;
import dev.portalix.test.client.mixin.ClientPortalRegistryAccessor;
import dev.portalix.test.client.mixin.PortalVisualAccessor;
import java.util.Collection;
import java.util.stream.Stream;

final class PortalOpeningReadiness {
  private PortalOpeningReadiness() {}

  static boolean fullyOpen(PortalLink link) {
    var registry = PortalixClient.portals();
    var visuals = ((ClientPortalRegistryAccessor) (Object) registry).portalix$visuals().values();
    double now = registry.time();
    return open(visuals, link.entrance().identity(), now) && open(visuals, link.exit().identity(), now);
  }

  static boolean fullyClosed(PortalLink link) {
    var registry = (ClientPortalRegistryAccessor) (Object) PortalixClient.portals();
    return Stream.concat(registry.portalix$visuals().values().stream(), registry.portalix$closing().stream())
        .map(visual -> (PortalVisualAccessor) visual).noneMatch(visual ->
            visual.portalix$identity() == link.entrance().identity()
                || visual.portalix$identity() == link.exit().identity());
  }

  private static boolean open(Collection<?> visuals, long identity, double now) {
    for (Object candidate : visuals) {
      var visual = (PortalVisualAccessor) candidate;
      if (visual.portalix$identity() != identity) continue;
      var animation = visual.portalix$animation();
      return animation.openingProgress(now) == 1 && animation.unlinkedOpacity(now) == 0;
    }
    return false;
  }
}
