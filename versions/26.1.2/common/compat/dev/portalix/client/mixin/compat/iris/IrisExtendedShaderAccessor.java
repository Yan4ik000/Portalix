package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExtendedShader.class)
interface IrisExtendedShaderAccessor {
  @Accessor("pipeline")
  IrisRenderingPipeline portalix$pipeline();
}
