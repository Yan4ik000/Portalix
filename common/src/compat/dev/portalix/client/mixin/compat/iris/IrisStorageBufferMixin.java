package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisScene;
import dev.portalix.client.compat.iris.IrisStorageHistory;
import dev.portalix.client.mixin.scene.GameRendererAccessor;
import java.nio.ByteBuffer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.buffer.BuiltShaderStorageInfo;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ShaderStorageBuffer.class)
abstract class IrisStorageBufferMixin implements IrisStorageHistory {
  @Shadow @Final protected int index;
  @Shadow @Final protected BuiltShaderStorageInfo info;
  @Shadow @Final protected ByteBuffer content;
  @Shadow protected int id;

  @Override
  public void portalix$restoreInitialContents() {
    GL43C.glBindBuffer(GL43C.GL_COPY_WRITE_BUFFER, id);
    GL43C.glClearBufferData(
        GL43C.GL_COPY_WRITE_BUFFER, GL43C.GL_R8, GL43C.GL_RED, GL43C.GL_BYTE, (ByteBuffer) null);
    if (!info.relative() && content != null && content.hasRemaining()) {
      GL43C.glBufferSubData(GL43C.GL_COPY_WRITE_BUFFER, 0, content);
    }
  }

  @ModifyVariable(method = "resizeIfRelative", at = @At("STORE"), name = "finalSize", allow = 1)
  private long portalix$indirectCommandStorage(long size) {
    if (!IrisScene.preparing() && IrisScene.current() == null) return size;
    // A fractional scale can round a cropped dimension down to zero.
    size = Math.max(size, info.size());
    if (IrisScene.prewarming()) return size;

    var pack = (IrisShaderPackAccessor) Iris.getCurrentPack().orElseThrow();
    var primary = ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).portalix$mainTarget();
    long fullWidth = (long) (primary.width * info.scaleX());
    long fullHeight = (long) (primary.height * info.scaleY());
    long fullSize = fullWidth * fullHeight * info.size();
    for (var pointer : pack.portalix$properties().getIndirectPointers().values()) {
      // An indirect dispatch reads three uints. Preserve offsets valid at native resolution.
      if (pointer.buffer() == index && pointer.offset() >= 0 && pointer.offset() <= fullSize - 3L * Integer.BYTES)
        size = Math.max(size, pointer.offset() + 3L * Integer.BYTES);
    }
    return size;
  }
}
