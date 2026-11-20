package dev.portalix.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import dev.portalix.client.gun.sound.SourcePositionalSound;
import dev.portalix.client.portal.audio.PortalAudioChannelAccess;
import dev.portalix.client.portal.audio.PortalSoundVoice;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
abstract class PortalAudioChannelMixin implements PortalAudioChannelAccess {
  @Shadow @Final private int source;
  @Unique private PortalSoundVoice portalix$voice;
  @Unique private boolean portalix$cancelled;
  @Unique private SourcePositionalSound portalix$sound;

  @Override
  public PortalSoundVoice portalix$audioVoice() {
    return portalix$voice;
  }

  @Override
  public void portalix$audioVoice(PortalSoundVoice voice) {
    portalix$voice = voice;
  }

  @Override
  public int portalix$source() {
    return source;
  }

  @Override
  public void portalix$cancelPlayback() {
    portalix$cancelled = true;
  }

  @Override
  public void portalix$bindSound(SourcePositionalSound sound) {
    portalix$sound = sound;
  }

  @Inject(method = "stopped", at = @At("HEAD"), cancellable = true)
  private void portalix$cancelled(CallbackInfoReturnable<Boolean> cir) {
    if (portalix$cancelled) cir.setReturnValue(true);
  }

  @Inject(method = "setVolume", at = @At("HEAD"), cancellable = true)
  private void portalix$volume(float gain, CallbackInfo ci) {
    if (portalix$voice != null) {
      portalix$voice.volume(gain);
      ci.cancel();
    }
  }

  @Inject(method = "setSelfPosition", at = @At("HEAD"), cancellable = true)
  private void portalix$position(Vec3 position, CallbackInfo ci) {
    if (portalix$voice != null) ci.cancel();
  }

  @WrapOperation(
      method = {"play", "unpause"},
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alSourcePlay(I)V"))
  private void portalix$play(int sourceId, Operation<Void> original) {
    if (portalix$cancelled) return;
    original.call(sourceId);
    if (portalix$sound != null) portalix$sound.onPlaybackStarted();
  }

  @Inject(method = "destroy", at = @At("HEAD"))
  private void portalix$destroy(CallbackInfo ci) {
    portalix$voice = null;
    portalix$sound = null;
  }
}
