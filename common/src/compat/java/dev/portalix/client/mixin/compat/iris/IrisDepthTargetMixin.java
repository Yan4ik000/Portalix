package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.portalix.client.compat.iris.IrisTargetHistory;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderTargets.class)
abstract class IrisDepthTargetMixin implements IrisTargetHistory {
  @Shadow private GpuTexture currentDepthTexture;
  @Shadow private boolean fullClearRequired;

  @Override
  public void portalix$invalidateHistory() {
    fullClearRequired = true;
  }

  @Definition(
      id = "cached",
      field = "Lnet/irisshaders/iris/targets/RenderTargets;cachedDepthBufferVersion:I")
  @Expression("this.cached != ?")
  @ModifyExpressionValue(method = "resizeIfNeeded", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private boolean portalix$depthIdentity(
      boolean changed, @Local(argsOnly = true) GpuTexture depth) {
    boolean replaced = currentDepthTexture != depth;
    if (replaced) fullClearRequired = true;
    return changed || replaced;
  }
}
