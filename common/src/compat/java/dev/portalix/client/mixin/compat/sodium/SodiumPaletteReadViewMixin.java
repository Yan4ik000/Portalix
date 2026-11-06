package dev.portalix.client.mixin.compat.sodium;

import dev.portalix.world.snapshot.PaletteReadView;
import net.caffeinemc.mods.sodium.client.world.BitStorageExtension;
import net.caffeinemc.mods.sodium.client.world.PalettedContainerROExtension;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

// Unpacking methods adapted from Sodium's PalettedContainerMixin (PolyForm Shield 1.0.0); see NOTICE.
@Pseudo
@Mixin(value = PaletteReadView.class, remap = false)
abstract class SodiumPaletteReadViewMixin<T>
    implements PalettedContainerROExtension<T>, PalettedContainerRO<T> {
  @Shadow @Final private PalettedContainer.Data<T> data;
  @Shadow @Final private Strategy<T> strategy;

  @Override
  public void sodium$unpack(T[] values) {
    if (values.length != strategy.entryCount())
      throw new IllegalArgumentException("Array is wrong size");
    ((BitStorageExtension) data.storage()).sodium$unpack(values, data.palette());
  }

  @Override
  public void sodium$unpack(
      T[] values, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    if (values.length != strategy.entryCount())
      throw new IllegalArgumentException("Array is wrong size");
    var storage = data.storage();
    var palette = data.palette();
    for (int y = minY; y <= maxY; y++)
      for (int z = minZ; z <= maxZ; z++)
        for (int x = minX; x <= maxX; x++) {
          int index = strategy.getIndex(x, y, z);
          values[index] = palette.valueFor(storage.get(index));
        }
  }

  @Override
  public PalettedContainerRO<T> sodium$copy() {
    return this;
  }
}
