package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import dev.portalix.client.portal.entity.PortalEntitySubmission;
import java.util.Map;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.LeashFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlockFeatureRenderer.class, CustomFeatureRenderer.class, FlameFeatureRenderer.class,
    ItemFeatureRenderer.class, LeashFeatureRenderer.class, ModelFeatureRenderer.class,
    ModelPartFeatureRenderer.class, NameTagFeatureRenderer.class,
    TextFeatureRenderer.class})
abstract class PortalEntityFeatureRenderMixin {
  @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
  private Object portalix$clipFeature(Object next) {
    if (next instanceof PortalEntitySubmission submission)
      PortalEntityClipping.rendering(submission.portalix$clipMask());
    else if (next instanceof Map.Entry<?, ?>) PortalEntityClipping.rendering(null);
    return next;
  }

  @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At("RETURN"))
  private void portalix$endFeatures(CallbackInfo ci) {
    PortalEntityClipping.rendering(null);
  }
}
