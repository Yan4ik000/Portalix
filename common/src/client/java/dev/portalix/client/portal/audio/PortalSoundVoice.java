package dev.portalix.client.portal.audio;

import static org.lwjgl.openal.AL10.*;

import dev.portalix.portal.PortalLink;
import java.util.List;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.SOFTSourceSpatialize;

public final class PortalSoundVoice {
  private static final double OCCLUSION_RESPONSE_RATE = 4;

  private final int source;
  private final SoundInstance sound;
  private float volume;
  private double distanceGain = 1;
  private double occlusion = 1;
  private long updatedAt;
  private List<PortalLink> route = List.of();
  private boolean relative;

  public PortalSoundVoice(int source, SoundInstance sound) {
    this.source = source;
    this.sound = sound;
    volume = alGetSourcef(source, AL_GAIN);
    alSourcei(source, AL_DISTANCE_MODEL, AL_NONE);
    if (AL.getCapabilities().AL_SOFT_source_spatialize) {
      alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL_TRUE);
    }
  }

  public SoundInstance sound() {
    return sound;
  }

  public void update(PortalAudioRouter.Playback playback) {
    var position = playback.position();
    alSourcei(source, AL_SOURCE_RELATIVE, playback.relative() ? AL_TRUE : AL_FALSE);
    alSource3f(source, AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
    long now = System.nanoTime();
    double blend = Math.clamp((now - updatedAt) * 1e-9 * OCCLUSION_RESPONSE_RATE, 0, 1);
    occlusion = updatedAt == 0 || relative != playback.relative() || !route.equals(playback.route())
        ? playback.occlusion()
        : occlusion + (playback.occlusion() - occlusion) * blend;
    updatedAt = now;
    route = playback.route();
    relative = playback.relative();
    distanceGain = playback.gain();
    volume(volume);
  }

  public void volume(float gain) {
    volume = gain;
    alSourcef(source, AL_GAIN, gain * (float) (distanceGain * occlusion));
  }
}
