package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.query.PlayerPortalVisibility;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class CombatRegression implements ServerRegressionSuite.Scenario {
  private enum Mode { NORMAL, SPRINT, SWEEP, STAB, SHIELD_FRONT, SHIELD_BACK }

  private record Hit(float damage, Vec3 velocity, float sweepDamage, Vec3 sweepVelocity) {}

  private final RegressionWorld world;
  private int modeIndex;
  private boolean throughPortal;
  private Zombie target;
  private Zombie nearby;
  private Hit local;
  private int preparedAt;

  CombatRegression(RegressionWorld world) {
    this.world = world;
  }

  @Override
  public boolean tick() {
    if (target == null) {
      prepare();
      return false;
    }
    // The embedded connection has no network event loop to advance its native player tick.
    world.player.doTick();
    var mode = Mode.values()[modeIndex];
    check(world.level.getServer().getTickCount() - preparedAt < 100,
        "Combatants did not become ready for " + mode);
    if (world.player.getAttackStrengthScale(0) < 1 || world.player.getItemSwapScale(0) < 1
        || !world.player.onGround() || shield(mode) && !target.isBlocking()) return false;

    float health = target.getHealth();
    float nearbyHealth = nearby.getHealth();
    world.player.setSprinting(mode == Mode.SPRINT);
    if (mode == Mode.STAB)
      world.player.stabAttack(EquipmentSlot.MAINHAND, target, 4, true, true, false);
    else world.player.attack(target);
    var hit = new Hit(health - target.getHealth(), target.getDeltaMovement(),
        nearbyHealth - nearby.getHealth(), nearby.getDeltaMovement());
    if (mode == Mode.SHIELD_FRONT) check(hit.damage() == 0, "A facing shield did not block the attack");
    else check(hit.damage() > 0, "The ready attacker did not damage its target: " + mode);
    if (mode == Mode.SWEEP) check(hit.sweepDamage() > 0, "The sword sweep missed the nearby target");

    if (throughPortal) {
      check(Math.abs(hit.damage() - local.damage()) < 1e-5, mode + " changed attack damage through a portal");
      near(hit.velocity(), local.velocity(), 1e-5, mode + " changed knockback through a portal");
      check(Math.abs(hit.sweepDamage() - local.sweepDamage()) < 1e-5, mode + " changed nearby sweep damage");
      near(hit.sweepVelocity(), local.sweepVelocity(), 1e-5, mode + " changed nearby sweep knockback");
      modeIndex++;
    } else local = hit;
    throughPortal = !throughPortal;
    world.reset();
    target = null;
    nearby = null;
    return modeIndex == Mode.values().length;
  }

  private void prepare() {
    var mode = Mode.values()[modeIndex];
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 106, 144));
    var exit = world.endpoint(Direction.EAST, new Vec3(192, 106, 192));
    var link = world.link(entrance, exit);
    Vec3 source = entrance.center().add(entrance.normal().scale(1.1)).subtract(0, 1.6, 0);
    Vec3 virtualSource = link.transform().point(source);
    Vec3 playerFeet;
    Vec3 targetFeet;
    Vec3 expectedSource;
    float yaw;
    if (throughPortal) {
      world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.publish(link);
      PlayerPortalVisibility.of(world.player).ready(link);
      playerFeet = source;
      targetFeet = exit.center().add(exit.normal().scale(1.2)).subtract(0, 1.6, 0);
      expectedSource = virtualSource;
      yaw = 0;
    } else {
      Vec3 center = new Vec3(168, 106, 168);
      world.clear(center, 5);
      PortalTopology.updateLinks(world.level, List.of());
      playerFeet = center.add(virtualSource.subtract(exit.center()));
      targetFeet = center.add(exit.normal().scale(1.2)).subtract(0, 1.6, 0);
      expectedSource = playerFeet;
      yaw = PortalLookAngles.transform(link.transform(), 0, 0).yaw();
    }
    floor(playerFeet);
    floor(targetFeet);
    var player = world.player;
    player.setGameMode(GameType.SURVIVAL);
    player.snapTo(playerFeet, yaw, 0);
    player.setYHeadRot(yaw);
    player.setYBodyRot(yaw);
    player.setDeltaMovement(Vec3.ZERO);
    player.setKnownMovement(Vec3.ZERO);
    player.fallDistance = 0;
    player.setSprinting(false);
    player.setItemSlot(EquipmentSlot.MAINHAND, switch (mode) {
      case SWEEP -> new ItemStack(Items.IRON_SWORD);
      case STAB -> new ItemStack(Items.IRON_SPEAR);
      default -> ItemStack.EMPTY;
    });
    target = zombie(targetFeet);
    nearby = zombie(targetFeet.add(exit.right().scale(.8)));
    if (shield(mode)) {
      float facing = (float) Math.toDegrees(Math.atan2(
          -(expectedSource.x - targetFeet.x), expectedSource.z - targetFeet.z));
      float shieldYaw = facing + (mode == Mode.SHIELD_BACK ? 180 : 0);
      target.setYRot(shieldYaw);
      target.setYHeadRot(shieldYaw);
      target.setYBodyRot(shieldYaw);
      target.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
      target.startUsingItem(InteractionHand.OFF_HAND);
    }
    preparedAt = world.level.getServer().getTickCount();
  }

  private static boolean shield(Mode mode) {
    return mode == Mode.SHIELD_FRONT || mode == Mode.SHIELD_BACK;
  }

  private void floor(Vec3 feet) {
    var center = BlockPos.containing(feet).below();
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        world.block(center.offset(x, 0, z), Blocks.STONE.defaultBlockState());
  }

  private Zombie zombie(Vec3 feet) {
    var entity = EntityTypes.HUSK.create(world.level, EntitySpawnReason.COMMAND);
    check(entity != null, "Could not create the combat target");
    entity.setPos(feet);
    entity.setNoAi(true);
    entity.setDeltaMovement(Vec3.ZERO);
    world.add(entity);
    entity.setNoGravity(false);
    return entity;
  }
}
