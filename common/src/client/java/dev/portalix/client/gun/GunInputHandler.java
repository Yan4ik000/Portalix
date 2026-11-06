package dev.portalix.client.gun;

import dev.portalix.PortalixMod;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.carry.CarryOwnerState;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.gun.GunCommandPayload;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class GunInputHandler {
  private static KeyMapping grabKey;
  private static KeyMapping resetKey;

  private boolean waitForFireRelease;
  private int nextActionTick;
  private int nextGrabTick;
  private int nextRepeatTick;
  private final CarryOwnerState carry;

  public GunInputHandler(CarryOwnerState carry) {
    this.carry = carry;
  }

  public static void initialize() {
    Category category = ClientPlatform.INSTANCE.registerKeyCategory(PortalixMod.id("controls"));
    grabKey = new KeyMapping(
        "key.portalix.grab", com.mojang.blaze3d.platform.InputConstants.KEY_F, category);
    resetKey = new KeyMapping(
        "key.portalix.reset", com.mojang.blaze3d.platform.InputConstants.KEY_R, category);
    ClientPlatform.INSTANCE.registerKeyMapping(grabKey);
    ClientPlatform.INSTANCE.registerKeyMapping(resetKey);
  }

  public void clear() {
    this.waitForFireRelease = false;
    this.nextActionTick = 0;
    this.nextGrabTick = 0;
    this.nextRepeatTick = 0;
  }

  public void firePressed(GunCommand command) {
    if (!this.waitForFireRelease) send(command);
  }

  public void tick(Minecraft mc, boolean equipped) {
    if (equipped && mc.gui.screen() == null && !mc.isPaused()) {
      while (grabKey.consumeClick()) {
        send(GunCommand.TOGGLE_GRAB);
      }

      while (resetKey.consumeClick()) {
        send(GunCommand.RESET);
      }

      if (!this.waitForFireRelease && mc.player.tickCount >= this.nextRepeatTick) {
        if (mc.options.keyAttack.isDown()) {
          send(GunCommand.FIRE_BLUE);
        } else if (mc.options.keyUse.isDown()) {
          send(GunCommand.FIRE_ORANGE);
        }
      }

      if (!mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown()) {
        this.nextRepeatTick = 0;
        this.waitForFireRelease = false;
      }
    } else {
      while (grabKey.consumeClick()) {}

      while (resetKey.consumeClick()) {}
    }
  }

  private void send(GunCommand command) {
    if (command == null || command == GunCommand.SYNC_STATE) return;
    Minecraft client = Minecraft.getInstance();
    if (!GunPermissions.canUse(client.player)
        || client.gui.screen() != null
        || client.isPaused()
        || !ClientNetworking.canServerReceive(GunCommandPayload.TYPE)) return;
    var animation = PortalixClient.localAnimation();
    if (animation.isHolstering() || animation.isDrawing()) return;

    int tick = client.player.tickCount;
    int nextAllowedTick = command == GunCommand.TOGGLE_GRAB ? nextGrabTick : nextActionTick;
    if (command != GunCommand.RELEASE && command != GunCommand.RESET && tick < nextAllowedTick)
      return;
    if (command.firing() && carry.localTarget() >= 0) {
      if (waitForFireRelease) return;
      waitForFireRelease = true;
    }

    if (command == GunCommand.TOGGLE_GRAB) {
      nextGrabTick = tick + GunFireTiming.ACTION_COOLDOWN_TICKS;
    } else if (command.firing()) {
      nextActionTick = tick + GunFireTiming.ACTION_COOLDOWN_TICKS;
      nextRepeatTick = tick + GunFireTiming.CLIENT_REPEAT_TICKS;
    }
    ClientNetworking.sendToServer(new GunCommandPayload(command));
  }
}
