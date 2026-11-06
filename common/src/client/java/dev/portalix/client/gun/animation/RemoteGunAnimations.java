package dev.portalix.client.gun.animation;

import dev.portalix.client.carry.CarryOwnerState;
import dev.portalix.client.gun.sound.GunSoundPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class RemoteGunAnimations {
  private final Minecraft client;
  private final CarryOwnerState carryState;
  private final GunSoundPlayer sounds;
  private final Map<Integer, RemotePlayer> players = new HashMap<>();
  private final GunAnimator absentAnimation = new GunAnimator();

  public RemoteGunAnimations(Minecraft client, CarryOwnerState carryState, GunSoundPlayer sounds) {
    this.client = client;
    this.carryState = carryState;
    this.sounds = sounds;
  }

  public GunAnimator animation(int playerId) {
    RemotePlayer player = players.get(playerId);
    if (player == null) return absentAnimation;
    Entity entity = entity(playerId);
    return entity != null && !player.presence.expects(entity.getUUID())
        ? absentAnimation
        : player.animation;
  }

  public GunAnimator receiveEvent(int playerId) {
    Entity entity = entity(playerId);
    RemotePlayer player = players.get(playerId);
    if (player == null || entity != null && !player.presence.expects(entity.getUUID())) return null;
    player.presence.received();
    return player.animation;
  }

  public GunAnimator synchronize(int playerId, UUID owner) {
    RemotePlayer player = players.get(playerId);
    if (player == null || !player.presence.expects(owner)) {
      if (player != null) {
        carryState.clearOwner(playerId);
        sounds.onUnequipped(playerId);
      }
      player = new RemotePlayer(owner, () -> carryState.heldEntityId(playerId));
      players.put(playerId, player);
    }
    player.presence.received();
    return player.animation;
  }

  public boolean tick() {
    boolean needsStateSync = false;
    var iterator = players.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      int playerId = entry.getKey();
      RemotePlayer player = entry.getValue();
      Entity entity = entity(playerId);
      if (player.presence.expired(entity == null ? null : entity.getUUID())) {
        needsStateSync |= player.presence.pendingSpawn();
        carryState.clearOwner(playerId);
        sounds.onUnequipped(playerId);
        iterator.remove();
      } else {
        player.animation.tick();
        player.animation.setSprinting(
            entity != null && player.presence.expects(entity.getUUID()) && entity.isSprinting());
      }
    }
    return needsStateSync;
  }

  public void clear() {
    players.clear();
  }

  private Entity entity(int playerId) {
    return client.level == null ? null : client.level.getEntity(playerId);
  }

  private static final class RemotePlayer {
    final GunAnimator animation;
    final RemotePlayerPresence presence;

    RemotePlayer(UUID owner, IntSupplier target) {
      presence = new RemotePlayerPresence(owner);
      animation = new GunAnimator(target);
    }
  }
}
