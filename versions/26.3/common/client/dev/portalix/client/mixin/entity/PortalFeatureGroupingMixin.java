package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PhaseSubmitGrouper")
abstract class PortalFeatureGroupingMixin {
  @Shadow @Final private List<SubmitNode> allSubmits;

  @Shadow
  public abstract void accept(SubmitNode submit, boolean strictlyOrdered);

  @Inject(method = "acceptFeatureGroup", at = @At("HEAD"), cancellable = true)
  private void portalix$splitClippedBatch(
      FeatureRendererType<?> type,
      Collection<? extends SubmitNode> submits,
      boolean strictlyOrdered,
      CallbackInfo ci) {
    if (submits.stream().noneMatch(submit -> PortalEntityClipping.mask(submit) != null)) return;
    for (var submit : submits)
      if (submit.featureType() != type)
        throw new IllegalArgumentException(submit + " was not of feature type " + type);
    for (var submit : submits) accept(submit, strictlyOrdered);
    ci.cancel();
  }

  @Definition(
      id = "lastGroup",
      field =
          "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PhaseSubmitGrouper;lastGroup:Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedGroup;")
  @Expression("@(this.lastGroup) != null")
  @ModifyExpressionValue(method = "addOrExtendGroup", at = @At("MIXINEXTRAS:EXPRESSION"), allow = 1)
  private @Coerce Object portalix$clipBoundary(@Coerce Object group) {
    if (group == null) return null;
    int first = ((PortalEntityClipping.Group) group).portalix$firstSubmit();
    return Objects.equals(
            PortalEntityClipping.mask(allSubmits.get(first)),
            PortalEntityClipping.mask(allSubmits.getLast()))
        ? group
        : null;
  }
}
