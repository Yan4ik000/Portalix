package dev.portalix.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

public interface MappedBufferAccess {
  void portalix$mappedRange(GpuBufferSlice range);
}
