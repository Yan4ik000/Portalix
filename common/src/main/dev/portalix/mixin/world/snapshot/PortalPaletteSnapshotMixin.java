package dev.portalix.mixin.world.snapshot;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.world.snapshot.PalettePacking;
import dev.portalix.world.snapshot.PaletteReadView;
import dev.portalix.world.snapshot.PaletteSnapshot;
import dev.portalix.world.snapshot.PaletteSnapshotAccess;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PalettedContainer.class)
abstract class PortalPaletteSnapshotMixin<T> implements PaletteSnapshotAccess<T> {
  @Shadow private volatile PalettedContainer.Data<T> data;
  @Shadow private Strategy<T> strategy;
  @Unique private final ReentrantLock portalix$lock = new ReentrantLock();
  @Unique private boolean portalix$shared;
  @Unique private PaletteReadView<T> portalix$readOnly;

  @Shadow
  protected abstract T get(int index);

  @WrapOperation(
      method = "<init>*",
      at = @At(value = "NEW", target = "net/minecraft/util/ThreadingDetector"))
  private ThreadingDetector portalix$unusedDetector(
      String name, Operation<ThreadingDetector> original) {

    return null;
  }

  @WrapMethod(method = "acquire")
  private void portalix$acquire(Operation<Void> original) {
    portalix$lock.lock();
  }

  @WrapMethod(method = "release")
  private void portalix$release(Operation<Void> original) {
    portalix$lock.unlock();
  }

  @WrapMethod(method = "pack")
  private PalettedContainerRO.PackedData<T> portalix$packPalette(
      Strategy<T> target, Operation<PalettedContainerRO.PackedData<T>> original) {
    portalix$lock.lock();
    try {
      return PalettePacking.pack(data, target);
    } finally {
      portalix$lock.unlock();
    }
  }

  @Unique
  private PalettedContainer.Data<T> portalix$snapshot() {
    portalix$lock.lock();
    try {
      portalix$shared = true;
      return data;
    } finally {
      portalix$lock.unlock();
    }
  }

  @Override
  public PalettedContainerRO<T> portalix$readOnly() {
    portalix$lock.lock();
    try {
      portalix$shared = true;
      if (portalix$readOnly == null || !portalix$readOnly.isViewOf(data))
        portalix$readOnly = new PaletteReadView<>(data, strategy);
      return portalix$readOnly;
    } finally {
      portalix$lock.unlock();
    }
  }

  @Override
  public PaletteSnapshot<T> portalix$packetSnapshot() {
    var snapshot = portalix$snapshot();
    var configuration =
        ((StrategyAccessor) strategy).portalix$configuration(snapshot.storage().getBits());
    if (!snapshot.configuration().equals(configuration))
      snapshot =
          new PalettedContainer.Data<>(configuration, snapshot.storage(), snapshot.palette());
    return new PaletteSnapshot<>(snapshot, strategy.globalMap());
  }

  @Override
  public boolean portalix$readSnapshot(FriendlyByteBuf buffer, PaletteSnapshot<T> snapshot) {
    portalix$lock.lock();
    try {
      if (!snapshot.read(buffer, strategy)) return false;
      data = snapshot.data();
      if (portalix$readOnly != null && !portalix$readOnly.isViewOf(data))
        portalix$readOnly = null;
      portalix$shared = true;
      return true;
    } finally {
      portalix$lock.unlock();
    }
  }

  @WrapOperation(
      method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/PalettedContainer$Data;copy()Lnet/minecraft/world/level/chunk/PalettedContainer$Data;"))
  @SuppressWarnings("unchecked")
  private PalettedContainer.Data<T> portalix$share(
      PalettedContainer.Data<T> previous,
      Operation<PalettedContainer.Data<T>> original,
      @Local(argsOnly = true) PalettedContainer<T> source) {
    return ((PortalPaletteSnapshotMixin<T>) (Object) source).portalix$snapshot();
  }

  @Inject(
      method = "<init>(Lnet/minecraft/world/level/chunk/PalettedContainer;)V",
      at = @At("RETURN"))
  private void portalix$sharedCopy(PalettedContainer<T> source, CallbackInfo ci) {
    portalix$shared = true;
  }

  @Unique
  private void portalix$detach() {
    if (portalix$shared) {
      data = data.copy();
      portalix$readOnly = null;
      portalix$shared = false;
    }
  }

  // Checked writes hold acquire(); unchecked writes belong to an already acquired or unpublished container.
  @ModifyExpressionValue(
      method = {"getAndSet(ILjava/lang/Object;)Ljava/lang/Object;", "set(ILjava/lang/Object;)V"},
      at = @At(value = "FIELD", target =
          "Lnet/minecraft/world/level/chunk/PalettedContainer;data:Lnet/minecraft/world/level/chunk/PalettedContainer$Data;"))
  private PalettedContainer.Data<T> portalix$writableData(
      PalettedContainer.Data<T> original,
      @Local(argsOnly = true) int index,
      @Local(argsOnly = true) T value) {
    if (portalix$shared && get(index) != value) {
      portalix$detach();
      return data;
    }
    return original;
  }

  @WrapMethod(method = "read")
  private void portalix$read(FriendlyByteBuf buffer, Operation<Void> original) {
    portalix$lock.lock();
    try {
      original.call(buffer);
      portalix$readOnly = null;
      portalix$shared = false;
    } finally {
      portalix$lock.unlock();
    }
  }

  @WrapOperation(
      method = "read",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/chunk/PalettedContainer;createOrReuseData(Lnet/minecraft/world/level/chunk/PalettedContainer$Data;I)Lnet/minecraft/world/level/chunk/PalettedContainer$Data;"))
  private PalettedContainer.Data<T> portalix$packetData(
      PalettedContainer<T> container,
      PalettedContainer.Data<T> previous,
      int bits,
      Operation<PalettedContainer.Data<T>> original) {

    return original.call(container, portalix$shared ? null : previous, bits);
  }

  @WrapMethod(method = "onResize")
  private int portalix$resize(int bits, T value, Operation<Integer> original) {
    portalix$lock.lock();
    try {
      portalix$detach();
      return original.call(bits, value);
    } finally {
      portalix$lock.unlock();
    }
  }
}
