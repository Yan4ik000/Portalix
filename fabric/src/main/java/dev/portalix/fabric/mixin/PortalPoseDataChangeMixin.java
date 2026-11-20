package dev.portalix.fabric.mixin;

import dev.portalix.fabric.PlayerPoseData;
import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class PortalPoseDataChangeMixin {
  @Inject(method = "onSyncedDataUpdated(Lnet/minecraft/network/syncher/EntityDataAccessor;)V",
      at = @At("TAIL"), require = 1, allow = 1)
  private void portalix$poseChanged(EntityDataAccessor<?> field, CallbackInfo callback) {
    Entity entity = (Entity) (Object) this;
    if (entity instanceof PlayerPoseData pose && pose.portalix$poseField(field.id()))
      PortalPassagePose.changed(entity);
  }
}
