package dev.portalix.client.portal;

import static dev.portalix.test.ClientPortalFixtures.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.portalix.api.PortalInstance;
import dev.portalix.client.portal.clip.PortalCullPlane;
import dev.portalix.client.render.ParticleView;
import dev.portalix.client.render.ParticleVisibility;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PortalPresentationTest {
  @Test
  void interpolationConsumesCrossingsWithoutMotionSpaceDiscontinuity() {
    var gun = UUID.fromString("284ac610-f339-4a4a-bbef-046c7cfc2c31");
    for (var entrance : Direction.values()) {
      for (var exit : Direction.values()) {
        var first = new PortalInstance(endpoint(new Vec3(0, 100, 0), entrance), 0, 1, PortalixVariant.STANDARD);
        var second = new PortalInstance(endpoint(new Vec3(32, 100, 0), exit), 0, 2, PortalixVariant.STANDARD);
        var link = PortalTopology.linkPair(gun, first, second).getFirst();
        Vec3 before = first.endpoint().center();
        Vec3 after = link.transform().point(before);
        Vec3 start = before.add(first.endpoint().normal().scale(2));
        Vec3 end = after.add(second.endpoint().normal().scale(2));
        for (double fraction : new double[] {0, .25, .49, .5, .75, 1}) {
          var path = new PortalInterpolationPath();
          path.append(List.of(new Crossing(1, link, before, after, Vec3.ZERO, 0, 0, .5,
              start, 1.62f, 0, 0, 1)));
          var sample = path.advance(start, end, fraction);
          Vec3 expected = fraction < .5
              ? start.lerp(before, fraction * 2) : after.lerp(end, fraction * 2 - 1);
          near(expected, sample.position(), 1e-8, "interpolation " + entrance + " -> " + exit);
          assertTrue(sample.crossings().size() == (fraction < .5 ? 0 : 1), "crossing consumed at plane");
          near(new Vec3(.2, -.4, .8), path.sourceVector(
              fraction < .5 ? link.transform().vector(new Vec3(.2, -.4, .8)) : new Vec3(.2, -.4, .8)),
              1e-7, "motion space follows pending crossings");
        }
      }
    }
  }

  @Test
  void clipPlanesAndParticleBoundsRemainInTheActiveExitView() {
    for (var direction : Direction.values()) {
      var portal = endpoint(new Vec3(2, 100, 3), direction);
      var plane = PortalCullPlane.of(portal);
      var front = portal.center().add(portal.normal().scale(2));
      var back = portal.center().subtract(portal.normal().scale(2));
      var camera = new Vec3(32, 110, -20);
      assertTrue(plane.distance(front.x, front.y, front.z) > 0, "front face accepted");
      assertTrue(plane.distance(back.x, back.y, back.z) < 0, "back face rejected");
      var relative = plane.relativeTo(camera);
      Vec3 local = front.subtract(camera);
      assertTrue(Math.abs(relative.distance(local.x, local.y, local.z) - plane.distance(front.x, front.y, front.z)) < 1e-8,
          "clip plane invariant under camera translation");
      var bounds = new AABB(front, front).inflate(.1);
      var view = new ParticleView(front, portal.right(), portal.up(),
          box -> plane.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
      assertTrue(ParticleVisibility.visible(bounds, view), "local particles visible in exit view");
      assertTrue(!ParticleVisibility.visible(new AABB(back, back).inflate(.1), view), "back-face particles isolated");
      assertTrue(!ParticleVisibility.visible(bounds.move(portal.normal().scale(256)), view), "distant particles culled");
    }
    var rope = ParticleVisibility.ropeBounds(Vec3.ZERO, new Vec3(0, 50, 0), new Vec3(4, 0, 0));
    assertTrue(rope.contains(new Vec3(0, 50, 0)), "curved rope bounds contain control point");
    assertTrue(ParticleVisibility.inRange(rope, new Vec3(0, 50, 0)), "rope visible near its arc");
  }
}
