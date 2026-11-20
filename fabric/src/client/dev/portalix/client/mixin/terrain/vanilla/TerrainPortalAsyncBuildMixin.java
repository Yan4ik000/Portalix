package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
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
                  "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"))
  private void portalix$asyncBuild(
      SectionRenderDispatcher.RenderSection section,
      RenderSectionRegion region,
      Operation<Void> original) {
    if (PortalSceneManager.isPortalView()
        || section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) section.compileAsync(region);
    else original.call(section, region);
  }
}
