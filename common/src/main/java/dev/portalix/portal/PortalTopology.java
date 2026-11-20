package dev.portalix.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.portal.geometry.ConvexPolyhedron;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalCollisionOpening;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PortalTopology {
  private record Entrance(long identity, long generation) {}

  public record Opening(
      List<ConvexPolyhedron.Plane> planes, List<ConvexPolyhedron> boundary) {
    private Opening(PortalEndpoint endpoint) {
      this(PortalCollisionOpening.planes(endpoint), PortalCollisionOpening.boundary(endpoint));
    }
  }

  private record Topology(
      List<PortalLink> links,
      EntranceIndex entrances,
      Map<Entrance, PortalLink> byEntrance,
      Map<PortalLinkId, PortalLink> byId,
      Map<PortalEndpoint, Opening> openings) {
    private static final Topology EMPTY =
        new Topology(List.of(), new EntranceIndex(List.of()), Map.of(), Map.of(), Map.of());
  }

  private volatile Topology topology = Topology.EMPTY;

  public static List<PortalLink> links(Level level) {
    return of(level).links();
  }

  public static List<PortalLink> entrances(
      Level level, double minX, double minZ, double maxX, double maxZ) {
    return of(level).entrances().query(minX, minZ, maxX, maxZ);
  }

  public static List<PortalLink> intersecting(Level level, AABB bounds) {
    double margin = PortalAperture.MAX_APERTURE_EXTENT;
    return entrances(
        level, bounds.minX - margin, bounds.minZ - margin,
        bounds.maxX + margin, bounds.maxZ + margin);
  }

  public static PortalLink find(Level level, long entrance, long generation) {
    return of(level).byEntrance().get(new Entrance(entrance, generation));
  }

  public static PortalLink resolve(Level level, PortalLinkId id) {
    return of(level).byId().get(id);
  }

  public static Opening collisionOpening(Level level, PortalEndpoint portal) {
    var opening = of(level).openings().get(portal);
    return opening == null ? new Opening(portal) : opening;
  }

  private static Topology of(Level level) {
    return level == null ? Topology.EMPTY : ((Access) level).portalix$links().topology;
  }

  public static void updateLinks(Level level, List<PortalLink> links) {
    var state = ((Access) level).portalix$links();
    var current = state.topology;
    var ordered = new ArrayList<>(links);
    ordered.sort(
        Comparator.comparing(PortalLink::gun)
            .thenComparingLong(link -> link.entrance().identity()));
    if (current.links().equals(ordered)) return;
    var byEntrance = new HashMap<Entrance, PortalLink>();
    var byId = new HashMap<PortalLinkId, PortalLink>();
    var openings = new HashMap<PortalEndpoint, Opening>();
    for (var link : ordered) {
      var portal = link.entrance().endpoint();
      byEntrance.putIfAbsent(new Entrance(link.entrance().identity(), link.generation()), link);
      byId.putIfAbsent(PortalLinkId.fromLink(link), link);
      openings.computeIfAbsent(portal, endpoint -> {
        var previous = current.openings().get(endpoint);
        return previous == null ? new Opening(endpoint) : previous;
      });
    }
    List<PortalLink> snapshot = List.copyOf(ordered);
    state.topology = snapshot.isEmpty() ? Topology.EMPTY : new Topology(
        snapshot, new EntranceIndex(snapshot), Map.copyOf(byEntrance), Map.copyOf(byId),
        Map.copyOf(openings));
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

  private static final class EntranceIndex {
    private final List<PortalLink> links;
    private final int[] byX;
    private final int[] byZ;

    EntranceIndex(List<PortalLink> links) {
      this.links = links;
      byX = new int[links.size()];
      byZ = new int[links.size()];
      for (int i = 0; i < links.size(); i++) byX[i] = byZ[i] = i;
      IntArrays.quickSort(byX, (a, b) -> Double.compare(center(a).x, center(b).x));
      IntArrays.quickSort(byZ, (a, b) -> Double.compare(center(a).z, center(b).z));
    }

    List<PortalLink> query(double minX, double minZ, double maxX, double maxZ) {
      if (links.isEmpty()) return links;
      int startX = bound(byX, minX, true, false);
      int endX = bound(byX, maxX, true, true);
      int startZ = bound(byZ, minZ, false, false);
      int endZ = bound(byZ, maxZ, false, true);
      int countX = endX - startX, countZ = endZ - startZ;
      if (countX <= 0 || countZ <= 0) return List.of();
      if (countX == links.size() && countZ == links.size()) return links;
      boolean x = countX <= countZ;
      int[] axis = x ? byX : byZ;
      int end = x ? endX : endZ;
      var selected = new IntArrayList();
      for (int i = x ? startX : startZ; i < end; i++) {
        int index = axis[i];
        Vec3 center = center(index);
        if (x ? center.z >= minZ && center.z <= maxZ : center.x >= minX && center.x <= maxX)
          selected.add(index);
      }
      selected.sort(null);
      var result = new ArrayList<PortalLink>(selected.size());
      for (int i = 0; i < selected.size(); i++) result.add(links.get(selected.getInt(i)));
      return result;
    }

    private int bound(int[] axis, double value, boolean x, boolean inclusive) {
      int low = 0, high = axis.length;
      while (low < high) {
        int middle = (low + high) >>> 1;
        Vec3 center = center(axis[middle]);
        double coordinate = x ? center.x : center.z;
        if (coordinate < value || inclusive && coordinate == value) low = middle + 1;
        else high = middle;
      }
      return low;
    }

    private Vec3 center(int index) {
      return links.get(index).entrance().endpoint().center();
    }
  }
}
