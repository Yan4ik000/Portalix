package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import dev.portalix.portal.traversal.EntityPortalState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
abstract class PortalNewMinecartMovementMixin extends MinecartBehavior {
  protected PortalNewMinecartMovementMixin(AbstractMinecart cart) {
    super(cart);
  }

  @Inject(method = "stepAlongTrack", at = @At("HEAD"))
  private void portalix$begin(BlockPos pos, RailShape shape, double remaining,
      CallbackInfoReturnable<Double> cir, @Share("crossing") LocalLongRef crossing) {
    crossing.set(EntityPortalState.of(minecart).crossingSequence());
  }

  @Inject(method = "stepAlongTrack", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
      shift = At.Shift.AFTER), cancellable = true)
  private void portalix$crossed(BlockPos pos, RailShape shape, double remaining,
      CallbackInfoReturnable<Double> cir, @Share("crossing") LocalLongRef crossing) {
    // Continue any remaining distance on the exit track, using the transformed velocity.
    if (EntityPortalState.of(minecart).crossingSequence() != crossing.get())
      cir.setReturnValue(remaining);
  }
}
