package dev.portalix.portal.camera;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PortalThirdPersonCamera {
  private PortalThirdPersonCamera() {}

  public static Pose resolve(
      Target target,
      Quaternionf orientation,
      Vec3 bob,
      PortalCameraCorrections history,
      double time,
      double distance,
      List<PortalLink> links,
      Predicate<PortalLink> ready,
      PortalCameraCollision.Query query) {
    Vec3 center = target.center();
    Vec3 focus = target.eye().add(history.eyeCorrection(time)).add(bob);
    var rotation = new Quaternionf(orientation);
    var path = new ArrayList<PortalLink>();
    for (var link : history.rewind(time)) {
      if (!PortalViewLimits.canCross(path, link)) break;
      center = link.transform().point(center);
      focus = link.transform().point(focus);
      rotation.premul(link.transform().rotation());
      PortalRoutes.append(path, link);
    }
    var focusMove = focus.subtract(center);
    var pivot = PortalCameraCollision.solve(center, focusMove, focusMove.length(), links, query, path, ready);
    for (var link : pivot.links()) {
      rotation.premul(link.transform().rotation());
      PortalRoutes.append(path, link);
    }
    var pivotPath = List.copyOf(path);
    var waitingPath = waitingPath(pivotPath, pivot);
    var backwards = rotation.transform(new Vector3f(0, 0, 1));
    if (distance < 0) backwards.negate();
    var boom =
        PortalCameraCollision.solve(
            pivot.position(), new Vec3(backwards.x, backwards.y, backwards.z), Math.abs(distance), links, query, path, ready);
    for (var link : boom.links()) {
      rotation.premul(link.transform().rotation());
      PortalRoutes.append(path, link);
    }
    Vec3 position = boom.position();
    for (var link : links) {
      if (!ready.test(link)) continue;
      var portal = link.entrance().endpoint();
      double depth = PortalAperture.signedDistance(portal, position);
      if (depth >= -PortalAperture.PLANE_EPSILON
          && depth < PortalCameraCollision.CLEARANCE
          && PortalAperture.containsPassage(portal, position))
        position = position.add(portal.normal().scale(PortalCameraCollision.CLEARANCE - depth));
    }
    return new Pose(position, rotation, List.copyOf(path), pivot.position(), pivotPath,
        waitingPath.isEmpty() ? waitingPath(path, boom) : waitingPath);
  }

  private static List<PortalLink> waitingPath(List<PortalLink> path, PortalCameraCollision.Result collision) {
    if (collision.blocked() == null) return List.of();
    var waiting = new ArrayList<PortalLink>(path.size() + 1);
    waiting.addAll(path);
    waiting.add(collision.blocked());
    return List.copyOf(waiting);
  }

  public record Target(Vec3 center, Vec3 eye) {}

  public record Pose(
      Vec3 position,
      Quaternionf rotation,
      List<PortalLink> path,
      Vec3 pivot,
      List<PortalLink> pivotPath,
      List<PortalLink> waitingPath) {}
}
