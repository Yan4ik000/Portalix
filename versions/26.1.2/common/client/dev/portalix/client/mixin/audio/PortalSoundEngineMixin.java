package dev.portalix.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import dev.portalix.client.gun.sound.SourcePositionalSound;
import dev.portalix.client.compat.minecraft.MinecraftAudioChannels;
import dev.portalix.client.portal.audio.PortalAudioChannelAccess;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class PortalSoundEngineMixin {
  @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
  @Shadow @Final private ChannelAccess channelAccess;

  @WrapOperation(
      method = "play",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
  private void portalix$bind(
      ChannelAccess.ChannelHandle handle,
      Consumer<Channel> setup,
      Operation<Void> original,
      @Local(argsOnly = true) SoundInstance sound) {
    var playback = PortalAudioRouter.positional(sound) ? PortalAudioRouter.playback(sound) : null;
    if (playback == null) {
      original.call(handle, setup);
      return;
    }
    original.call(
        handle,
        (Consumer<Channel>)
            channel -> {
              setup.accept(channel);
              if (sound instanceof SourcePositionalSound imported)
                ((PortalAudioChannelAccess) channel).portalix$bindSound(imported);
              MinecraftAudioChannels.bind(channel, sound, playback);
            });
  }

  @WrapOperation(
      method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
  private void portalix$cancelPlayback(
      ChannelAccess.ChannelHandle handle,
      Consumer<Channel> stop,
      Operation<Void> original,
      @Local(argsOnly = true) SoundInstance sound) {
    if (!(sound instanceof SourcePositionalSound)) {
      original.call(handle, stop);
      return;
    }
    original.call(
        handle,
        (Consumer<Channel>)
            channel -> {
              ((PortalAudioChannelAccess) channel).portalix$cancelPlayback();
              stop.accept(channel);
            });
  }

  @Inject(method = "updateSource", at = @At("TAIL"))
  private void portalix$update(Camera camera, CallbackInfo ci) {
    if (camera.isInitialized()) MinecraftAudioChannels.update(camera, instanceToChannel, channelAccess);
  }
}
