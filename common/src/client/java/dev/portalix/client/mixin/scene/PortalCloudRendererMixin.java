package dev.portalix.client.mixin.scene;

import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class PortalCloudRendererMixin {
  @Unique private static CloudRenderer.TextureData portalix$resource;
  @Shadow private CloudRenderer.TextureData texture;
  @Shadow private boolean needsRebuild;

  @Inject(
      method =
          "apply(Ljava/util/Optional;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
      at = @At("TAIL"))
  private void portalix$loaded(
      Optional<CloudRenderer.TextureData> data,
      ResourceManager resources,
      ProfilerFiller profiler,
      CallbackInfo ci) {
    portalix$resource = texture;
  }

  @Inject(method = "render", at = @At("HEAD"))
  private void portalix$resource(
      int color,
      CloudStatus status,
      float height,
      int range,
      Vec3 camera,
      long time,
      float partial,
      CallbackInfo ci) {

    if (texture != portalix$resource) {
      texture = portalix$resource;
      needsRebuild = true;
    }
  }
}
