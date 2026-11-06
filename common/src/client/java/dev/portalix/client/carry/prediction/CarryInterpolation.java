package dev.portalix.client.carry.prediction;

import dev.portalix.carry.CarryCollisionWorld;
import dev.portalix.carry.CarryMotionPath;
import dev.portalix.portal.PortalLink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

final class CarryInterpolation {
  private Vec3 position;
  private Vec3 velocity;
  private double sampledAt = Double.NEGATIVE_INFINITY;
  private final List<PortalLink> inputFrame = new ArrayList<>();
  private CarryMotionPath sampledPath;
  private int sampledCrossings;

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

  Vec3 inputPosition() {
    Vec3 point = position;
    for (var link : inputFrame.reversed()) point = link.transform().inversePoint(point);
    return point;
  }

  Vec3 inputVelocity() {
    Vec3 movement = velocity;
    for (var link : inputFrame.reversed()) movement = link.transform().inverse().vector(movement);
    return movement;
  }

  Quaternionf rotation() {
    var rotation = new Quaternionf();
    for (var link : inputFrame) rotation.premul(link.transform().rotation());
    return rotation;
  }

  private void crossed(PortalLink link) {
    if (!inputFrame.isEmpty() && inputFrame.getLast().inverse().equals(link))
      inputFrame.removeLast();
    else inputFrame.add(link);
  }

  void rebase(PortalLink link) {
    if (!inputFrame.isEmpty() && inputFrame.getFirst().equals(link)) inputFrame.removeFirst();
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

  Vec3 samplePath(CarryMotionPath path, float partial, double startTick) {
    double time = startTick + partial;
    if (time <= sampledAt) return position;
    if (sampledPath != path) {
      sampledPath = path;
      sampledCrossings = 0;
    }
    var crossed = path.crossings(partial);
    for (int i = sampledCrossings; i < crossed.size(); i++) crossed(crossed.get(i));
    sampledCrossings = crossed.size();
    position = path.center(partial);
    velocity = path.movement(partial);
    sampledAt = time;
    return position;
  }
}
