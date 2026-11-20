package dev.portalix.client.mixin.gun;

import dev.portalix.PortalixMod;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerGunPoseMixin {
  @Inject(
      method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
      at = @At("TAIL"))
  private void portalix$aim(AvatarRenderState state, CallbackInfo ci) {
    if (PortalixMod.isPortalix(state.getMainHandItemStack())
        && !state.isVisuallySwimming
        && !state.isFallFlying) {
      PlayerModel model = (PlayerModel) (Object) this;
      ModelPart arm = state.mainArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
      arm.xRot = (float) (-Math.PI / 2) + model.head.xRot;
      arm.yRot = model.head.yRot;
      arm.zRot = 0.0F;
    }
  }
}
