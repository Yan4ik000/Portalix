package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import dev.portalix.client.portal.clip.GlClipState;
import dev.portalix.client.portal.clip.PortalClipping;
import java.util.Map;
import java.util.WeakHashMap;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import net.irisshaders.iris.pipeline.programs.IrisBindings;
import net.irisshaders.iris.pipeline.programs.IrisProgram;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
abstract class IrisClipBindingsMixin {
  @Unique private static final int portalix$CLIP_BINDING = IrisBindings.PUSH_CONSTANTS + 1;
  @Unique private static final Map<GlProgram, Boolean> portalix$clipPrograms = new WeakHashMap<>();
  @Unique private @Nullable GpuBufferSlice portalix$clipSlice;

  @Inject(method = "setupDraw", at = @At("HEAD"))
  private void portalix$invalidateCustomDraw(@Coerce Object pass, CallbackInfo ci) {
    if (((RenderPassInterface) pass).iris$getCustomPass() != null) {
      portalix$clipSlice = null;
      GlClipState.setEnabled(false);
    }
  }

  @Inject(method = "submitRenderPass", at = @At("TAIL"))
  private void portalix$releaseClipBinding(CallbackInfo ci) {
    portalix$clipSlice = null;
  }

  @ModifyExpressionValue(method = "setupDraw", at = @At(value = "INVOKE", target =
      "Lcom/mojang/renderpearl/backend/opengl/GlRenderPipeline;program()Lcom/mojang/renderpearl/backend/opengl/GlProgram;"),
      allow = 1)
  private GlProgram portalix$bindClip(GlProgram program) {
    if (!(program instanceof IrisProgram)) {
      // A vanilla program can use the same index for a different uniform block.
      portalix$clipSlice = null;
    } else if (portalix$clipPrograms.computeIfAbsent(program, IrisClipBindingsMixin::portalix$bindClipBlock)) {
      var clip = PortalClipping.uniform();
      if (!clip.equals(portalix$clipSlice)) {
        GL33C.glBindBufferRange(GL33C.GL_UNIFORM_BUFFER, portalix$CLIP_BINDING,
            ((GlBuffer) clip.buffer()).handle(), clip.offset(), clip.length());
        portalix$clipSlice = clip;
      }
      GlClipState.setEnabled(PortalClipping.active());
    }
    return program;
  }

  @Unique
  private static boolean portalix$bindClipBlock(GlProgram program) {
    // Iris owns the other UBO bindings and rebuilds its uniform table when switching pipelines.
    int id = program.getProgramId();
    int index = GL33C.glGetUniformBlockIndex(id, "iris_" + PortalClipping.UNIFORM_NAME);
    if (index == GL33C.GL_INVALID_INDEX) {
      index = GL33C.glGetUniformBlockIndex(id, PortalClipping.UNIFORM_NAME);
      if (index == GL33C.GL_INVALID_INDEX) return false;
    }
    GL33C.glUniformBlockBinding(id, index, portalix$CLIP_BINDING);
    return true;
  }
}
