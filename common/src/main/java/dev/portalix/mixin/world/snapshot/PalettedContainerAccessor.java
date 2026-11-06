package dev.portalix.mixin.world.snapshot;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PalettedContainer.class)
public interface PalettedContainerAccessor {
  @Invoker("<init>")
  static <T> PalettedContainer<T> portalix$create(
      Strategy<T> strategy, Configuration configuration, BitStorage storage, Palette<T> palette) {
    throw new AssertionError();
  }
}
