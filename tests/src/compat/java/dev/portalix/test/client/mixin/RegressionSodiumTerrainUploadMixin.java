package dev.portalix.test.client.mixin;

import dev.portalix.test.client.PortalTerrainUploadProbe;
import java.util.Collection;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderRegionManager.class, remap = false)
abstract class RegressionSodiumTerrainUploadMixin {
  @Inject(method = "uploadResults(Ljava/util/Collection;Lnet/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager;)V",
      at = @At("RETURN"))
  private void portalix$uploaded(Collection<BuilderTaskOutput> results, UniformBufferManager uniforms, CallbackInfo callback) {
    for (BuilderTaskOutput result : results) {
      if (!(result instanceof ChunkBuildOutput)) continue;
      long section = result.section.getPosition().asLong();
      PortalTerrainUploadProbe.uploaded(section);
    }
  }
}
