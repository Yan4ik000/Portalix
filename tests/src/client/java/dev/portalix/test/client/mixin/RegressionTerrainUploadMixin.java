package dev.portalix.test.client.mixin;

import dev.portalix.test.client.PortalTerrainUploadProbe;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RegressionTerrainUploadMixin {
  @Inject(method = "setSectionMesh", at = @At("RETURN"))
  private void portalix$uploaded(SectionMesh mesh, CallbackInfoReturnable<SectionMesh> callback) {
    var section = (SectionRenderDispatcher.RenderSection) (Object) this;
    if (section.getSectionMesh() == mesh) PortalTerrainUploadProbe.uploaded(section.getSectionNode());
  }
}
