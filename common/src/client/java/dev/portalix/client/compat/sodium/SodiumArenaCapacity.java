package dev.portalix.client.compat.sodium;

public final class SodiumArenaCapacity {
  private static final long MINIMUM_CAPACITY = 1L << 20;

  private SodiumArenaCapacity() {}

  public static long calculate(long required, long nativeCapacity, int stride) {
    long alignment = 4L * stride;
    long size = Math.max(required, MINIMUM_CAPACITY);
    long units = size / alignment + (size % alignment == 0 ? 0 : 1);
    long lower = Long.highestOneBit(units - 1);
    long rounded = lower <= Long.MAX_VALUE / alignment / 2 ? lower * 2 * alignment : Long.MAX_VALUE;
    return Math.min(align(nativeCapacity, stride), rounded);
  }

  public static long align(long capacity, int stride) {
    long alignment = 4L * stride;
    return capacity - capacity % alignment;
  }
}
