package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
  // Staged geometry is resolved before the feature dispatcher releases its per-group draw lists.
  private static final ScopedValue<List<Runnable>> deferredGroup = ScopedValue.newInstance();
  private static final Map<PipelineFormat, RenderPipeline> pipelines = new HashMap<>();

  private IrisEffectTarget() {}

  public static RenderPipeline pipeline(RenderPipeline source, GpuFormat format) {
    if (source.getColorTargetState().format() == format) return source;
    return pipelines.computeIfAbsent(new PipelineFormat(source, format), key ->
        new PipelineVariant(key.source, PortalixMod.id("pipeline/iris/"
            + key.source.getLocation().getNamespace() + "/" + key.source.getLocation().getPath()
            + "/" + key.format.name().toLowerCase(Locale.ROOT)))
            .colorFormat(key.format).build());
  }

  private record PipelineFormat(RenderPipeline source, GpuFormat format) {}

  public static boolean supportsColorEffects() {
    return ShaderBackend.INSTANCE == null || ShaderBackend.INSTANCE.supportsColorEffects();
  }

  public static void deferDraw(Runnable draw) {
    if (deferredGroup.isBound()) deferredGroup.get().add(draw);
    else ShaderBackend.INSTANCE.submit(draw);
  }

  public static Runnable captureDraws(Runnable prepareDraws) {
    var draws = new ArrayList<Runnable>();
    ScopedValue.where(deferredGroup, draws).run(prepareDraws);
    return draws.isEmpty() ? null : () -> draws.forEach(Runnable::run);
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
