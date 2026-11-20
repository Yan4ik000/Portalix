package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.client.world.ChunkAttachmentAccess;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.neoforged.neoforge.client.network.ClientPayloadHandler", remap = false)
abstract class PortalAttachmentSyncMixin {
  @WrapOperation(
      method = "handle(Lnet/neoforged/neoforge/network/payload/SyncAttachmentsPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
      at = @At(value = "INVOKE", ordinal = 1, target =
          "Lnet/neoforged/neoforge/attachment/AttachmentSync;receiveSyncedDataAttachments(Lnet/neoforged/neoforge/attachment/AttachmentHolder;Lnet/minecraft/core/RegistryAccess;Ljava/util/List;[B)V"),
      require = 1, allow = 1)
  private static void portalix$rememberChunkAttachments(AttachmentHolder holder, RegistryAccess registries,
      List<AttachmentType<?>> types, byte[] data, Operation<Void> original, @Local ChunkAccess chunk) {
    original.call(holder, registries, types, data);
    if (chunk instanceof LevelChunk fullChunk)
      for (var type : types) ChunkAttachmentAccess.attachmentReceived(fullChunk, type, holder.hasData(type));
  }
}
