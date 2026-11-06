package dev.portalix.client.mixin.compat.sodium;

import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
    remap = false)
abstract class SodiumSectionCacheInvalidationMixin {
  @Shadow @Final private ClonedChunkSectionCache sectionCache;

  @Inject(method = "onSectionRemoved", at = @At("HEAD"))
  private void portalix$forgetSnapshot(int x, int y, int z, CallbackInfo ci) {
    sectionCache.invalidate(SectionPos.asLong(x, y, z));
  }
}
