package dev.portalix.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StateSyncSchedulerTest {
  @Test
  void burstsCoalesceWithoutLosingTheLastUpdateOrStarvingOtherOwners() {
    int interval = 20;
    var scheduler = new StateSyncScheduler(interval);
    UUID first = new UUID(1, 1), second = new UUID(2, 2), forgotten = new UUID(3, 3);
    var sent = new ArrayList<UUID>();
    assertTrue(scheduler.requestSync(first, 100));
    for (int request = 0; request < 3; request++) assertFalse(scheduler.requestSync(first, 100));
    assertTrue(scheduler.requestSync(second, 100));
    assertFalse(scheduler.requestSync(second, 100));
    assertTrue(scheduler.requestSync(forgotten, 100));
    assertFalse(scheduler.requestSync(forgotten, 100));
    scheduler.forget(forgotten);
    scheduler.flushPending(100 + interval - 1, sent::add);
    assertTrue(sent.isEmpty());
    scheduler.flushPending(100 + interval, sent::add);
    assertEquals(2, sent.size());
    assertEquals(Set.of(first, second), Set.copyOf(sent));

    assertFalse(scheduler.requestSync(first, 100 + interval));
    assertFalse(scheduler.requestSync(second, 100 + interval));
    assertTrue(scheduler.requestSync(forgotten, 100 + interval));
    for (int request = 0; request < 3; request++)
      assertFalse(scheduler.requestSync(forgotten, 100 + interval));
    scheduler.forget(first);
    scheduler.flushPending(100 + interval * 2L - 1, sent::add);
    assertEquals(2, sent.size());
    scheduler.flushPending(100 + interval * 2L, sent::add);
    assertEquals(4, sent.size());
    assertEquals(Set.of(second, forgotten), Set.copyOf(sent.subList(2, 4)));
    scheduler.flushPending(100 + interval * 2L, sent::add);
    assertEquals(4, sent.size());

    assertTrue(scheduler.requestSync(first, 100 + interval * 2L));
    assertFalse(scheduler.requestSync(second, 100 + interval * 2L));
    assertFalse(scheduler.requestSync(forgotten, 100 + interval * 2L));
    scheduler.clear();
    scheduler.flushPending(100 + interval * 3L, sent::add);
    assertEquals(4, sent.size());
    assertTrue(scheduler.requestSync(first, 100 + interval * 3L));
    assertTrue(scheduler.requestSync(second, 100 + interval * 3L));
    assertTrue(scheduler.requestSync(forgotten, 100 + interval * 3L));
  }
}
