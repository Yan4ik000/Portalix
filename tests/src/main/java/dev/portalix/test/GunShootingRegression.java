package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.network.gun.GunCommand;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.server.PortalServerSession;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class GunShootingRegression implements ServerRegressionSuite.Scenario {
  private final RegressionWorld world;
  private final PortalServerSession session;
  private final boolean noGravity;
  private final UUID gun;
  private int started = -1;
  private int phase;

  GunShootingRegression(RegressionWorld world) {
    this.world = world;
    session = new PortalServerSession(world.level.getServer());
    noGravity = world.player.isNoGravity();
    var stack = new ItemStack(ModItems.PORTALIX.get());
    gun = PortalixIdentity.ensure(stack);
  }

  @Override
  public boolean tick() {
    int now = world.level.getServer().getTickCount();
    session.tick(world.level.getServer());
    if (started < 0) {
      world.player.setNoGravity(true);
      var stack = new ItemStack(ModItems.PORTALIX.get());
      stack.set(PortalixIdentity.ID.get(), gun);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, stack);
      world.clear(new Vec3(160, 104, 152), 8);
      for (int x = 151; x <= 167; x++)
        for (int y = 101; y <= 107; y++)
          world.block(new BlockPos(x, y, 160), Blocks.STONE.defaultBlockState());
      fire(GunCommand.FIRE_BLUE, 154.5);
      started = now;
      return false;
    }
    check(now - started < 100, "Portalix did not complete firing and reset");
    if (phase == 0) {
      if (now - started < 10) return false;
      fire(GunCommand.FIRE_ORANGE, 163.5);
      phase = 1;
      return false;
    }
    var links = PortalTopology.links(world.level).stream().filter(link -> link.gun().equals(gun)).toList();
    if (phase == 1) {
      if (links.isEmpty()) return false;
      check(links.size() == 2, "Two shots did not create a traversable portal pair");
      session.handle(world.player, GunCommand.RESET);
      phase = 2;
      return false;
    }
    if (phase == 2) {
      check(links.isEmpty(), "Reset left the portal pair active");
      fire(GunCommand.FIRE_BLUE, 154.5);
      started = now;
      phase = 3;
      return false;
    }
    if (phase == 3) {
      if (now - started < 10) return false;
      fire(GunCommand.FIRE_ORANGE, 163.5);
      phase = 4;
      return false;
    }
    if (phase == 4) {
      if (links.isEmpty()) return false;
      check(links.size() == 2, "The gun could not create a new pair after reset");
      var support = PortalSurface.placementFootprint(links.getFirst().entrance().endpoint()).blocks().getFirst();
      world.block(support, Blocks.AIR.defaultBlockState());
      phase = 5;
      return false;
    }
    check(links.isEmpty(), "Breaking portal support left a traversable pair");
    return true;
  }

  private void fire(GunCommand command, double x) {
    world.player.snapTo(x, 104 - world.player.getEyeHeight(), 152.5, 0, 0);
    world.player.setDeltaMovement(Vec3.ZERO);
    session.handle(world.player, command);
  }

  @Override
  public void close() {
    try {
      session.clear();
    } finally {
      world.player.setNoGravity(noGravity);
    }
  }
}
