package dev.portalix.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.client.render.ParticleVisibility;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.gun.PortalImpactPayload;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ImpactSparkSimulationTest {
  static Stream<Arguments> detailPolicies() {
    return Stream.of(Arguments.of(false, 1), Arguments.of(true, 8));
  }

  @ParameterizedTest
  @MethodSource("detailPolicies")
  void distantImpactsEmitMoveAndExpireWithoutCollisionQueries(boolean placed, int stride) {
    var observer = new Vec3(0, 100, 0);
    var impact = impact(placed, observer.add(120, 0, 0));
    var renderer = new ImpactSparkRenderer(impact, placed ? 80 : 60);
    ImpactSparkSimulation.CollisionTracer noCollision = (from, to) -> {
      fail("A zero collision budget must still animate without querying the world");
      return null;
    };
    assertNull(renderer.bounds(0, stride), "Particles must be emitted before they have render bounds");
    renderer.advance(.1, noCollision, 0, stride);
    var first = renderer.bounds(.1, stride);
    assertFinite(first);
    assertTrue(ParticleVisibility.inRange(first, observer), "The 120 m effect belongs in the render range");
    assertTrue(first.getCenter().distanceTo(impact.position()) < 3,
        "New impact bounds must stay near the actual distant impact");
    renderer.advance(.2, noCollision, 0, stride);
    var moving = renderer.bounds(.2, stride);
    assertFinite(moving);
    assertTrue(first.getCenter().distanceTo(moving.getCenter()) > .01,
        "Disabling collision queries froze the particle motion");
    assertTrue(ParticleVisibility.inRange(moving, observer));
    for (double age : new double[] {.4, .6, .8, 1}) renderer.advance(age, noCollision, 0, stride);
    assertNull(renderer.bounds(1, stride), "An expired impact must stop contributing visible bounds");
  }

  @Test
  void nearbyCollisionBudgetReflectsOnlyTheSelectedSparksOffRealGeometry() {
    var simulation = simulation(false, Vec3.ZERO);
    var first = simulation.sparks.getFirst();
    double initialSpeed = velocity(first).length();
    var wall = new AABB(.01, -2, -2, .2, 2, 2);
    var queries = new AtomicInteger();
    var contacts = new AtomicInteger();
    simulation.advance(.01, (from, to) -> {
      queries.incrementAndGet();
      assertFinite(from);
      assertFinite(to);
      return wall.clip(from, to).map(point -> {
        contacts.incrementAndGet();
        return new ImpactSparkSimulation.Contact(point, new Vec3(-1, 0, 0));
      }).orElse(null);
    }, 2, 1);
    assertEquals(2, queries.get(), "Only the two collision-enabled sparks may trace this integration step");
    assertEquals(2, contacts.get(), "The authored wall must intercept both outgoing sparks");
    for (int index = 0; index < 2; index++) {
      var reflected = simulation.sparks.get(index);
      assertTrue(reflected.vx < 0, "A wall contact must reverse the incoming normal velocity");
      assertTrue(reflected.x < wall.minX, "Reflected particles must remain outside the solid wall");
      assertFinite(position(reflected));
      assertFinite(velocity(reflected));
    }
    assertTrue(velocity(first).length() < initialSpeed, "The collision must dissipate energy");
    assertTrue(simulation.sparks.get(2).vx > 0,
        "An unselected spark must retain ballistic motion rather than borrow another spark's contact");
  }

  @ParameterizedTest
  @MethodSource("detailPolicies")
  void reducedDetailAndTickPartitioningPreserveTheRenderedSparks(boolean placed, int stride) {
    var position = new Vec3(120, 100, 0);
    var continuous = simulation(placed, position);
    var partitioned = simulation(placed, position);
    ImpactSparkSimulation.CollisionTracer noCollision = (from, to) -> {
      fail("Ballistic simulation must not query collisions with a zero budget");
      return null;
    };
    continuous.advance(.2, noCollision, 0, 1);
    for (double age : new double[] {.05, .1, .15, .2})
      partitioned.advance(age, noCollision, 0, stride);
    int rendered = 0;
    for (int index = 0; index < partitioned.sparks.size(); index += stride) {
      var expected = continuous.sparks.get(index);
      var actual = partitioned.sparks.get(index);
      assertTrue(Double.isFinite(actual.birth), "Reduced detail must not prevent emission");
      assertEquals(expected.birth, actual.birth);
      assertEquals(expected.life, actual.life);
      assertTrue(position(expected).distanceTo(position(actual)) < 1e-10,
          "Render detail or tick partitioning changed a selected spark's trajectory");
      assertTrue(velocity(expected).distanceTo(velocity(actual)) < 1e-10);
      assertFinite(position(actual));
      rendered++;
    }
    assertTrue(rendered > 0);
  }

  private static ImpactSparkSimulation simulation(boolean placed, Vec3 position) {
    return new ImpactSparkSimulation(position.add(-2, 0, 0), position, new Vec3(1, 0, 0),
        false, 80, placed);
  }

  private static PortalImpactPayload impact(boolean placed, Vec3 position) {
    return new PortalImpactPayload(17, position.add(-120, 0, 0), position, new Vec3(1, 0, 0),
        false, placed, 100, PortalixVariant.STANDARD);
  }

  private static Vec3 position(ImpactSparkSimulation.Spark spark) {
    return new Vec3(spark.x, spark.y, spark.z);
  }

  private static Vec3 velocity(ImpactSparkSimulation.Spark spark) {
    return new Vec3(spark.vx, spark.vy, spark.vz);
  }

  private static void assertFinite(AABB bounds) {
    assertNotNull(bounds, "A live impact must have renderable bounds");
    assertFinite(new Vec3(bounds.minX, bounds.minY, bounds.minZ));
    assertFinite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ));
    assertTrue(bounds.getXsize() > 0 && bounds.getYsize() > 0 && bounds.getZsize() > 0);
  }

  private static void assertFinite(Vec3 point) {
    assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z),
        "Non-finite particle state: " + point);
  }
}
