package dev.portalix.client.carry.prediction;

import java.util.NavigableMap;
import java.util.TreeMap;

final class CarryAcknowledgements {
  private static final int CAPACITY = 256;
  private final NavigableMap<Long, Double> times = new TreeMap<>();
  private long acknowledged;
  private double acknowledgedTime;

  public void submit(long sequence, double time) {
    times.put(sequence, time);
    if (times.size() > CAPACITY) times.pollFirstEntry();
  }

  public void confirm(long sequence) {
    if (sequence <= acknowledged) return;
    Double time = times.get(sequence);
    if (time == null) return;
    acknowledged = sequence;
    acknowledgedTime = time;
    times.headMap(sequence, true).clear();
  }

  public double acknowledgedTime(long sequence, double otherwise) {
    Double time = times.get(sequence);
    return time != null
        ? time
        : sequence == acknowledged && acknowledged > 0 ? acknowledgedTime : otherwise;
  }
}
