package dev.portalix.mixin.traversal;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Avatar.class)
abstract class PortalAvatarDimensionsMixin {
  @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
  private EntityDimensions portalix$dimensions(EntityDimensions normal, Pose pose) {
    return PortalPassagePose.dimensions((Avatar) (Object) this, pose, normal);
  }
}
