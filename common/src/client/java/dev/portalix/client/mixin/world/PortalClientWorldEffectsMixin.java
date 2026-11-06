package dev.portalix.client.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.portal.audio.PortalEffectObservers;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.render.ParticleViewDistance;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.audio.PortalSoundPropagation.Observer;
import dev.portalix.portal.geometry.PortalAperture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class PortalClientWorldEffectsMixin {
  @Shadow private int rainSoundTime;
  @Unique private PortalEffectObservers.Sampling portalix$sampling;
  @Unique private Observer portalix$observer;
  @Unique private final Map<List<PortalLink>, Integer> portalix$rainTimes = new HashMap<>();

  @WrapOperation(
      method = "doAddParticle",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(DDD)D"))
  private double portalix$particleDistance(
      Vec3 camera, double x, double y, double z, Operation<Double> original) {
    return PortalSceneManager.particleDistance(original.call(camera, x, y, z), x, y, z);
  }

  @WrapOperation(
      method = "doAddParticle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"))
  private Particle portalix$particleRange(
      ParticleEngine engine,
      ParticleOptions options,
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz,
      Operation<Particle> original,
      @Local(argsOnly = true, ordinal = 0) boolean overrideLimiter) {
    if (portalix$observer != null
        && portalix$observer.exit() != null
        && PortalAperture.signedDistance(portalix$observer.exit(), new Vec3(x, y, z))
            < -PortalAperture.PLANE_EPSILON) return null;
    Particle particle = original.call(engine, options, x, y, z, dx, dy, dz);
    if (particle != null)
      ((ParticleViewDistance) particle).portalix$viewDistanceSquared(
          overrideLimiter ? Double.POSITIVE_INFINITY : 1024.0);
    return particle;
  }

  @WrapMethod(method = "animateTick")
  private void portalix$animate(int x, int y, int z, Operation<Void> original) {
    var client = Minecraft.getInstance();
    portalix$sampling = PortalEffectObservers.sample(
        (ClientLevel) (Object) this, client.gameRenderer.mainCamera().position(),
        new BlockPos(x, y, z), 31, 31);
    try {
      for (var observer : portalix$sampling.observers()) {
        portalix$observer = observer;
        if (observer.exit() == null) {
          original.call(x, y, z);
        } else {
          var pos = BlockPos.containing(observer.position());
          original.call(pos.getX(), pos.getY(), pos.getZ());
        }
      }
    } finally {
      portalix$observer = null;
      portalix$sampling = null;
    }
  }

  @Inject(
      method = "doAnimateTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
      cancellable = true)
  private void portalix$oneBlockTick(
      int x,
      int y,
      int z,
      int radius,
      RandomSource random,
      Block marker,
      BlockPos.MutableBlockPos pos,
      CallbackInfo callback) {
    if (portalix$sampling != null && !portalix$sampling.owns(portalix$observer, pos, radius - 1, radius - 1))
      callback.cancel();
  }

  @WrapOperation(
      method = "tickWeatherEffects",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 portalix$weatherOrigin(Camera camera, Operation<Vec3> original) {
    return portalix$observer == null ? original.call(camera) : portalix$observer.position();
  }

  @WrapOperation(
      method = "tickWeatherEffects",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
  private Biome.Precipitation portalix$weatherSample(
      ClientLevel level, BlockPos position, Operation<Biome.Precipitation> original) {
    return portalix$sampling == null || portalix$sampling.owns(portalix$observer, position)
        ? original.call(level, position)
        : Biome.Precipitation.NONE;
  }

  @WrapMethod(method = "tickWeatherEffects")
  private void portalix$weather(Operation<Void> original) {
    var client = Minecraft.getInstance();
    int radius = client.options.weatherRadius().get();
    var listener = client.gameRenderer.mainCamera().position();
    portalix$sampling = PortalEffectObservers.sample(
        (ClientLevel) (Object) this, listener, BlockPos.containing(listener), radius, 10);
    portalix$rainTimes.keySet().retainAll(
        portalix$sampling.observers().stream().map(Observer::links).toList());
    int primaryRainTime = rainSoundTime;
    try {
      for (var observer : portalix$sampling.observers()) {
        portalix$observer = observer;
        rainSoundTime = observer.exit() == null
            ? primaryRainTime : portalix$rainTimes.getOrDefault(observer.links(), 0);
        original.call();
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
