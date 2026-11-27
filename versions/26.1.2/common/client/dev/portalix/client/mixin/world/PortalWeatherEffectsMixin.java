package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.audio.PortalEffectObservers;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.audio.PortalSoundPropagation.Observer;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatherEffectRenderer.class)
abstract class PortalWeatherEffectsMixin {
  @Shadow private int rainSoundTime;
  @Unique private PortalEffectObservers.Sampling portalix$sampling;
  @Unique private Observer portalix$observer;
  @Unique private final Map<List<PortalLink>, Integer> portalix$rainTimes = new HashMap<>();

  @WrapOperation(method = "tickRainParticles", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$weatherOrigin(Camera camera, Operation<Vec3> original) {
    return portalix$observer == null ? original.call(camera) : portalix$observer.position();
  }

  @WrapOperation(method = "tickRainParticles", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/renderer/WeatherEffectRenderer;getPrecipitationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
  private Biome.Precipitation portalix$weatherSample(WeatherEffectRenderer renderer,
      Level level, BlockPos position, Operation<Biome.Precipitation> original) {
    return portalix$sampling == null || portalix$sampling.owns(portalix$observer, position)
        ? original.call(renderer, level, position)
        : Biome.Precipitation.NONE;
  }

  @WrapOperation(method = "tickRainParticles", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
  private void portalix$rainParticle(ClientLevel level, ParticleOptions particle,
      double x, double y, double z, double dx, double dy, double dz, Operation<Void> original) {
    if (portalix$observer == null || portalix$observer.exit() == null
        || PortalAperture.signedDistance(portalix$observer.exit(), new Vec3(x, y, z))
            >= -PortalAperture.PLANE_EPSILON)
      original.call(level, particle, x, y, z, dx, dy, dz);
  }

  @WrapMethod(method = "tickRainParticles")
  private void portalix$weather(ClientLevel level, Camera camera, int tick, ParticleStatus particles,
      int radius, Operation<Void> original) {
    var listener = camera.position();
    var sampling = level.getRainLevel(1.0F) <= 0 ? null : PortalEffectObservers.sample(
        level, listener, BlockPos.containing(listener), radius, 10);
    if (sampling == null) {
      portalix$rainTimes.clear();
      original.call(level, camera, tick, particles, radius);
      return;
    }
    portalix$sampling = sampling;
    portalix$rainTimes.keySet().retainAll(
        sampling.observers().stream().map(Observer::links).toList());
    int primaryRainTime = rainSoundTime;
    try {
      for (var observer : sampling.observers()) {
        portalix$observer = observer;
        rainSoundTime = observer.exit() == null
            ? primaryRainTime : portalix$rainTimes.getOrDefault(observer.links(), 0);
        original.call(level, camera, tick, particles, radius);
        if (observer.exit() == null) primaryRainTime = rainSoundTime;
        else portalix$rainTimes.put(observer.links(), rainSoundTime);
      }
    } finally {
      portalix$observer = null;
      portalix$sampling = null;
      rainSoundTime = primaryRainTime;
    }
  }
}
