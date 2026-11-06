package dev.portalix.portal.audio;

import static dev.portalix.test.PortalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.geometry.PortalAperture;
import dev.portalix.sound.SourceSoundAttenuation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PortalSoundPropagationTest {
  private record Segment(Vec3 start, Vec3 end, double radius) {}

  static Stream<PortalEndpoint> portals() {
    return orientations().stream().map(portal -> at(portal, new Vec3(1, 10, -3)));
  }

  @ParameterizedTest
  @MethodSource("portals")
  void aperturePreservesSourceDirectionAndRejectsTheBackFaces(PortalEndpoint entrance) {
    for (PortalEndpoint orientation : orientations()) {
      var exit = at(orientation, new Vec3(200, 120, 300));
      var link = link(entrance, exit);
      var routes = PortalSoundPropagation.prepare(List.of(link, link.inverse()));
      for (double side : new double[] {-.3, 0, .3}) {
        Vec3 emitter = exit.center().add(exit.normal().scale(3)).subtract(exit.right().scale(side));
        Vec3 image = entrance.center().subtract(entrance.normal().scale(3)).add(entrance.right().scale(side));
        for (double front : new double[] {0, 2}) {
          Vec3 listener = entrance.center().add(entrance.normal().scale(front));
          var path = routes.findPath(listener, emitter, distance -> 1 / (1 + distance));
          assertEquals(List.of(link), path.links());
          assertEquals(listener.distanceTo(image), path.distance(), 1e-8);
          assertVector(image, path.position(), 1e-8);
          assertEquals(1 / (1 + path.distance()), path.gain(), 1e-10);
        }
      }
      Vec3 listener = entrance.center().add(entrance.normal().scale(2));
      Vec3 emitter = exit.center().add(exit.normal().scale(3));
      assertTrue(routes.findPath(listener, exit.center().subtract(exit.normal()), distance -> 1 / (1 + distance))
          .links().isEmpty());
      assertTrue(routes.findPath(entrance.center().subtract(entrance.normal()), emitter, distance -> 1 / (1 + distance))
          .links().isEmpty());
    }
  }

  @ParameterizedTest
  @MethodSource("portals")
  void offAxisSourceBendsAtTheApertureBoundary(PortalEndpoint entrance) {
    var exit = endpoint(new Vec3(200, 120, 300), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
    var link = link(entrance, exit);
    var routes = PortalSoundPropagation.prepare(List.of(link));
    double halfWidth = PortalAperture.passageHalfWidth(entrance);
    for (double side : new double[] {-1, 1}) {
      Vec3 listener = entrance.center().add(entrance.normal().scale(2));
      Vec3 emitter = exit.center().add(exit.normal().scale(3)).subtract(exit.right().scale(2 * side));
      var path = routes.findPath(listener, emitter, distance -> 1 / (1 + distance));
      double expected = Math.hypot(2, halfWidth) + Math.hypot(3, 2 - halfWidth);
      assertEquals(List.of(link), path.links());
      assertEquals(expected, path.distance(), 1e-7);
      Vec3 bend = entrance.center().add(entrance.right().scale(halfWidth * side));
      assertVector(bend.subtract(listener).normalize(), path.position().subtract(listener).normalize(), 1e-7);
    }
  }

  @ParameterizedTest
  @CsvSource({"0, 0, -1", ".6, 0, -1", "0, 1, -1"})
  void twoHopRoutesTraceOnlyTheirPhysicalWorldSegments(double offset, double normalX, double normalZ) {
    var first = link(endpoint(new Vec3(0, 10, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)),
        endpoint(new Vec3(100, 10, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)));
    double gap = normalX == 0 ? 4 : .1;
    var secondTemplate = link(endpoint(new Vec3(100 + offset, 10, gap), new Vec3(normalX, 0, normalZ), new Vec3(0, 1, 0)),
        endpoint(new Vec3(200, 10, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)));
    var second = new PortalLink(new java.util.UUID(9, 8), 2, secondTemplate.entrance(), secondTemplate.exit());
    Vec3 listener = first.entrance().endpoint().center().add(0, 0, 2);
    Vec3 emitter = second.exit().endpoint().center().add(0, 0, 3);
    var segments = new ArrayList<Segment>();
    var path = PortalSoundPropagation.prepare(List.of(first, second)).findPath(listener, emitter,
        distance -> 1 / (1 + distance), .75, (start, end, radius) -> {
          segments.add(new Segment(start, end, radius));
          return 1;
        });
    assertEquals(List.of(first, second), path.links());
    int middle = -1;
    for (int i = 0; i < segments.size(); i++) {
      var segment = segments.get(i);
      if (segment.radius == 0 && segment.start.distanceTo(first.exit().endpoint().center()) < 1) middle = i;
    }
    assertTrue(middle > 0 && middle + 1 < segments.size(), "two hops require three adjacent world rays");
    var entering = segments.get(middle - 1);
    var connecting = segments.get(middle);
    var leaving = segments.get(middle + 1);
    assertVector(listener, entering.start, 1e-9);
    assertVector(emitter, leaving.end, 1e-9);
    assertEquals(0, entering.radius);
    assertEquals(.75, leaving.radius);
    Vec3 firstPoint = entering.end.subtract(first.entrance().endpoint().normal().scale(1e-4));
    Vec3 secondPoint = connecting.end.subtract(second.entrance().endpoint().normal().scale(1e-4));
    assertTrue(PortalAperture.contains(first.entrance().endpoint(), firstPoint));
    assertTrue(PortalAperture.contains(second.entrance().endpoint(), secondPoint));
    assertVector(first.transform().point(firstPoint),
        connecting.start.subtract(first.exit().endpoint().normal().scale(1e-4)), 1e-8);
    assertVector(second.transform().point(secondPoint),
        leaving.start.subtract(second.exit().endpoint().normal().scale(1e-4)), 1e-8);
    assertTrue(PortalAperture.signedDistance(second.entrance().endpoint(), first.transform().point(firstPoint)) >= -1e-8);
    assertTrue(PortalAperture.signedDistance(first.exit().endpoint(), secondPoint) >= -1e-8);
    double physical = listener.distanceTo(firstPoint) + first.transform().point(firstPoint).distanceTo(secondPoint)
        + second.transform().point(secondPoint).distanceTo(emitter);
    assertEquals(physical, path.distance(), 1e-8);
    assertTrue(path.distance() <= 5 + Math.hypot(offset, gap) + 1e-7,
        "the shortest path cannot be longer than a valid path through both centers");
    if (normalX == 0) assertEquals(Math.hypot(9, offset), path.distance(), 1e-7);
  }

  @ParameterizedTest
  @CsvSource({"1, 1, true, false", "1, .1, false, false", ".01, .2, true, false", ".4, 1, true, true"})
  void strongestRouteIncludesTransmissionAndDistance(double directTransmission, double segmentTransmission, boolean throughPortal, boolean sourceFloor) {
    var entrance = endpoint(new Vec3(0, 10, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0));
    var exit = at(entrance, new Vec3(sourceFloor ? 1000 : 20, 10, 0));
    var link = link(entrance, exit);
    Vec3 listener = entrance.center().add(sourceFloor ? 10 : 0, 0, sourceFloor ? 66.207 : 2);
    Vec3 emitter = exit.center().add(sourceFloor ? -10 : 0, 0, sourceFloor ? 66.207 : 3);
    DoubleUnaryOperator distanceGain = sourceFloor
        ? distance -> SourceSoundAttenuation.gain(75, distance) : distance -> 1 / (1 + distance);
    var path = PortalSoundPropagation.prepare(List.of(link)).findPath(listener, emitter,
        distanceGain, 1,
        (start, end, radius) -> start.equals(listener) && end.equals(emitter) ? directTransmission : segmentTransmission);
    double directGain = directTransmission * distanceGain.applyAsDouble(listener.distanceTo(emitter));
    double portalDistance = sourceFloor ? 2 * Math.hypot(66.207, 9.5) : 5;
    double portalGain = segmentTransmission * segmentTransmission * distanceGain.applyAsDouble(portalDistance);
    assertEquals(throughPortal ? List.of(link) : List.of(), path.links());
    assertEquals(Math.max(directGain, portalGain), path.gain(), 1e-10);
    assertEquals(path.distanceGain() * path.transmission(), path.gain(), 1e-10);
  }
}
