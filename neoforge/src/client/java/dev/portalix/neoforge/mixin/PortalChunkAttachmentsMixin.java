package dev.portalix.neoforge.mixin;

import dev.portalix.client.world.ChunkAttachmentAccess;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
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
    var chunk = (LevelChunk) (Object) this;
    var holder = chunk.getAttachmentHolder();
    // NeoForge's Sent event syncs the attachments present after the full chunk packet.
    attachments.forEach(holder::removeData);
    attachments.clear();
  }
}
