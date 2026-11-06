package dev.portalix.client.mixin.traversal;

import dev.portalix.client.portal.RemotePortalInterpolation;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.geometry.PortalTransform;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
abstract class PortalRemoteHeadMixin implements RemotePortalInterpolation.LivingAccess {
  @Shadow protected double lerpYHeadRot;

  @ModifyVariable(method = "lerpHeadTo", at = @At("HEAD"), argsOnly = true)
  private float portalix$headTarget(float yaw) {
    var interpolation = RemotePortalInterpolation.of((LivingEntity) (Object) this);
    return interpolation == null ? yaw : interpolation.headYaw(yaw);
  }

  @Override
  public void portalix$transformHead(PortalTransform transform) {
    var living = (LivingEntity) (Object) this;
    living.setYHeadRot(PortalLookAngles.transform(transform, living.getYHeadRot(), 0).yaw());
    living.yHeadRotO = PortalLookAngles.transform(transform, living.yHeadRotO, 0).yaw();
    lerpYHeadRot = PortalLookAngles.transform(transform, (float) lerpYHeadRot, 0).yaw();
  }
}
