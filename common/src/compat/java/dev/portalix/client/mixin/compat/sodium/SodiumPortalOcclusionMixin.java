package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.portalix.client.portal.terrain.PortalOcclusionContext;
import java.util.Arrays;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = RenderSection.class, remap = false)
abstract class SodiumPortalOcclusionMixin {
  @Shadow @Final private int chunkX;
  @Shadow @Final private int chunkY;
  @Shadow @Final private int chunkZ;
  @Shadow private long[] visibilityData;

  @ModifyExpressionValue(
      method = "setRenderState",
      at = @At(value = "FIELD", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;visibilityData:[J"),
      require = 1, allow = 1)
  private long[] portalix$stableConnectivity(long[] next) {
    // Sodium uses reference equality to detect connectivity changes after a rebuild.
    return Arrays.equals(visibilityData, next) ? visibilityData : next;
  }

  @ModifyReturnValue(method = "getVisibilityData", at = @At("RETURN"))
  private long[] portalix$opening(long[] original) {
    return PortalOcclusionContext.open(SectionPos.asLong(chunkX, chunkY, chunkZ))
        ? BuiltSectionInfo.EMPTY.visibilityData
        : original;
  }
}
