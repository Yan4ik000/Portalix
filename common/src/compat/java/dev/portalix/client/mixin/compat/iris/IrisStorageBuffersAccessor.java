package dev.portalix.client.mixin.compat.iris;

import java.util.List;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderStorageBufferHolder.class)
public interface IrisStorageBuffersAccessor {
  @Accessor("ACTIVE_BUFFERS")
  static List<ShaderStorageBuffer> portalix$activeBuffers() {
    throw new AssertionError();
  }
}
