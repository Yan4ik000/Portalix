package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import dev.portalix.client.portal.entity.PortalEntitySubmission;
import net.neoforged.neoforge.client.extensions.SubmitNodeStorageExtension.MultiLayerBlockModelSubmit;
import net.neoforged.neoforge.client.submit.ExtendedBlockFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtendedBlockFeatureRenderer.class)
abstract class PortalMultiLayerBlockFeatureMixin {
  @WrapOperation(method = "renderMultiLayerBlockModelSubmits", at = @At(value = "INVOKE", target =
      "Lnet/neoforged/neoforge/client/extensions/SubmitNodeStorageExtension$MultiLayerBlockModelSubmit;translucent()Z"), allow = 1)
  private static boolean portalix$clipFeature(MultiLayerBlockModelSubmit submit, Operation<Boolean> original) {
    PortalEntityClipping.rendering(((PortalEntitySubmission) (Object) submit).portalix$clipMask());
    return original.call(submit);
  }

  @Inject(method = "renderMultiLayerBlockModelSubmits", at = @At("RETURN"))
  private static void portalix$endFeatures(CallbackInfo ci) {
    PortalEntityClipping.rendering(null);
  }
}
