package dev.portalix.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class StateSyncScheduler {
  private final int interval;
  private final Map<UUID, Long> accepted = new HashMap<>();
  private final Set<UUID> pending = new HashSet<>();

  StateSyncScheduler(int interval) {
    if (interval <= 0) {
      throw new IllegalArgumentException("Positive interval required");
    }

    this.interval = interval;
  }

  private boolean tryAcquire(UUID owner, long tick) {
    Long previous = this.accepted.get(owner);
    if (previous != null && tick - previous < this.interval) {
      return false;
    }

    this.accepted.put(owner, tick);
    return true;
  }

  boolean requestSync(UUID owner, long tick) {
    if (this.tryAcquire(owner, tick)) {
      this.pending.remove(owner);
      return true;
    } else {
      this.pending.add(owner);
      return false;
    }
  }

  void flushPending(long tick, Consumer<UUID> response) {
    Iterator<UUID> iterator = this.pending.iterator();

    while (iterator.hasNext()) {
      UUID owner = iterator.next();
      if (this.tryAcquire(owner, tick)) {
        iterator.remove();
        response.accept(owner);
      }
    }
  }

  void forget(UUID owner) {
    this.accepted.remove(owner);
    this.pending.remove(owner);
  }

  void clear() {
    this.accepted.clear();
    this.pending.clear();
  }
}
