package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShaderStorageBuffer.class)
public interface IrisStorageBufferAccessor {
  @Invoker("destroy")
  void portalix$destroy();
}
