package dev.portalix.world.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Random;
import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.Test;

class LightSectionSnapshotTest {
  @Test
  void independentSnapshotsRetainEveryColumnAcrossUpdatesAndRemovals() {
    var initial = new Long2ObjectOpenHashMap<DataLayer>();
    for (int i = -100; i <= 100; i++) initial.put(i * 8192L + (i & 31), new DataLayer(i & 15));
    var current = new LightSectionSnapshot(initial);
    var reference = new HashMap<Long, DataLayer>(initial);
    var frozen = current.copy();
    var frozenReference = new HashMap<>(reference);
    var random = new Random(0x4c49474854L);
    for (int operation = 0; operation < 64; operation++) {
      long section = random.nextInt(-100, 101) * 8192L + random.nextInt(32);
      if (operation % 3 == 0) assertSame(reference.remove(section), current.remove(section));
      else {
        var layer = new DataLayer(operation & 15);
        assertSame(reference.put(section, layer), current.put(section, layer));
      }
      assertSame(reference.get(section), current.get(section));
      assertSame(frozenReference.get(section), frozen.get(section));
    }
    for (var entry : reference.entrySet()) assertSame(entry.getValue(), current.get(entry.getKey()));
    for (var entry : frozenReference.entrySet()) assertSame(entry.getValue(), frozen.get(entry.getKey()));
    var branch = frozen.copy();
    for (long section : frozenReference.keySet()) branch.remove(section);
    for (long section : frozenReference.keySet()) {
      assertFalse(branch.contains(section));
      assertSame(frozenReference.get(section), frozen.get(section));
    }
  }
}
