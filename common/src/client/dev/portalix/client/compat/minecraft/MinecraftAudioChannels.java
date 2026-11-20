package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.audio.Channel;
import dev.portalix.client.portal.audio.PortalAudioChannelAccess;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import dev.portalix.client.portal.audio.PortalSoundVoice;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;

public final class MinecraftAudioChannels {
  private MinecraftAudioChannels() {}

  public static void bind(Channel channel, SoundInstance sound, PortalAudioRouter.Playback playback) {
    var access = (PortalAudioChannelAccess) channel;
    var voice = new PortalSoundVoice(access.portalix$source(), sound);
    access.portalix$audioVoice(voice);
    voice.update(playback);
  }

  public static void update(Camera camera,
      Map<SoundInstance, ChannelAccess.ChannelHandle> channels, ChannelAccess access) {
    PortalAudioRouter.updateListener(camera.position());
    var transmission = MinecraftSoundOcclusion.prepare();
    var updates = new IdentityHashMap<SoundInstance, PortalAudioRouter.Playback>();
    for (var sound : channels.keySet()) {
      if (PortalAudioRouter.positional(sound))
        updates.put(sound, PortalAudioRouter.playback(sound, transmission));
    }
    if (updates.isEmpty()) return;
    access.executeOnChannels(playing -> playing.forEach(channel -> {
      var voice = ((PortalAudioChannelAccess) channel).portalix$audioVoice();
      if (voice == null) return;
      var playback = updates.get(voice.sound());
      if (playback != null) voice.update(playback);
    }));
  }
}
