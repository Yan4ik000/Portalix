package dev.portalix.client.carry.prediction;

import dev.portalix.carry.CarryCollisionWorld;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalRoutes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

final class CarryInterpolation {
  private Vec3 position;
  private Vec3 velocity;
  private double sampledAt = Double.NEGATIVE_INFINITY;
  private final List<PortalLink> inputFrame = new ArrayList<>();

  CarryInterpolation(Vec3 position, Vec3 velocity) {
    this.position = position;
    this.velocity = velocity;
  }

  Vec3 position() {
    return position;
  }

  Vec3 velocity() {
    return velocity;
  }

  double sampledAt() {
    return sampledAt;
  }

  Vec3 inputPosition() {
    Vec3 point = position;
    for (var link : inputFrame.reversed()) point = link.transform().inversePoint(point);
    return point;
  }

  Vec3 worldVector(Vec3 vector) {
    for (var link : inputFrame) vector = link.transform().vector(vector);
    return vector;
  }

  @Nullable Vec3 offsetPosition(CarryCollisionWorld collisions, Vec3 offset) {
    if (offset.equals(Vec3.ZERO)) return position;
    Vec3 target = position.add(worldVector(offset));
    return CarryHoldPrediction.canCorrectWithinSpace(collisions, position, target) ? target : null;
  }

  void correct(Vec3 correctedPosition, Vec3 movement) {
    position = correctedPosition;
    velocity = velocity.add(worldVector(movement));
  }

  Vec3 inputVelocity() {
    return inputVector(velocity);
  }

  Vec3 inputVector(Vec3 movement) {
    for (var link : inputFrame.reversed()) movement = link.transform().inverse().vector(movement);
    return movement;
  }

  Quaternionf rotation() {
    var rotation = new Quaternionf();
    for (var link : inputFrame) rotation.premul(link.transform().rotation());
    return rotation;
  }

  private void crossed(PortalLink link) {
    if (!inputFrame.isEmpty() && PortalRoutes.sameIdentity(inputFrame.getLast(), link.inverse()))
      inputFrame.removeLast();
    else inputFrame.add(link);
  }

  void rebase(PortalLink link) {
    if (!inputFrame.isEmpty() && PortalRoutes.sameIdentity(inputFrame.getFirst(), link)) inputFrame.removeFirst();
    else inputFrame.addFirst(link.inverse());
  }

  Vec3 sample(CarryCollisionWorld collisions, Vec3 desired, Vec3 movement, double time) {
    if (time <= sampledAt) return position;
    for (var link : inputFrame) {
      desired = link.transform().point(desired);
      movement = link.transform().vector(movement);
    }
    var swept = collisions.sweep(position, desired);
    position = swept.path().end();
    velocity = swept.constrain(movement);
    for (var node : swept.path().nodes()) {
      var link = node.crossing();
      if (link == null) continue;
      crossed(link);
    }
    sampledAt = time;
    return position;
  }

  void sample(CarryHoldPrediction.Sample sample, double time) {
    position = sample.position();
    velocity = sample.velocity();
    inputFrame.clear();
    inputFrame.addAll(sample.crossings());
    sampledAt = time;
  }
}
