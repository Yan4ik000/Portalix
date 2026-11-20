package dev.portalix.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.test.PlayerTicketDistanceAccess;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(DistanceManager.class)
abstract class RegressionDistanceManagerMixin implements PlayerTicketDistanceAccess {
  @Unique private PlayerTicketDistancesAccessor portalix$playerTickets;

  @WrapOperation(method = "runAllUpdates", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/server/level/DistanceManager$PlayerTicketTracker;runAllUpdates()V"))
  private void portalix$capturePlayerTickets(@Coerce Object tracker, Operation<Void> original) {
    original.call(tracker);
    portalix$playerTickets = (PlayerTicketDistancesAccessor) tracker;
  }

  @Override
  public int portalix$playerTicketDistance(long chunk) {
    return portalix$playerTickets.portalix$distance(chunk);
  }
}
