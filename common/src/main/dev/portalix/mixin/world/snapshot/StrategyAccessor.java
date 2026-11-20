package dev.portalix.mixin.world.snapshot;

import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Strategy.class)
public interface StrategyAccessor {
  @Invoker("getConfigurationForBitCount")
  Configuration portalix$configuration(int bits);

  @Invoker("getConfigurationForPaletteSize")
  Configuration portalix$paletteConfiguration(int size);
}
