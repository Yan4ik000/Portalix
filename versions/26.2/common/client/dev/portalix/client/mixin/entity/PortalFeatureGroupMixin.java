package dev.portalix.client.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.entity.PortalEntityClipping;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedGroup")
abstract class PortalFeatureGroupMixin implements PortalEntityClipping.Group {
  @Shadow @Final private int fromInclusive;

  @Override
  public int portalix$firstSubmit() {
    return fromInclusive;
  }

  @WrapMethod(method = "execute")
  private void portalix$clip(
      FeatureFrameContext context,
      FeatureRendererMap renderers,
      List<SubmitNode> submits,
      Operation<Void> original) {
    PortalClipping.withEntity(
        PortalEntityClipping.mask(submits.get(fromInclusive)),
        () -> original.call(context, renderers, submits));
  }
}
