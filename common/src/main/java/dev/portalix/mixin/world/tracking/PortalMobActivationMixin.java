package dev.portalix.mixin.world.tracking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.portal.query.PlayerPortalVisibility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mob.class)
abstract class PortalMobActivationMixin {
  @WrapOperation(
      method = "checkDespawn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"))
  private double portalix$observedDistance(
      Entity observer, Entity entity, Operation<Double> original) {
    double distance = original.call(observer, entity);
    int activeRadius = entity.getType().getCategory().getNoDespawnDistance();
    if (distance < (double) activeRadius * activeRadius) return distance;
    long chunk = entity.chunkPosition().pack();
    var position = entity.position();
    for (var player : entity.level().players()) {
      if (player instanceof ServerPlayer server) {
        var visibility = PlayerPortalVisibility.of(server);
        if (visibility.simulationChunks().contains(chunk)) return 0;
        for (var eye : visibility.eyes())
          distance = Math.min(distance, eye.distanceToSqr(position));
      }
    }
    return distance;
  }
}
