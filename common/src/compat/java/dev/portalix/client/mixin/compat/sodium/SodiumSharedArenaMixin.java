package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.portalix.client.compat.sodium.SodiumSharedArenaRetirement;
import net.caffeinemc.mods.sodium.client.gpu.arena.SharedBufferArena;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = SharedBufferArena.class, remap = false)
abstract class SodiumSharedArenaMixin implements SodiumSharedArenaRetirement {
  @Unique private boolean portalix$recycleOnRetirement;

  @Override
  public void portalix$recycleOnRetirement(boolean recycle) {
    portalix$recycleOnRetirement = recycle;
  }

  @WrapOperation(
      method = "deleteShared",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/GpuBuffer;close()V"))
  private void portalix$returnRetiredBuffer(GpuBuffer buffer, Operation<Void> operation) {
    if (portalix$recycleOnRetirement) {
      var parent = ((BufferArenaAccessor) this).portalix$parent();
      ((ArenaAggregatorAccessor) parent).portalix$releaseBufferForReuse(buffer);
    } else {
      operation.call(buffer);
    }
  }
}
