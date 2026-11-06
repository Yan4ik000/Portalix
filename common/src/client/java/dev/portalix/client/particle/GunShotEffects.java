package dev.portalix.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.portalix.PortalixMod;
import dev.portalix.client.render.LinearParticleRenderer;
import dev.portalix.client.render.ParticleGeometry;
import dev.portalix.client.render.ParticleRenderTypes;
import dev.portalix.client.render.ParticleView;
import dev.portalix.client.render.ParticleVisibility;
import dev.portalix.network.gun.GunShotPayload;
import dev.portalix.network.gun.PortalImpactPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class GunShotEffects {
  private static final double SPARK_COLLISION_RANGE_SQUARED = 64.0 * 64.0;
  private static final Identifier TEX_MUZZLE =
      PortalixMod.id("textures/effects/particle_glow_05.png");
  private static final int MAX_SHOTS = 128;
  private static final int IMPACT_LIFETIME_TICKS = 25;
  private static final int COLLISION_TRACE_BUDGET = 256;

  private final List<Shot> shots = new ArrayList<>();
  private final List<Impact> impacts = new ArrayList<>();
  private final Minecraft client;
  private final Consumer<Vec3> missResponse;
  private final PoseStack identityPose = new PoseStack();

  public GunShotEffects(Minecraft client, Consumer<Vec3> missResponse) {
    this.client = client;
    this.missResponse = missResponse;
  }

  private static long elapsedTicks(long fired, long received) {
    return fired < 0 || received < fired ? 0 : received - fired;
  }

  public void onShotFired(GunShotPayload data) {
    Minecraft mc = this.client;
    if (mc.level != null && mc.player != null) {
      if (data.sequence() >= 0L
          && shots.stream().anyMatch(shot -> shot.data.sequence() == data.sequence())) return;

      long elapsed = elapsedTicks(data.firedServerTick(), mc.level.getGameTime());
      if (elapsed > data.impactAfterTicks() + IMPACT_LIFETIME_TICKS) {
        return;
      }

      if (this.shots.size() >= MAX_SHOTS) {
        this.shots.removeFirst();
      }

      this.shots.add(new Shot(data, mc.player.tickCount - elapsed));
    }
  }

  public void clear() {
    this.shots.clear();
    this.impacts.clear();
  }

  public void onPortalImpact(PortalImpactPayload data) {
    if (client.level == null || client.player == null) return;
    long elapsed = elapsedTicks(data.serverTick(), client.level.getGameTime());
    if (elapsed > IMPACT_LIFETIME_TICKS
        || impacts.stream().anyMatch(impact -> impact.data.sequence() == data.sequence())) return;
    if (impacts.size() >= MAX_SHOTS) impacts.removeFirst();
    ParticleStatus detail = client.options.particles().get();
    int stride = detail == ParticleStatus.ALL ? 1 : detail == ParticleStatus.DECREASED ? 2 : 8;
    impacts.add(new Impact(data, client.player.tickCount - elapsed, stride));
    if (!data.placed()) missResponse.accept(data.position());
  }

  public void tick() {
    Minecraft mc = this.client;
    if (mc.level != null && mc.player != null) {
      double time = mc.player.tickCount;
      this.shots.removeIf(
          shot ->
              time - shot.tick
                  > shot.data.impactAfterTicks() + IMPACT_LIFETIME_TICKS);
      impacts.removeIf(impact -> time - impact.tick > IMPACT_LIFETIME_TICKS);
      ImpactSparkSimulation.CollisionTracer trace =
          (start, end) -> {
            BlockHitResult hit =
                mc.level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, mc.player));
            return hit.getType() == Type.MISS
                ? null
                : new ImpactSparkSimulation.Contact(
                    hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()));
          };
      trace = limitCollisionTraces(trace, COLLISION_TRACE_BUDGET);
      int nearby = 0;

      for (Impact impact : impacts) {
        if (impact.collisionVisible
            && mc.player.distanceToSqr(impact.data.position()) <= SPARK_COLLISION_RANGE_SQUARED) {
          nearby++;
        }
      }

      int collisionParticles = Math.clamp(COLLISION_TRACE_BUDGET / (Math.max(1, nearby) * 2), 0, 8);
      ParticleStatus detail = mc.options.particles().get();
      if (detail == ParticleStatus.DECREASED) {
        collisionParticles /= 2;
      }

      if (detail == ParticleStatus.MINIMAL) {
        collisionParticles = 0;
      }

      for (Shot shot : shots) {
        if (time - shot.tick >= shot.data.impactAfterTicks()
            && shot.data.normal().lengthSqr() == 0
            && !shot.responded) {
          shot.responded = true;
          missResponse.accept(shot.data.end());
        }
      }
      for (Impact impact : impacts) {
        boolean traceCollisions = impact.collisionVisible
            && mc.player.distanceToSqr(impact.data.position()) <= SPARK_COLLISION_RANGE_SQUARED;
        impact.sparks.advance(
            (time - impact.tick) / 20.0,
            trace,
            traceCollisions ? collisionParticles : 0,
            impact.stride);
      }
    }
  }

  private static ImpactSparkSimulation.CollisionTracer limitCollisionTraces(
      ImpactSparkSimulation.CollisionTracer trace, int limit) {
    int[] remaining = new int[] {limit};
    return (start, end) -> {
      if (remaining[0] <= 0) {
        return null;
      }

      remaining[0]--;
      return trace.trace(start, end);
    };
  }

  public void submit(ParticleView view, OrderedSubmitNodeCollector collector) {
    Minecraft mc = this.client;
    if (mc.level != null && mc.player != null) {
      double time = mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
      Vec3 camera = view.position();

      for (Shot shot : this.shots) {
        double age = Math.max(0.0, time - shot.tick);
        shot.projectile.advance((float) age / 20.0F);
        double length = shot.length;
        boolean orange = shot.data.orange();

        if (age < 0.8) {
          float life = (float) (age / 0.8);
          float fade = 1.0F - life;
          int rgb = shot.data.variant().particleGlowColor(orange);
          int red = rgb >> 16 & 255;
          int green = rgb >> 8 & 255;
          int blue = rgb & 255;

          Vec3 muzzle = shot.data.start();
          Vec3 direction = shot.data.launchDirection().normalize();
          Vector3f right = view.right().toVector3f();
          Vector3f up = view.up().toVector3f();

          LinearParticleRenderer.submit(
              collector,
              this.identityPose,
              ParticleRenderTypes.linearTintedParticle(TEX_MUZZLE, 15, false),
              TEX_MUZZLE,
              (pose, vertices) -> {
                for (int i = 0; i < 4; i++) {
                  double distance = 0.01 + 0.06 * (i / 3.0);
                  Vec3 position = muzzle.add(direction.scale(distance));
                  Vector3f center =
                      new Vector3f(
                          (float) (position.x - camera.x),
                          (float) (position.y - camera.y),
                          (float) (position.z - camera.z));
                  float radius = (0.16F + 0.10F * (i / 3.0F)) * fade;
                  int alpha = (int) (240 * fade);
                  ParticleGeometry.sprite(pose, vertices, center, right, up, radius, red, green, blue, alpha);
                }
              });
        }

        if (length >= 0.01 && ParticleVisibility.visible(shot.bounds, view)) {
          PortalShotParticleRenderer.submit(shot.projectile, view, collector);
        }
      }
      for (Impact impact : impacts) {
        double age = (time - impact.tick) / 20.0;
        if (age >= 0 && age < IMPACT_LIFETIME_TICKS / 20.0) {
          AABB bounds = impact.sparks.bounds(age, impact.stride);
          impact.collisionVisible = bounds != null && ParticleVisibility.visible(bounds, view);
          if (impact.collisionVisible) impact.sparks.submit(age, view, collector, impact.stride);
        }
      }
    }
  }

  private static final class Impact {
    final PortalImpactPayload data;
    final double tick;
    final int stride;
    final ImpactSparkRenderer sparks;
    boolean collisionVisible = true;

    Impact(PortalImpactPayload data, double tick, int stride) {
      this.data = data;
      this.tick = tick;
      this.stride = stride;
      this.sparks = new ImpactSparkRenderer(data, data.placed() ? 80 : 60);
    }
  }

  private static final class Shot {
    final GunShotPayload data;
    boolean responded;
    final PortalShotParticleSystem projectile;
    final double tick;
    final double length;
    final AABB bounds;

    Shot(GunShotPayload data, double tick) {
      this.data = data;
      this.tick = tick;
      this.length = data.start().distanceTo(data.end());
      Vec3 auxiliary = data.start().add(data.launchDirection().normalize().scale(this.length));
      this.bounds = ParticleVisibility.ropeBounds(data.start(), auxiliary, data.end());
      this.projectile =
          new PortalShotParticleSystem(
              data.start(),
              data.end(),
              data.launchDirection(),
              data.orange(),
              data.impactAfterTicks() / 20.0F,
              data.variant());
    }
  }
}
