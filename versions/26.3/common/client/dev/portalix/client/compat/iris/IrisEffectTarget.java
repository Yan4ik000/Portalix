package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IrisEffectTarget {
  private static final ScopedValue<List<Runnable>> handDraws = ScopedValue.newInstance();
  private static final Map<PipelineFormat, RenderPipeline> pipelines = new HashMap<>();
  private static RenderTarget target;

  private IrisEffectTarget() {}

  public static RenderPipeline pipeline(RenderPipeline source, GpuFormat format) {
    if (source.getColorTargetStates().getFirst().format() == format) return source;
    return pipelines.computeIfAbsent(new PipelineFormat(source, format), key ->
        new PipelineVariant(key.source, PortalixMod.id("pipeline/iris/"
            + key.source.getLocation().getNamespace() + "/" + key.source.getLocation().getPath()
            + "/" + key.format.name().toLowerCase(Locale.ROOT)))
            .colorFormat(key.format).build());
  }

  private record PipelineFormat(RenderPipeline source, GpuFormat format) {}

  public static GpuTextureView color(RenderTarget fallback) {
    return (target == null ? fallback : target).getColorTextureView();
  }

  public static GpuTextureView depth(RenderTarget fallback) {
    return (target == null ? fallback : target).getDepthTextureView();
  }

  public static RenderTarget current() {
    return target;
  }

  public static RenderScope override(RenderTarget replacement) {
    var previous = target;
    target = replacement;
    return () -> target = previous;
  }

  public static boolean supportsColorEffects() {
    return ShaderBackend.INSTANCE == null || ShaderBackend.INSTANCE.supportsColorEffects();
  }

  public static void deferDraw(Runnable draw) {
    ShaderBackend.INSTANCE.submit(draw);
  }

  public static void handPass(Runnable render) {
    ScopedValue.where(handDraws, new ArrayList<>()).run(render);
  }

  public static boolean enqueue(Runnable draw) {
    if (!handDraws.isBound()) return false;
    handDraws.get().add(draw);
    return true;
  }

  public static void drawHandEffects() {
    handDraws.get().forEach(Runnable::run);
  }

  public static RenderScope begin() {
    return ShaderBackend.INSTANCE == null ? null : ShaderBackend.INSTANCE.beginEffect();
  }

  public static void clear() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.clearEffects();
    pipelines.clear();
  }

  public static void endFrame() {
    if (ShaderBackend.INSTANCE != null) ShaderBackend.INSTANCE.endFrame();
  }
}
