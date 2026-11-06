package dev.portalix.world.chunk;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import org.junit.jupiter.api.Test;
import dev.portalix.api.PortalEndpoint;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

class ChunkAlgorithmsTest {
  @Test
  void retainedTerrainSharesOneGlobalViewWithoutExtendingObservedTerrain() {
    var dimension = ResourceKey.<Level>create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    int radius = 16;
    for (int players : new int[] {1, 2}) {
      var retained = new PortalRetainedTerrain(2);
      var ids = new ArrayList<UUID>();
      for (int player = 0; player < players; player++) {
        var id = new UUID(0, player + 1);
        ids.add(id);
        var center = new ChunkPos(player * 256, -player * 256);
        var primary = ChunkTrackingView.of(center, radius);
        var endpoint = new PortalEndpoint(dimension,
            new Vec3((center.x() + radius - 1) * 16 + 8, 100, center.z() * 16 + 8),
            new Vec3(0, 0, 1), new Vec3(0, 1, 0), 1, 2);
        retained.observe(id, endpoint, primary, radius, position -> true);
      }
      long fullColumns = 0;
      int sharedRadius = -1;
      for (var id : ids) {
        var claim = retained.claim(id, radius);
        if (claim == null) continue;
        if (sharedRadius == -1) sharedRadius = claim.radius();
        assertEquals(sharedRadius, claim.radius(), "equal eligible views share the same radius");
        long width = 2L * (claim.radius() + 2) + 1;
        fullColumns += width * width;
        var chunks = new LongOpenHashSet();
        claim.addChunks(chunks);
        assertFalse(chunks.isEmpty());
        for (long chunk : chunks)
          assertTrue(claim.captured().contains(ChunkPos.unpack(chunk)), "cache cannot request unseen client terrain");
        int margin = claim.captured().viewDistance() - claim.radius();
        assertTrue(Math.abs(claim.center().x() - claim.captured().center().x()) <= margin
            && Math.abs(claim.center().z() - claim.captured().center().z()) <= margin,
            "weighted loading and its FULL halo stay inside the previous native view");
      }
      long budgetWidth = 2L * (radius + 2) + 1;
      assertTrue(fullColumns <= budgetWidth * budgetWidth, "all retained native FULL areas fit one global view");
      if (players == 1) assertEquals(radius, retained.claim(ids.getFirst(), radius).radius());
      for (int index = 1; index < ids.size(); index++) retained.release(ids.get(index));
      assertEquals(radius, retained.claim(ids.getFirst(), radius).radius(), "released shares return to remaining views");
      retained.observe(ids.getFirst(), null, ChunkTrackingView.EMPTY, radius, position -> true);
      assertNull(retained.claim(ids.getFirst(), radius), "unequip/reset releases the budget");
    }
  }

  @Test
  void portalLoadingDoesNotStarveExistingChunkRequests() {
    var queue = new Long2ObjectLinkedOpenHashMap<List<Runnable>>();
    var queues = List.of(queue);
    for (long position = 0; position < 16; position++) queue.put(position, List.of());
    var order = new PortalChunkTicketOrder(position -> position < 16 ? 4 : 0);
    int backgroundGrants = 0;
    for (int grant = 0; grant < 16 * 8; grant++) {
      queue.put(1000L + grant, List.of());
      order.prioritize(position -> position < 16 ? 4 : 0);
      var selected = order.next(queues, 0);
      assertNotNull(selected);
      queues.get(selected.level()).remove(selected.position());
      order.retired(selected.level(), selected.position());
      if (selected.position() < 16) {
        assertEquals(backgroundGrants++, selected.position());
      }
    }
    assertEquals(16, backgroundGrants);
  }

  @Test
  void nearestReadyQueueMatchesAFullSortAtDifferentBudgetsAndInsertionOrders() {
    var random = new Random(0x4348554e4bL);
    for (int run = 0; run < 5; run++) {
      var pending = new LongOpenHashSet();
      for (int i = 0; i < 40; i++) pending.add(random.nextLong(-2000, 2000));
      LongUnaryOperator rank = value -> Math.abs(value % 31);
      LongPredicate ready = value -> value % 3 != 0;
      for (int quota : new int[] {0, 1, 7, 64}) {
        long[] expected = pending.longStream().filter(ready).boxed()
            .sorted(Comparator.comparingLong((Long value) -> rank.applyAsLong(value)).thenComparingLong(Long::longValue))
            .limit(quota).mapToLong(Long::longValue).toArray();
        assertArrayEquals(expected, ChunkSendQueue.nearest(pending, quota, rank, ready));
      }
    }
  }

  @Test
  void deliveryBudgetsBalanceRetirementAndLoadsAndRespectInvalidation() {
    var queue = new ChunkDeliveryQueue();
    queue.sent(10);
    queue.sent(11);
    queue.invalidate(10);
    assertFalse(queue.delivered(10));
    assertTrue(queue.delivered(11));
    queue.sent(10);
    assertTrue(queue.delivered(10));
    queue.retire(10);
    queue.retire(11);
    assertTrue(queue.requested(11));
    var batch = queue.select(new long[] {1, 100, 101}, 3, value -> value);
    assertEquals(3, batch.size());
    assertArrayEquals(new long[] {10}, batch.forgotten());
    assertArrayEquals(new long[] {1, 100}, batch.loaded());
    assertFalse(queue.delivered(10));
    assertTrue(queue.delivered(11));
    assertFalse(queue.hasRetirements());
    queue.clear();
    assertFalse(queue.delivered(11));
  }
}
