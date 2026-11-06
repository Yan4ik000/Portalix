package dev.portalix.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalCollisionOpening;
import dev.portalix.server.portal.PortalClosureResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class PortalTopology {
  private record Topology(
      List<PortalLink> links, Map<PortalEndpoint, List<ConvexPolyhedron>> boundaries) {}

  private volatile Topology topology = new Topology(List.of(), Map.of());

  public static List<PortalLink> links(Level level) {
    return level == null ? List.of() : ((Access) level).portalix$links().topology.links();
  }

  public static List<ConvexPolyhedron> collisionBoundary(Level level, PortalEndpoint portal) {
    var shapes = ((Access) level).portalix$links().topology.boundaries().get(portal);
    return shapes == null ? PortalCollisionOpening.boundary(portal) : shapes;
  }

  public static void updateLinks(Level level, List<PortalLink> links) {
    var state = ((Access) level).portalix$links();
    var current = state.topology;
    var ordered = new ArrayList<>(links);
    ordered.sort(
        Comparator.comparing(PortalLink::gun)
            .thenComparingLong(link -> link.entrance().identity()));
    if (current.links().equals(ordered)) return;
    var retired = current.links().stream().filter(link -> !ordered.contains(link)).toList();
    var releases =
        level instanceof ServerLevel server
            ? PortalClosureResolver.prepare(server, retired)
            : List.<PortalClosureResolver.Release>of();
    var previous = current.boundaries();
    var boundaries = new HashMap<PortalEndpoint, List<ConvexPolyhedron>>();
    for (var link : ordered) {
      var portal = link.entrance().endpoint();
      var shapes = previous.get(portal);
      boundaries.put(portal, shapes == null ? PortalCollisionOpening.boundary(portal) : shapes);
    }
    state.topology = new Topology(List.copyOf(ordered), Map.copyOf(boundaries));
    releases.forEach(PortalClosureResolver.Release::resolve);
  }

  public static List<PortalLink> linkPair(UUID gun, PortalInstance blue, PortalInstance orange) {
    if (blue == null
        || orange == null
        || !blue.endpoint().dimension().equals(orange.endpoint().dimension())) return List.of();
    long generation = Math.max(blue.identity(), orange.identity());
    return List.of(
        new PortalLink(gun, generation, blue, orange),
        new PortalLink(gun, generation, orange, blue));
  }

  public interface Access {
    PortalTopology portalix$links();
  }
}
