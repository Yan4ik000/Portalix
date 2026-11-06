package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumArenaBatch;
import net.caffeinemc.mods.sodium.client.gpu.arena.RegionAllocatorHandle;
import net.caffeinemc.mods.sodium.client.gpu.arena.SharedBufferArena;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = SharedBufferArena.class, remap = false)
abstract class SodiumArenaBatchMixin implements SodiumArenaBatch {
  @Unique private RegionAllocatorHandle portalix$releasing;

  @Shadow
  protected abstract void removeOwner(RegionAllocatorHandle owner);

  @Shadow
  protected abstract void addOwner(RegionAllocatorHandle owner);

  @Override
  public void portalix$beginRelease(RegionAllocatorHandle owner) {
    if (portalix$releasing != null)
      throw new IllegalStateException("Arena release already active");
    removeOwner(owner);
    portalix$releasing = owner;
  }

  @Override
  public void portalix$endRelease() {
    addOwner(portalix$releasing);
    portalix$releasing = null;
  }

  @WrapOperation(
      method = "updateUsed",
      require = 2,
      allow = 2,
      at = {
        @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/gpu/arena/SharedBufferArena;removeOwner(Lnet/caffeinemc/mods/sodium/client/gpu/arena/RegionAllocatorHandle;)V"),
        @At(value = "INVOKE", target =
            "Lnet/caffeinemc/mods/sodium/client/gpu/arena/SharedBufferArena;addOwner(Lnet/caffeinemc/mods/sodium/client/gpu/arena/RegionAllocatorHandle;)V")
      })
  private void portalix$releaseAccounting(
      SharedBufferArena arena, RegionAllocatorHandle owner, Operation<Void> original) {
    if (owner != portalix$releasing) original.call(arena, owner);
  }
}
