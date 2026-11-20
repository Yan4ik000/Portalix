package dev.portalix.client.mixin.gpu;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.util.TransientBlockAllocator;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransientBlockAllocator.class)
abstract class TransientBlockPoolMixin<T> {
  @Shadow @Final private long blockSize;
  @Shadow @Final private TransientBlockAllocator.Allocator<T> allocator;
  @Shadow @Final private ReferenceArrayList<T> freeBlocks;
  @Shadow @Final private ReferenceArrayList<T> usedBlocks;
  @Shadow @Final private ReferenceArrayList<T> specialBlocks;
  @Shadow private T currentBlock;
  @Shadow private long currentOffset;
  @Unique private int portalix$peakBlocks;
  @Unique private boolean portalix$closing;

  @Inject(method = "close", at = @At("HEAD"))
  private void portalix$close(CallbackInfo ci) {
    portalix$closing = true;
  }

  @WrapMethod(method = "rotate")
  private Runnable portalix$rotate(Operation<Runnable> original) {
    currentBlock = null;
    currentOffset = blockSize;
    portalix$peakBlocks = Math.max(portalix$peakBlocks, usedBlocks.size());
    portalix$trim();
    if (usedBlocks.isEmpty() && specialBlocks.isEmpty()) return () -> {};
    var used = usedBlocks.clone();
    var special = specialBlocks.clone();
    usedBlocks.clear();
    specialBlocks.clear();

    return () -> {
      freeBlocks.addAll(used);
      special.forEach(allocator::free);
      portalix$trim();
    };
  }

  @Unique
  private void portalix$trim() {
    int retained = portalix$closing ? 0 : portalix$peakBlocks;
    while (freeBlocks.size() > retained) allocator.free(freeBlocks.pop());
  }
}
