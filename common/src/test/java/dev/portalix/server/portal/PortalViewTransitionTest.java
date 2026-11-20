package dev.portalix.server.portal;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import dev.portalix.portal.query.PlayerPortalVisibility;
import org.junit.jupiter.api.Test;

class PortalViewTransitionTest {
  @Test
  void eachCrossingPostponesNativeDistanceForThirtyTicks() {
    for (int limit : new int[] {4, 8, 16, 32}) {
      var state = new PlayerPortalVisibility();
      assertEquals(limit, state.trackingDistance(0, limit));
      for (long tick : new long[] {0, 1, 6, 35, 64}) {
        state.portalCrossed(tick);
        assertEquals(PortalViewLimits.preview(limit), state.trackingDistance(tick, limit));
        assertEquals(PortalViewLimits.preview(limit), state.trackingDistance(tick + 29, limit));
        assertEquals(limit, state.trackingDistance(tick + 30, limit));
      }
      state.portalCrossed(95);
      state.resumeLoading();
      assertEquals(limit, state.trackingDistance(96, limit));
    }
  }

  @Test
  void oldTerrainWaitsThenReleasesFromTheOutside() {
    assertEquals(32, PortalViewTransition.retainedRadius(32, 29, 32));
    assertEquals(32, PortalViewTransition.retainedRadius(32, 30, 32));
    double previous = 32;
    for (int tick = 31; tick <= 400; tick++) {
      double retained = PortalViewTransition.retainedRadius(32, tick, 32);
      assertTrue(retained <= previous);
      assertTrue(retained >= 8);
      previous = retained;
    }
    assertEquals(8, previous);
    assertEquals(4, PortalViewTransition.retainedRadius(32, 10, 4));
  }
}
