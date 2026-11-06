package dev.portalix.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubtitleOverlay.class)
abstract class PortalSubtitleMixin {
  @WrapOperation(
      method = "onPlaySound",
      at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$audiblePosition(
      double x, double y, double z, Operation<Vec3> original,
      @Local(argsOnly = true) SoundInstance sound) {
    return PortalAudioRouter.positional(sound)
        ? PortalAudioRouter.subtitlePosition(sound) : original.call(x, y, z);
  }
}
