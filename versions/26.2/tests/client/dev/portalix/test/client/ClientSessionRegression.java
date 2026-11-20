package dev.portalix.test.client;

import static dev.portalix.test.client.ClientAssertions.check;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.portalix.carry.CarryBindings;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.carry.CarryOwnerState;
import dev.portalix.client.carry.ClientCarryController;
import dev.portalix.client.gun.animation.RemoteGunAnimations;
import dev.portalix.client.gun.sound.GunSoundPlayer;
import dev.portalix.client.portal.ClientPortalMovement;
import dev.portalix.client.portal.ClientPortalRegistry;
import dev.portalix.client.portal.camera.PortalCameraController;
import dev.portalix.client.portal.scene.PortalSceneManager;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixVariant;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.network.gun.GunCommandPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalViewLimits;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

public final class ClientSessionRegression {
  private enum Stage { START, DISCONNECTED, REJOIN, SERVER_READY, EQUIPMENT_SYNC, CLEAR_EQUIPMENT, RESTORED, DONE }
  private record GpuImage(GpuTextureView view, GpuTexture texture) {}

  private static final int STAGE_TIMEOUT_TICKS = 400;
  private static final List<String> GROUPS = List.of(
      "lifecycle/queued movement precedes the native KeepAlive response",
      "lifecycle/relative position packets end unconfirmed traversal even without displacement",
      "lifecycle/carry prediction survives owner and flight tracking changes",
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
        verifyQueuedKeepAliveOrder();
        verifyCarryTrackingChanges(client);
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
        verifyRelativePositionPackets(client, oldLinks.stream()
            .filter(PortalSceneManager::hasRenderedView).findFirst().orElseThrow());
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
          var stack = new ItemStack(ModItems.gun(PortalixVariant.STANDARD));
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

  private static void verifyRelativePositionPackets(Minecraft client, PortalLink link) {
    var player = client.player;
    var entrance = link.entrance().endpoint();
    Vec3 start = entrance.center().add(entrance.normal().scale(1.25)).subtract(0, 1, 0);
    Vec3 movement = entrance.normal().scale(-2.5);
    int radius = client.options.getEffectiveRenderDistance();
    int preview = PortalViewLimits.preview(radius);
    for (double height : new double[] {8, 0}) {
      player.snapTo(start, player.getYRot(), player.getXRot());
      player.setDeltaMovement(movement);
      player.move(MoverType.PLAYER, movement);
      check(ClientPortalMovement.awaitingCrossing(), "The client did not predict the fixture traversal");
      check(PortalSceneManager.terrainRadius() == preview && PortalSceneManager.fogRadius(radius) == preview,
          "The speculative traversal did not use the incoming portal's preview radius");
      Vec3 expected = link.transform().inversePoint(player.position()).add(0, height, 0);
      client.getConnection().handleMovePlayer(ClientboundPlayerPositionPacket.of(Integer.MAX_VALUE,
          new PositionMoveRotation(new Vec3(0, height, 0), Vec3.ZERO, 0, 0), Relative.ALL));
      check(!ClientPortalMovement.awaitingCrossing() && player.position().distanceToSqr(expected) < 1e-12,
          "A relative position packet did not return to the entrance frame, dy=" + height);
      check(PortalSceneManager.terrainRadius() == radius && PortalSceneManager.fogRadius(radius) == radius,
          "A relative position packet retained the portal preview horizon, dy=" + height);
    }
  }

  private static void verifyCarryTrackingChanges(Minecraft client) {
    var level = client.level;
    var carry = new ClientCarryController(client);
    var ownerState = new CarryOwnerState(client, carry::release);
    var sound = new GunSoundPlayer(client, ownerState::heldEntityId, ownerState::holdTicks);
    var animations = new RemoteGunAnimations(client, ownerState, sound);
    var profile = new GameProfile(UUID.randomUUID(), "PortalixCarryTest");
    int ownerId = Integer.MAX_VALUE - 2;
    int id = Integer.MAX_VALUE - 1;
    check(level.getEntity(ownerId) == null && level.getEntity(id) == null, "Carry fixture entity IDs are in use");
    var owner = carryOwner(level, profile);
    owner.setId(ownerId);
    Vec3 position = client.player.position();
    var item = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(Items.IRON_INGOT));
    item.setId(id);
    UUID uuid = item.getUUID();
    level.addEntity(owner);
    level.addEntity(item);
    var grab = new CarryGrabPayload(id, uuid, 1, ownerId, profile.id(), 10, position, Vec3.ZERO, List.of());
    try {
      animations.synchronize(ownerId, profile.id());
      animations.tick();
      carry.grab(grab);
      check(carry.owner(item) == owner && carry.predicted(item), "The remote hold fixture did not bind");

      level.removeEntity(ownerId, Entity.RemovalReason.DISCARDED);
      for (int tick = 11; tick <= 20; tick++) {
        carry.receive(new CarryStatePayload(id, uuid, 1, tick, position, Vec3.ZERO, false, false));
        carry.tick();
        animations.tick();
      }
      check(carry.owner(item) == null && carry.predicted(item),
          "Untracking the owner discarded the still-observed hold");
      var returnedOwner = carryOwner(level, profile);
      returnedOwner.setId(ownerId);
      level.addEntity(returnedOwner);
      animations.synchronize(ownerId, profile.id());
      carry.grab(new CarryGrabPayload(id, uuid, 1, ownerId, profile.id(), 20, position, Vec3.ZERO, List.of()));
      carry.release(new CarryReleasePayload(id, uuid, 1, position, Vec3.ZERO, false, 19));
      check(carry.owner(item) == returnedOwner && CarryBindings.holderId(item) == ownerId
              && carry.predicted(item),
          "Reacquiring the owner failed or an old release replaced the active hold");

      var release = new CarryReleasePayload(id, uuid, 1, position, new Vec3(.25, 0, 0), true, 21);
      carry.release(release);
      carry.grab(grab);
      check(carry.predicted(item) && carry.owner(item) == null && CarryBindings.holderId(item) < 0,
          "A released target retained a stale hold");
      level.removeEntity(id, Entity.RemovalReason.DISCARDED);
      carry.tick();

      var returnedItem = new ItemEntity(level, position.x, position.y, position.z, new ItemStack(Items.IRON_INGOT));
      returnedItem.setId(id);
      returnedItem.setUUID(uuid);
      level.addEntity(returnedItem);
      carry.receive(new CarryStatePayload(id, uuid, 1, 22, position, release.velocity(), true, false));
      check(carry.predicted(returnedItem), "A flight snapshot could not resume after target re-tracking");
      carry.receive(new CarryStatePayload(id, uuid, 1, 23, position, Vec3.ZERO, true, true));
      for (int tick = 0; tick < 20; tick++) carry.tick();
      carry.receive(new CarryStatePayload(id, uuid, 1, 22, position, release.velocity(), true, false));
      carry.release(release);
      carry.grab(grab);
      check(!carry.predicted(returnedItem) && CarryBindings.holderId(returnedItem) < 0,
          "An old hold or flight revived a completed carrying generation");
    } finally {
      CarryBindings.unbindOwner(level, ownerId);
      level.removeEntity(id, Entity.RemovalReason.DISCARDED);
      level.removeEntity(ownerId, Entity.RemovalReason.DISCARDED);
      sound.clear();
      carry.clear();
    }
  }

