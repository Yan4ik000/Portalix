package dev.portalix.client.mixin.gun;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.PortalixClient;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.gun.GunCommand;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class GunInputMixin {
  @Unique
  private static boolean portalix$canUseGun() {
    LocalPlayer player = Minecraft.getInstance().player;
    return GunPermissions.canUse(player);
  }

  @WrapOperation(
      method = "handleKeybinds",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"))
  private boolean portalix$fireClick(KeyMapping key, Operation<Boolean> original) {
    boolean clicked = original.call(key);
    Minecraft mc = Minecraft.getInstance();
    if (clicked && portalix$canUseGun() && (key == mc.options.keyAttack || key == mc.options.keyUse)) {
      PortalixClient.ensureSession(mc);
      PortalixClient.input()
          .firePressed(key == mc.options.keyAttack ? GunCommand.FIRE_BLUE : GunCommand.FIRE_ORANGE);
    }
    return clicked;
  }

  @Inject(method = "handleKeybinds", at = @At("HEAD"))
  private void portalix$blockSwap(CallbackInfo ci) {
    if (portalix$canUseGun()) {
      while (Minecraft.getInstance().options.keySwapOffhand.consumeClick()) {}
    }
  }

  @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
  private void portalix$attack(CallbackInfoReturnable<Boolean> ci) {
    if (portalix$canUseGun()) {
      ci.setReturnValue(false);
    }
  }

  @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
  private void portalix$continue(boolean down, CallbackInfo ci) {
    if (portalix$canUseGun()) {
      ci.cancel();
    }
  }

  @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
  private void portalix$use(CallbackInfo ci) {
    if (portalix$canUseGun()) {
      ci.cancel();
    }
  }
}
