package dev.portalix.client.portal.terrain;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.LevelRenderer;

public final class PortalFrameTerrainBudget {
  private static long frame;
  private static long availableNanos;
  private static long remainingNanos;
  private static int unclaimedViews;
  private static long previousFrameAt;
  private static long averageFrameNanos;
  private static final Map<LevelRenderer, Allocation> views = new IdentityHashMap<>();
  private static final Set<LevelRenderer> preparing = Collections.newSetFromMap(new IdentityHashMap<>());
  private static final LinkedHashSet<LevelRenderer> waiting = new LinkedHashSet<>();

  private PortalFrameTerrainBudget() {}

  public static void beginFrame() {
    frame++;
    var client = Minecraft.getInstance();
    long now = System.nanoTime();
    long duration = previousFrameAt == 0 ? client.getFrameTimeNs() : now - previousFrameAt;
    previousFrameAt = now;
    duration = Math.clamp(duration, 1_000_100L, 100_000_000L);
    averageFrameNanos = averageFrameNanos == 0 ? duration
        : averageFrameNanos + (long) ((duration - averageFrameNanos) * .05);
    int fps = client.getFramerateLimitTracker().getFramerateLimit();
    if (client.options.enableVsync().get()) {
      int refresh = client.getWindow().getRefreshRate();
      if (refresh > 0) fps = fps > 0 ? Math.min(fps, refresh) : refresh;
    }
    // Like Sodium's upload allowance, preparation receives a tenth of the target frame.
    long targetNanos = fps > 0 && fps < Options.UNLIMITED_FRAMERATE_CUTOFF
        ? 1_000_000_000L / fps : averageFrameNanos;
    availableNanos = targetNanos / 10;
    remainingNanos = availableNanos;
    preparing.clear();
    preparing.add(client.levelRenderer);
    unclaimedViews = 1;
    views.clear();
  }

  public static void planViews(int remoteViews, Collection<LevelRenderer> remoteRenderers) {
    unclaimedViews = Math.max(0, remoteViews + 1 - views.size());
    preparing.clear();
    preparing.add(Minecraft.getInstance().levelRenderer);
    preparing.addAll(remoteRenderers);
  }

  public static void release(LevelRenderer view) {
    views.remove(view);
    preparing.remove(view);
    waiting.remove(view);
  }

  public static Allocation claim(LevelRenderer view) {
    var allocation = views.get(view);
    if (allocation != null) return allocation;
    long allowance = Math.max(0, availableNanos) / Math.max(1, unclaimedViews);
    availableNanos -= allowance;
    if (unclaimedViews > 0) unclaimedViews--;
    allocation = new Allocation(view, allowance);
    views.put(view, allocation);
    return allocation;
  }

  public static long frame() {
    return frame;
  }

  public static final class Allocation {
    private final LevelRenderer view;
    private long allowanceNanos;
    private boolean deferred;
    private int operations;
    private boolean finished;

    private Allocation(LevelRenderer view, long allowanceNanos) {
      this.view = view;
      this.allowanceNanos = allowanceNanos;
    }

    public boolean canPrepare() {
      if (operations == 0) waiting.add(view);
      // Deferred views get the next indivisible step before a new frame's earlier callers.
      if (remainingNanos <= 0) {
        deferred = true;
        return false;
      }
      for (var pending : waiting) {
        if (pending == view) break;
        if (preparing.contains(pending)) {
          deferred = true;
          return false;
        }
      }
      boolean allowed = finished ? unclaimedViews == 0 && availableNanos > 0
          : operations == 0 || allowanceNanos > 0;
      deferred = !allowed;
      return allowed;
    }

    public void skipped() {
      waiting.remove(view);
      deferred = false;
    }

    public void prepared(long durationNanos) {
      operations++;
      remainingNanos -= durationNanos;
      waiting.remove(view);
      if (finished) availableNanos -= durationNanos;
      else {
        allowanceNanos -= durationNanos;
        if (allowanceNanos < 0) {
          availableNanos += allowanceNanos;
          allowanceNanos = 0;
        }
      }
    }

    public void finish() {
      // Admission may be followed by a native eligibility check that skips the work.
      if (!deferred) waiting.remove(view);
      if (finished) return;
      availableNanos += allowanceNanos;
      allowanceNanos = 0;
      finished = true;
    }
  }
}
