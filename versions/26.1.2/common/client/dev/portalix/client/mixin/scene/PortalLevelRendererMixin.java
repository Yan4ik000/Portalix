package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.PortalRenderQueue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class PortalLevelRendererMixin {
  @Shadow @Final private RenderBuffers renderBuffers;

  @Inject(
      method = {"lambda$addMainPass$0", "lambda$addMainPass$1"},
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
          ordinal = 1, shift = At.Shift.AFTER),
      require = 1, allow = 1)
  private void portalix$afterTerrain(CallbackInfo ci) {
    var storage = ((LevelRendererAccessor) this).portalix$submitNodeStorage();
    PortalRenderQueue.drawPhase(storage, renderBuffers.bufferSource(), Phase.AFTER_TERRAIN);
  }

  @WrapMethod(method = "renderLevel")
  private void portalix$renderViews(
      GraphicsResourceAllocator resources,
      DeltaTracker delta,
      boolean outline,
      CameraRenderState camera,
      Matrix4fc modelView,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean sky,
      ChunkSectionsToRender terrain,
      Operation<Void> original) {
    if (!PortalSceneManager.isPortalView()) PortalSceneManager.render(delta, modelView);
    var shaders = ShaderBackend.INSTANCE;
    if (shaders == null) {
      original.call(resources, delta, outline, camera, modelView, fog, fogColor, sky, terrain);
    } else {
      shaders.renderLevel(
          () -> original.call(resources, delta, outline, camera, modelView, fog, fogColor, sky, terrain));
    }
  }
}
