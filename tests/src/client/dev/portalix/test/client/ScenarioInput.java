package dev.portalix.test.client;

import dev.portalix.test.client.mixin.MouseHandlerAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class ScenarioInput {
  private final Minecraft client;

  ScenarioInput(Minecraft client) { this.client = client; }

  boolean lookAt(Vec3 target) {
    Vec3 direction = target.subtract(client.player.getEyePosition());
    float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
    float yawDelta = Mth.wrapDegrees(yaw - client.player.getYRot());
    float pitchDelta = pitch - client.player.getXRot();
    double sensitivity = client.options.sensitivity().get() * .6F + .2F;
    double scale = sensitivity * sensitivity * sensitivity * 8 * .15;
    var mouse = (MouseHandlerAccessor) client.mouseHandler;
    mouse.portalix$horizontal(Math.clamp(yawDelta, -3, 3) / scale
        * (client.options.invertMouseX().get() ? -1 : 1));
    mouse.portalix$vertical(Math.clamp(pitchDelta, -3, 3) / scale
        * (client.options.invertMouseY().get() ? -1 : 1));
    try {
      mouse.portalix$turn(1.0 / 20);
    } finally {
      mouse.portalix$horizontal(0);
      mouse.portalix$vertical(0);
    }
    return Math.abs(yawDelta) < .2 && Math.abs(pitchDelta) < .2;
  }

  boolean walkTo(Vec3 target) {
    Vec3 offset = target.subtract(client.player.position());
    if (offset.horizontalDistance() < .22) {
      stop();
      return client.player.getDeltaMovement().horizontalDistance() < .03;
    }
    boolean aligned = lookAt(new Vec3(target.x, client.player.getEyeY(), target.z));
    client.options.keyUp.setDown(aligned);
    return false;
  }

  void forward(boolean moving) { client.options.keyUp.setDown(moving); }

  void click(KeyMapping key) { KeyMapping.click(key.getDefaultKey()); }

  void grab() { click(KeyMapping.get("key.portalix.grab")); }

  void resetPortals() { click(KeyMapping.get("key.portalix.reset")); }

  void slot(int index) { click(client.options.keyHotbarSlots[index]); }

  void stop() {
    client.options.keyUp.setDown(false);
    client.options.keyDown.setDown(false);
    client.options.keyLeft.setDown(false);
    client.options.keyRight.setDown(false);
    client.options.keyJump.setDown(false);
    client.options.keyShift.setDown(false);
    client.options.keySprint.setDown(false);
    client.options.keyAttack.setDown(false);
    client.options.keyUse.setDown(false);
  }
}
