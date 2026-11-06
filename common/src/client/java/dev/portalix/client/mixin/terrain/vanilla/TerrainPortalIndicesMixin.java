package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.portalix.client.compat.minecraft.VanillaSceneTerrain;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import dev.portalix.client.portal.terrain.PortalTranslucentIndices;
import dev.portalix.client.portal.terrain.PortalViewIndicesAccess;
import dev.portalix.client.portal.terrain.SectionMeshBuffersAccess;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class TerrainPortalIndicesMixin implements PortalViewIndicesAccess {
  @Shadow private ViewArea viewArea;
  @Unique private PortalTranslucentIndices portalix$indices = new PortalTranslucentIndices();

  @Shadow public abstract SectionRenderDispatcher sectionRenderDispatcher();

  @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
  private void portalix$uploadIndices(Matrix4fc matrix, CallbackInfoReturnable<?> cir) {
    portalix$indices.prepare(sectionRenderDispatcher());
  }

  @ModifyExpressionValue(
      method = "scheduleTranslucentSectionResort",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
  private ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$sortWindow(
      ObjectArrayList<SectionRenderDispatcher.RenderSection> sections,
      @Share("portalix$sortWindow") LocalRef<ObjectArrayList<SectionRenderDispatcher.RenderSection>> window) {
    var selected = window.get();
    if (selected == null) {
      selected = VanillaSceneTerrain.renderSections(sections);
      window.set(selected);
    }
    return selected;
  }

  @ModifyExpressionValue(
      method = "scheduleTranslucentSectionResort",
      at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
          target = "Lnet/minecraft/client/renderer/LevelRenderer;nearbyVisibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
  private ObjectArrayList<SectionRenderDispatcher.RenderSection> portalix$nearbySortWindow(
      ObjectArrayList<SectionRenderDispatcher.RenderSection> sections) {
    return VanillaSceneTerrain.renderSections(sections);
  }

  @Inject(method = "scheduleResort", at = @At("HEAD"), cancellable = true)
  private void portalix$independentSort(
      SectionRenderDispatcher.RenderSection section, TranslucencyPointOfView pointOfView,
      Vec3 camera, boolean blockChanged, boolean nearby, CallbackInfo ci) {
    if (!(viewArea instanceof PortalTerrainViewArea)) return;
    portalix$indices.schedule(section, pointOfView, camera, blockChanged, nearby);
    ci.cancel();
  }

  @WrapOperation(
      method = "prepareChunkRenders",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;getRenderSectionSlice(Lnet/minecraft/client/renderer/chunk/SectionMesh;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSectionBufferSlice;"))
  private SectionRenderDispatcher.RenderSectionBufferSlice portalix$viewIndices(
      SectionRenderDispatcher dispatcher,
      SectionMesh mesh,
      ChunkSectionLayer layer,
      Operation<SectionRenderDispatcher.RenderSectionBufferSlice> original) {
    if (!(mesh instanceof SectionMeshBuffersAccess buffers)) return null;
    var slice = buffers.portalix$slice(layer);
    if (viewArea instanceof PortalTerrainViewArea
        && layer == ChunkSectionLayer.TRANSLUCENT
        && slice != null
        && mesh instanceof CompiledSectionMesh compiled)
      return portalix$indices.slice(compiled, slice);
    return slice;
  }

  @Inject(method = "close", at = @At("HEAD"))
  private void portalix$close(CallbackInfo ci) {
    portalix$clearIndices();
  }

  @Override
  public PortalTranslucentIndices portalix$indices() {
    return portalix$indices;
  }

  @Override
  public void portalix$indices(PortalTranslucentIndices indices) {
    portalix$indices = indices;
  }

  @Override
  public void portalix$clearIndices() {
    portalix$indices.close();
  }
}
