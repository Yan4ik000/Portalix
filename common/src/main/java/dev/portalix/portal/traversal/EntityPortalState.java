package dev.portalix.portal.traversal;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.camera.PortalCameraCorrections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class EntityPortalState {
  boolean moving;
  boolean segmentMoving;
  int poseStarted;
  boolean measuringPose;
  boolean authoritative;
  Vec3 replayVelocity;
  boolean fallImmune;
  PortalLink collisionLink;
  PortalCollisionWorld collisionScene;
  long sequence;
  private PortalEntityMovement.Crossing lastCrossing;
  PortalEntityMovement.MoveResult lastMove;
  private final PortalCameraCorrections cameraHistory = new PortalCameraCorrections();
  private double cameraEyeHeightShift;
  final List<PortalEntityMovement.Crossing> predictions = new ArrayList<>();
  final List<PortalEntityMovement.Crossing> outgoingCrossings = new ArrayList<>();
  final Set<UUID> crossingObservers = new HashSet<>();
  final Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> outgoingUpdates =
      new HashMap<>();
  private long authoritativeEvent;
  private long authoritativeTick = Long.MIN_VALUE;
  final Set<UUID> snapshotObservers = new HashSet<>();

  public static EntityPortalState of(Entity entity) {
    return ((Access) entity).portalix$motionState();
  }

  public boolean segmentMoving() {
    return segmentMoving;
  }

  public boolean hasPredictions() {
    return !predictions.isEmpty();
  }

  public long crossingSequence() {
    return sequence;
  }

  boolean acceptSnapshot(long tick, long event) {
    if (tick <= authoritativeTick || event < authoritativeEvent) return false;
    authoritativeTick = tick;
    authoritativeEvent = event;
    return true;
  }

  void resetPredictions(long event) {
    predictions.clear();
    sequence = event;
    if (lastCrossing != null && lastCrossing.eventId() > event) lastCrossing = null;
    collisionLink = null;
    fallImmune = false;
    resetCamera();
  }

  void resetPredictionsToAuthority() {
    resetPredictions(authoritativeEvent);
  }

  public boolean acceptAuthoritative(long event) {
    if (event <= authoritativeEvent) return false;
    authoritativeEvent = event;
    return true;
  }

  public List<PortalEntityMovement.Crossing> acceptAuthoritative(
      List<PortalEntityMovement.Crossing> crossings) {
    long previous = authoritativeEvent;
    if (!acceptAuthoritative(crossings.getLast().eventId())) return List.of();
    int first = 0;
    while (crossings.get(first).eventId() <= previous) first++;
    return first == 0 ? crossings : crossings.subList(first, crossings.size());
  }

  void initializeCrossingSequence(long event) {
    sequence = event;
    authoritativeEvent = event;
  }

  public PortalCameraCorrections cameraHistory() {
    return cameraHistory;
  }

  public double cameraEyeHeightShift() {
    return cameraEyeHeightShift;
  }

  public void eyeHeightChanged(float before, float after) {
    cameraEyeHeightShift += after - before;
  }

  public Vec3 eyeCorrection(double time) {
    return cameraHistory.eyeCorrection(time);
  }

  public void resetCamera() {
    cameraHistory.clear();
  }

  public void resetForWorld() {
    lastCrossing = null;
    lastMove = null;
    resetPredictions(0);
    collisionScene = null;
    outgoingCrossings.clear();
    crossingObservers.clear();
    outgoingUpdates.clear();
    authoritativeEvent = 0;
    authoritativeTick = Long.MIN_VALUE;
    snapshotObservers.clear();
  }

  void cameraCrossed(
      Entity entity, PortalEntityMovement.Crossing crossing, Vec3 earlierRenderFeet) {
    double time = entity.tickCount - 1 + crossing.moveFraction();
    Vec3 eye = new Vec3(0, entity.getEyeHeight(), 0);
    Vec3 shift =
        crossing
            .link()
            .transform()
            .point(crossing.beforeFeet().add(0, crossing.beforeEyeHeight(), 0))
            .subtract(crossing.afterFeet())
            .subtract(eye);
    cameraHistory.discardThrough(entity.tickCount - 1);
    cameraHistory.crossed(
        entity.tickCount,
        time,
        crossing.link(),
        shift,
        crossing.beforeFeet(),
        crossing.afterFeet(),
        crossing.previousFeet(),
        earlierRenderFeet,
        entity.getBoundingBox().getCenter());
  }

  public PortalEntityMovement.Crossing lastCrossing() {
    return lastCrossing;
  }

  void crossed(PortalEntityMovement.Crossing crossing) {
    sequence = Math.max(sequence, crossing.eventId());
    lastCrossing = crossing;
  }

  public interface Access {
    EntityPortalState portalix$motionState();
  }
}
