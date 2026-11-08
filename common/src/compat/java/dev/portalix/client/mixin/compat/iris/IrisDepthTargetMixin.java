package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisTargetHistory;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderTargets.class)
abstract class IrisDepthTargetMixin implements IrisTargetHistory {
  @Shadow private GpuTexture currentDepthTexture;
  @Shadow private GpuTexture noHand;
  @Shadow private int cachedWidth;
  @Shadow private int cachedHeight;
  @Shadow private boolean fullClearRequired;

  @Override
  public void portalix$invalidateHistory() {
    fullClearRequired = true;
  }

  @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target =
      "Lcom/mojang/blaze3d/systems/GpuDevice;createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;"),
      require = 2, allow = 2)
  private GpuTexture portalix$prewarmDepth(
      GpuDevice device, String label, int usage, GpuFormat format, int width, int height,
      int layers, int levels, Operation<GpuTexture> original) {
    if (IrisScene.prewarming())
      return original.call(device, label, usage, format, 1, 1, layers, levels);
    return original.call(device, label, usage, format, width, height, layers, levels);
  }

  // Enter only the native depth reallocation branch when a warmed view first renders at the same size.
  @ModifyVariable(method = "resizeIfNeeded", at = @At(value = "LOAD", ordinal = 1),
      name = "depthFormatChanged", require = 1, allow = 1)
  private boolean portalix$allocateViewDepth(boolean changed) {
    return changed || noHand.getWidth(0) != cachedWidth || noHand.getHeight(0) != cachedHeight;
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
