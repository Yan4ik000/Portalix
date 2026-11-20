package dev.portalix.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.portalix.client.world.ChunkAttachmentAccess;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentChange;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AttachmentChange.class, remap = false)
abstract class PortalAttachmentChangeMixin {
  @WrapOperation(method = "tryApply", at = @At(value = "INVOKE", target =
      "Lnet/fabricmc/fabric/api/attachment/v1/AttachmentTarget;setAttached(Lnet/fabricmc/fabric/api/attachment/v1/AttachmentType;Ljava/lang/Object;)Ljava/lang/Object;"),
      require = 1, allow = 1)
  private Object portalix$rememberChunkAttachment(AttachmentTarget target, AttachmentType<?> type,
      @Nullable Object value, Operation<Object> original) {
    var previous = original.call(target, type, value);
    if (target instanceof LevelChunk chunk) ChunkAttachmentAccess.attachmentReceived(chunk, type, value != null);
    return previous;
  }
}
