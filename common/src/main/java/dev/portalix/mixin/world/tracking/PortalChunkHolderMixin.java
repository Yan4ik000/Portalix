package dev.portalix.mixin.world.tracking;

import dev.portalix.world.chunk.ChunkDeliveryAccess;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
abstract class PortalChunkHolderMixin {
  @Inject(method = "updateFutures", at = @At(value = "FIELD",
      target = "Lnet/minecraft/server/level/ChunkHolder;tickingChunkFuture:Ljava/util/concurrent/CompletableFuture;",
      opcode = Opcodes.PUTFIELD, ordinal = 1, shift = At.Shift.AFTER), require = 1, allow = 1)
  private void portalix$stopCollectingUpdates(ChunkMap map, Executor executor, CallbackInfo ci) {
    var level = ((ChunkMapAccessor) map).portalix$level();
    long column = ((ChunkHolder) (Object) this).getPos().pack();
    for (var player : level.players())
      ((ChunkDeliveryAccess) player.connection.chunkSender).portalix$invalidate(level, column);
  }
}
