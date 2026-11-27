package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityClipMask;
import dev.portalix.client.portal.entity.PortalEntitySubmission;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage.ShadowSubmit;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShadowFeatureRenderer.class)
abstract class PortalEntityFeatureShadowMixin {
  @Shadow @Final private static RenderType SHADOW_RENDER_TYPE;
  @Unique private static final ScopedValue<List<ShadowSubmit>> portalix$shadowGroup = ScopedValue.newInstance();

  @WrapMethod(method = "renderTranslucent")
  private void portalix$drawShadowGroups(SubmitNodeCollection nodes, MultiBufferSource.BufferSource buffers,
      Operation<Void> original) {
    var shadows = nodes.getShadowSubmits();
    if (shadows.stream().noneMatch(shadow -> portalix$mask(shadow) != null)) {
      original.call(nodes, buffers);
      return;
    }

    buffers.endBatch(SHADOW_RENDER_TYPE);
    for (int start = 0; start < shadows.size(); ) {
      var mask = portalix$mask(shadows.get(start));
      int end = start + 1;
      while (end < shadows.size() && Objects.equals(mask, portalix$mask(shadows.get(end)))) end++;
      var group = shadows.subList(start, end);
      PortalClipping.withEntity(mask, () -> ScopedValue.where(portalix$shadowGroup, group).run(() -> {
        original.call(nodes, buffers);
        buffers.endBatch(SHADOW_RENDER_TYPE);
      }));
      start = end;
    }
  }

  @WrapOperation(method = "renderTranslucent", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/SubmitNodeCollection;getShadowSubmits()Ljava/util/List;"))
  private List<ShadowSubmit> portalix$selectShadowGroup(SubmitNodeCollection nodes,
      Operation<List<ShadowSubmit>> original) {
    return portalix$shadowGroup.isBound() ? portalix$shadowGroup.get() : original.call(nodes);
  }

  @Unique
  private static PortalEntityClipMask portalix$mask(ShadowSubmit shadow) {
    return ((PortalEntitySubmission) (Object) shadow).portalix$clipMask();
  }
}
