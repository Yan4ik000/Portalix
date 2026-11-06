package dev.portalix.client.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.client.gun.sound.SoundVoiceLimiter;
import dev.portalix.client.portal.render.PortalOpeningAnimation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleView;
import dev.portalix.network.portal.PortalStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.sound.GunSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ClientPortalRegistry {
  private record Key(UUID gun, boolean orange) {}

  private final Map<Key, PortalVisual> portals = new HashMap<>();
  private final Map<UUID, PortalStatePayload> states = new HashMap<>();
  private long revision = -1;
  private final List<PortalVisual> closing = new ArrayList<>();
  private final Minecraft client = Minecraft.getInstance();
  private final SoundVoiceLimiter responses = new SoundVoiceLimiter(client);

  public boolean hidesBlockOutline(BlockPos pos) {
    if (client.level == null) return false;
    for (PortalVisual portal : portals.values()) {
      if (!portal.endpoint.dimension().equals(client.level.dimension())) continue;
      if (portal.footprint.contains(pos)) return true;
    }
    return false;
  }

  public double time() {
    return client.level == null
        ? 0
        : (client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false))
            / 20.0;
  }

  public void applySnapshot(PortalStatePayload state) {
    if (client.level == null || state.revision() <= revision) return;
    if (state.blue() == null && state.orange() == null) states.remove(state.gun());
    else states.put(state.gun(), state);
    PortalTopology.updateLinks(
        client.level,
        states.values().stream()
            .flatMap(
                pair -> PortalTopology.linkPair(pair.gun(), pair.blue(), pair.orange()).stream())
            .filter(link -> link.entrance().endpoint().dimension().equals(client.level.dimension()))
            .toList());
    revision = state.revision();
    double now = time();
    boolean linked = state.blue() != null && state.orange() != null;
    update(
        new Key(state.gun(), false),
        state.blue(),
        state.serverTick(),
        linked,
        state.effects(),
        now);
    update(
        new Key(state.gun(), true),
        state.orange(),
        state.serverTick(),
        linked,
        state.effects(),
        now);
  }

  public List<PortalLink> links() {
    return PortalTopology.links(client.level);
  }

  public int activeMask(UUID gun) {
    var state = states.get(gun);
    return state == null ? 0 : (state.blue() == null ? 0 : 1) | (state.orange() == null ? 0 : 2);
  }

  public boolean secondaryActive(UUID gun) {
    var state = states.get(gun);
    return state != null
        && state.orange() != null
        && (state.blue() == null || state.orange().identity() > state.blue().identity());
  }

  private void update(
      Key key,
      PortalInstance member,
      long serverTick,
      boolean linked,
      boolean effects,
      double now) {
    PortalVisual old = portals.get(key);
    if (old != null && member != null && old.identity == member.identity()) {
      if (!linked) old.unlink(now);
      return;
    }
    if (old != null) {
      if (effects) {
        old.close(now);
        closing.add(old);
        play(
            member == null
                ? GunSounds.PORTAL_FIZZLE
                : old.orange ? GunSounds.PORTAL_CLOSE_ORANGE : GunSounds.PORTAL_CLOSE_BLUE,
            old.endpoint);
      }
      old.stopAmbient();
      portals.remove(key);
    }
    if (member == null || !member.endpoint().dimension().equals(client.level.dimension())) return;
    double age = Math.max(0, serverTick - member.bornTick()) / 20.0;
    PortalVisual visual = new PortalVisual(client, member, key.orange(), now - age, now);
    portals.put(key, visual);
    if (effects && age < PortalOpeningAnimation.OPEN_SECONDS) {
      SoundInstance opening =
          play(
              key.orange() ? GunSounds.PORTAL_OPEN_ORANGE : GunSounds.PORTAL_OPEN_BLUE,
              member.endpoint());
      if (opening != null) play(GunSounds.PORTAL_OPEN_LAYER, member.endpoint());
    }
  }

  private SoundInstance play(GunSounds sound, PortalEndpoint endpoint) {
    return responses.play(sound, null, endpoint.center());
  }

  public void tick() {
    double now = time();
    Vec3 camera = client.gameRenderer.mainCamera().position();
    for (PortalVisual portal : portals.values()) {
      boolean ready =
          links().stream()
              .anyMatch(
                  link ->
                      link.entrance().identity() == portal.identity
                          && PortalSceneManager.hasRenderedView(link));
      portal.tick(now, ready, camera);
    }
    closing.removeIf(portal -> portal.advanceClosing(now));
    responses.tick();
  }

  public void submit(ParticleView view, OrderedSubmitNodeCollector collection) {
    double now = time();
    for (PortalVisual portal : portals.values()) portal.submit(now, view, collection);
    for (PortalVisual portal : closing) portal.submit(now, view, collection);
  }

  public void clear() {
    for (PortalVisual portal : portals.values()) portal.stopAmbient();
    portals.clear();
    states.clear();
    closing.clear();
    revision = -1;
    responses.clear();
  }
}
