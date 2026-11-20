package dev.portalix.world.snapshot;

import dev.portalix.mixin.world.snapshot.StrategyAccessor;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;
import net.minecraft.world.level.chunk.Strategy;

public record PaletteSnapshot<T>(PalettedContainer.Data<T> data, IdMap<T> registry) {
  public boolean read(FriendlyByteBuf buffer, Strategy<T> strategy) {
    if (registry != strategy.globalMap()
        || data.storage().getSize() != strategy.entryCount()
        || !data.configuration()
            .equals(
                ((StrategyAccessor) strategy)
                    .portalix$configuration(data.storage().getBits()))) return false;
    int start = buffer.readerIndex();
    if (matches(buffer)) return true;
    buffer.readerIndex(start);
    return false;
  }

  private boolean matches(FriendlyByteBuf buffer) {
    var palette = data.palette();
    if (buffer.readByte() != data.storage().getBits()) return false;
    if (palette instanceof SingleValuePalette<?>) {
      int id = registry.getId(palette.valueFor(0));
      if (id < 0 || buffer.readVarInt() != id) return false;
    } else if (palette instanceof LinearPalette<?> || palette instanceof HashMapPalette<?>) {
      int size = palette.getSize();
      if (buffer.readVarInt() != size) return false;
      for (int index = 0; index < size; index++) {
        int id = registry.getId(palette.valueFor(index));
        if (id < 0 || buffer.readVarInt() != id) return false;
      }
    } else if (!(palette instanceof GlobalPalette<?>)) return false;
    for (long value : data.storage().getRaw()) if (buffer.readLong() != value) return false;
    return true;
  }
}
