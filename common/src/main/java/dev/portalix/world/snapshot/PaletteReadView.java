package dev.portalix.world.snapshot;

import dev.portalix.mixin.world.snapshot.PalettedContainerAccessor;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;

public final class PaletteReadView<T> implements PalettedContainerRO<T> {
  private final PalettedContainer.Data<T> data;
  private final Strategy<T> strategy;

  public PaletteReadView(PalettedContainer.Data<T> data, Strategy<T> strategy) {
    this.data = data;
    this.strategy = strategy;
  }

  public Strategy<T> strategy() {
    return strategy;
  }

  public boolean isViewOf(PalettedContainer.Data<T> candidate) {
    return data == candidate;
  }

  @Override
  public T get(int x, int y, int z) {
    return data.palette().valueFor(data.storage().get(strategy.getIndex(x, y, z)));
  }

  @Override
  public void getAll(Consumer<T> consumer) {
    IntSet present = new IntArraySet();
    data.storage().getAll(present::add);
    present.forEach(index -> consumer.accept(data.palette().valueFor(index)));
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    data.write(buffer, strategy.globalMap());
  }

  @Override
  public int getSerializedSize() {
    return data.getSerializedSize(strategy.globalMap());
  }

  @Override
  public int bitsPerEntry() {
    return data.storage().getBits();
  }

  @Override
  public boolean maybeHas(Predicate<T> predicate) {
    return data.palette().maybeHas(predicate);
  }

  @Override
  public void forEachInPalette(Consumer<T> consumer) {
    for (int index = 0; index < data.palette().getSize(); index++)
      consumer.accept(data.palette().valueFor(index));
  }

  @Override
  public void count(PalettedContainer.CountConsumer<T> output) {
    var palette = data.palette();
    if (palette.getSize() == 1) {
      output.accept(palette.valueFor(0), data.storage().getSize());
      return;
    }
    var counts = new Int2IntOpenHashMap();
    data.storage().getAll(index -> counts.addTo(index, 1));
    counts
        .int2IntEntrySet()
        .forEach(entry -> output.accept(palette.valueFor(entry.getIntKey()), entry.getIntValue()));
  }

  @Override
  public PalettedContainer<T> copy() {
    return PalettedContainerAccessor.portalix$create(
        strategy, data.configuration(), data.storage().copy(), data.palette().copy());
  }

  @Override
  public PalettedContainer<T> recreate() {
    return new PalettedContainer<>(data.palette().valueFor(0), strategy);
  }

  @Override
  public PackedData<T> pack(Strategy<T> targetStrategy) {
    return PalettePacking.pack(data, targetStrategy);
  }
}
