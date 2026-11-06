package dev.portalix.client.mixin.terrain.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.mixin.terrain.SectionRenderDispatcherAccessor;
import dev.portalix.client.portal.terrain.SectionMeshBuffersAccess;
import dev.portalix.client.portal.terrain.SectionMeshTaskAccess;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class TerrainMeshPublicationMixin {
  @Shadow @Final SectionRenderDispatcher this$0;
  @Shadow @Final public AtomicReference<SectionMesh> sectionMesh;

  @Shadow
  private void releaseSectionMesh(SectionMesh mesh) {
    throw new AssertionError();
  }

  @WrapOperation(
      method = "setSectionNode",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;reset()V"))
  private void portalix$resetPreviousPosition(
      SectionRenderDispatcher.RenderSection section, Operation<Void> original) {
    if (section.getBoundingBox() != null) original.call(section);
  }

  @WrapMethod(method = "reset")
  private void portalix$reset(Operation<Void> original) {
    this$0.lock();
    try {
      original.call();
    } finally {
      this$0.unlock();
    }
  }

  @WrapMethod(method = "setSectionMesh")
  private SectionMesh portalix$publish(SectionMesh mesh, Operation<SectionMesh> original) {
    this$0.lock();
    try {
      if (((SectionRenderDispatcherAccessor) this$0).portalix$closed()
          || ((SectionMeshTaskAccess) mesh).portalix$cancelled()) return mesh;
      ((SectionMeshBuffersAccess) mesh)
          .portalix$origin(
              ((SectionRenderDispatcher.RenderSection) (Object) this)
                  .getRenderOrigin()
                  .immutable());
      return original.call(mesh);
    } finally {
      this$0.unlock();
    }
  }

  @Unique
  private boolean portalix$acceptUpload(CompiledSectionMesh mesh) {
    if (((SectionRenderDispatcherAccessor) this$0).portalix$closed()
        || sectionMesh.get() != mesh && ((SectionMeshTaskAccess) mesh).portalix$cancelled()) {
      releaseSectionMesh(mesh);
      return false;
    }
    return true;
  }

  @Inject(method = "vertexBufferUploadCallback", at = @At("HEAD"), cancellable = true)
  private void portalix$vertexUpload(
      CompiledSectionMesh mesh, ChunkSectionLayer layer, CallbackInfo ci) {
    if (!portalix$acceptUpload(mesh)) {
      ci.cancel();
      return;
    }
    ((SectionMeshBuffersAccess) mesh)
        .portalix$slice(layer, this$0.getRenderSectionSlice(mesh, layer));
  }

  @Inject(method = "indexBufferUploadCallback", at = @At("HEAD"), cancellable = true)
  private void portalix$indexUpload(
      CompiledSectionMesh mesh, ChunkSectionLayer layer, boolean sorted, CallbackInfo ci) {
    if (!portalix$acceptUpload(mesh)) {
      ci.cancel();
      return;
    }
    ((SectionMeshBuffersAccess) mesh)
        .portalix$slice(layer, this$0.getRenderSectionSlice(mesh, layer));
  }
}
