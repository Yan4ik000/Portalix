package dev.portalix.client.portal;

import dev.portalix.client.PortalixClient;
import dev.portalix.network.portal.EntityPortalCrossingPayload;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.geometry.PortalTransform;
import dev.portalix.portal.traversal.EntityPortalState;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.portal.traversal.PortalEntityMovement.Crossing;
import dev.portalix.portal.traversal.PortalPassagePose;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.SteppedInterpolationHandler;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RemotePortalInterpolation {
  private static final ScopedValue<Object> PACKET_BUNDLE = ScopedValue.newInstance();
  private final Entity entity;
  private final PortalInterpolationPath path = new PortalInterpolationPath();
  private final List<PortalInterpolationPath.TimedCrossing> vehicleCrossings = new ArrayList<>();
  private final ArrayDeque<Arrival> arrivals = new ArrayDeque<>(1);
  private Vec3 target;
  private float yaw;
  private float pitch;
  private float scale;
  private double steps;
  private boolean forwarding;

  public RemotePortalInterpolation(Entity entity) {
    this.entity = entity;
  }

  public static void inPacketBundle(Runnable handler) {
    ScopedValue.where(PACKET_BUNDLE, new Object()).run(handler);
  }

  public interface Access {
    RemotePortalInterpolation portalix$interpolation();

    @Nullable RemotePortalInterpolation portalix$existingInterpolation();

    default @Nullable RemotePortalInterpolation portalix$activeInterpolation() {
      var interpolation = portalix$existingInterpolation();
      return interpolation != null && interpolation.intercepting() ? interpolation : null;
    }

    int portalix$length();
  }

  public interface LivingAccess {
    void portalix$transformHead(PortalTransform transform);
  }

  public interface MinecartAccess {
    RemotePortalInterpolation portalix$interpolation();

    void portalix$resetInterpolation();
  }

  public interface MotionAccess {
    void portalix$transformMotion(PortalTransform transform);
  }

  public static RemotePortalInterpolation of(Entity entity) {
    if (entity instanceof AbstractMinecart minecart
        && minecart.getBehavior() instanceof MinecartAccess access)
      return access.portalix$interpolation();
    InterpolationHandler interpolation = entity.getInterpolation();
    return interpolation instanceof Access access ? access.portalix$interpolation() : null;
  }

  public boolean active() {
    return driving() || !arrivals.isEmpty();
  }

  public boolean driving() {
    return steps > 0;
  }

  public boolean intercepting() {
    return active() && !forwarding;
  }

  public PositionAndRotation target() {
    return arrivals.isEmpty() ? PositionAndRotation.of(target, yaw, pitch) : arrivals.getLast().target;
  }

  public List<Crossing> pendingCrossings() {
    if (arrivals.isEmpty()) return path.crossings();
    var pending = new ArrayList<>(path.crossings());
    var later = arrivals.iterator();
    if (driving()) later.next();
    later.forEachRemaining(arrival -> pending.addAll(arrival.crossings));
    return List.copyOf(pending);
  }

  public void receive(EntityPortalCrossingPayload packet, List<Crossing> crossings) {
    var interpolation = entity.getInterpolation();
    // Let vanilla finish the current step, including its fractional progress and rotation.
    boolean playingNativeSteps = !driving() && interpolation instanceof SteppedInterpolationHandler
        && interpolation.hasActiveInterpolation();
    if (!active() && !playingNativeSteps) interpolation.cancel();
    int length =
        interpolation instanceof Access access
            ? access.portalix$length()
            : NewMinecartBehavior.POS_ROT_LERP_TICKS;
    resetMinecart();
    if (interpolation instanceof SteppedInterpolationHandler) {
      var arrival = new Arrival(packet, crossings, length);
      arrivals.addLast(arrival);
      if (arrivals.size() == 1 && !playingNativeSteps) start(arrival);
    } else {
      path.append(crossings);
      scale = packet.poseScale();
      target(packet.position(), packet.yaw(), packet.pitch(), length);
    }
    if (!(entity instanceof LocalPlayer))
      entity.lerpHeadTo(packet.headYaw(), Math.max(1, Mth.ceil(length / tickSpeed())));
    entity.lerpMotion(sourceMotion(packet.velocity()));
  }

  public void update(@Nullable PositionPath position, float yRot, float xRot,
      boolean hasRotation, int length) {
    if (arrivals.isEmpty()) {
      target(position == null ? target : position.endPosition(),
          hasRotation ? yRot : yaw, hasRotation ? xRot : pitch, length);
    } else {
      arrivals.getLast().update(position, yRot, xRot, hasRotation);
    }
  }

  private void start(Arrival arrival) {
    path.append(arrival.crossings);
    var landing = arrival.landing;
    target(landing.position(), landing.yRot(), landing.xRot(), arrival.length);
  }

  public void target(Vec3 position, float yRot, float xRot, int length) {
    target = position;
    yaw = yRot;
    pitch = xRot;
    steps = Math.max(1, length);
  }

  public float headYaw(float finalYaw) {
    return active() ? PortalInterpolationPath.sourceAngles(pendingCrossings(), finalYaw, 0).yaw() : finalYaw;
  }

  public Vec3 sourceMotion(Vec3 motion) {
    return arrivals.isEmpty() ? path.sourceVector(motion)
        : PortalInterpolationPath.sourceVector(pendingCrossings(), motion);
  }

  public void scale(float value) {
    if (arrivals.isEmpty()) scale = value;
    else arrivals.getLast().scale = value;
  }

  public void finishAccepted() {
    for (var passenger : entity.getPassengers()) {
      var remote = of(passenger);
      if (remote != null) remote.finishAccepted();
    }
    for (var crossing : pendingCrossings()) {
      var position = crossing.afterFeet().add(
          crossing.link().transform().vector(entity.position().subtract(crossing.beforeFeet())));
      cross(crossing, position, 1, false);
    }
    cancel();
  }

  public void cancel() {
    if (forwarding || !active()) return;
    PortalPassagePose.set(entity, arrivals.isEmpty() ? scale : arrivals.getLast().scale);
    resetMinecart();
    steps = 0;
    path.clear();
    arrivals.clear();
    vehicleCrossings.clear();
  }

  public void tick() {
    if (!driving()) {
      if (arrivals.isEmpty()) return;
      var interpolation = entity.getInterpolation();
      if (interpolation.hasActiveInterpolation()) {
        if (vehicleCrossings.isEmpty()) return;
        finishRiderPlayback(interpolation);
      }
      start(arrivals.getFirst());
      if (vehicleCrossings.isEmpty()) return;
    }
    EntityPortalState.of(entity).cameraHistory().discardThrough(entity.tickCount - 1);
    double elapsed = Math.min(steps, tickSpeed());
    double alpha = elapsed / steps;
    var vehicle = entity.isPassenger() ? of(entity.getVehicle()) : null;
    boolean following = vehicle != null && (vehicle.active() || !vehicleCrossings.isEmpty());
    if (following) {
      for (var passage : vehicleCrossings) {
        if (path.isEmpty() && arrivals.size() > 1) {
          finish();
          if (!driving()) {
            finishRiderPlayback(entity.getInterpolation());
            start(arrivals.getFirst());
          }
        }
        if (!path.isEmpty() && path.first().link().equals(passage.crossing().link())) {
          var crossing = path.first();
          path.removeFirst();
          cross(crossing, passage.fraction());
        }
      }
      vehicleCrossings.clear();
    } else {
      var sample = path.advance(entity.position(), target, alpha);
      for (var passage : sample.crossings()) {
        cross(passage.crossing(), passage.fraction());
        for (var passenger : entity.getIndirectPassengers()) {
          var interpolation = of(passenger);
          if (interpolation != null && interpolation.active())
            interpolation.vehicleCrossings.add(passage);
        }
      }
      entity.setPos(sample.position());
    }
    // The local passenger follows the vehicle position but owns their look direction.
    if (!(entity instanceof LocalPlayer)) {
      var angles = path.sourceAngles(yaw, pitch);
      entity.setYRot((float) Mth.rotLerp(alpha, entity.getYRot(), angles.yaw()));
      entity.setXRot((float) Mth.lerp(alpha, entity.getXRot(), angles.pitch()));
    }
    if (following && !path.isEmpty()) steps = Math.max(1, vehicle.steps);
    else if ((steps -= Math.min(steps, elapsed)) == 0) finish();
  }

  private float tickSpeed() {
    return entity.getInterpolation() instanceof SteppedInterpolationHandler
        ? entity.level().getRelativeTickSpeed() : 1;
  }

  private void finish() {
    path.clear();
    steps = 0;
    if (arrivals.isEmpty()) {
      PortalPassagePose.set(entity, scale);
      resetMinecart();
      return;
    }

    var arrival = arrivals.removeFirst();
    PortalPassagePose.set(entity, arrival.scale);
    var interpolation = entity.getInterpolation();
    // Tracking resets its history at each crossing. The buffered paths belong to this exit frame.
    forwarding = true;
    try {
      for (var update : arrival.updates)
        interpolation.interpolateTo(update.position, update.yaw, update.pitch, update.hasRotation);
    } finally {
      forwarding = false;
    }
    if (!arrivals.isEmpty() && !interpolation.hasActiveInterpolation()) start(arrivals.getFirst());
  }

  private void finishRiderPlayback(InterpolationHandler interpolation) {
    forwarding = true;
    try {
      // The seat supplies position; finish the old frame's turns before following the vehicle.
      var target = interpolation.target();
      if (target != null && !(entity instanceof LocalPlayer)) {
        entity.setYRot(target.yRot());
        entity.setXRot(target.xRot());
      }
      interpolation.cancel();
    } finally {
      forwarding = false;
    }
  }

  private void resetMinecart() {
    if (entity instanceof AbstractMinecart minecart
        && minecart.getBehavior() instanceof MinecartAccess access)
      access.portalix$resetInterpolation();
  }

  private void cross(Crossing crossing, double fraction) {
    cross(crossing, crossing.afterFeet(), fraction, true);
  }

  private void cross(Crossing crossing, Vec3 position, double fraction, boolean publishVisualCrossing) {
    Vec3 previous = new Vec3(entity.xo, entity.yo, entity.zo);
    float beforeYaw = entity.getYRot(), beforePitch = entity.getXRot();
    float beforeEyeHeight = entity.getEyeHeight();
    transformFrame(crossing);
    var angles = new PortalLookAngles(entity.getYRot(), entity.getXRot());
    entity.setPos(position);
    var earlierRenderFeet = EntityMotion.rebaseHistory(
        entity, crossing.link(), crossing.beforeFeet(), crossing.afterFeet(), previous, 0, fraction);
    entity.resetFallDistance();
    var actual = new Crossing(
        crossing.eventId(),
        crossing.link(),
        crossing.beforeFeet(),
        crossing.afterFeet(),
        entity.getDeltaMovement(),
        angles.yaw(),
        angles.pitch(),
        fraction,
        previous,
        beforeEyeHeight,
        beforeYaw,
        beforePitch,
        crossing.poseScale());
    if (publishVisualCrossing) PortalEntityMovement.reconcileCrossing(entity, actual, earlierRenderFeet);
    else PortalEntityMovement.completeAcceptedCrossing(entity, actual);
    PortalixClient.carryController().crossed(entity, crossing.link());
  }

  private void transformFrame(Crossing crossing) {
    float beforeYaw = entity.getYRot(), beforePitch = entity.getXRot();
    var transform = crossing.link().transform();
    var angles = PortalLookAngles.transform(transform, beforeYaw, beforePitch);
    var oldAngles = PortalLookAngles.transform(transform, entity.yRotO, entity.xRotO);
    entity.setYRot(angles.yaw());
    entity.setXRot(angles.pitch());
    entity.yRotO = oldAngles.yaw();
    entity.xRotO = oldAngles.pitch();
    if (entity instanceof LivingEntity living) {
      living.yBodyRot = PortalLookAngles.transform(transform, living.yBodyRot, 0).yaw();
      living.yBodyRotO = PortalLookAngles.transform(transform, living.yBodyRotO, 0).yaw();
      ((LivingAccess) living).portalix$transformHead(transform);
    }
    entity.setDeltaMovement(transform.vector(entity.getDeltaMovement()));
    if (entity instanceof AbstractMinecart) entity.lerpMotion(entity.getDeltaMovement());
    if (entity instanceof MotionAccess motion) motion.portalix$transformMotion(transform);
    PortalPassagePose.set(entity, crossing.poseScale());
  }

  private static final class Arrival {
    private final List<Crossing> crossings;
    private final PositionAndRotation landing;
    private final int length;
    private final List<PositionUpdate> updates = new ArrayList<>();
    private PositionAndRotation target;
    private float scale;
    private @Nullable Object landingBundle = PACKET_BUNDLE.isBound() ? PACKET_BUNDLE.get() : null;

    Arrival(EntityPortalCrossingPayload packet, List<Crossing> crossings, int length) {
      this.crossings = crossings;
      this.length = length;
      landing = PositionAndRotation.of(packet.position(), packet.yaw(), packet.pitch());
      target = landing;
      scale = packet.poseScale();
    }

    void update(@Nullable PositionPath position, float yaw, float pitch, boolean hasRotation) {
      if (landingBundle != null) {
        boolean bundledLanding = PACKET_BUNDLE.isBound() && landingBundle == PACKET_BUNDLE.get();
        landingBundle = null;
        // The tracker samples the crossing bundle's landing again after its history was cleared.
        if (bundledLanding && position instanceof PositionPath.Stepped stepped && stepped.steps().size() == 1
            && stepped.steps().getFirst().tickOffset() == 1 && stepped.endPosition().equals(landing.position())
            && (!hasRotation || yaw == landing.yRot() && pitch == landing.xRot())) return;
      }
      updates.add(new PositionUpdate(position, yaw, pitch, hasRotation));
      target = PositionAndRotation.of(position == null ? target.position() : position.endPosition(),
          hasRotation ? yaw : target.yRot(), hasRotation ? pitch : target.xRot());
    }
  }

  private record PositionUpdate(@Nullable PositionPath position, float yaw, float pitch, boolean hasRotation) {}
}
