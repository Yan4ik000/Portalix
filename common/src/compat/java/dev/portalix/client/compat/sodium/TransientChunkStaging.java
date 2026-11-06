package dev.portalix.client.compat.sodium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.render.TransientStagingAccess;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gpu.arena.staging.StagingBuffer;

public final class TransientChunkStaging implements StagingBuffer {
  private final Map<GpuBuffer, ArrayList<Copy>> pending = new IdentityHashMap<>();

  @Override
  public void enqueueCopy(ByteBuffer data, GpuBuffer destination, long offset) {
    // BufferArena flushes before releasing upload data or relocating any allocation.
    if (!data.hasRemaining()) return;
    var copies = pending.computeIfAbsent(destination, ignored -> new ArrayList<>());
    copies.add(new Copy(offset, data.slice(), copies.size()));
  }

  public void enqueueCopy(byte[] data, GpuBuffer destination, long offset) {
    enqueueCopy(ByteBuffer.wrap(data), destination, offset);
  }

  @Override
  public void flush() {
    if (pending.isEmpty()) return;
    var encoder = RenderSystem.getDevice().createCommandEncoder();
    int blockSize =
        Math.toIntExact(
            ((TransientStagingAccess) encoder.transientMemory()).portalix$stagingBlockSize());
    try {
      for (var entry : pending.entrySet()) {
        var copies = entry.getValue();
        copies.sort(Comparator.comparingLong(Copy::offset));
        long remaining = 0;
        long previousEnd = -1;
        boolean overlapping = false;
        for (var copy : copies) {
          overlapping |= copy.offset < previousEnd;
          previousEnd = Math.max(previousEnd, copy.offset + copy.data.remaining());
          remaining += copy.data.remaining();
        }
        // Only disjoint writes can be reordered to merge adjacent destination ranges.
        if (overlapping) copies.sort(Comparator.comparingInt(Copy::order));
        int first = 0;
        int position = 0;
        while (remaining != 0) {
          int size = (int) Math.min(remaining, blockSize);
          GpuBufferSlice source;
          try (var mapped =
              encoder.transientMemory().allocateStaging(size, 4, GpuBuffer.USAGE_COPY_SRC)) {
            int index = first;
            int readPosition = position;
            for (int written = 0; written < size; ) {
              var data = copies.get(index).data;
              int length = Math.min(data.remaining() - readPosition, size - written);
              mapped.data().put(written, data, readPosition, length);
              written += length;
              readPosition += length;
              if (readPosition == data.remaining()) {
                index++;
                readPosition = 0;
              }
            }
            source = mapped.slice();
          }
          long readOffset = source.offset();
          long offset = -1;
          long length = 0;
          for (int copied = 0; copied < size; ) {
            var copy = copies.get(first);
            long destinationOffset = copy.offset + position;
            int part = Math.min(copy.data.remaining() - position, size - copied);
            if (length != 0 && destinationOffset != offset + length) {
              encoder.copyToBuffer(
                  new GpuBufferSlice(source.buffer(), readOffset, length),
                  entry.getKey().slice(offset, length));
              readOffset += length;
              length = 0;
            }
            if (length == 0) offset = destinationOffset;
            length += part;
            copied += part;
            position += part;
            if (position == copy.data.remaining()) {
              first++;
              position = 0;
            }
          }
          encoder.copyToBuffer(
              new GpuBufferSlice(source.buffer(), readOffset, length),
              entry.getKey().slice(offset, length));
          remaining -= size;
        }
      }
    } finally {
      pending.clear();
    }
  }

  @Override
  public void flip() {}

  @Override
  public void delete() {
    pending.clear();
  }

  @Override
  public long getUploadSizeLimit(long frameDuration) {
    return 25_600_000L;
  }

  @Override
  public String toString() {
    return "Device transient memory";
  }

  private record Copy(long offset, ByteBuffer data, int order) {}
}
