package dev.portalix.client.mixin.gun;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.portalix.client.PortalixClient;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.gun.GunCommand;
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

  @Definition(id = "keyAttack", field = "Lnet/minecraft/client/Options;keyAttack:Lnet/minecraft/client/KeyMapping;")
  @Definition(id = "consumeClick", method = "Lnet/minecraft/client/KeyMapping;consumeClick()Z")
  @Expression("?.keyAttack.consumeClick()")
  @ModifyExpressionValue(
      method = "handleKeybinds",
      at = @At("MIXINEXTRAS:EXPRESSION"),
      require = 2, allow = 2)
  private boolean portalix$firePrimary(boolean clicked) {
    return portalix$fireClick(clicked, GunCommand.FIRE_BLUE);
  }

  @Definition(id = "keyUse", field = "Lnet/minecraft/client/Options;keyUse:Lnet/minecraft/client/KeyMapping;")
  @Definition(id = "consumeClick", method = "Lnet/minecraft/client/KeyMapping;consumeClick()Z")
  @Expression("?.keyUse.consumeClick()")
  @ModifyExpressionValue(
      method = "handleKeybinds",
      at = @At("MIXINEXTRAS:EXPRESSION"),
      require = 2, allow = 2)
  private boolean portalix$fireSecondary(boolean clicked) {
    return portalix$fireClick(clicked, GunCommand.FIRE_ORANGE);
  }

  @Unique
  private static boolean portalix$fireClick(boolean clicked, GunCommand command) {
    if (clicked && portalix$canUseGun()) {
      PortalixClient.ensureSession(Minecraft.getInstance());
      PortalixClient.input().firePressed(command);
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
