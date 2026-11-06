package dev.portalix.client.mixin.compat.iris;

import java.util.Map;
import java.util.function.Function;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PipelineManager.class)
public interface PipelineManagerAccessor {
  @Accessor("pipelineFactory")
  Function<NamespacedId, WorldRenderingPipeline> portalix$factory();

  @Accessor("pipelinesPerDimension")
  Map<NamespacedId, WorldRenderingPipeline> portalix$dimensions();

  @Accessor("pipeline")
  void portalix$pipeline(WorldRenderingPipeline pipeline);
}
