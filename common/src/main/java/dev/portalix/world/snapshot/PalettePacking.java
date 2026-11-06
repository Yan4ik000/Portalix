package dev.portalix.world.snapshot;

import dev.portalix.mixin.world.snapshot.StrategyAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;

public final class PalettePacking {
  private PalettePacking() {}

  public static <T> PalettedContainerRO.PackedData<T> pack(
      PalettedContainer.Data<T> data, Strategy<T> strategy) {
    var storage = data.storage();
    var source = data.palette();
    var configuration = (StrategyAccessor) strategy;
    if (storage.getBits() == 0
        && configuration.portalix$paletteConfiguration(1).bitsInStorage() == 0)
      return new PalettedContainerRO.PackedData<>(List.of(source.valueFor(0)), Optional.empty(), 0);
    int capacity = Math.min(source.getSize(), storage.getSize());
    int capacityBits = Integer.SIZE - Integer.numberOfLeadingZeros(capacity - 1);
    var palette = new HashMapPalette<T>(capacityBits);
    var resize = PaletteResize.<T>noResizeExpected();
    int[] translation = null;
    int[] expanded = null;
    if (storage.getBits() <= 8) {
      int[] ids = new int[source.getSize()];
      int sourceBits = storage.getBits();
      if (sourceBits == 0) ids[0] = palette.idFor(source.valueFor(0), resize) + 1;
      else {
        int perWord = Long.SIZE / sourceBits;
        long mask = (1L << sourceBits) - 1;
        int remaining = storage.getSize();
        for (long word : storage.getRaw()) {
          int count = Math.min(perWord, remaining);
          for (int entry = 0; entry < count; entry++) {
            int id = (int) (word & mask);
            if (ids[id] == 0) ids[id] = palette.idFor(source.valueFor(id), resize) + 1;
            word >>>= sourceBits;
          }
          remaining -= count;
        }
      }
      translation = ids;
    } else {
      expanded = new int[storage.getSize()];
      storage.unpack(expanded);
      int previous = -1, encoded = -1;
      for (int index = 0; index < expanded.length; index++) {
        int id = expanded[index];
        if (id != previous) {
          previous = id;
          encoded = palette.idFor(source.valueFor(id), resize);
        }
        expanded[index] = encoded;
      }
    }
    int bits = configuration.portalix$paletteConfiguration(palette.getSize()).bitsInStorage();
    Optional<LongStream> values;
    if (bits == 0) {
      values = Optional.empty();
    } else {
      long[] packed =
          translation == null
              ? new SimpleBitStorage(bits, strategy.entryCount(), expanded).getRaw()
              : translate(storage, translation, bits, strategy.entryCount());
      values = Optional.of(Arrays.stream(packed));
    }
    return new PalettedContainerRO.PackedData<>(palette.getEntries(), values, bits);
  }

  private static long[] translate(BitStorage storage, int[] ids, int bits, int size) {
    Objects.checkFromIndexSize(0, size, storage.getSize());
    int perWord = Long.SIZE / bits;
    long[] packed = new long[(size + perWord - 1) / perWord];
    int sourceBits = storage.getBits();
    if (sourceBits == 0) return packed;
    int sourcePerWord = Long.SIZE / sourceBits;
    long mask = (1L << sourceBits) - 1;
    long[] source = storage.getRaw();
    if (sourceBits == bits) {
      int xor = -1;
      for (int id = 0; id < ids.length; id++) {
        if (ids[id] == 0) continue;
        int difference = id ^ (ids[id] - 1);
        if (xor < 0) xor = difference;
        else if (xor != difference) {
          xor = -1;
          break;
        }
      }
      if (xor >= 0) {
        long repeated = 0;
        for (int entry = 0; entry < perWord; entry++) repeated |= (long) xor << (entry * bits);
        long used = -1L >>> (Long.SIZE - perWord * bits);
        for (int word = 0; word < packed.length; word++)
          packed[word] = (source[word] ^ repeated) & used;
        int tail = size % perWord;
        if (tail != 0) packed[packed.length - 1] &= -1L >>> (Long.SIZE - tail * bits);
        return packed;
      }
    }
    int sourceWord = 0, remaining = 0;
    long input = 0;
    for (int word = 0, index = 0; word < packed.length; word++) {
      int count = Math.min(perWord, size - index);
      long output = 0;
      for (int entry = 0; entry < count; entry++, index++) {
        if (remaining == 0) {
          input = source[sourceWord++];
          remaining = sourcePerWord;
        }
        output |= (long) (ids[(int) (input & mask)] - 1) << (entry * bits);
        input >>>= sourceBits;
        remaining--;
      }
      packed[word] = output;
    }
    return packed;
  }
}
