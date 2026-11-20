package dev.portalix.client.compat.iris;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import dev.portalix.client.mixin.compat.iris.IrisStorageBufferAccessor;
import dev.portalix.client.mixin.compat.iris.IrisStorageBuffersAccessor;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.gl.program.Program;
import org.lwjgl.opengl.GL20C;

final class IrisPipelineCreation {
  final IntOpenHashSet programs = new IntOpenHashSet();
  final IntOpenHashSet shaders = new IntOpenHashSet();
  final List<Runnable> resources = new ArrayList<>();
  private final Set<?> existingBuffers = Set.copyOf(IrisStorageBuffersAccessor.portalix$activeBuffers());

  void rollback() {
    Program.unbind();
    resources.reversed().forEach(Runnable::run);
    for (int program : programs) {
      if (GL20C.glIsProgram(program)) GlStateManager.glDeleteProgram(program);
    }
    for (int shader : shaders) {
      if (GL20C.glIsShader(shader) && GL20C.glGetShaderi(shader, GL20C.GL_DELETE_STATUS) == GL20C.GL_FALSE)
        GlStateManager.glDeleteShader(shader);
    }
    IrisStorageBuffersAccessor.portalix$activeBuffers().removeIf(buffer -> {
      if (existingBuffers.contains(buffer)) return false;
      ((IrisStorageBufferAccessor) buffer).portalix$destroy();
      return true;
    });
  }
}
