package dev.portalix.client.portal;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.server.portal.PlayerPortalMoveState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MovementClockTest {
  @Test
  void acceptsTheFractionalTickAfterAFullCatchUpFrame() {
    var timer = new DeltaTracker.Timer(20, 455, millis -> millis);
    var server = new PlayerPortalMoveState(0);
    server.acknowledgeClock(server.clockProbe(1000).getId());
    for (long frame : new long[] {1000, 1006}) {
      int ticks = Math.min(10, timer.advanceGameTime(frame));
      assertEquals(frame == 1000 ? 10 : 1, ticks);
      for (int i = 0; i < ticks; i++) {
        assertTrue(server.beginMovement(1025, Vec3.ZERO));
        server.endClientTick(1025);
      }
    }
    assertFalse(server.beginMovement(1025, Vec3.ZERO));
  }
}
