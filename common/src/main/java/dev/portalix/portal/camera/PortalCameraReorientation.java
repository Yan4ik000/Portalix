package dev.portalix.portal.camera;

import org.joml.Matrix3fc;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

public final class PortalCameraReorientation {
  private final boolean preserveForward;
  private final Quaterniond commandView = new Quaterniond();
  // Source applies this transient rotation only when displaying the command view.
  private final Quaterniond correction = new Quaterniond();
  private final Vector3d referenceUp = new Vector3d(0, 1, 0);
  private final Vector3d axis = new Vector3d(0, 0, 1);
  private PortalLookAngles commandAngles = new PortalLookAngles(0, 0);
  private boolean restoringUp;

  public PortalCameraReorientation() {
    this(false);
  }

  public PortalCameraReorientation(boolean preserveForward) {
    this.preserveForward = preserveForward;
  }

  public void resetView(float yaw, float pitch) {
    commandView.rotationYXZ(
        Math.PI - Math.toRadians(yaw), -Math.toRadians(Math.clamp(pitch, -90, 90)), 0);
    correction.identity();
    referenceUp.set(0, 1, 0);
    restoringUp = false;
    refreshLookAngles();
  }

  public void resetView(Quaternionfc orientation) {
    var angles = PortalLookAngles.from(orientation);
    resetView(angles.yaw(), angles.pitch());
    if (preserveForward)
      correction
          .set(commandView)
          .conjugate()
          .mul(new Quaterniond(orientation).normalize())
          .normalize();
    else
      correction
          .set(orientation)
          .normalize()
          .mul(new Quaterniond(commandView).conjugate())
          .normalize();
  }

  public void teleport(Matrix3fc rotation) {
    if (preserveForward) {
      teleportKeepingDirection(rotation);
      return;
    }
    var portal = new Quaterniond().setFromNormalized(rotation).normalize();
    var visible = visibleRotation().premul(portal);
    var previousUp = portal.transform(new Vector3d(referenceUp));
    commandView.premul(portal).normalize();
    if (previousUp.distanceSquared(referenceUp) > 1e-6) {
      var forward = commandView.transform(new Vector3d(0, 0, -1));
      var up = commandView.transform(new Vector3d(0, 1, 0));
      boolean alternate =
          new Vector3d(0, 1, 0).cross(forward).dot(new Vector3d(up).cross(forward)) < 0;
      boolean preserveHeading = alternate && Math.abs(Math.abs(previousUp.y) - 1) <= 1e-6;
      beginReorientation(alternate, preserveHeading);
    }
    correction.set(visible).mul(new Quaterniond(commandView).conjugate()).normalize();
    refreshLookAngles();
  }

  private void teleportKeepingDirection(Matrix3fc rotation) {
    var portal = new Quaterniond().setFromNormalized(rotation).normalize();
    var visible = visibleRotation().premul(portal);
    commandView.set(visible);
    referenceUp.set(0, 1, 0);
    restoringUp = false;
    setBasis(visible.transform(new Vector3d(0, 0, -1)), referenceUp);
    correction.set(commandView).conjugate().mul(visible).normalize();
    refreshLookAngles();
  }

  private void beginReorientation(boolean alternate, boolean preserveHeading) {
    var forward = commandView.transform(new Vector3d(0, 0, -1));
    var up = commandView.transform(new Vector3d(0, 1, 0));
    double vertical = Math.abs(forward.y);
    if (vertical > .95 || alternate && vertical > .75) {
      var pole = new Vector3d(0, Math.copySign(1, forward.y), 0);
      if (!preserveHeading) {
        var right = commandView.transform(new Vector3d(1, 0, 0));
        double tilt = Math.acos(Math.clamp(right.dot(pole), -1, 1)) - Math.PI / 2;
        new Quaterniond().rotationAxis(tilt, up.x, up.y, up.z).transform(forward);
      }
      if (alternate) {
        double turn = Math.acos(Math.clamp(pole.dot(forward), -1, 1)) + Math.toRadians(1);
        var turnAxis = new Vector3d(forward).cross(pole);
        if (turnAxis.lengthSquared() > 1e-20) {
          turnAxis.normalize();
          new Quaterniond().rotationAxis(turn, turnAxis.x, turnAxis.y, turnAxis.z).transform(forward);
        }
      }
      referenceUp.set(0, 1, 0);
      restoringUp = false;
      setBasis(forward, referenceUp);
      return;
    }

    var heading = new Vector3d(forward.x, 0, forward.z).normalize();
    if (up.y < 0) {
      referenceUp.set(up).sub(new Vector3d(heading).mul(up.dot(heading))).normalize();
      axis.set(heading);
      if (new Vector3d(0, 1, 0).cross(heading).dot(referenceUp) > 0) axis.negate();
    } else {
      referenceUp.set(up);
      axis.set(up).cross(0, 1, 0);
      if (axis.lengthSquared() > 1e-12) axis.normalize();
    }
    restoringUp = referenceUp.y < 1 - 1e-12;
    setBasis(forward, referenceUp);
  }

