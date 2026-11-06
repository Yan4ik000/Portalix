package dev.portalix.client.portal.scene;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class SceneChunkEvents {
  private final LongOpenHashSet additions = new LongOpenHashSet();
  private final LongOpenHashSet removals = new LongOpenHashSet();

  public void added(long chunk) {
    if (!removals.remove(chunk)) additions.add(chunk);
  }

  public void removed(long chunk) {
    if (!additions.remove(chunk)) removals.add(chunk);
  }

  public void clear() {
    additions.clear();
    removals.clear();
  }

  public boolean drain(LongConsumer added, Consumer<LongCollection> removed) {
    if (additions.isEmpty() && removals.isEmpty()) return false;
    if (!removals.isEmpty()) removed.accept(removals);
    for (long chunk : additions) added.accept(chunk);
    clear();
    return true;
  }
}
