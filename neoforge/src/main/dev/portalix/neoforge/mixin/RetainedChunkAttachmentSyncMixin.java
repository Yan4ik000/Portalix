package dev.portalix.neoforge.mixin;

import dev.portalix.neoforge.NeoForgePortalPlatform;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AttachmentSync.class, remap = false)
abstract class RetainedChunkAttachmentSyncMixin {
  @Inject(method = "onChunkSent", at = @At("HEAD"), cancellable = true)
  private static void portalix$keepSyncedAttachments(ChunkWatchEvent.Sent event, CallbackInfo ci) {
    // A retained chunk was still receiving attachment deltas. Its handlers already have initial data.
    if (NeoForgePortalPlatform.isResumedWatch(event)) ci.cancel();
  }
}