  public void update(float seconds) {
    if (seconds <= 0 || !active()) return;
    double correctionAngle =
        2
            * Math.atan2(
                Math.sqrt(
                    correction.x * correction.x
                        + correction.y * correction.y
                        + correction.z * correction.z),
                Math.abs(correction.w));
    if (correctionAngle > 0) {
      double remaining = correctionAngle * Math.exp(-3.465735912322998 * seconds);
      if (remaining < Math.toRadians(.15)) correction.identity();
      else correction.slerp(new Quaterniond(), 1 - remaining / correctionAngle).normalize();
    }
    if (restoringUp) {
      double angle = Math.atan2(Math.hypot(referenceUp.x, referenceUp.z), referenceUp.y);
      if (new Vector3d(referenceUp).cross(0, 1, 0).dot(axis) < 0) axis.negate();
      if (angle <= Math.toRadians(.1)) {
        commandView.premul(new Quaterniond().rotationAxis(angle, axis.x, axis.y, axis.z)).normalize();
        referenceUp.set(0, 1, 0);
        restoringUp = false;
        setBasis(commandView.transform(new Vector3d(0, 0, -1)), referenceUp);
      } else {
        double step =
            Math.min(
                angle,
                Math.max(
                    angle * -Math.expm1(-5.545177459716797 * seconds), Math.toRadians(10) * seconds));
        var rotation = new Quaterniond().rotationAxis(step, axis.x, axis.y, axis.z);
        rotation.transform(referenceUp).normalize();
        commandView.premul(rotation).normalize();
      }
    }
    refreshLookAngles();
  }

  public void mouse(double yawDegrees, double pitchDegrees) {
    if (yawDegrees == 0 && pitchDegrees == 0) return;
    // Portal input turns about the independent reference up, never the display correction.
    var forward = commandView.transform(new Vector3d(0, 0, -1));
    var right = commandView.transform(new Vector3d(1, 0, 0));
    right.fma(-right.dot(referenceUp), referenceUp).normalize();
    new Quaterniond()
        .rotationAxis(-Math.toRadians(yawDegrees), referenceUp.x, referenceUp.y, referenceUp.z)
        .transform(right);
    double elevation =
        Math.atan2(forward.dot(referenceUp), new Vector3d(forward).cross(referenceUp).length());
    elevation = Math.clamp(elevation - Math.toRadians(pitchDegrees), -Math.PI / 2, Math.PI / 2);
    forward.set(referenceUp).cross(right).mul(Math.cos(elevation));
    forward.fma(Math.sin(elevation), referenceUp).normalize();
    var up = new Vector3d(right).cross(forward).normalize();
    commandView.identity().lookAlong(forward, up).conjugate().normalize();
    if (restoringUp && pitchDegrees != 0 && referenceUp.y > .9) finishReorientation();
    refreshLookAngles();
  }

  public Quaternionf orientation() {
    return visibleRotation().get(new Quaternionf());
  }

  public Quaternionf commandOrientation() {
    return commandView.get(new Quaternionf());
  }

  public PortalLookAngles angles() {
    return commandAngles;
  }

  public void look(float yaw, float pitch) {
    if (wrap(yaw - commandAngles.yaw()) == 0 && pitch == commandAngles.pitch()) return;
    commandView.rotationYXZ(Math.PI - Math.toRadians(yaw), -Math.toRadians(pitch), 0);
    setBasis(commandView.transform(new Vector3d(0, 0, -1)), referenceUp);
    refreshLookAngles();
  }

  public void look(Quaternionfc orientation) {
    commandView.set(orientation).normalize();
    setBasis(commandView.transform(new Vector3d(0, 0, -1)), referenceUp);
    refreshLookAngles();
  }

  private void finishReorientation() {
    // A fixed look direction cannot define a right axis against a moving up at either pole.
    // Hand pitch control back in the upright frame and display the remaining roll continuously.
    var visible = visibleRotation();
    var forward = commandView.transform(new Vector3d(0, 0, -1));
    referenceUp.set(0, 1, 0);
    restoringUp = false;
    setBasis(forward, referenceUp);
    correction.set(visible).mul(new Quaterniond(commandView).conjugate()).normalize();
  }

  private void setBasis(Vector3d forward, Vector3d up) {
    var right = new Vector3d(forward).cross(up);
    if (right.lengthSquared() < 1e-12) {
      // At either pitch limit the screen right still defines the heading.
      commandView.transform(new Vector3d(1, 0, 0), right);
      right.fma(-right.dot(forward), forward);
    }
    up = right.normalize().cross(forward, new Vector3d()).normalize();
    commandView.identity().lookAlong(forward, up).conjugate().normalize();
  }

  private Quaterniond visibleRotation() {
    return preserveForward
        ? new Quaterniond(commandView).mul(correction).normalize()
        : new Quaterniond(commandView).premul(correction).normalize();
  }

  private void refreshLookAngles() {
    commandAngles = PortalLookAngles.from(commandView);
  }

  public boolean active() {
    return restoringUp || Math.abs(correction.w) < 1 - 1e-12;
  }

  private static double wrap(double degrees) {
    return degrees - Math.floor((degrees + 180) / 360) * 360;
  }
}
