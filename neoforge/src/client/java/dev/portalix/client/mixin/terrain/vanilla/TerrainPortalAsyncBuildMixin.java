package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent.AdditionalSectionRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalAsyncBuildMixin {
  @WrapOperation(
      method = "compileSections",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Ljava/util/List;)V"))
  private void portalix$asyncBuild(
      SectionRenderDispatcher.RenderSection section,
      RenderSectionRegion region,
      List<AdditionalSectionRenderer> additionalRenderers,
      Operation<Void> original) {
    if (PortalSceneManager.isPortalView()
        || section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED)
      section.compileAsync(region, additionalRenderers);
    else original.call(section, region, additionalRenderers);
  }
}
