package dev.portalix.client.mixin.terrain;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.client.portal.terrain.PortalRenderSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ViewArea.class, SectionUpdateTracker.class})
abstract class PortalSectionStorageMixin {
  @WrapOperation(
      method = "<init>",
      at = @At(value = "NEW", target = "net/minecraft/client/RotatingSectionStorage"))
  private <T extends RotatingSectionStorage.Value> RotatingSectionStorage<T> portalix$storage(
      int radius,
      int minY,
      int maxY,
      RotatingSectionStorage.ValueCreator<T> factory,
      Operation<RotatingSectionStorage<T>> original) {
    var camera = PortalSceneManager.camera();
    var level = Minecraft.getInstance().level;

    return new PortalRenderSections<>(
        radius,
        minY,
        maxY,
        factory,
        camera == null ? SectionPos.of(0, 0, 0) : SectionPos.of(camera.position()),
        chunk ->
            level != null
                && level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)));
  }
}
