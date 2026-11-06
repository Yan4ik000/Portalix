package dev.portalix.portal;

import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class PortalRoutes {
  private PortalRoutes() {}

  public static boolean sameIdentity(PortalLink first, PortalLink second) {
    return first.generation() == second.generation() && first.gun().equals(second.gun())
        && first.entrance().identity() == second.entrance().identity()
        && first.exit().identity() == second.exit().identity();
  }

  public static boolean matches(List<PortalLink> first, List<PortalLink> second) {
    if (first.size() != second.size()) return false;
    for (int index = 0; index < first.size(); index++)
      if (!sameIdentity(first.get(index), second.get(index))) return false;
    return true;
  }

  public static void append(List<PortalLink> path, PortalLink link) {
    if (!path.isEmpty() && path.getLast().inverse().equals(link)) path.removeLast();
    else path.add(link);
  }

  public static List<PortalLink> simplify(List<PortalLink> path) {
    var result = new ArrayList<PortalLink>();
    for (var link : path) {
      if (!result.isEmpty() && sameIdentity(result.getLast(), link.inverse())) result.removeLast();
      else result.add(link);
    }
    return List.copyOf(result);
  }

  public static List<PortalLinkId> simplifyIds(List<PortalLinkId> path) {
    var result = new ArrayList<PortalLinkId>();
    for (var link : path) {
      if (!result.isEmpty() && result.getLast().equals(link.inverse())) result.removeLast();
      else result.add(link);
    }
    return List.copyOf(result);
  }

  public static PortalLink resolve(PortalLink reference, List<PortalLink> available) {
    for (var link : available) if (sameIdentity(reference, link)) return link;
    return null;
  }

  public static double distance(Vec3 source, Vec3 target, List<PortalLink> route) {
    if (route.isEmpty()) return source.distanceTo(target);
    var points = new ArrayList<Vec3>();
    for (var link : route) points.add(link.entrance().endpoint().center());
    double previousLength = Double.POSITIVE_INFINITY;
    for (int pass = 0; pass < 16; pass++) {
      for (int i = 0; i < route.size(); i++) {
        Vec3 before = i == 0 ? source : route.get(i - 1).transform().point(points.get(i - 1));
        Vec3 after =
            route
                .get(i)
                .transform()
                .inverse()
                .point(i + 1 == route.size() ? target : points.get(i + 1));
        points.set(
            i, PortalAperture.shortestPathPoint(route.get(i).entrance().endpoint(), before, after));
      }
      double length = 0;
      Vec3 from = source;
      for (int i = 0; i < route.size(); i++) {
        length += from.distanceTo(points.get(i));
        from = route.get(i).transform().point(points.get(i));
      }
      length += from.distanceTo(target);
      if (previousLength - length < 1e-8) return length;
      previousLength = length;
    }
    return previousLength;
  }
}
