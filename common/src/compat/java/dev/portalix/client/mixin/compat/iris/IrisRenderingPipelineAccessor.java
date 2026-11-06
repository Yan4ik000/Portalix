package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IrisRenderingPipeline.class)
public interface IrisRenderingPipelineAccessor {
  @Accessor("initializedBlockIds")
  void portalix$blockIdsInitialized(boolean initialized);

  @Accessor("defaultFB")
  GlFramebuffer portalix$beforeTranslucents();

  @Accessor("defaultFBAlt")
  GlFramebuffer portalix$afterTranslucents();

  @Accessor("renderTargets")
  RenderTargets portalix$targets();
}
