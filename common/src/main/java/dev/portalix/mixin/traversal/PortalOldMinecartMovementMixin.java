package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import dev.portalix.portal.traversal.EntityPortalState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldMinecartBehavior.class)
abstract class PortalOldMinecartMovementMixin extends MinecartBehavior {
  protected PortalOldMinecartMovementMixin(AbstractMinecart cart) {
    super(cart);
  }

  @Inject(method = "moveAlongTrack", at = @At("HEAD"))
  private void portalix$begin(ServerLevel level, CallbackInfo ci,
      @Share("crossing") LocalLongRef crossing) {
    crossing.set(EntityPortalState.of(minecart).crossingSequence());
  }

  @Inject(method = "moveAlongTrack", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
      shift = At.Shift.AFTER), cancellable = true)
  private void portalix$crossed(ServerLevel level, CallbackInfo ci,
      @Share("crossing") LocalLongRef crossing) {
    // The rest of this step still refers to the entrance rail and would overwrite exit motion.
    if (EntityPortalState.of(minecart).crossingSequence() != crossing.get()) ci.cancel();
  }
}
