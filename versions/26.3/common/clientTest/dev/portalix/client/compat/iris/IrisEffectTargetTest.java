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
    IrisEffectTarget.handPass(() -> {
      for (var draw : preparation) assertTrue(IrisEffectTarget.enqueue(() -> rendered.add(draw)));
      preparation.clear();
      assertTrue(rendered.isEmpty());
      IrisEffectTarget.drawHandEffects();
    });
    IrisEffectTarget.handPass(IrisEffectTarget::drawHandEffects);
    assertEquals(List.of("first", "second"), rendered);
    assertFalse(IrisEffectTarget.enqueue(() -> rendered.add("outside the hand pass")));
  }
}
