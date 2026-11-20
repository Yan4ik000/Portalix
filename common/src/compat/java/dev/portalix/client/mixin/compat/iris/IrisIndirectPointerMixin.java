package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.properties.IndirectPointer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilledIndirectPointer.class)
abstract class IrisIndirectPointerMixin {
  @Unique private ShaderStorageBufferHolder portalix$storage;
  @Unique private int portalix$index;

  @Inject(method = "basedOff", at = @At("RETURN"))
  private static void portalix$storageOwner(
      ShaderStorageBufferHolder storage, IndirectPointer pointer,
      CallbackInfoReturnable<FilledIndirectPointer> cir) {
    if (cir.getReturnValue() == null) return;
    var resolved = (IrisIndirectPointerMixin) (Object) cir.getReturnValue();
    resolved.portalix$storage = storage;
    resolved.portalix$index = pointer.buffer();
  }

  @Inject(method = "buffer", at = @At("HEAD"), cancellable = true)
  private void portalix$currentBuffer(CallbackInfoReturnable<Integer> cir) {
    if (portalix$storage != null)
      cir.setReturnValue(portalix$storage.getBufferIndex(portalix$index));
  }
}
