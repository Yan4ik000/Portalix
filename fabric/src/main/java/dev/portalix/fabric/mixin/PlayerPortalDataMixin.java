package dev.portalix.fabric.mixin;

import dev.portalix.portal.traversal.PortalPassagePose;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerPortalDataMixin implements PortalPassagePose.Access {
  @Unique private static final EntityDataAccessor<Float> PORTALIX_POSE_SCALE =
      SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);

  @Inject(method = "defineSynchedData", at = @At("TAIL"))
  private void definePortalPose(SynchedEntityData.Builder builder, CallbackInfo callback) {
    builder.define(PORTALIX_POSE_SCALE, 1f);
  }

  @Override
  public float portalix$poseScale() {
    return ((Player) (Object) this).getEntityData().get(PORTALIX_POSE_SCALE);
  }

  @Override
  public void portalix$poseScale(float scale) {
    ((Player) (Object) this).getEntityData().set(PORTALIX_POSE_SCALE, scale);
  }

  @Override
  public boolean portalix$poseField(int id) {
    return id == PORTALIX_POSE_SCALE.id();
  }
}
