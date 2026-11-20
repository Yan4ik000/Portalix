package dev.portalix.client.carry.prediction;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CarrySnapshotClockTest {
  @Test
  void reanchorsFlightWhenTheServerRunsAtHalfTheClientRate() {
    var clock = new CarrySnapshotClock();
    for (int tick = -8; tick <= 0; tick++) clock.receive(tick, 2 * tick);
    var velocity = new Vec3(1.25, 0, 0);
    assertEquals(2, clock.tickDuration());
    var flight = new CarryFlightPrediction(0, Vec3.ZERO, velocity, clock.tickDuration(), 0,
        Vec3.ZERO, velocity.scale(.5));
    assertEquals(velocity.scale(.5), flight.position(1));
    for (int tick = 1; tick <= 100; tick++) {
      double now = 2 * tick;
      Vec3 snapshot = velocity.scale(tick);
      assertEquals(snapshot.x, flight.position(now).x, 1e-9);
      clock.receive(tick, now);
      flight.update(tick, snapshot, velocity, clock.tickDuration(), now,
          flight.position(now), flight.velocity(now));
      assertEquals(.625, flight.position(now + 1).x - snapshot.x, 1e-9);
      assertEquals(.625, flight.velocity(now + 1).x, 1e-9);
    }
  }

  @Test
  void estimatesCadenceAcrossBatchesAndIgnoresIsolatedDeliveryJitter() {
    for (double interval : new double[] {.25, 1, 2.5}) {
      var clock = new CarrySnapshotClock();
      for (int tick = 0; tick <= 20; tick++) clock.receive(tick, tick * interval);
      assertEquals(interval, clock.tickDuration(), 1e-10);

      for (int tick = 21; tick <= 24; tick++) clock.receive(tick, 24 * interval);
      for (int tick = 25; tick <= 30; tick++) {
        clock.receive(tick, (tick + (tick == 27 ? .9 : 0)) * interval);
        assertEquals(interval, clock.tickDuration(), 1e-10);
      }
      for (int batch = 1; batch <= 8; batch++) {
        int end = 30 + 3 * batch;
        for (int tick = end - 2; tick <= end; tick++) clock.receive(tick, end * interval);
        assertEquals(interval, clock.tickDuration(), 1e-10);
      }
      for (int tick = 55; tick <= 62; tick++) {
        clock.receive(tick, 1000 + tick * interval);
        assertEquals(interval, clock.tickDuration(), 1e-10);
      }
    }
  }

  @Test
  void lateFlightSnapshotResumesFromTheDisplayedPositionAfterExtrapolationStops() {
    var speed = new Vec3(1.25, 0, 0);
    for (double interval : new double[] {.125, 1, 2}) {
      var flight = new CarryFlightPrediction(1, Vec3.ZERO, speed, interval, 0,
          Vec3.ZERO, speed.scale(1 / interval));
      assertEquals(speed, flight.position(interval));
      assertEquals(speed.x / interval, flight.velocity(interval).x, 1e-9);
      var stopped = flight.position(1000);
      assertTrue(stopped.x >= speed.x && stopped.x < speed.x * 2);
      assertEquals(stopped, flight.position(1001));
      assertEquals(Vec3.ZERO, flight.velocity(1000));

      flight.update(2, speed, speed, interval, 1000, stopped, Vec3.ZERO);
      assertEquals(stopped, flight.position(1000));
      assertEquals(Vec3.ZERO, flight.velocity(1000));
      for (int frame = 1; frame <= 8; frame++)
        assertTrue(flight.position(1000 + interval * frame / 8).x >= speed.x);
      double next = 1000 + interval;
      flight.update(3, speed.scale(2), speed, interval, next,
          flight.position(next), flight.velocity(next));
      assertTrue(flight.position(next + interval).x > speed.x * 2);
      assertEquals(speed.scale(3.5), flight.position(1100));
    }
  }
}
