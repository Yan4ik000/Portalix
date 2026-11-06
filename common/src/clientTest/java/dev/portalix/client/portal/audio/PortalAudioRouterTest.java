package dev.portalix.client.portal.audio;

import static dev.portalix.test.ClientPortalFixtures.endpoint;
import static dev.portalix.test.ClientPortalFixtures.near;
import static org.junit.jupiter.api.Assertions.*;

import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PortalAudioRouterTest {
  @ParameterizedTest
  @EnumSource(Direction.class)
  void routerRefreshesPlaybackAndKeepsPropagationDelayGeometric(Direction direction) {
    var gun = UUID.fromString("ac0359e2-56b1-48a3-9412-10d117596389");
    var first = new PortalInstance(endpoint(new Vec3(0, 100, 0), direction), 0, 1, PortalixVariant.STANDARD);
    var second = new PortalInstance(endpoint(new Vec3(1000, 100, 0), direction), 0, 2, PortalixVariant.STANDARD);
    var pair = PortalTopology.linkPair(gun, first, second);
    var topology = new AtomicReference<List<PortalLink>>(pair);
    var traces = new AtomicInteger();
    try {
      PortalAudioRouter.reset();
      PortalAudioRouter.configure(topology::get, (start, end, radius) -> {
        traces.incrementAndGet();
        return .8;
      });
      var normal = first.endpoint().normal();
      var listener = first.endpoint().center().add(normal.scale(3));
      var emitter = second.endpoint().center().add(normal.scale(4));
      assertEquals(49, PortalAudioRouter.delayDistanceSquared(listener, emitter), 1e-7);
      var hiddenEmitter = second.endpoint().center().subtract(normal.scale(4));
      assertEquals(listener.distanceToSqr(hiddenEmitter),
          PortalAudioRouter.delayDistanceSquared(listener, hiddenEmitter), 1e-7);
      assertEquals(0, traces.get(), "propagation delay must not perform world occlusion traces");

      var sound = new PositionalSound(emitter, false);
      PortalAudioRouter.updateListener(listener);
      var playback = PortalAudioRouter.playback(sound);
      assertEquals(List.of(pair.getFirst()), playback.route());
      assertEquals(1 - 7.0 / 32, playback.gain(), 1e-9);
      assertEquals(.64, playback.occlusion(), 1e-9);
      near(first.endpoint().center().subtract(normal.scale(4)), playback.position(), 1e-7, "audible source image");
      near(playback.position(), PortalAudioRouter.subtitlePosition(sound), 1e-7, "subtitles follow the selected source");

      int priorTraces = traces.get();
      var prepared = PortalAudioRouter.playback(sound, (start, end, radius) -> .5);
      assertEquals(.25, prepared.occlusion(), 1e-9);
      assertEquals(playback.route(), prepared.route());
      assertEquals(priorTraces, traces.get(), "a prepared acoustic context replaces the configured callback");
      var relative = PortalAudioRouter.playback(new PositionalSound(Vec3.ZERO, true));
      assertTrue(relative.relative());
      assertTrue(relative.route().isEmpty());
      assertEquals(1, relative.gain());
      assertEquals(1, relative.occlusion());
      assertEquals(priorTraces, traces.get(), "listener-relative audio must not use world occlusion");

      topology.set(List.of());
      var direct = PortalAudioRouter.playback(sound);
      assertTrue(direct.route().isEmpty());
      near(emitter, direct.position(), 1e-9, "removed topology is reflected without a listener update");
      assertEquals(0, direct.gain());
      PortalAudioRouter.updateListener(emitter.add(normal.scale(2)));
      assertEquals(1 - 2.0 / 32, PortalAudioRouter.playback(sound).gain(), 1e-9);
      PortalAudioRouter.reset();
      assertEquals(0, PortalAudioRouter.playback(sound).gain(), "a new session cannot retain the previous listener");
    } finally {
      PortalAudioRouter.reset();
      PortalAudioRouter.configure(List::of, (start, end, radius) -> 1);
    }
  }

  private static final class PositionalSound extends AbstractSoundInstance {
    PositionalSound(Vec3 position, boolean relative) {
      super(Identifier.withDefaultNamespace("test.audio"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
      sound = new Sound(getIdentifier(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, false, false, 32);
      x = position.x;
      y = position.y;
      z = position.z;
      this.relative = relative;
    }
  }
}
