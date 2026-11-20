package dev.portalix.client.compat.iris;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrisEffectTargetTest {
  @Test
  void capturedGroupKeepsPreparedDrawsAfterTheFeatureListIsCleared() {
    var preparation = new ArrayList<>(List.of("first", "second"));
    var rendered = new ArrayList<String>();
    var replay = IrisEffectTarget.captureDraws(() -> {
      for (var draw : preparation) IrisEffectTarget.deferDraw(() -> rendered.add(draw));
    });
    preparation.clear();
    assertTrue(rendered.isEmpty());
    assertNotNull(replay);
    replay.run();
    assertEquals(List.of("first", "second"), rendered);
    assertNull(IrisEffectTarget.captureDraws(() -> {}));
  }
}
