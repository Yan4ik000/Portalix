package dev.portalix.client.gun.sound;

import dev.portalix.gun.GunPermissions;
import dev.portalix.network.gun.GunEventKind;
import dev.portalix.sound.GunSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class GunSoundPlayer {
  private static final int INITIAL_ACTIVATION_TICK = -100;
  private static final int EQUIP_DEBOUNCE_TICKS = 10;

  private final Map<Integer, CarryLoopSound> holds = new HashMap<>();
  private final List<CarryLoopSound> fading = new ArrayList<>();
  private final SoundVoiceLimiter responses;
  private SoundInstance activation;
  private int lastActivation = INITIAL_ACTIVATION_TICK;
  private final Minecraft client;
  private final IntUnaryOperator target;
  private final IntUnaryOperator holdTicks;

  public GunSoundPlayer(Minecraft client, IntUnaryOperator target, IntUnaryOperator holdTicks) {
    this.client = client;
    this.target = target;
    this.holdTicks = holdTicks;
    this.responses = new SoundVoiceLimiter(client);
  }

  public void onGunEvent(int player, GunEventKind kind) {
    if (client.level == null || !(client.level.getEntity(player) instanceof Player owner)) return;
    switch (kind) {
      case FIRE_BLUE -> play(player, GunSounds.FIRE_BLUE);
      case FIRE_ORANGE -> play(player, GunSounds.FIRE_ORANGE);
      case GRAB_FAILED -> play(player, GunSounds.GRAB_FAILED);
      case GRABBED -> {
        stopHoldImmediate(player);
        if (!GunPermissions.canCarry(owner) || target.applyAsInt(player) < 0) return;
        CarryLoopSound loop = new CarryLoopSound(client, owner);
        holds.put(player, loop);
        client.getSoundManager().play(loop);
      }
      case THROWN, RELEASED -> {
        stopHold(player);
        play(player, GunSounds.RELEASE);
      }
      case RESET -> {
        if (holds.containsKey(player)) play(player, GunSounds.RELEASE);
        stopHold(player);
        play(player, GunSounds.FIZZLE);
      }
      default -> {}
    }
  }

  private SoundInstance play(int player, GunSounds sound) {
    Entity entity = client.level == null ? null : client.level.getEntity(player);
    return entity == null ? null : responses.play(sound, entity, entity.getEyePosition());
  }

  public void playInvalidSurface(Vec3 position) {
    if (responses.play(GunSounds.INVALID_SURFACE, null, position) != null)
      responses.play(GunSounds.INVALID, null, position);
  }

  public void onEquipped() {
    Minecraft mc = this.client;
    if (mc.player != null && mc.player.tickCount - this.lastActivation >= EQUIP_DEBOUNCE_TICKS) {
      this.lastActivation = mc.player.tickCount;
      if (this.activation != null) {
        mc.getSoundManager().stop(this.activation);
      }

      this.activation = this.play(mc.player.getId(), GunSounds.DRAW);
    }
  }

  public void onUnequipped(int player) {
    if (this.holds.containsKey(player)) this.play(player, GunSounds.RELEASE);
    this.stopHold(player);
    Minecraft mc = this.client;
    responses.stopEmitter(player);

    if (mc.player != null && player == mc.player.getId() && this.activation != null) {
      mc.getSoundManager().stop(this.activation);
      this.activation = null;
    }
  }

  private void stopHold(int player) {
    CarryLoopSound loop = this.holds.remove(player);
    if (loop != null) {
      loop.finish();
      this.fading.add(loop);
    }
  }

  private void stopHoldImmediate(int player) {
    CarryLoopSound loop = this.holds.remove(player);
    if (loop != null) {
      loop.abort();
      this.client.getSoundManager().stop(loop);
    }
    this.fading.removeIf(
        sound -> {
          if (sound.owner().getId() == player) {
            sound.abort();
            this.client.getSoundManager().stop(sound);
            return true;
          }
          return false;
        });
  }

  public void clear() {
    responses.clear();
    for (CarryLoopSound loop : holds.values()) {
      loop.abort();
      client.getSoundManager().stop(loop);
    }
    holds.clear();
    for (CarryLoopSound loop : fading) {
      loop.abort();
      client.getSoundManager().stop(loop);
    }
    fading.clear();
    if (activation != null) client.getSoundManager().stop(activation);
    activation = null;
    lastActivation = INITIAL_ACTIVATION_TICK;
  }

  public void synchronizeHold(int player) {
    Entity entity = client.level == null ? null : client.level.getEntity(player);
    CarryLoopSound old = holds.get(player);
    if (!(entity instanceof Player owner)
        || !GunPermissions.canCarry(owner)
        || target.applyAsInt(player) < 0) {
      stopHold(player);
      return;
    }
    if (old != null && old.owner() != owner) {
      stopHoldImmediate(player);
      old = null;
    }
    if (old == null || old.isStopped() || !client.getSoundManager().isActive(old)) {
      if (old != null) {
        old.abort();
        client.getSoundManager().stop(old);
      }
      CarryLoopSound loop = new CarryLoopSound(client, owner, true, holdTicks.applyAsInt(player));
      holds.put(player, loop);
      client.getSoundManager().play(loop);
    }
  }

  public void tick() {
    responses.tick();
    fading.removeIf(sound -> sound.isStopped() || !client.getSoundManager().isActive(sound));
    var iterator = holds.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      CarryLoopSound sound = entry.getValue();
      Player owner = sound.owner();
      if (client.level == null || client.level.getEntity(owner.getId()) != owner) {
        sound.abort();
        client.getSoundManager().stop(sound);
        iterator.remove();
      } else if (!GunPermissions.canCarry(owner) || target.applyAsInt(owner.getId()) < 0) {
        sound.finish();
        fading.add(sound);
        iterator.remove();
      } else if (sound.readyForLoop()) {
        sound.finish(2);
        fading.add(sound);
        CarryLoopSound loop = new CarryLoopSound(client, owner, true, sound.age);
        entry.setValue(loop);
        client.getSoundManager().play(loop);
      }
    }
    if (client.level != null) {
      for (AbstractClientPlayer player : client.level.players()) {
        if (target.applyAsInt(player.getId()) >= 0) synchronizeHold(player.getId());
      }
    }
  }
}
