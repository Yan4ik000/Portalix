package dev.portalix.client.carry;

import dev.portalix.carry.CarryBindings;
import dev.portalix.network.gun.GunStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

public final class CarryOwnerState {
  private final Minecraft client;
  private final BiConsumer<Integer, Integer> release;
  private final Map<Integer, Long> holdStarted = new HashMap<>();

  public CarryOwnerState(Minecraft client, BiConsumer<Integer, Integer> release) {
    this.client = client;
    this.release = release;
  }

  public int heldEntityId(int owner) {
    return CarryBindings.heldEntityId(client.level, owner);
  }

  public int localTarget() {
    LocalPlayer player = client.player;
    return player == null ? -1 : heldEntityId(player.getId());
  }

  public int holdTicks(int owner) {
    Long started = holdStarted.get(owner);
    return started == null || client.level == null
        ? 0
        : Math.clamp(client.level.getGameTime() - started, 0, Integer.MAX_VALUE);
  }

  public void apply(GunStatePayload snapshot) {
    ClientLevel level = client.level;
    if (level != null) {
      if (heldEntityId(snapshot.playerId()) != snapshot.targetId()) {
        clearOwner(snapshot.playerId());
      }

      if (snapshot.targetId() >= 0) {
        holdStarted.put(snapshot.playerId(), level.getGameTime() - snapshot.holdTicks());
        CarryBindings.bind(
            level, snapshot.playerId(), snapshot.targetId(), snapshot.owner(), snapshot.target());
      } else holdStarted.remove(snapshot.playerId());
    }
  }

  public void clearOwner(int owner) {
    holdStarted.remove(owner);
    ClientLevel level = client.level;
    int target = heldEntityId(owner);
    if (target >= 0) {
      release.accept(owner, target);
    }

    CarryBindings.unbindOwner(level, owner);
  }
}
