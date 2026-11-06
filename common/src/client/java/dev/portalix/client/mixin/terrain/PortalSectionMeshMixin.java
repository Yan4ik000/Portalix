package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class PortalSectionMeshMixin {
  @Shadow @Final SectionRenderDispatcher this$0;

  @WrapOperation(
      method = "addSectionBuffersToUberBuffer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/vertex/UberGpuBuffer;addAllocation(Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/UberGpuBuffer$UploadCallback;Ljava/nio/ByteBuffer;)Z"))
  private <T> boolean portalix$liveAllocation(
      UberGpuBuffer<T> buffer,
      T mesh,
      UberGpuBuffer.UploadCallback<T> callback,
      ByteBuffer data,
      Operation<Boolean> original) {

    if (((SectionRenderDispatcherAccessor) this$0).portalix$closed()) return true;
    return original.call(buffer, mesh, callback, data);
  }

  @Inject(method = "releaseSectionMesh", at = @At("HEAD"), cancellable = true)
  private void portalix$unallocatedMesh(SectionMesh mesh, CallbackInfo ci) {

    if (mesh == CompiledSectionMesh.UNCOMPILED) ci.cancel();
  }
}
