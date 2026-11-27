package dev.portalix.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.portalix.client.platform.ClientPlatform.Phase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalRenderQueueTest {
  @Test
  void drawsSubmissionsInPhaseOrderAndReleasesThemOnce() {
    var deferred = new ArrayList<Runnable>();
    var drawn = new ArrayList<String>();
    var released = new ArrayList<String>();
    var feature = new PortalRenderQueue.Feature<String>(submits -> {
      var snapshot = List.copyOf(submits);
      deferred.add(() -> drawn.addAll(snapshot));
    }, released::add);
    var queue = new PortalRenderQueue();
    queue.add(Phase.AFTER_TERRAIN, feature, "first");
    queue.add(Phase.AFTER_TERRAIN, feature, "second");
    queue.add(Phase.SOLID, feature, "discarded");

    queue.draw(Phase.AFTER_TERRAIN);
    assertFalse(queue.hasSubmissions(Phase.AFTER_TERRAIN));
    assertTrue(queue.hasSubmissions(Phase.SOLID));
    queue.clear();

    assertEquals(List.of("first", "second", "discarded"), released);
    assertTrue(drawn.isEmpty());
    deferred.forEach(Runnable::run);
    assertEquals(List.of("first", "second"), drawn);
  }

  @Test
  void releasesSubmissionsIfDrawingFails() {
    var released = new ArrayList<String>();
    var feature = new PortalRenderQueue.Feature<String>(submits -> {
      throw new IllegalStateException("Draw failed");
    }, released::add);
    var queue = new PortalRenderQueue();
    queue.add(Phase.SOLID, feature, "mesh");

    assertThrows(IllegalStateException.class, () -> queue.draw(Phase.SOLID));
    queue.clear();
    assertEquals(List.of("mesh"), released);
  }
}
