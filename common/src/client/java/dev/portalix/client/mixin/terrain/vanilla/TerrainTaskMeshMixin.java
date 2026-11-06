package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.terrain.SectionMeshTaskAccess;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets =
        "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
abstract class TerrainTaskMeshMixin {
  @WrapOperation(
      method = "doTask",
      at = @At(value = "NEW", target = "net/minecraft/client/renderer/chunk/CompiledSectionMesh"))
  private CompiledSectionMesh portalix$task(
      TranslucencyPointOfView view,
      SectionCompiler.Results results,
      Operation<CompiledSectionMesh> original) {
    var mesh = original.call(view, results);
    ((SectionMeshTaskAccess) mesh)
        .portalix$task(((SectionTaskAccessor) this).portalix$cancelled());
    return mesh;
  }
}
