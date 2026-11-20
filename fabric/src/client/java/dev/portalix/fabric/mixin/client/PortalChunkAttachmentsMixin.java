package dev.portalix.fabric.mixin.client;

import dev.portalix.client.world.ChunkAttachmentAccess;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class PortalChunkAttachmentsMixin implements ChunkAttachmentAccess<AttachmentType<?>> {
  @Unique private Set<AttachmentType<?>> portalix$serverAttachments;

  @Override
  public void portalix$receivedAttachment(AttachmentType<?> type, boolean present) {
    if (present) {
      if (portalix$serverAttachments == null) portalix$serverAttachments = new HashSet<>();
      portalix$serverAttachments.add(type);
    } else if (portalix$serverAttachments != null) {
      portalix$serverAttachments.remove(type);
    }
  }

  @Inject(method = "replaceWithPacketData", at = @At("HEAD"), require = 1, allow = 1)
  private void portalix$resetSyncedAttachments(CallbackInfo ci) {
    var attachments = portalix$serverAttachments;
    if (attachments == null || attachments.isEmpty()) return;
    // Fabric's initial sync follows the full chunk packet and only includes present values.
    var target = (AttachmentTarget) this;
    for (var type : attachments)
      if (target.hasAttached(type)) target.removeAttached(type);
    attachments.clear();
  }
}
