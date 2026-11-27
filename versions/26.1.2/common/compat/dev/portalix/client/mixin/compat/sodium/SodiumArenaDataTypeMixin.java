package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.compat.sodium.SodiumArenaCapacity;
import dev.portalix.client.compat.sodium.SodiumSharedArenaRetirement;
import net.caffeinemc.mods.sodium.client.gl.arena.SharedBufferArena;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.gl.arena.ArenaAggregator$DataType",
    remap = false)
abstract class SodiumArenaDataTypeMixin {
  @Shadow @Final int stride;

  @ModifyExpressionValue(
      method = "ensureSharedArena(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;JI)Lnet/caffeinemc/mods/sodium/client/gl/arena/SharedBufferArena;",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/gl/arena/ArenaAggregator$DataType;calculateArenaSize(IJ)J"),
      require = 1,
      allow = 1)
  private long portalix$demandSize(
      long nativeCapacity, @Local(argsOnly = true) long requiredCapacity) {
    return SodiumArenaCapacity.calculate(requiredCapacity * stride, nativeCapacity, stride);
  }

  @ModifyExpressionValue(
      method = "createSharedArena",
      at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/gl/buffer/GlMutableBuffer;getSize()J"))
  private long portalix$alignedCapacity(long bytes) {
    return SodiumArenaCapacity.align(bytes, stride);
  }

  @WrapOperation(
      method = "update",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/caffeinemc/mods/sodium/client/gl/arena/SharedBufferArena;deleteShared(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;)V"))
  private void portalix$recycleRetiredArena(SharedBufferArena arena, CommandList commands, Operation<Void> operation) {
    var retirement = (SodiumSharedArenaRetirement) arena;
    retirement.portalix$recycleOnRetirement(true);
    try {
      operation.call(arena, commands);
    } finally {
      retirement.portalix$recycleOnRetirement(false);
    }
  }
}
