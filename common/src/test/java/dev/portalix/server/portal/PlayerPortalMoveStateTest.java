package dev.portalix.server.portal;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlayerPortalMoveStateTest {
  @Test
  void catchUpAndLateTickMarkersPreserveNativePacing() {
    var replay = new PlayerPortalMoveState(1000);
    for (int tick = 0; tick < 10; tick++) {
      assertTrue(replay.beginMovement(1000, Vec3.ZERO), "A first-frame catch-up tick was rejected");
      for (int segment = 0; segment < 3; segment++)
        assertTrue(replay.beginMovement(1000, Vec3.ZERO), "A segment consumed another client tick");
      replay.endClientTick(1000);
    }
    assertFalse(replay.beginMovement(1000, Vec3.ZERO), "A single frame exceeded its native tick limit");
    assertTrue(replay.beginMovement(1001, Vec3.ZERO), "The next frame lost its timer's fractional tick");
    replay.endClientTick(1001);
    assertFalse(replay.beginMovement(1050, Vec3.ZERO), "The fractional phase was granted a second time");
    var boundary = new PlayerPortalMoveState(1000);
    for (int tick = 0; tick < 10; tick++) boundary.endClientTick(1000);
    for (int tick = 0; tick < 20; tick++) {
      assertEquals(tick > 0, boundary.beginMovement(1000 + tick * 50L, Vec3.ZERO),
          "A rejected tick ending after its deadline kept blocking normal movement");
      boundary.endClientTick(1002 + tick * 50L);
    }
  }

  @Test
  void idleTicksAndAcceleratedInputCannotBankTime() {
    var idle = new PlayerPortalMoveState(1000);
    for (int tick = 0; tick < 200; tick++) idle.endClientTick(1000 + tick * 50L);
    for (int tick = 0; tick < 10; tick++) {
      assertTrue(idle.beginMovement(11_000, Vec3.ZERO), "Idle tick markers blocked normal movement");
      idle.endClientTick(11_000);
    }
    assertFalse(idle.beginMovement(11_000, Vec3.ZERO), "Idle tick markers banked extra movement time");
    var accelerated = new PlayerPortalMoveState(1000);
    int accepted = 0;
    for (int tick = 0; tick < 200; tick++) {
      long time = 1000 + tick * 25L;
      accelerated.acknowledgeKeepAlive(time);
      var probe = accelerated.clockProbe(time);
      if (probe != null) accelerated.acknowledgeClock(probe.getId());
      if (accelerated.beginMovement(time, Vec3.ZERO)) accepted++;
      accelerated.endClientTick(time);
    }
    assertEquals(110, accepted, "A sustained 40 Hz stream exceeded elapsed time plus the initial frame allowance");
    assertFalse(accelerated.beginMovement(6000, Vec3.ZERO), "Rejected tick markers granted movement before its deadline");
    for (int tick = 0; tick < 20; tick++) {
      long time = 6001 + tick * 50L;
      assertTrue(accelerated.beginMovement(time, Vec3.ZERO), "Returning to 20 Hz left movement permanently blocked");
      accelerated.endClientTick(time);
    }
  }

  @Test
  void pongBoundsTimeWithoutDiscardingOlderQueuedMovement() {
    var synchronizedClock = new PlayerPortalMoveState(1000);
    var probe = synchronizedClock.clockProbe(101_000);
    assertNotNull(probe);
    assertNull(synchronizedClock.clockProbe(102_000), "A delayed clock probe was replaced before its response");
    synchronizedClock.acknowledgeClock(probe.getId());
    for (int tick = 0; tick < 10; tick++) {
      assertTrue(synchronizedClock.beginMovement(101_000, Vec3.ZERO),
          "Pong rejected replay catch-up ticks from the same client frame");
      synchronizedClock.endClientTick(101_000);
    }
    assertFalse(synchronizedClock.beginMovement(101_000, Vec3.ZERO), "A confirmed clock retained idle credit");
    synchronizedClock.acknowledgeClock(probe.getId());
    synchronizedClock.resetForWorld();
    assertFalse(synchronizedClock.beginMovement(101_000, Vec3.ZERO), "A duplicate Pong or world reset erased debt");
    var nextProbe = synchronizedClock.clockProbe(102_000);
    assertNotNull(nextProbe, "A completed clock probe prevented the next synchronization");
    synchronizedClock.acknowledgeClock(nextProbe.getId() ^ 1);
    assertNull(synchronizedClock.clockProbe(103_000), "An unrelated Pong acknowledged a clock probe");
    // Movement queued before the Pong must be replayed before its causal clock bound is applied.
    for (int tick = 0; tick < 80; tick++) {
      assertTrue(synchronizedClock.beginMovement(105_000, Vec3.ZERO), "A long queue preceding Pong was rejected");
      synchronizedClock.endClientTick(105_000);
    }
    synchronizedClock.acknowledgeClock(nextProbe.getId());
    assertFalse(synchronizedClock.beginMovement(105_000, Vec3.ZERO), "A late Pong reset consumed movement time");
    assertTrue(synchronizedClock.beginMovement(105_050, Vec3.ZERO), "A late Pong blocked the next regular tick");
  }

  @Test
  void keepAliveReplacesAWithheldPongWithoutReplenishingTime() {
    var withheldClock = new PlayerPortalMoveState(1000);
    var withheldProbe = withheldClock.clockProbe(1000);
    assertNotNull(withheldProbe, "The withheld-Pong fixture has no clock probe");
    for (long sentAt = 16_000; sentAt <= 61_000; sentAt += 15_000) {
      withheldClock.acknowledgeKeepAlive(sentAt);
      assertFalse(withheldClock.isClockProbe(withheldProbe.getId()),
          "An acknowledged native heartbeat retained its superseded clock probe");
      withheldClock.acknowledgeClock(withheldProbe.getId());
      withheldClock.acknowledgeClock(withheldProbe.getId());
      withheldProbe = withheldClock.clockProbe(sentAt + 1);
      assertNotNull(withheldProbe, "A withheld Pong permanently stopped clock synchronization");
      withheldClock.acknowledgeClock(withheldProbe.getId() ^ 1);
      assertNull(withheldClock.clockProbe(sentAt + 1001),
          "An unrelated Pong retired the pending clock probe");
      withheldClock.acknowledgeKeepAlive(sentAt);
      assertTrue(withheldClock.isClockProbe(withheldProbe.getId()),
          "An older native heartbeat retired a newer clock probe");
    }
    for (int tick = 0; tick < 20; tick++) {
      assertTrue(withheldClock.beginMovement(61_000, Vec3.ZERO),
          "A native heartbeat rejected the two client frames surrounding a main-thread stall");
      withheldClock.endClientTick(61_000);
    }
    assertFalse(withheldClock.beginMovement(61_000, Vec3.ZERO),
        "Withholding Pong and client tick markers banked a minute of replay time");
    withheldClock.acknowledgeKeepAlive(61_000);
    withheldClock.resetForWorld();
    assertFalse(withheldClock.beginMovement(61_000, Vec3.ZERO),
        "A repeated native heartbeat or world reset replenished consumed movement time");
    assertTrue(withheldClock.beginMovement(61_050, Vec3.ZERO),
        "A withheld-Pong burst blocked the next normally paced tick");
  }

  @Test
  void queuedTicksConsumeOnlyElapsedTimeAroundKeepAlive() {
    int queuedTicks = 80;
    long sentAt = 1050;
    long delivery = sentAt + queuedTicks * 50L;
    var beforeHeartbeat = new PlayerPortalMoveState(sentAt);
    var afterHeartbeat = new PlayerPortalMoveState(sentAt);
    var freshHeartbeat = new PlayerPortalMoveState(sentAt);
    beforeHeartbeat.clockProbe(sentAt);
    afterHeartbeat.clockProbe(sentAt);
    afterHeartbeat.acknowledgeKeepAlive(sentAt);
    freshHeartbeat.acknowledgeKeepAlive(delivery);
    for (int tick = 0; tick < queuedTicks + 10; tick++) {
      assertTrue(beforeHeartbeat.beginMovement(delivery, Vec3.ZERO),
          "Movement queued before a native heartbeat was truncated");
      beforeHeartbeat.endClientTick(delivery);
      assertTrue(afterHeartbeat.beginMovement(delivery, Vec3.ZERO),
          "Movement queued after an earlier native heartbeat was truncated");
      afterHeartbeat.endClientTick(delivery);
      assertEquals(tick < 20, freshHeartbeat.beginMovement(delivery, Vec3.ZERO),
          "A fresh native heartbeat retained stale movement credit");
      freshHeartbeat.endClientTick(delivery);
    }
    assertFalse(beforeHeartbeat.beginMovement(delivery, Vec3.ZERO), "Queued ticks exceeded elapsed time");
    beforeHeartbeat.acknowledgeKeepAlive(delivery);
    assertFalse(beforeHeartbeat.beginMovement(delivery, Vec3.ZERO), "A late heartbeat replenished consumed time");
    assertFalse(afterHeartbeat.beginMovement(delivery, Vec3.ZERO), "An earlier heartbeat granted extra burst time");
    beforeHeartbeat.resetForWorld();
    assertFalse(beforeHeartbeat.beginMovement(delivery, Vec3.ZERO), "Changing worlds erased movement debt");
  }

  @Test
  void nativeAndReplayedMovementShareOneTickBudget() {
    var mixed = new PlayerPortalMoveState(1000);
    for (int tick = 0; tick < 10; tick++) {
      mixed.recordNativeMovement(6, 0);
      assertFalse(mixed.movementStarted(), "An ordinary packet activated portal movement validation");
      assertTrue(mixed.beginMovement(1000, Vec3.ZERO)
              && mixed.withinMovementBudget(4, mixed.movementBudget(0, false)),
          "A replay did not inherit the native path budget");
      mixed.recordReplayDistance(4);
      mixed.recordNativeMovement(0, 100);
      mixed.resetForWorld();
      assertFalse(mixed.withinMovementBudget(.1, mixed.movementBudget(100, false)),
          "Mixing native packets, replay or world resets replenished the tick's path or impulse budget");
      mixed.endClientTick(1000);
    }
    assertFalse(mixed.beginMovement(1000, Vec3.ZERO),
        "Mixed movement bypassed the exhausted replay clock");
  }
}
