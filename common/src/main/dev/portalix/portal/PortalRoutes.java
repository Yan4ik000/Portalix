package dev.portalix.portal;

import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.geometry.PortalTransform;
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
    int count = route.size();
    if (count == 1) {
      var transform = route.getFirst().transform();
      Vec3 crossing = PortalAperture.shortestPathPoint(
          transform.entrance(), source, transform.inversePoint(target));
      return source.distanceTo(crossing) + transform.point(crossing).distanceTo(target);
    }
    var points = new Vec3[count];
    var transforms = new PortalTransform[count];
    for (int i = 0; i < count; i++) {
      transforms[i] = route.get(i).transform();
      points[i] = transforms[i].entrance().center();
    }
    double previousLength = Double.POSITIVE_INFINITY;
    for (int pass = 0; pass < 16; pass++) {
      for (int i = 0; i < count; i++) {
        Vec3 before = i == 0 ? source : transforms[i - 1].point(points[i - 1]);
        Vec3 after = transforms[i].inversePoint(i + 1 == count ? target : points[i + 1]);
        points[i] = PortalAperture.shortestPathPoint(transforms[i].entrance(), before, after);
      }
      double length = 0;
      Vec3 from = source;
      for (int i = 0; i < count; i++) {
        length += from.distanceTo(points[i]);
        from = transforms[i].point(points[i]);
      }
      length += from.distanceTo(target);
      if (previousLength - length < 1e-8) return length;
      previousLength = length;
    }
    return previousLength;
  }
}
