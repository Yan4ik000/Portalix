package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LevelRenderer.class, priority = 1100)
abstract class TerrainPortalAsyncBuildMixin {
  @WrapOperation(method = "compileSections", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;rebuildSectionSync(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
  private void portalix$asyncBuild(SectionRenderDispatcher dispatcher, RenderSection section,
      RenderRegionCache cache, Operation<Void> original) {
    if (PortalSceneManager.isPortalView() || section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED)
      section.rebuildSectionAsync(cache);
    else original.call(dispatcher, section, cache);
  }
}
