package dev.portalix.client.portal.audio;

import dev.portalix.client.gun.sound.SourcePositionalSound;

public interface PortalAudioChannelAccess {
  PortalSoundVoice portalix$audioVoice();

  void portalix$audioVoice(PortalSoundVoice voice);

  int portalix$source();

  void portalix$cancelPlayback();

  void portalix$bindSound(SourcePositionalSound sound);
}
