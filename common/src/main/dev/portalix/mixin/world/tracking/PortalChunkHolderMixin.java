package dev.portalix.mixin.world.tracking;

import dev.portalix.world.chunk.ChunkDeliveryAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
abstract class PortalChunkHolderMixin {
  @Shadow private int oldTicketLevel;

  @Inject(method = "demoteFullChunk", at = @At("HEAD"), require = 1, allow = 1)
  private void portalix$stopCollectingUpdates(ChunkMap map, FullChunkStatus status, CallbackInfo ci) {
    if (status.isOrAfter(FullChunkStatus.BLOCK_TICKING)
        || !ChunkLevel.fullStatus(oldTicketLevel).isOrAfter(FullChunkStatus.BLOCK_TICKING)) return;
    var level = ((ChunkMapAccessor) map).portalix$level();
    long column = ((ChunkHolder) (Object) this).getPos().pack();
    for (var player : level.players())
      ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$invalidate(player, column);
  }
}
