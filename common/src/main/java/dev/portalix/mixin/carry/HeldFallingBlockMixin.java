package dev.portalix.mixin.carry;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.carry.CarryBindings;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FallingBlockEntity.class)
public abstract class HeldFallingBlockMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;time:I",
              opcode = Opcodes.PUTFIELD))
  private void portalix$age(FallingBlockEntity entity, int time, Operation<Void> original) {
    if (CarryBindings.holderId(entity) < 0) original.call(entity, time);
  }
}
