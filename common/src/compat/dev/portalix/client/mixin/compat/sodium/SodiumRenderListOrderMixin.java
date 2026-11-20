package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumPortalFrustum;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.RenderListProvider;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = RenderListProvider.class, remap = false)
interface SodiumRenderListOrderMixin {
  @WrapOperation(method = "createRenderLists", at = @At(value = "INVOKE", target =
      "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"),
      allow = 1)
  private static SectionPos portalix$eyeOrder(Viewport viewport, Operation<SectionPos> original) {
    return original.call(SodiumPortalFrustum.drawViewport(viewport));
  }
}
