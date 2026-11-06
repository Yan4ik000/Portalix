package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.sodium.SodiumArenaCapacity;
import dev.portalix.client.compat.sodium.SodiumSharedArenaRetirement;
import net.caffeinemc.mods.sodium.client.gpu.arena.SharedBufferArena;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.gpu.arena.ArenaAggregator$DataType",
    remap = false)
abstract class SodiumArenaDataTypeMixin {
  @Shadow @Final int stride;

  @ModifyExpressionValue(
      method = "ensureSharedArena(JI)Lnet/caffeinemc/mods/sodium/client/gpu/arena/SharedBufferArena;",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/gpu/arena/ArenaAggregator$DataType;calculateArenaSize(IJ)J"),
      require = 1,
      allow = 1)
  private long portalix$demandSize(
      long nativeCapacity, @Local(argsOnly = true) long requiredCapacity) {
    return SodiumArenaCapacity.calculate(requiredCapacity * stride, nativeCapacity, stride);
  }

  @ModifyExpressionValue(
      method = "createSharedArena",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/GpuBuffer;size()J"))
  private long portalix$alignedCapacity(long bytes) {
    return SodiumArenaCapacity.align(bytes, stride);
  }

  @WrapOperation(
      method = "update",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/gpu/arena/SharedBufferArena;deleteShared()V"))
  private void portalix$recycleRetiredArena(SharedBufferArena arena, Operation<Void> operation) {
    var retirement = (SodiumSharedArenaRetirement) arena;
    retirement.portalix$recycleOnRetirement(true);
    try {
      operation.call(arena);
    } finally {
      retirement.portalix$recycleOnRetirement(false);
    }
  }
}
