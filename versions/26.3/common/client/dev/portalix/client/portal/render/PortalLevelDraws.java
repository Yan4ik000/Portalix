package dev.portalix.client.portal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LevelTargetBundle;

public final class PortalLevelDraws {
  private static final ScopedValue<PortalLevelDraws> CURRENT = ScopedValue.newInstance();
  private final List<Runnable> solid = new ArrayList<>();
  private final List<Runnable> beforeOit = new ArrayList<>();
  private final List<Runnable> afterTerrain = new ArrayList<>();
  private Consumer<OitInputs> oitLight;
  private OitPortalRefraction oitRefraction;
  private GraphicsResourceAllocator resources;

  public void render(GraphicsResourceAllocator resources, Runnable render) {
    this.resources = resources;
    try {
      ScopedValue.where(CURRENT, this).run(render);
    } finally {
      solid.clear();
      beforeOit.clear();
      afterTerrain.clear();
      oitLight = null;
      if (oitRefraction != null) {
        oitRefraction.close();
        oitRefraction = null;
      }
      this.resources = null;
    }
  }

  static void solid(Runnable draw) {
    CURRENT.get().solid.add(draw);
  }

  public static void afterTerrain(Runnable draw) {
    CURRENT.get().afterTerrain.add(draw);
  }

  static void beforeOit(Runnable draw) {
    CURRENT.get().beforeOit.add(draw);
  }

  public static boolean hasSolid() {
    return !CURRENT.get().solid.isEmpty();
  }

  public static boolean hasAfterTerrain() {
    return !CURRENT.get().afterTerrain.isEmpty();
  }

  public static void oitLight(Consumer<OitInputs> draw) {
    CURRENT.get().oitLight = draw;
  }

  public static void drawOitLight(LevelTargetBundle targets) {
    var draws = CURRENT.get();
    if (draws.oitLight == null) return;
    draws.oitLight.accept(oitInputs(targets));
    draws.oitLight = null;
  }

  static void captureRefraction(RenderTarget target, List<OitPortalRefraction.Wave> waves) {
    var draws = CURRENT.get();
    draws.oitRefraction = new OitPortalRefraction(draws.resources, target, waves);
    draws.oitRefraction.captureOpaque();
  }

  public static void drawOitRefraction(LevelTargetBundle targets) {
    var draws = CURRENT.get();
    var refraction = draws.oitRefraction;
    if (refraction == null) return;
    draws.oitRefraction = null;
    try (refraction) {
      var inputs = oitInputs(targets);
      refraction.draw(inputs.depthBounds(), inputs.transmittance());
    }
  }

  private static OitInputs oitInputs(LevelTargetBundle targets) {
    return new OitInputs(targets.main.get().getDepthTextureView(),
        targets.depthBoundsCulled.get().getColorTextureView(),
        targets.transmittance.stream().map(handle -> handle.get().getColorTextureView()).toList());
  }

  public record OitInputs(
      GpuTextureView opaqueDepth, GpuTextureView depthBounds, List<GpuTextureView> transmittance) {}

  public static void drawSolid() {
    var draws = CURRENT.get().solid;
    draws.forEach(Runnable::run);
    draws.clear();
  }

  public static void drawBeforeOit() {
    var draws = CURRENT.get().beforeOit;
    draws.forEach(Runnable::run);
    draws.clear();
  }

  public static void drawAfterTerrain() {
    var draws = CURRENT.get().afterTerrain;
    draws.forEach(Runnable::run);
    draws.clear();
  }
}
