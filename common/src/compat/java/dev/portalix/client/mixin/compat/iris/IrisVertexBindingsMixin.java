package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderPipeline.class)
abstract class IrisVertexBindingsMixin {
  @Shadow @Final private VertexFormat[] vertexFormatPerBuffer;
  @Shadow public abstract VertexFormat getVertexFormatBinding(int index);
  @Unique private VertexFormat[] portalix$irisBindings;

  @WrapMethod(method = "getVertexFormatBindings")
  private VertexFormat[] portalix$completeBindings(Operation<VertexFormat[]> original) {
    var rewritten = original.call();
    if (rewritten.length >= vertexFormatPerBuffer.length) return rewritten;
    // Iris replaces the first binding with a short array; Minecraft validates every binding slot.
    var previous = portalix$irisBindings;
    var complete = previous == null ? vertexFormatPerBuffer.clone() : previous;
    for (int index = 0; index < vertexFormatPerBuffer.length; index++) {
      var expected = index < rewritten.length ? rewritten[index]
          : vertexFormatPerBuffer[index] == null ? null : getVertexFormatBinding(index);
      if (complete[index] != expected) {
        if (complete == previous) complete = complete.clone();
        complete[index] = expected;
      }
    }
    portalix$irisBindings = complete;
    return complete;
  }
}
