package dev.portalix.mixin.traversal;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
  @Invoker("getEffectiveGravity")
  double portalix$effectiveGravity();

  @Invoker("getAirDrag")
  float portalix$airDrag();
}
