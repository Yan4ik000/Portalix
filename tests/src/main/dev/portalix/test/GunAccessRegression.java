package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.portalix.carry.CarryBindings;
import dev.portalix.gun.GunFireTiming;
import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.traversal.PortalEntityMovement;
import dev.portalix.server.PortalServerEvents;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class GunAccessRegression implements ServerRegressionSuite.Scenario {
  private final RegressionWorld world;
  private final RegressionGameTest.Participant peer;
  private final UUID trustedGun;
  private final UUID deniedGun;
  private int nextTick;
  private int phase;
  private boolean trustedAdded;
  private boolean restricted;

  GunAccessRegression(RegressionWorld world) {
    this.world = world;
    peer = new RegressionGameTest.Participant(world.level, "PortalixDenied");
    try {
      peer.connect(world.configureConnection);
      peer.player.setGameMode(GameType.CREATIVE);
      peer.player.setNoGravity(true);
      check(!world.level.getServer().getPlayerList().isOp(peer.player.nameAndId())
              && !world.level.getServer().getPlayerList().isOp(world.player.nameAndId()),
          "Access fixture requires two non-operator players");
      GunShootingRegression.prepareWall(world);
      trustedGun = equipGun(world.player);
      deniedGun = equipGun(peer.player);
      addTrusted();
      command("mode restricted");
      restricted = true;

      aim(peer.player, 154.5);
      var target = world.item(peer.player.getEyePosition().add(0, 0, 1.25));
      PortalServerEvents.receiveGunCommand(peer.player, GunCommand.TOGGLE_GRAB);
      check(CarryBindings.holderId(target) == -1, "A denied player grabbed an entity");
      target.discard();
      fire(peer.player, GunCommand.FIRE_BLUE, 154.5);
      nextTick = tickCount() + GunFireTiming.ACTION_COOLDOWN_TICKS;
    } catch (RuntimeException | Error failure) {
      try { close(); }
      catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  @Override
  public boolean tick() {
    int now = tickCount();
    if (now < nextTick) return false;
    switch (phase++) {
      case 0 -> fire(peer.player, GunCommand.FIRE_ORANGE, 163.5);
      case 1 -> {
        check(links(deniedGun).isEmpty(), "A denied player created a portal pair");
        fire(world.player, GunCommand.FIRE_BLUE, 154.5);
        command("remove " + world.player.getUUID());
        trustedAdded = false;
        addTrusted();
      }
      case 2 -> fire(world.player, GunCommand.FIRE_ORANGE, 163.5);
      case 3 -> {
        check(links(trustedGun).isEmpty(), "Regranting access revived a revoked shot in flight");
        fire(world.player, GunCommand.FIRE_BLUE, 154.5);
      }
      case 4 -> {
        var pair = links(trustedGun);
        check(pair.size() == 2, "An allowlisted player could not create a portal pair");
        checkDeniedTraversal(pair.getFirst());
        return true;
      }
      default -> throw new AssertionError("Unexpected access test phase");
    }
    nextTick = now + GunFireTiming.ACTION_COOLDOWN_TICKS;
    return false;
  }

  private void checkDeniedTraversal(PortalLink link) {
    var denied = peer.player;
    var originalGun = denied.getMainHandItem();
    denied.setItemSlot(EquipmentSlot.MAINHAND, world.player.getMainHandItem().copy());
    try {
      PortalServerEvents.receiveGunCommand(denied, GunCommand.RESET);
      check(links(trustedGun).size() == 2, "A denied player reset an existing pair");
    } finally {
      denied.setItemSlot(EquipmentSlot.MAINHAND, originalGun);
    }

    PlayerPortalVisibility.of(denied).ready(link);
    var entry = link.entrance().endpoint();
    var start = entry.center().add(entry.normal().scale(2)).subtract(0, denied.getBbHeight() / 2, 0);
    var motion = entry.normal().scale(-4);
    denied.snapTo(start, 0, 0);
    denied.setDeltaMovement(motion);
    var movement = PortalEntityMovement.moveAuthoritatively(denied, MoverType.PLAYER, motion, motion);
    check(movement != null && movement.crossings().size() == 1
            && movement.crossings().getFirst().link().equals(link),
        "A player without gun access could not cross an allowlisted player's portal");
  }

  private static UUID equipGun(ServerPlayer player) {
    var stack = new ItemStack(ModItems.gun(PortalixVariant.STANDARD));
    player.setItemSlot(EquipmentSlot.MAINHAND, stack);
    return PortalixIdentity.ensure(stack);
  }

  private static void aim(ServerPlayer player, double x) {
    player.snapTo(x, 104 - player.getEyeHeight(), 152.5, 0, 0);
    player.setDeltaMovement(Vec3.ZERO);
  }

  private static void fire(ServerPlayer player, GunCommand shot, double x) {
    aim(player, x);
    PortalServerEvents.receiveGunCommand(player, shot);
  }

  private List<PortalLink> links(UUID gun) {
    return PortalTopology.links(world.level).stream().filter(link -> link.gun().equals(gun)).toList();
  }

  private int tickCount() {
    return world.level.getServer().getTickCount();
  }

  private void addTrusted() {
    command("add @a[name=" + world.player.getGameProfile().name() + "]");
    trustedAdded = true;
  }

  private void command(String command) {
    var server = world.level.getServer();
    try {
      check(server.getCommands().getDispatcher().execute(
          "portalix access " + command, server.createCommandSourceStack()) == 1,
          "Could not apply access fixture command " + command);
    } catch (CommandSyntaxException failure) {
      throw new AssertionError("Could not apply access fixture command " + command, failure);
    }
  }

  @Override
  public void close() {
    try {
      if (restricted) command("mode all");
      if (trustedAdded) command("remove " + world.player.getUUID());
      if (restricted) {
        PortalServerEvents.receiveGunCommand(world.player, GunCommand.RESET);
        PortalServerEvents.receiveGunCommand(peer.player, GunCommand.RESET);
      }
    } finally {
      peer.close();
    }
  }
}
