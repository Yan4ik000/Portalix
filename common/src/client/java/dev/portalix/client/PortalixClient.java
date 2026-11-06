package dev.portalix.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.portalix.client.carry.CarryOwnerState;
import dev.portalix.client.compat.minecraft.MinecraftSoundOcclusion;
import dev.portalix.client.carry.ClientCarryController;
import dev.portalix.client.gun.GunInputHandler;
import dev.portalix.client.gun.animation.GunAnimator;
import dev.portalix.client.gun.render.GunRenderEvents;
import dev.portalix.client.particle.GunShotEffects;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.ClientPortalRegistry;
import dev.portalix.client.portal.PortalViewReadiness;
import dev.portalix.client.portal.audio.PortalAudioRouter;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.portal.PortalTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class PortalixClient {
  private static PortalClientSession current;

  public static ItemStack displayedGun() {
    return current.equipAnimation.displayed();
  }

  public static GunAnimator localAnimation() {
    return current.localAnimation;
  }

  public static ClientCarryController carryController() {
    return current.carryController;
  }

  public static GunShotEffects shotEffects() {
    return current.shots;
  }

  public static CarryOwnerState carryState() {
    return current.carryState;
  }

  public static GunInputHandler input() {
    return current.input;
  }

  public static ClientPortalRegistry portals() {
    return current.portals;
  }

  static PortalClientSession session() {
    return current;
  }

  static void replaceSession(ClientLevel level, LocalPlayer player) {
    RenderSystem.assertOnRenderThread();
    if (current != null) current.close();
    current = new PortalClientSession(level, player);
  }

  public static void ensureSession(Minecraft mc) {
    if (current == null || current.level != mc.level || current.player != mc.player) {
      replaceSession(mc.level, mc.player);
    }
  }

  public static GunAnimator animation(int playerId) {
    return current.animation(playerId);
  }

  public static void register() {
    ClientPlatform.INSTANCE.registerFeatures();
    GunRenderEvents.register();
    ModResourceReload.register();
    GunInputHandler.initialize();
  }

  public static void initialize() {
    PortalCameraController.setPortalSupplier(
        () -> PortalTopology.links(Minecraft.getInstance().level));
    ModResourceReload.load();
    Minecraft initialClient = Minecraft.getInstance();
    current = new PortalClientSession(initialClient.level, initialClient.player);
    PortalViewReadiness.register();
    PortalAudioRouter.configure(
        () -> PortalTopology.links(Minecraft.getInstance().level), MinecraftSoundOcclusion::transmission);
  }

  public static void tick(Minecraft client) {
    ensureSession(client);
    current.tick(client);
  }

  public static void disconnected(LocalPlayer player) {
    Minecraft.getInstance().execute(() -> {
      if (current != null && current.player == player) replaceSession(null, null);
    });
  }
}
