package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class IrisEffectTarget {
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

  public static RenderTarget current() {
    return ShaderBackend.INSTANCE == null ? Minecraft.getInstance().gameRenderer.mainRenderTarget()
        : ShaderBackend.INSTANCE.effectTarget();
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
