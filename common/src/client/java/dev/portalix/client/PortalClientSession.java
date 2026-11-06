package dev.portalix.client;

import dev.portalix.carry.CarryBindings;
import dev.portalix.client.carry.CarryOwnerState;
import dev.portalix.client.carry.ClientCarryController;
import dev.portalix.client.gun.GunInputHandler;
import dev.portalix.client.gun.animation.GunAnimationClip;
import dev.portalix.client.gun.animation.GunAnimator;
import dev.portalix.client.gun.animation.GunEquipAnimation;
import dev.portalix.client.gun.animation.RemoteGunAnimations;
import dev.portalix.client.gun.sound.GunSoundPlayer;
import dev.portalix.client.particle.GunShotEffects;
import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.client.portal.ClientPortalRegistry;
import dev.portalix.client.portal.PortalViewReadiness;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.network.gun.GunCommandPayload;
import dev.portalix.network.gun.GunEventPayload;
import dev.portalix.network.gun.GunStatePayload;
import dev.portalix.portal.PortalTopology;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

final class PortalClientSession implements AutoCloseable {
  final ClientLevel level;
  final LocalPlayer player;
  final ClientCarryController carryController;
  final CarryOwnerState carryState;
  final GunAnimator localAnimation;
  final GunShotEffects shots;
  final GunSoundPlayer sounds;
  final ClientPortalRegistry portals = new ClientPortalRegistry();
  final GunInputHandler input;
  final GunEquipAnimation equipAnimation;
  private final RemoteGunAnimations remoteAnimations;
  private boolean needsStateSync = true;

  PortalClientSession(ClientLevel level, LocalPlayer player) {
    this.level = level;
    this.player = player;
    Minecraft client = Minecraft.getInstance();
    carryController = new ClientCarryController(client);
    carryState = new CarryOwnerState(client, carryController::release);
    localAnimation = new GunAnimator(carryState::localTarget);
    sounds = new GunSoundPlayer(client, carryState::heldEntityId, carryState::holdTicks);
    shots = new GunShotEffects(client, sounds::playInvalidSurface);
    input = new GunInputHandler(carryState);
    equipAnimation = new GunEquipAnimation(localAnimation);
    remoteAnimations = new RemoteGunAnimations(client, carryState, sounds);
  }

  GunAnimator animation(int id) {
    return isLocalPlayer(id) ? localAnimation : remoteAnimations.animation(id);
  }

  void receiveEvent(GunEventPayload event) {
    GunAnimator animation =
        isLocalPlayer(event.playerId())
            ? localAnimation
            : remoteAnimations.receiveEvent(event.playerId());
    if (animation != null) {
      animation.onGunEvent(event.kind());
      sounds.onGunEvent(event.playerId(), event.kind());
    } else {
      requestStateSync();
    }
  }

  void applySnapshot(GunStatePayload snapshot) {
    Entity entity = level == null ? null : level.getEntity(snapshot.playerId());
    if (entity != null && !entity.getUUID().equals(snapshot.owner())) {
      requestStateSync();
      return;
    }

    GunAnimator animation =
        isLocalPlayer(snapshot.playerId())
            ? localAnimation
            : remoteAnimations.synchronize(snapshot.playerId(), snapshot.owner());
    carryState.apply(snapshot);
    animation.synchronizeHold(snapshot.holdTicks());
    animation.synchronizePortals(snapshot.activeMask(), snapshot.orange());
    sounds.synchronizeHold(snapshot.playerId());
  }

  private boolean isLocalPlayer(int playerId) {
    return player != null && player.getId() == playerId;
  }

  void requestStateSync() {
    needsStateSync = true;
  }

  void tick(Minecraft mc) {
    if (this.needsStateSync
        && mc.player != null
        && ClientNetworking.canServerReceive(GunCommandPayload.TYPE)) {
      ClientNetworking.sendToServer(new GunCommandPayload(GunCommand.SYNC_STATE));
      this.needsStateSync = false;
    }

    if (!mc.isPaused()) {
      this.localAnimation.tick();
      this.shots.tick();
      this.portals.tick();
      PortalViewReadiness.tick(mc);
      this.carryController.tick();
      this.sounds.tick();
      if (remoteAnimations.tick()) requestStateSync();
    }

    boolean holding = GunPermissions.canUse(mc.player);
    var transition =
        this.equipAnimation.tick(
            holding ? mc.player.getMainHandItem() : ItemStack.EMPTY,
            mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot());
    if (transition == GunAnimationClip.DRAW) this.sounds.onEquipped();
    if (transition == GunAnimationClip.HOLSTER) {
      if (mc.player != null) {
        this.sounds.onUnequipped(mc.player.getId());
        this.carryController.release(mc.player.getId(), this.carryState.localTarget());
      }
      this.input.clear();
    }

    if (holding) {
      this.localAnimation.setSprinting(mc.player.isSprinting());
    }

    this.input.tick(mc, holding && !localAnimation.isHolstering() && !localAnimation.isDrawing());
  }

  @Override
  public void close() {
    this.sounds.clear();
    this.carryController.clear();
    this.shots.clear();
    this.portals.clear();
    PortalViewReadiness.clear();
    ClientPortalMovement.clear();
    PortalAudioRouter.reset();
    remoteAnimations.clear();
    if (this.level != null) {
      CarryBindings.clear(this.level);
      PortalTopology.updateLinks(this.level, List.of());
    }
    PortalCameraController.reset();
    PortalSceneManager.clear();
  }
}
