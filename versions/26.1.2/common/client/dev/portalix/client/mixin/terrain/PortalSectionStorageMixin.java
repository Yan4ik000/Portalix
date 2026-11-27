package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.compat.PortalTerrainBackend;
import dev.portalix.client.portal.terrain.PortalTerrainViewArea;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
abstract class PortalSectionStorageMixin {
  @WrapOperation(method = "allChanged", at = @At(value = "NEW", target = "net/minecraft/client/renderer/ViewArea"))
  private ViewArea portalix$storage(SectionRenderDispatcher dispatcher, Level level,
      int radius, LevelRenderer renderer, Operation<ViewArea> original) {
    if (!PortalTerrainBackend.usesVanillaChunkEvents()) return original.call(dispatcher, level, radius, renderer);
    return new PortalTerrainViewArea(dispatcher, (ClientLevel) level, radius,
        renderer, null, SectionPos.of(0, 0, 0));
  }
}
