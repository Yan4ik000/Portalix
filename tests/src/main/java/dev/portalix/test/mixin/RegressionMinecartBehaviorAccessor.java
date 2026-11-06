package dev.portalix.test.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractMinecart.class)
public interface RegressionMinecartBehaviorAccessor {
  @Mutable
  @Accessor("behavior")
  void portalix$behavior(MinecartBehavior behavior);
}
