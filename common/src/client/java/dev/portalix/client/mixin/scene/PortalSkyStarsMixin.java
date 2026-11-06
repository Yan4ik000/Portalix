package dev.portalix.client.mixin.scene;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.portalix.client.portal.scene.SharedStarMesh;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SkyRenderer.class)
abstract class PortalSkyStarsMixin {
  @Shadow private int starIndexCount;

  @WrapMethod(method = "buildStars")
  private GpuBuffer portalix$stars(Operation<GpuBuffer> original) {
    var mesh =
        SharedStarMesh.acquire(() -> new SharedStarMesh.Mesh(original.call(), starIndexCount));
    starIndexCount = mesh.indexCount();
    return mesh.buffer();
  }

  @Definition(
      id = "starBuffer",
      field =
          "Lnet/minecraft/client/renderer/SkyRenderer;starBuffer:Lcom/mojang/blaze3d/buffers/GpuBuffer;")
  @Definition(id = "close", method = "Lcom/mojang/blaze3d/buffers/GpuBuffer;close()V")
  @Expression("this.starBuffer.close()")
  @WrapOperation(method = "close", at = @At("MIXINEXTRAS:EXPRESSION"))
  private void portalix$releaseStars(GpuBuffer buffer, Operation<Void> original) {
    SharedStarMesh.release(buffer, () -> original.call(buffer));
  }
}
