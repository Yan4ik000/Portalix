package dev.portalix.server.portal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalCrossingSync;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PlayerPortalMoveState {
  private static final int MOVEMENT_TICK_MILLIS = 50;
  private static final int MAX_FRAME_TICKS = 10;
  // The client's frame timer keeps its fractional tick after running a batch. The next
  // frame can tick one millisecond later. Netty KeepAlive can also precede an old batch.
  private static final int CLOCK_PHASE_MILLIS = MAX_FRAME_TICKS * MOVEMENT_TICK_MILLIS - 1;
  private static final int KEEP_ALIVE_PHASE_MILLIS = CLOCK_PHASE_MILLIS + MAX_FRAME_TICKS * MOVEMENT_TICK_MILLIS;
  private static final int CLOCK_PROBE_INTERVAL_MILLIS = 1000;
  private long lastSequence;
  private long completedSequence;
  private long crossingSequenceBeforeReplay;
  private boolean replaying;
  private double travelled;
  private double impulseSquared;
  private ServerLevel level;
  private int clientTick;
  private int movementClientTick = Integer.MIN_VALUE;
  private int movementAttemptClientTick = Integer.MIN_VALUE;
  private int movementBudgetTick = Integer.MIN_VALUE;
  private long nextClientTickTime;
  private long nextClockProbeTime;
  private volatile ClockProbe pendingClockProbe;
  private long clockAcknowledgedAt = Long.MIN_VALUE;
  private long lastReplayTime;
  private int continuationEntityId;
  private int replayedEntityId;
  private Vec3 replayedMovement = Vec3.ZERO;

  public PlayerPortalMoveState(long time) {
    nextClientTickTime = time - CLOCK_PHASE_MILLIS;
  }

  public static PlayerPortalMoveState of(ServerPlayer player) {
    return ((Access) player).portalix$movementState();
  }

  boolean beginReplay(ServerPlayer player, long sequence) {
    enterLevel(player.level());
    if (sequence <= lastSequence) return false;
    lastSequence = sequence;
    crossingSequenceBeforeReplay = EntityPortalState.of(player).crossingSequence();
    replaying = true;
    return true;
  }

  void endReplay() {
    replaying = false;
  }

  void completeReplay(long sequence) {
    completedSequence = sequence;
  }

  public ClientboundPingPacket clockProbe(long time) {
    if (pendingClockProbe != null || time < nextClockProbeTime) return null;
    nextClockProbeTime = time + CLOCK_PROBE_INTERVAL_MILLIS;
    // Negative Pong IDs belong to the movement clock. Chunk refreshes use positive IDs.
    var probe = new ClockProbe(ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, 0), time);
    pendingClockProbe = probe;
    return new ClientboundPingPacket(probe.id());
  }

  public boolean isClockProbe(int id) {
    var probe = pendingClockProbe;
    return probe != null && probe.id() == id;
  }

  public void acknowledgeClock(int id) {
    var probe = pendingClockProbe;
    if (probe == null || id != probe.id()) return;
    acknowledgeClockAt(probe.sentAt(), CLOCK_PHASE_MILLIS);
  }

  public void acknowledgeKeepAlive(long sentAt) {
    acknowledgeClockAt(sentAt, KEEP_ALIVE_PHASE_MILLIS);
  }

  private void acknowledgeClockAt(long sentAt, int phase) {
    // An ordered response bounds subsequent ticks, even if the client withholds our Pong.
    nextClientTickTime = Math.max(nextClientTickTime, sentAt - phase);
    clockAcknowledgedAt = Math.max(clockAcknowledgedAt, sentAt);
    var probe = pendingClockProbe;
    if (probe != null && probe.sentAt() <= sentAt) pendingClockProbe = null;
  }

  private record ClockProbe(int id, long sentAt) {}

  public long completedSequence() {
    return completedSequence;
  }

  public int clientTick() {
    return clientTick;
  }

  public boolean beginMovement(long time, Vec3 velocity) {
    movementAttemptClientTick = clientTick;
    if (time < nextClientTickTime) return false;
    movementClientTick = clientTick;
    recordMovement(0, velocity.lengthSqr());
    return true;
  }

  public void enterLevel(ServerLevel level) {
    if (this.level == level) return;
    resetForWorld();
    this.level = level;
  }

  public boolean movementStarted() {
    return movementClientTick == clientTick;
  }

  public void continueMovement(Entity subject, long time) {
    continuationEntityId = subject.getId();
    lastReplayTime = time;
  }

  public boolean continuesMovement(Entity subject) {
    return continuationEntityId == subject.getId();
  }

  public void nativeBaselineRefreshed() {
    // The ordered response follows the queued continuation. Switch back after vanilla resets its baseline.
    if (clockAcknowledgedAt > lastReplayTime) continuationEntityId = 0;
  }

  public boolean beginNativeMovement(
      long time, Vec3 velocity, double distance, boolean fallFlying, boolean checkDistance) {
    if (distance == 0) return true;
    if (checkDistance && !withinMovementBudget(distance, movementBudget(velocity.lengthSqr(), fallFlying)))
      return false;
    return beginMovement(time, velocity);
  }

  public double movementImpulseSquared(double original) {
    return movementBudgetTick == clientTick ? impulseSquared : original;
  }

  public double movementBudget(double impulseSquared, boolean fallFlying) {
    return (fallFlying ? 300 : 100) + movementImpulseSquared(impulseSquared);
  }

  public boolean withinMovementBudget(double distance, double budget) {
    double path = (movementBudgetTick == clientTick ? travelled : 0) + distance;
    return path * path <= budget;
  }

  public void recordNativeMovement(double distance, double impulseSquared) {
    if (distance != 0) recordMovement(distance, impulseSquared);
  }

  public void recordReplayDistance(double distance) {
    travelled += distance;
  }

  private void recordMovement(double distance, double impulseSquared) {
    if (movementBudgetTick != clientTick) {
      movementBudgetTick = clientTick;
      travelled = 0;
      this.impulseSquared = impulseSquared;
    }
    travelled += distance;
  }

  public List<PortalLink> activeCrossings(ServerPlayer player) {
    return replaying ? PortalCrossingSync.crossingsSince(player, crossingSequenceBeforeReplay) : List.of();
  }

  public Vec3 recordReplay(Entity subject, Vec3 movement) {
    replayedMovement = movementWithReplay(subject, movement);
    replayedEntityId = subject.getId();
    return replayedMovement;
  }

  public Vec3 movementWithReplay(Entity subject, Vec3 movement) {
    return replayedMovement(subject) ? replayedMovement.add(movement) : movement;
  }

  public Vec3 crossedReplay(Entity subject, PortalLink link) {
    if (!replayedMovement(subject)) return null;
    replayedMovement = link.transform().vector(replayedMovement);
    return replayedMovement;
  }

  public boolean replayedMovement(Entity subject) {
    return replayedEntityId != 0 && replayedEntityId == subject.getId();
  }

  public void endClientTick(long time) {
    if (movementClientTick == clientTick
        || movementAttemptClientTick != clientTick && time >= nextClientTickTime)
      nextClientTickTime += MOVEMENT_TICK_MILLIS;
    clientTick++;
    replayedEntityId = 0;
    replayedMovement = Vec3.ZERO;
  }

  public void resetForWorld() {
    lastSequence = 0;
    completedSequence = 0;
    crossingSequenceBeforeReplay = 0;
    replaying = false;
    level = null;
    continuationEntityId = 0;
    replayedEntityId = 0;
    replayedMovement = Vec3.ZERO;
  }

  public interface Access {
    PlayerPortalMoveState portalix$movementState();
  }
}
