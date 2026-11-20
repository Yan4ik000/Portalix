package dev.portalix.server.portal;

import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.PortalViewTransition;
import dev.portalix.portal.query.PlayerPortalVisibility;
import org.junit.jupiter.api.Test;

class PortalViewTransitionTest {
  @Test
  void eachCrossingHoldsPreviewForThirtyTicksThenGraduallyRestoresTrackingDistance() {
    for (int limit : new int[] {4, 8, 16, 32}) {
      var state = new PlayerPortalVisibility();
      int preview = PortalViewLimits.preview(limit);
      assertEquals(limit, state.trackingDistance(0, limit));
      for (long tick : new long[] {0, 1, 6, 35, 64}) {
        state.portalCrossed(tick);
        assertEquals(preview, state.trackingDistance(tick, limit));
        assertTrue(state.delayingLoading(tick + 29));
        assertEquals(preview, state.trackingDistance(tick + 29, limit));
        assertFalse(state.delayingLoading(tick + 30));
        assertEquals(preview, state.trackingDistance(tick + 30, limit));
        int halfway = state.trackingDistance(tick + 42, limit);
        if (limit > preview) assertTrue(preview < halfway && halfway < limit);
        else assertEquals(limit, halfway);
        assertEquals(10, state.loadingRadius(tick + 42, 4, 16));
        assertTrue(state.loadingTransitionActive(tick + 52));
        assertFalse(state.loadingTransitionActive(tick + 53));
        assertEquals(limit, state.trackingDistance(tick + 53, limit));
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
    for (int tick = 31; tick < 390; tick++) {
      double retained = PortalViewTransition.retainedRadius(32, tick, 32);
      assertTrue(retained <= previous);
      assertTrue(retained > 8);
      previous = retained;
    }
    assertEquals(8, PortalViewTransition.retainedRadius(32, 390, 32));
    assertEquals(4, PortalViewTransition.retainedRadius(32, 10, 4));
  }
}
