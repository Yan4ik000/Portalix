package dev.portalix.world.snapshot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainerRO;

public interface PaletteSnapshotAccess<T> {
  PalettedContainerRO<T> portalix$readOnly();

  PaletteSnapshot<T> portalix$packetSnapshot();

  boolean portalix$readSnapshot(FriendlyByteBuf buffer, PaletteSnapshot<T> snapshot);
}
