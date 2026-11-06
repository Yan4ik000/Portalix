package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.carry.CarryBindings;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.carry.ClientCarryController;
import dev.portalix.client.portal.ClientPortalRegistry;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.gun.GunCommandPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class ClientSessionRegression {
  private enum Stage { START, DISCONNECTED, REJOIN, SERVER_READY, EQUIPMENT_SYNC, CLEAR_EQUIPMENT, RESTORED, DONE }
  private record GpuImage(GpuTextureView view, GpuTexture texture) {}

  private static final int STAGE_TIMEOUT_TICKS = 400;
  private static final List<String> GROUPS = List.of(
      "lifecycle/normal disconnect shuts down the old integrated server and releases portal GPU views",
      "lifecycle/rejoin in the same JVM creates clean client and server portal/carry sessions",
      "lifecycle/gun component registration and equipment synchronization survive world replacement");
  private static Stage stage = Stage.START;
  private static int stageTicks;
  private static ClientLevel oldLevel;
  private static LocalPlayer oldPlayer;
  private static IntegratedServer oldServer;
  private static ClientPortalRegistry oldRegistry;
  private static ClientCarryController oldCarry;
  private static List<PortalLink> oldLinks;
  private static List<GpuImage> oldGpuImages;
  private static CompletableFuture<UUID> equipment;
  private static CompletableFuture<Void> cleared;
  private static UUID gun;

  private ClientSessionRegression() {}

  public static boolean tick(Minecraft client) {
    if (stage == Stage.DONE) return true;
    check(++stageTicks <= STAGE_TIMEOUT_TICKS,
        "Client lifecycle regression timed out during " + stage);
    switch (stage) {
      case START -> {
        check(client.level != null && client.player != null && client.getSingleplayerServer() != null,
            "Lifecycle regression requires the first connected world");
        oldLevel = client.level;
        oldPlayer = client.player;
        oldServer = client.getSingleplayerServer();
        oldRegistry = PortalixClient.portals();
        oldCarry = PortalixClient.carryController();
        oldLinks = List.copyOf(oldRegistry.links());
        check(!oldLinks.isEmpty() && oldLinks.stream().anyMatch(PortalSceneManager::hasRenderedView),
            "Lifecycle regression must begin with the captured portal GPU scene still alive");
        var images = new ArrayList<GpuImage>();
        for (var link : oldLinks) {
          var image = PortalSceneManager.portalImage(link.entrance().endpoint());
          if (image == null) continue;
          var view = image.texture();
          var texture = view.texture();
          check(!view.isClosed() && !texture.isClosed(), "The live portal image already owns a closed GPU resource");
          if (images.stream().noneMatch(saved -> saved.view == view && saved.texture == texture))
            images.add(new GpuImage(view, texture));
        }
        check(!images.isEmpty(), "Lifecycle regression requires an actual live portal GPU image before disconnect");
        oldGpuImages = List.copyOf(images);
        transition(Stage.DISCONNECTED);
        client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
      }
      case DISCONNECTED -> {
        if (client.level != null || client.player != null || client.getSingleplayerServer() != null
            || !oldServer.isShutdown() || !(client.gui.screen() instanceof TitleScreen)
            || client.gui.overlay() != null || PortalixClient.portals() == oldRegistry)
          return false;
        check(PortalTopology.links(oldLevel).isEmpty(), "Disconnected world retained its portal topology");
        for (var link : oldLinks) {
          check(oldRegistry.activeMask(link.gun()) == 0, "Closed client session retained portal snapshots");
        }
        check(CarryBindings.heldEntityId(oldLevel, oldPlayer.getId()) == -1,
            "Disconnected world retained the local carry binding");
        assertClearedClientState(client);
        check(PortalCameraController.currentView() == null,
            "Camera view survived the complete disconnect to the title screen");
        for (var image : oldGpuImages) {
          check(image.view.isClosed(), "Disconnected portal retained its native GPU texture view");
          check(image.texture.isClosed(), "Disconnected portal retained its native GPU texture allocation");
        }
        String world = "portalix-regression-" + UUID.randomUUID();
        var settings = new LevelSettings(world, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, true), true,
            WorldDataConfiguration.DEFAULT);
        transition(Stage.REJOIN);
        client.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(1, false, false),
            lookup -> lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                .value().createWorldDimensions(), new TitleScreen());
      }
      case REJOIN -> {
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null
            || client.gui.screen() != null || client.gui.overlay() != null)
          return false;
        check(client.level != oldLevel && client.player != oldPlayer
            && client.getSingleplayerServer() != oldServer,
            "World rejoin reused a previous world, player or server instance");
        check(PortalixClient.portals() != oldRegistry && PortalixClient.carryController() != oldCarry,
            "World rejoin reused state owned by the old client session");
        assertClearedClientState(client);
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        equipment = server.submit(() -> {
          var player = server.getPlayerList().getPlayer(playerId);
          check(player != null, "Rejoined integrated server has no player");
          var level = player.level();
          level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
          level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
          level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
          check(PortalTopology.links(level).isEmpty(), "New server inherited the old portal registry");
          check(CarryBindings.heldEntityId(level, player.getId()) == -1,
              "New server inherited the old carry binding");
          check(player.getMainHandItem().isEmpty(), "Fresh world inherited the old equipment");
          var stack = new ItemStack(ModItems.PORTALIX.get());
          UUID identity = PortalixIdentity.ensure(stack);
          player.setItemSlot(EquipmentSlot.MAINHAND, stack);
          return identity;
        });
        transition(Stage.SERVER_READY);
      }
      case SERVER_READY -> {
        if (!equipment.isDone()) return false;
        gun = equipment.join();
        transition(Stage.EQUIPMENT_SYNC);
      }
      case EQUIPMENT_SYNC -> {
        if (!gun.equals(PortalixIdentity.get(client.player.getMainHandItem()))
            || !gun.equals(PortalixIdentity.get(PortalixClient.displayedGun())))
          return false;
        check(ClientNetworking.canServerReceive(GunCommandPayload.TYPE),
            "Gun command registration did not survive the rejoin");
        assertClearedClientState(client);
        var server = client.getSingleplayerServer();
        UUID playerId = client.player.getUUID();
        cleared = server.submit(() -> {
          var player = server.getPlayerList().getPlayer(playerId);
          check(player != null && gun.equals(PortalixIdentity.get(player.getMainHandItem())),
              "Rejoined server lost the synchronized gun identity");
          check(PortalTopology.links(player.level()).isEmpty(), "Rejoined server gained old portal links");
          player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        });
        transition(Stage.CLEAR_EQUIPMENT);
      }
      case CLEAR_EQUIPMENT -> {
        if (!cleared.isDone()) return false;
        cleared.join();
        transition(Stage.RESTORED);
      }
      case RESTORED -> {
        if (!client.player.getMainHandItem().isEmpty() || !PortalixClient.displayedGun().isEmpty())
          return false;
        check(PortalixClient.portals().activeMask(gun) == 0, "Fresh equipment created an inherited portal pair");
        assertClearedClientState(client);
        transition(Stage.DONE);
        return true;
      }
      default -> throw new AssertionError("Unexpected client lifecycle stage " + stage);
    }
    return false;
  }

  public static List<String> passedGroups() {
    check(stage == Stage.DONE, "Lifecycle groups requested before completion");
    return GROUPS;
  }

  private static void assertClearedClientState(Minecraft client) {
    check(PortalixClient.portals().links().isEmpty() && PortalTopology.links(client.level).isEmpty(),
        "Client session inherited portal topology");
    check(PortalixClient.carryState().localTarget() == -1, "Client session inherited a held target");
    check(!PortalCameraController.active(), "Client session retained a portal camera correction");
    var view = PortalCameraController.currentView();
    check(view == null || view.eyeLink() == null && view.spacePath().isEmpty(),
        "Client camera still refers to a previous portal space");
    check(PortalSceneManager.terrainWorkCameras().isEmpty(), "Previous portal terrain work cameras remain active");
    for (var link : oldLinks) {
      check(PortalixClient.portals().activeMask(link.gun()) == 0,
          "Client session inherited the previous gun's portal snapshots");
      check(!PortalSceneManager.hasRenderedView(link)
          && PortalSceneManager.portalImage(link.entrance().endpoint()) == null,
          "Client session retained an image from the previous GPU scene");
    }
  }

  private static void transition(Stage next) {
    stage = next;
    stageTicks = 0;
    System.out.println("PORTALIX CLIENT SESSION REGRESSION: " + next);
  }
}
