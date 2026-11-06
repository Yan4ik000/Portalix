package dev.portalix.world.chunk;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class PortalChunkPriorityUpdates {
  private final Executor dispatcher;
  private final Consumer<List<PortalChunkObservers>> apply;
  private final AtomicReference<List<PortalChunkObservers>> pending = new AtomicReference<>();
  private List<PortalChunkObservers> current = List.of();

  public PortalChunkPriorityUpdates(
      Executor dispatcher, Consumer<List<PortalChunkObservers>> apply) {
    this.dispatcher = dispatcher;
    this.apply = apply;
  }

  public void update(List<PortalChunkObservers> priorities) {
    if (priorities.size() == current.size()) {
      boolean same = true;
      for (int i = 0; i < priorities.size(); i++)
        same &= priorities.get(i).samePriority(current.get(i));
      if (same) return;
    }
    current = List.copyOf(priorities);
    if (pending.getAndSet(current) == null)
      dispatcher.execute(() -> apply.accept(pending.getAndSet(null)));
  }
}
