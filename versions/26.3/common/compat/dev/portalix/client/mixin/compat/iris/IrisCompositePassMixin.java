package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import java.util.List;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.minecraft.client.renderer.BindGroupLayouts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FrontendRenderPass.class)
abstract class IrisCompositePassMixin {
  @Shadow private FrontendRenderPipeline boundPipeline;

  @ModifyExpressionValue(method = "validateDraw", at = @At(value = "INVOKE", target =
      "Lcom/mojang/renderpearl/frontend/FrontendRenderPipeline;uniforms()Ljava/util/List;"), allow = 1)
  private List<BindGroupLayout.UniformDescription> portalix$customCompositeUniforms(
      List<BindGroupLayout.UniformDescription> uniforms) {
    return portalix$isCustomComposite() && uniforms.equals(BindGroupLayouts.IN_SAMPLER.uniforms())
        ? List.of() : uniforms;
  }

  @ModifyExpressionValue(method = "validateDraw", at = @At(value = "INVOKE", target =
      "Lcom/mojang/renderpearl/frontend/FrontendRenderPipeline;wantsDepthTexture()Z"), allow = 1)
  private boolean portalix$customCompositeDepth(boolean wantsDepth) {
    return wantsDepth && !portalix$isCustomComposite();
  }

  @Unique
  private boolean portalix$isCustomComposite() {
    // Iris uses this pipeline for vertex input and supplies the actual shader and framebuffer.
    return ((RenderPassInterface) this).iris$getCustomPass() != null
        && boundPipeline.name().equals(CompositeRenderer.COMPOSITE_PIPELINE.getLocation().toString());
  }
}
