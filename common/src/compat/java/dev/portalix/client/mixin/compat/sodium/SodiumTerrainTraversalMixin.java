package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.sodium.SodiumPortalFrustum;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.AbstractTraversableMultiForest;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.TraversableTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = {TraversableTree.class, AbstractTraversableMultiForest.class}, remap = false)
abstract class SodiumTerrainTraversalMixin {
  @WrapOperation(method = "traverse", at = @At(value = "INVOKE", target =
      "Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;getChunkCoord()Lnet/minecraft/core/SectionPos;"))
  private SectionPos portalix$eyeOrder(Viewport viewport, Operation<SectionPos> original) {
    // These coordinates order the nodes. Distance tests still use the exit's transform.
    return original.call(SodiumPortalFrustum.drawViewport(viewport));
  }
}
