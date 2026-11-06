package dev.portalix.carry;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.portal.traversal.PortalCollisionWorld;
import dev.portalix.portal.traversal.PortalEntityCollisions;
import dev.portalix.portal.traversal.WorldCollisionShapes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalHullSweep {
  private PortalHullSweep() {}

  static CarryCollisionSweep.Result move(
      Entity entity, AABB hull, Vec3 displacement, boolean crossAtEnd) {
    var nodes = new ArrayList<CarryMotionPath.Node>();
    nodes.add(new CarryMotionPath.Node(hull.getCenter(), null));
    var zeroProgress = new HashSet<PortalLink>();
    Vec3 remaining = displacement;
    var normals = new ArrayList<Vec3>();
    while (remaining.lengthSqr() > 1e-20) {
      if (nodes.size() > CarryMotionPath.MAX_POINTS - 4) break;
      var link = PortalEntityCollisions.intersectingPortal(entity, hull, remaining);
      if (link == null) {
        var obstacles = WorldCollisionShapes.boxes(entity, hull.expandTowards(remaining));
        var swept = CarryCollisionSweep.move(hull, remaining, obstacles);
        nodes.addAll(swept.path().nodes().subList(1, swept.path().nodes().size()));
        normals.addAll(swept.contactNormals());
        break;
      }
      var crossing =
          PortalAperture.crossing(link.entrance().endpoint(), hull.getCenter(), remaining);
      Vec3 wanted = crossing.isPresent() ? remaining.scale(crossing.get().fraction()) : remaining;
      var scene = PortalCollisionWorld.query(entity, link, hull.expandTowards(wanted));
      List<Vec3> path = scene.path(hull, wanted);
      Vec3 moved = path.getLast().subtract(hull.getCenter());
      for (Vec3 point : path.subList(1, path.size()))
        nodes.add(new CarryMotionPath.Node(point, null));
      normals.addAll(
          CarryCollisionSweep.axes(
              (Math.abs(moved.x - wanted.x) > 1e-8 ? 1 : 0)
                  | (Math.abs(moved.y - wanted.y) > 1e-8 ? 2 : 0)
                  | (Math.abs(moved.z - wanted.z) > 1e-8 ? 4 : 0)));
      hull = hull.move(moved);
      if (crossing.isEmpty()
          || !crossAtEnd && crossing.get().fraction() >= 1 - 1e-12
          || Math.abs(PortalAperture.signedDistance(link.entrance().endpoint(), hull.getCenter()))
              > 1e-7
          || !PortalAperture.contains(link.entrance().endpoint(), hull.getCenter())) break;
      if (crossing.get().fraction() > 1e-9) zeroProgress.clear();
      else if (!zeroProgress.add(link)) break;
      Vec3 arrival = link.transform().point(hull.getCenter());
      arrival =
          arrival.subtract(
              link.exit()
                  .endpoint()
                  .normal()
                  .scale(PortalAperture.signedDistance(link.exit().endpoint(), arrival)));
      AABB nextHull = hull.move(arrival.subtract(hull.getCenter()));
      if (PortalCollisionWorld.query(entity, link.inverse(), nextHull).intersects(nextHull)) break;
      if (nodes.size() + 2 > CarryMotionPath.MAX_POINTS) break;
      nodes.add(new CarryMotionPath.Node(arrival, link));
      hull = nextHull;
      remaining = link.transform().vector(crossing.get().remaining());
      normals.replaceAll(link.transform()::vector);
    }
    return new CarryCollisionSweep.Result(CarryMotionPath.ofNodes(nodes), normals);
  }
}
