package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.portalix.client.compat.sodium.TransientChunkStaging;
import net.caffeinemc.mods.sodium.client.gpu.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(value = RenderRegionManager.class, remap = false)
abstract class SodiumStagingMixin {
  @WrapMethod(method = "createStagingBuffer")
  private static StagingBuffer portalix$staging(Operation<StagingBuffer> original) {
    return new TransientChunkStaging();
  }
}
