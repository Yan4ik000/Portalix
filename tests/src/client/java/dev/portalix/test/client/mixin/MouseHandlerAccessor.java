package dev.portalix.test.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
  @Accessor("accumulatedDX") void portalix$horizontal(double value);
  @Accessor("accumulatedDY") void portalix$vertical(double value);
  @Invoker("turnPlayer") void portalix$turn(double seconds);
}
