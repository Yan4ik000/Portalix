package dev.portalix.server.portal;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class PortalWarmTerrainTest {
  @Test
  void reversalsAndChainsReuseOnlyDeliveredCurrentColumns() {
    var warm = new PortalWarmTerrain();
    var a = new ChunkPos(0, 0);
    var b = new ChunkPos(100, 0);
    var c = new ChunkPos(200, 0);
    long outer = ChunkPos.pack(31, 0);
    long stale = ChunkPos.pack(25, 0);
    long untouched = ChunkPos.pack(12, 0);
    var received = new LongOpenHashSet(new long[] {outer, stale, a.pack(), b.pack(), c.pack()});
    var pins = new LongOpenHashSet();
    warm.crossed(a, b, ChunkTrackingView.of(a, 32), 32, 8, 8, 8, 0, received, key -> key != stale,
        key -> assertTrue(pins.add(key)));
    assertEquals(new LongOpenHashSet(new long[] {a.pack(), outer}), pins);
    assertFalse(warm.contains(untouched));
    assertFalse(warm.contains(stale));

    warm.crossed(b, c, ChunkTrackingView.of(b, 8), 32, 8, 8, 8, 20, received, key -> true,
        key -> assertTrue(pins.add(key)));
    assertTrue(warm.contains(outer));
    assertTrue(warm.contains(b.pack()));
    assertFalse(warm.contains(c.pack()));
    warm.crossed(c, a, ChunkTrackingView.of(c, 8), 32, 8, 8, 8, 28, received, key -> true,
        key -> assertTrue(pins.add(key)));
    for (int tick = 29; tick <= 57; tick++) {
      warm.advance(tick, 32, key -> true, key -> assertTrue(pins.remove(key)));
      assertTrue(warm.contains(outer), "A warm return must bridge the entire new loading delay");
    }
    assertFalse(pins.contains(b.pack()), "Leaving C must not refresh B's old departure");
    assertFalse(pins.contains(stale), "Revisiting an area may not acquire previously absent terrain");
    for (int tick = 58; tick < 500; tick++)
      warm.advance(tick, 32, key -> key != outer, key -> assertTrue(pins.remove(key)));
    assertEquals(new LongOpenHashSet(new long[] {outer}), pins, "Wait for the independent native ticket");
    warm.advance(500, 32, key -> true, key -> assertTrue(pins.remove(key)));
    assertTrue(pins.isEmpty());
  }

  @Test
  void multipleCrossingsInOneMoveCanDepartFromAnAlreadyDeliveredPreview() {
    var warm = new PortalWarmTerrain();
    var a = new ChunkPos(-150, 0);
    var b = new ChunkPos(0, 0);
    var c = new ChunkPos(150, 0);
    var primary = ChunkTrackingView.of(a, 32);
    long preview = ChunkPos.pack(8, 0);
    long unrelated = ChunkPos.pack(24, 0);
    var present = new LongOpenHashSet(new long[] {a.pack(), b.pack(), preview, unrelated});
    var held = new LongOpenHashSet();
    warm.crossed(a, b, primary, 32, 8, 8, 8, 10, present, key -> true, key -> assertTrue(held.add(key)));
    warm.crossed(b, c, primary, 32, 8, 8, 8, 10, present, key -> true, key -> assertTrue(held.add(key)));
    assertEquals(new LongOpenHashSet(new long[] {a.pack(), b.pack(), preview}), held);
  }

  @Test
  void intersectingViewsSharePinsAndRadiusChangesDrainThemUnderABudget() {
    var warm = new PortalWarmTerrain();
    var a = new ChunkPos(0, 0);
    var b = new ChunkPos(12, 0);
    var primary = ChunkTrackingView.of(a, 32);
    var delivered = new LongOpenHashSet();
    primary.forEach(pos -> delivered.add(pos.pack()));
    var pins = new LongOpenHashSet();
    warm.crossed(a, b, primary, 32, 8, 8, 8, 0, delivered, key -> true, key -> assertTrue(pins.add(key)));
    assertEquals(delivered, pins);
    warm.crossed(b, a, ChunkTrackingView.of(b, 8), 32, 8, 8, 8, 1, delivered, key -> true,
        key -> assertTrue(pins.add(key)));
    int total = pins.size();
    for (int tick = 2; tick <= 500; tick++) {
      int before = pins.size();
      for (int move = 0; move < 5; move++)
        warm.advance(tick, 4, key -> true, key -> assertTrue(pins.remove(key)));
      assertTrue(before - pins.size() <= 16);
    }
    assertTrue(total > 3000);
    assertTrue(pins.isEmpty());
    warm.clear(key -> fail("All references were already released"));
  }
}
