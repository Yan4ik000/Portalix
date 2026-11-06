package dev.portalix.client.portal.terrain;

public final class TerrainBufferSizing {
  private TerrainBufferSizing() {}

  public static long allocationSize(long bytes, int alignment) {
    long padding = Integer.bitCount(alignment) == 1 && alignment <= 32 ? 0 : alignment;
    long aligned = (bytes + padding + 31) / 32 * 32;
    // Native TLSF maps its small search bins to blocks of 128 + 16 * bin bytes.
    if (aligned < 256) return 128 + aligned / 2;
    long interval = Long.highestOneBit(aligned) / 8;
    return (aligned + interval - 1) / interval * interval;
  }

  public static long nextHeap(long maximum, long previous, long pending) {
    long demand = Math.max(pending, previous * 2);
    if (demand >= maximum) return maximum;
    return Math.min(maximum, demand <= 1 ? 1 : Long.highestOneBit(demand - 1) << 1);
  }
}
