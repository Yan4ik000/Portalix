package dev.portalix.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
abstract class PortalAudioDelayMixin {
  @WrapOperation(
      method =
          "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(DDD)D"))
  private double portalix$distanceDelay(
      Vec3 listener,
      double x,
      double y,
      double z,
      Operation<Double> original,
      @Local(argsOnly = true) SoundSource source) {
    if (source == SoundSource.MUSIC || source == SoundSource.MASTER)
      return original.call(listener, x, y, z);
    return PortalAudioRouter.delayDistanceSquared(listener, new Vec3(x, y, z));
  }
}
