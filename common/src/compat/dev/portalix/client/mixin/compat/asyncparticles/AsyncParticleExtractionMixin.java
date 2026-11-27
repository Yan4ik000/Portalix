package dev.portalix.client.mixin.compat.asyncparticles;

import com.google.common.collect.Iterators;
import dev.portalix.client.portal.scene.PortalSceneManager;
import fun.qu_an.minecraft.asyncparticles.client.addon.GpuParticleGroup;
import fun.qu_an.minecraft.asyncparticles.client.config.ConfigHelper;
import java.util.Iterator;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(QuadParticleGroup.class)
abstract class AsyncParticleExtractionMixin {
  @ModifyVariable(method = "extractRenderState", at = @At("STORE"), ordinal = 0)
  private Iterator<SingleQuadParticle> portalix$portalParticles(Iterator<SingleQuadParticle> particles) {
    if (!PortalSceneManager.isPortalView() || !ConfigHelper.isGpuParticles()
        || !(this instanceof GpuParticleGroup group)) return particles;
    var gpuParticles = group.asyncparticles$getGpuParticles();
    if (gpuParticles.isEmpty()) return particles;
    return Iterators.concat(particles, Iterators.filter(gpuParticles.iterator(), SingleQuadParticle::isAlive));
  }
}
