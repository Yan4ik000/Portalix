package dev.portalix.client.carry.prediction;

import java.util.Arrays;

final class CarrySnapshotClock {
  private final double[] intervals = {1, 1, 1, 1, 1};
  private final double[] ordered = new double[intervals.length];
  private int next;
  private long previousTick = Long.MIN_VALUE;
  private long latestTick = Long.MIN_VALUE;
  private double previousTime;
  private double latestTime;
  private double tickDuration = 1;

  void receive(long tick, double now) {
    if (tick <= latestTick) return;
    if (latestTick == Long.MIN_VALUE) {
      latestTime = now;
    } else if (now > latestTime) {
      // Close the previous delivery batch once its final server tick is known.
      if (previousTick != Long.MIN_VALUE) {
        intervals[next] = (latestTime - previousTime) / (latestTick - previousTick);
        next = (next + 1) % intervals.length;
        System.arraycopy(intervals, 0, ordered, 0, intervals.length);
        Arrays.sort(ordered);
        tickDuration = ordered[ordered.length / 2];
      }
      previousTick = latestTick;
      previousTime = latestTime;
      latestTime = now;
    }
    latestTick = tick;
  }

  double tickDuration() {
    return tickDuration;
  }
}
