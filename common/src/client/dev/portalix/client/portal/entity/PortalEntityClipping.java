package dev.portalix.client.portal.entity;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

public final class PortalEntityClipping {
  private static final Map<SubmitNode, PortalEntityClipMask> MASKS = new IdentityHashMap<>();
  private static PortalEntitySlice slice;
  private static PortalEntityClipMask submitting;

  private PortalEntityClipping() {}

  public static void begin(PortalEntitySlice value) {
    slice = value;
    submitting =
        value.portal() == null
            ? null
            : new PortalEntityClipMask(
                value.portal(), value.anchor(), value.replica(), value.oval(), false);
  }

  public static void end() {
    slice = null;
    submitting = null;
  }

  public static void beginShadow(PortalEntitySlice value) {
    begin(value);
    if (value.portal() != null) {

      submitting =
          new PortalEntityClipMask(
              value.portal(), value.anchor(), value.replica(), value.oval(), true);
    }
  }

  public static void withItem(Runnable render) {
    if (slice == null || slice.portal() == null) {
      render.run();
      return;
    }
    var previous = submitting;
    submitting =
        new PortalEntityClipMask(slice.portal(), slice.anchor(), slice.replica(), false, false);
    try {
      render.run();
    } finally {
      submitting = previous;
    }
  }

  public static void remember(SubmitNode node) {
    if (submitting != null) MASKS.put(node, submitting);
  }

  public static PortalEntityClipMask mask(SubmitNode node) {
    return MASKS.get(node);
  }

  public static void clear() {
    MASKS.clear();
    end();
  }

  public interface Group {
    int portalix$firstSubmit();
  }
}
