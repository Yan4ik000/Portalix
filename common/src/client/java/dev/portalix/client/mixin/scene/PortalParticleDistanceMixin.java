package dev.portalix.client.mixin.scene;

import dev.portalix.client.render.ParticleViewDistance;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
abstract class PortalParticleDistanceMixin implements ParticleViewDistance {
  @Unique private double portalix$viewDistanceSquared = Double.POSITIVE_INFINITY;

  @Override
  public double portalix$viewDistanceSquared() {
    return portalix$viewDistanceSquared;
  }

  @Override
  public void portalix$viewDistanceSquared(double distance) {
    portalix$viewDistanceSquared = distance;
  }
}
