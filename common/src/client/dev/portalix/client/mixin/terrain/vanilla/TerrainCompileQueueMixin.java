package dev.portalix.client.mixin.terrain.vanilla;

import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection.SectionTask;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SectionTaskDynamicQueue.class, priority = 900)
abstract class TerrainCompileQueueMixin {
  @Shadow @Final private List<SectionTask> tasks;

  @WrapOperation(method = "poll", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"))
  private double portalix$viewDistance(BlockPos origin, Position camera, Operation<Double> original,
      @Share("portalix$workCameras") LocalRef<PortalSceneManager.TerrainWorkCameras> captured) {
    var cameras = captured.get();
    if (cameras == null) {
      cameras = PortalSceneManager.terrainWorkCameras();
      captured.set(cameras);
    }
    return cameras.isEmpty() ? original.call(origin, camera) : cameras.distanceSquared(origin);
  }

  @Inject(method = "poll", at = @At("HEAD"))
  private void portalix$compactCancelled(CallbackInfoReturnable<SectionTask> cir) {
    tasks.removeIf(task -> ((SectionTaskAccessor) task).portalix$cancelled().get());
  }
}
