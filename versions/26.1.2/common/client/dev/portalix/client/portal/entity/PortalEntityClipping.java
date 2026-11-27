package dev.portalix.client.portal.entity;

import dev.portalix.client.mixin.feature.RenderTypeStateAccessor;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class PortalEntityClipping {
  private static final Map<RenderType, PortalEntityClipMask> MASKS = new IdentityHashMap<>();
  private static final Map<Layer, RenderType> LAYERS = new HashMap<>();
  private static PortalEntityClipMask rendering;
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

  public static PortalEntityClipMask submitting() {
    return submitting;
  }

  public static void rendering(PortalEntityClipMask mask) {
    rendering = mask;
  }

  public static boolean rendering() {
    return rendering != null;
  }

  public static RenderType submissionType(RenderType source) {
    return layer(source, submitting);
  }

  public static RenderType renderType(RenderType source) {
    return layer(source, rendering);
  }

  private static RenderType layer(RenderType source, PortalEntityClipMask mask) {
    if (mask == null || MASKS.containsKey(source)) return source;
    return LAYERS.computeIfAbsent(new Layer(source, mask), key -> {
      RenderType layer = RenderType.create("portalix_clipped", ((RenderTypeStateAccessor) source).portalix$state());
      MASKS.put(layer, mask);
      return layer;
    });
  }

  public static PortalEntityClipMask mask(RenderType type) {
    return MASKS.get(type);
  }

  public static void clear() {
    MASKS.clear();
    LAYERS.clear();
    rendering = null;
    end();
  }

  private record Layer(RenderType type, PortalEntityClipMask mask) {}
}
