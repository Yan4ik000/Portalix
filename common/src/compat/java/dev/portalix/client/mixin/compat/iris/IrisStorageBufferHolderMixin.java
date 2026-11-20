package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisStorageHistory;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ShaderStorageBufferHolder.class)
abstract class IrisStorageBufferHolderMixin implements IrisStorageHistory {
  @Shadow private ShaderStorageBuffer[] buffers;

  @Override
  public void portalix$restoreInitialContents() {
    GL43C.glMemoryBarrier(GL43C.GL_BUFFER_UPDATE_BARRIER_BIT);
    int previous = GL43C.glGetInteger(GL43C.GL_COPY_WRITE_BUFFER_BINDING);
    try {
      for (var buffer : buffers) {
        if (buffer != null) ((IrisStorageHistory) buffer).portalix$restoreInitialContents();
      }
    } finally {
      GL43C.glBindBuffer(GL43C.GL_COPY_WRITE_BUFFER, previous);
    }
  }
}
