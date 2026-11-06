package dev.portalix.client.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.client.gun.sound.SourcePositionalSound;
import dev.portalix.client.particle.PortalParticleEffect;
import dev.portalix.client.particle.PortalParticleMesh;
import dev.portalix.client.platform.ClientPlatform.Phase;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.render.PortalOpeningAnimation;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.render.ParticleView;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import dev.portalix.item.PortalixVariant;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.sound.GunSounds;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class PortalVisual {
  private static final double AMBIENT_DISTANCE = 128;
  private final Minecraft client;
  final PortalEndpoint endpoint;
  final long identity;
  final boolean orange;
  private final PortalixVariant variant;
  private final PortalOpeningAnimation animation;
  private final PortalParticleEffect particles;
  private final AABB bounds;
  private final PortalParticleMesh mesh = new PortalParticleMesh();
  final List<BlockPos> footprint;
  private SoundInstance ambient;
  private double closed = -1;

  PortalVisual(Minecraft client, PortalInstance member, boolean orange, double born, double now) {
    this.client = client;
    endpoint = member.endpoint();
    identity = member.identity();
    this.orange = orange;
    variant = member.variant();
    animation = new PortalOpeningAnimation(born, false);
    particles = new PortalParticleEffect(variant, orange, identity, born, now);

    bounds = new AABB(endpoint.center(), endpoint.center()).inflate(4);
    footprint = PortalSurface.placementFootprint(endpoint).blocks();
  }

  void unlink(double now) {
    animation.link(false, now);
  }

  boolean advanceClosing(double now) {
    particles.advance(now);
    return particles.finished();
  }

  void close(double now) {
    closed = now;
    particles.close(now);
  }

  void stopAmbient() {
    if (ambient != null) {
      client.getSoundManager().stop(ambient);
      ambient = null;
    }
  }

  void tick(double now, boolean ready, Vec3 camera) {
    particles.advance(now);
    animation.link(ready, now);
    if (PortalAudioRouter.delayDistanceSquared(camera, endpoint.center())
        <= AMBIENT_DISTANCE * AMBIENT_DISTANCE) {
      if (ambient == null || !client.getSoundManager().isActive(ambient)) {
        ambient =
            new SourcePositionalSound(
                client, GunSounds.PORTAL_AMBIENT, null, endpoint.center(), true);
        client.getSoundManager().play(ambient);
      }
    } else stopAmbient();
  }

  void submit(double now, ParticleView view, OrderedSubmitNodeCollector collection) {
    var camera = view.position();
    if (!loaded() || !view.frustum().test(bounds)) return;
    particles.advance(now);
    var geometry =
        mesh.build(
            particles,
            endpoint,
            view.right(),
            view.up(),
            client.options.particles().get() == ParticleStatus.ALL
                ? 1
                : client.options.particles().get() == ParticleStatus.DECREASED ? 2 : 8);
    if (closed >= 0 && geometry.isEmpty()) return;
    float open = animation.openingProgress(closed < 0 ? now : closed);
    if (closed < 0 && camera.subtract(endpoint.center()).dot(endpoint.normal()) > 0)
      ClientPlatform.INSTANCE.submit(collection,
          Phase.SOLID,
          new PortalSurfaceRenderer.Submit(
              endpoint,
              orange,
              variant,
              open,
              animation.unlinkedOpacity(now),
              (float) (now % 1000),
              true,
              true,
              camera,
              List.of()));
    if (!geometry.isEmpty())
      ClientPlatform.INSTANCE.submit(collection,
          Phase.AFTER_TERRAIN,
          new PortalSurfaceRenderer.Submit(
              endpoint,
              orange,
              variant,
              open,
              animation.unlinkedOpacity(now),
              (float) (now % 1000),
              false,
              closed < 0,
              camera,
              geometry));
  }

  private boolean loaded() {
    if (client.level == null || !endpoint.dimension().equals(client.level.dimension()))
      return false;
    for (BlockPos pos : footprint) {
      if (!client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
    }
    return true;
  }
}
