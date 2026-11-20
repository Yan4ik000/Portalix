package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.Arrays;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderPipeline.class)
abstract class IrisVertexBindingsMixin {
  @Shadow @Final private List<VertexFormat> vertexFormatPerBuffer;
  @Shadow public abstract VertexFormat getVertexFormatBinding(int index);
  @Unique private List<VertexFormat> portalix$irisBindings;

  @WrapMethod(method = "getVertexFormatBindings")
  private List<VertexFormat> portalix$completeBindings(Operation<List<VertexFormat>> original) {
    var rewritten = original.call();
    if (rewritten.size() >= vertexFormatPerBuffer.size()) return rewritten;
    // Iris replaces the first binding with a single-element list, while the others are still used.
    var previous = portalix$irisBindings;
    VertexFormat[] complete = previous == null ? vertexFormatPerBuffer.toArray(VertexFormat[]::new) : null;
    for (int index = 0; index < vertexFormatPerBuffer.size(); index++) {
      var expected = index < rewritten.size() ? rewritten.get(index)
          : vertexFormatPerBuffer.get(index) == null ? null : getVertexFormatBinding(index);
      if (complete == null && previous.get(index) != expected)
        complete = previous.toArray(VertexFormat[]::new);
      if (complete != null) complete[index] = expected;
    }
    if (complete != null) portalix$irisBindings = Arrays.asList(complete);
    return portalix$irisBindings;
  }
}
