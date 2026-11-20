package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.world.snapshot.PaletteSnapshotAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = PalettedContainer.class, priority = 900, remap = false)
abstract class SodiumPaletteSnapshotMixin<T> implements PaletteSnapshotAccess<T> {
  @Inject(method = "sodium$copy", at = @At("HEAD"), cancellable = true)
  private void portalix$immutableCopy(CallbackInfoReturnable<PalettedContainerRO<T>> cir) {
    cir.setReturnValue(portalix$readOnly());
  }
}
