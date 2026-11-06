package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelRenderer.class)
abstract class PortalLevelRendererMixin {
  @WrapMethod(method = "render")
  private void portalix$renderViews(
      GraphicsResourceAllocator resources,
      DeltaTracker delta,
      boolean outline,
      CameraRenderState camera,
      Matrix4fc modelView,
      GpuBufferSlice fog,
      Vector4f fogColor,
      boolean sky,
      Operation<Void> original) {
    if (!PortalSceneManager.isPortalView()) PortalSceneManager.render(delta, modelView);
    original.call(resources, delta, outline, camera, modelView, fog, fogColor, sky);
  }
}
