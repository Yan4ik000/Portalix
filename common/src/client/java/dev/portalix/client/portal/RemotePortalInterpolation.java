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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;

public final class RemotePortalInterpolation {
  private final Entity entity;
  private final PortalInterpolationPath path = new PortalInterpolationPath();
  private final List<PortalInterpolationPath.TimedCrossing> vehicleCrossings = new ArrayList<>();
  private Vec3 target;
  private float yaw;
  private float pitch;
  private float scale;
  private int steps;

  public RemotePortalInterpolation(Entity entity) {
    this.entity = entity;
  }

  public interface Access {
    RemotePortalInterpolation portalix$interpolation();

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
    return steps > 0;
  }

  public Vec3 position() {
    return target;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public List<Crossing> pendingCrossings() {
    return path.crossings();
  }

  public void receive(EntityPortalCrossingPayload packet, List<Crossing> crossings) {
    var interpolation = entity.getInterpolation();
    if (!active() && interpolation != null) interpolation.cancel();
    int length =
        interpolation instanceof Access access
            ? access.portalix$length()
            : NewMinecartBehavior.POS_ROT_LERP_TICKS;
    resetMinecart();
    path.append(crossings);
    scale = packet.poseScale();
    target(packet.position(), packet.yaw(), packet.pitch(), length);
    if (!(entity instanceof LocalPlayer)) entity.lerpHeadTo(packet.headYaw(), length);
    entity.lerpMotion(path.sourceVector(packet.velocity()));
  }

  public void target(Vec3 position, float yRot, float xRot, int length) {
    target = position;
    yaw = yRot;
    pitch = xRot;
    steps = Math.max(1, length);
  }

  public float headYaw(float finalYaw) {
    return active() ? path.sourceAngles(finalYaw, 0).yaw() : finalYaw;
  }

  public Vec3 sourceMotion(Vec3 motion) {
    return active() ? path.sourceVector(motion) : motion;
  }

  public void scale(float value) {
    scale = value;
  }

  public void finishAccepted() {
    for (var passenger : entity.getPassengers()) {
      var remote = of(passenger);
      if (remote != null) remote.finishAccepted();
    }
    while (!path.isEmpty()) {
      var crossing = path.first();
      path.removeFirst();
      var position = crossing.afterFeet().add(
          crossing.link().transform().vector(entity.position().subtract(crossing.beforeFeet())));
      cross(crossing, position, 1, false);
    }
    cancel();
  }

  public void cancel() {
    if (active()) {
      PortalPassagePose.set(entity, scale);
      resetMinecart();
    }
    steps = 0;
    path.clear();
    vehicleCrossings.clear();
  }

  public void tick() {
    EntityPortalState.of(entity).cameraHistory().discardThrough(entity.tickCount - 1);
    double alpha = 1.0 / steps;
    var vehicle = entity.isPassenger() ? of(entity.getVehicle()) : null;
    boolean following = vehicle != null && (vehicle.active() || !vehicleCrossings.isEmpty());
    if (following) {
      for (var passage : vehicleCrossings) {
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
    else if (--steps == 0) {
      path.clear();
      PortalPassagePose.set(entity, scale);
      resetMinecart();
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
}
