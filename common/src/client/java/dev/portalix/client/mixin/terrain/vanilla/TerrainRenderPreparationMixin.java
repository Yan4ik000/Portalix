package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import dev.portalix.client.portal.terrain.SectionMeshBuffersAccess;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class TerrainRenderPreparationMixin {
  @Shadow private ViewArea viewArea;
  @Unique private static final ChunkSectionLayer[] PORTALIX_NO_LAYERS = {};

  @Inject(method = "clearVisibleSections", at = @At("HEAD"))
  private void portalix$clearRasterSections(CallbackInfo ci) {
    if (viewArea instanceof PortalTerrainViewArea area) area.clearRasterSections();
  }

  @ModifyExpressionValue(
      method = "extractSectionDrawGroups",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
  private ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$rasterSections(
      ObjectArrayList<SectionRenderDispatcher.RenderSection> sections) {
    return VanillaSceneTerrain.renderSections(sections);
  }

  @WrapOperation(
      method = "extractSectionDrawGroups",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;values()[Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"))
  private ChunkSectionLayer[] portalix$meshLayers(
      Operation<ChunkSectionLayer[]> original, @Local SectionMesh mesh) {
    if (mesh instanceof SectionMeshBuffersAccess buffers) return buffers.portalix$allocatedLayers();
    if (mesh == CompiledSectionMesh.EMPTY || mesh == CompiledSectionMesh.UNCOMPILED)
      return PORTALIX_NO_LAYERS;
    return original.call();
  }

}
