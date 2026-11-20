package dev.portalix.client.world;

import net.minecraft.world.level.chunk.LevelChunk;

public interface ChunkAttachmentAccess<T> {
  void portalix$receivedAttachment(T type, boolean present);

  @SuppressWarnings("unchecked")
  static <T> void attachmentReceived(LevelChunk chunk, T type, boolean present) {
    ((ChunkAttachmentAccess<T>) chunk).portalix$receivedAttachment(type, present);
  }
}
