package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.item.ModItems;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.traversal.EntityPortalState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class NativeProjectileRegression {
  private enum Kind {
    SNOWBALL(Items.SNOWBALL), ARROW(Items.BOW), TRIDENT(Items.TRIDENT);

    final Item weapon;
    Kind(Item weapon) { this.weapon = weapon; }
  }

  private NativeProjectileRegression() {}

  static void run(RegressionWorld world) {
    for (Direction direction : new Direction[] {Direction.SOUTH, Direction.EAST}) {
      for (Kind kind : Kind.values()) {
        for (boolean livingTarget : new boolean[] {false, true}) {
          scenario(world, kind, direction, livingTarget);
          world.reset();
        }
      }
    }
  }

  private static void scenario(RegressionWorld world, Kind kind, Direction direction, boolean livingTarget) {
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 104, 144));
    var exit = world.endpoint(direction, new Vec3(192, 104, 192));
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.clear(exit.center().add(exit.normal().scale(5)), 5);
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.publish(world.link(entrance, exit));
    PortalLink link = dev.portalix.portal.PortalTopology.links(world.level).getFirst();
    Vec3 eye = entrance.center().add(entrance.normal().scale(2));
    world.player.snapTo(eye.subtract(0, world.player.getEyeHeight(), 0), 0, 0);
    world.player.setDeltaMovement(Vec3.ZERO);
    world.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 16));
    world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind.weapon));
    var existing = new HashSet<UUID>();
    for (Entity entity : world.level.getAllEntities()) existing.add(entity.getUUID());
    String context = kind + " -> " + direction + (livingTarget ? " entity hit" : " block hit");
    try {
      ItemStack weapon = world.player.getMainHandItem();
      kind.weapon.use(world.level, world.player, InteractionHand.MAIN_HAND);
      if (kind != Kind.SNOWBALL)
        check(kind.weapon.releaseUsing(weapon, world.level, world.player,
            kind.weapon.getUseDuration(weapon, world.player) - 20), context + " native weapon release failed");
      world.player.stopUsingItem();
      var launched = new ArrayList<Projectile>();
      for (Entity entity : world.level.getAllEntities())
        if (entity instanceof Projectile projectile && !existing.contains(entity.getUUID())) launched.add(projectile);
      check(launched.size() == 1, context + " native item use did not spawn exactly one projectile: " + launched);
      Projectile projectile = launched.getFirst();
      check(projectile.getOwner() == world.player, context + " lost the native launch owner");
      check(projectile.getDeltaMovement().length() > 1, context + " was not launched by native weapon physics");
      Projectile control = create(kind, world);
      world.add(control);
      control.setNoGravity(false);
      control.setOwner(world.player);
      Vec3 controlStart = new Vec3(168, 108, 168);
      world.clear(controlStart, 8);
      control.setPos(controlStart);
      control.setDeltaMovement(projectile.getDeltaMovement());
      Vec3 start = projectile.position();
      for (int tick = 0; tick < 6 && EntityPortalState.of(projectile).lastCrossing() == null; tick++) {
        world.level.tickNonPassenger(control);
        world.level.tickNonPassenger(projectile);
      }
      var crossing = EntityPortalState.of(projectile).lastCrossing();
      check(crossing != null && crossing.link().equals(link), context + " native tick did not cross the portal");
      check(projectile.isAlive() && projectile.getOwner() == world.player, context + " was removed or changed owner at the seam");
      near(projectile.position(), link.transform().point(start.add(control.position().subtract(controlStart))),
          2e-5, context + " native movement lost the unconsumed portion of its tick");
      near(projectile.getDeltaMovement(), link.transform().vector(control.getDeltaMovement()),
          2e-5, context + " changed native gravity, drag or transformed momentum");
      check(projectile.getDeltaMovement().y < 0, context + " disabled native gravity");
      control.discard();
      if (livingTarget) entityHit(world, projectile, link, kind, context);
      else blockHit(world, projectile, link, kind, context);
    } finally {
      world.player.stopUsingItem();
      var spawned = new ArrayList<Entity>();
      for (Entity entity : world.level.getAllEntities())
        if (entity instanceof Projectile && !existing.contains(entity.getUUID())) spawned.add(entity);
      spawned.forEach(Entity::discard);
      world.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORTALIX.get()));
    }
  }

  private static Projectile create(Kind kind, RegressionWorld world) {
    Projectile result = switch (kind) {
      case SNOWBALL -> EntityTypes.SNOWBALL.create(world.level, EntitySpawnReason.COMMAND);
      case ARROW -> EntityTypes.ARROW.create(world.level, EntitySpawnReason.COMMAND);
      case TRIDENT -> EntityTypes.TRIDENT.create(world.level, EntitySpawnReason.COMMAND);
    };
    check(result != null, "Native projectile factory failed: " + kind);
    return result;
  }

  private static void entityHit(RegressionWorld world, Projectile projectile, PortalLink link, Kind kind, String context) {
    var target = EntityTypes.BLAZE.create(world.level, EntitySpawnReason.COMMAND);
    check(target != null, "Blaze factory failed");
    world.add(target);
    target.setNoAi(true);
    target.setPos(link.exit().endpoint().center().add(link.exit().endpoint().normal().scale(4)).subtract(0, .9, 0));
    float health = target.getHealth();
    for (int tick = 0; tick < 20 && projectile.isAlive() && target.getHealth() == health; tick++)
      world.level.tickNonPassenger(projectile);
    check(target.getHealth() < health, context + " failed to damage the actual exit-side entity");
    check(target.getLastHurtByMob() == world.player, context + " changed the damage-source owner");
    if (kind == Kind.SNOWBALL) check(projectile.isRemoved(), context + " did not run native snowball removal");
    else if (kind == Kind.TRIDENT)
      check(projectile.isAlive() && projectile.getDeltaMovement().length() < .5,
          context + " did not retain the native trident after-hit state");
  }

  private static void blockHit(RegressionWorld world, Projectile projectile, PortalLink link, Kind kind, String context) {
    var exit = link.exit().endpoint();
    BlockPos wall = BlockPos.containing(exit.center().add(exit.normal().scale(6)));
    for (BlockPos pos : BlockPos.betweenClosed(wall.offset(-1, -2, -1), wall.offset(1, 2, 1)))
      world.block(pos, Blocks.STONE.defaultBlockState());
    for (int tick = 0; tick < 20 && projectile.isAlive(); tick++) world.level.tickNonPassenger(projectile);
    if (kind == Kind.SNOWBALL) {
      check(projectile.isRemoved(), context + " did not disappear on the exit-side block");
    } else {
      check(projectile.isAlive() && projectile.position().distanceToSqr(Vec3.atCenterOf(wall)) < 9,
          context + " did not reach the exit-side wall");
      Vec3 embedded = projectile.position();
      world.level.tickNonPassenger(projectile);
      near(projectile.position(), embedded, 1e-10, context + " continued through the block after impact");
      check(projectile.getOwner() == world.player, context + " lost its owner after embedding");
    }
  }

}
