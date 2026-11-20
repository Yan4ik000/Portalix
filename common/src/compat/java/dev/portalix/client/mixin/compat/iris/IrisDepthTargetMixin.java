package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisTargetHistory;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderTargets.class)
abstract class IrisDepthTargetMixin implements IrisTargetHistory {
  @Shadow private boolean fullClearRequired;
  @Unique private boolean portalix$prewarmed;
  @Unique private boolean portalix$setupReset;

  @Override
  public void portalix$invalidateHistory() {
    fullClearRequired = true;
    portalix$setupReset = true;
  }

  @Override
  public boolean portalix$consumeSetupReset() {
    boolean reset = portalix$setupReset;
    portalix$setupReset = false;
    return reset;
  }

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$prepared(CallbackInfo ci) {
    portalix$prewarmed = IrisScene.prewarming();
  }

  @WrapOperation(method = "create", at = @At(value = "INVOKE", target =
      "Lnet/irisshaders/iris/targets/RenderTarget$Builder;setDimensions(II)Lnet/irisshaders/iris/targets/RenderTarget$Builder;"),
      allow = 1)
  private RenderTarget.Builder portalix$prewarmColor(
      RenderTarget.Builder builder, int width, int height, Operation<RenderTarget.Builder> original) {
    return IrisScene.prewarming()
        ? original.call(builder, 1, 1)
        : original.call(builder, width, height);
  }

  @ModifyVariable(method = "resizeIfNeeded", at = @At("STORE"), name = "sizeChanged", allow = 1)
  private boolean portalix$allocatePreparedTargets(boolean changed) {
    boolean prepared = portalix$prewarmed;
    portalix$prewarmed = false;
    return changed || prepared;
  }

  @Definition(
      id = "cached",
      field = "Lnet/irisshaders/iris/targets/RenderTargets;cachedDepthBufferVersion:I")
  @Expression("this.cached != ?")
  @ModifyExpressionValue(method = "resizeIfNeeded", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private boolean portalix$depthIdentity(
      boolean changed, @Local(argsOnly = true) GpuTexture depth) {
    boolean replaced = ((RenderTargets) (Object) this).getDepthTexture() != depth;
    if (replaced) portalix$invalidateHistory();
    return changed || replaced;
  }
}