  private static RemotePlayer carryOwner(ClientLevel level, GameProfile profile) {
    var player = new RemotePlayer(level, profile) {
      @Override
      public GameType gameMode() { return GameType.CREATIVE; }
    };
    player.getAbilities().mayBuild = true;
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.gun(PortalixVariant.STANDARD)));
    return player;
  }

  private static void verifyQueuedKeepAliveOrder() {
    var connection = new Connection(PacketFlow.CLIENTBOUND);
    var channel = new EmbeddedChannel(connection);
    try {
      var queuedTick = ServerboundClientTickEndPacket.INSTANCE;
      channel.eventLoop().execute(() -> channel.writeAndFlush(queuedTick));
      var response = new ServerboundKeepAlivePacket(1);
      connection.send(response);
      channel.runPendingTasks();
      check(channel.readOutbound() == queuedTick && channel.readOutbound() == response,
          "KeepAlive overtook the queued movement tick");
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  public static List<String> passedGroups() {
    check(stage == Stage.DONE, "Lifecycle groups requested before completion");
    return GROUPS;
  }

  private static void assertClearedClientState(Minecraft client) {
    check(PortalixClient.portals().links().isEmpty() && PortalTopology.links(client.level).isEmpty(),
        "Client session inherited portal topology");
    check(client.player == null || CarryBindings.heldEntityId(client.level, client.player.getId()) == -1,
        "Client session inherited a held target");
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
