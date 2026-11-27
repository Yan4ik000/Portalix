package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;
import static dev.portalix.test.ServerRegressionSuite.near;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.camera.PortalLookAngles;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.portal.query.PortalInteractionPath;
import dev.portalix.portal.query.PortalRaycast;
import dev.portalix.server.portal.PortalClosureResolver;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class CombatRegression implements ServerRegressionSuite.Scenario {
  private enum Mode { NORMAL, SPRINT, SWEEP, STAB, SHIELD_FRONT, SHIELD_BACK, OCCLUDED_SHORTCUT }

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
    interactionStopsAtThirdEntrance();
    weaponMarginRespectsCover();
    staleLookCanStillReachTheExposedPartOfATarget();
  }

  private void staleLookCanStillReachTheExposedPartOfATarget() {
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 106, 144));
    var exit = world.endpoint(Direction.SOUTH, new Vec3(192, 106, 192));
    var link = world.link(entrance, exit);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.publish(link);
    var player = world.player;
    PlayerPortalVisibility.of(player).ready(link);
    float pitch = -(float) Math.toDegrees(Math.atan(.301 / 3));
    player.snapTo(entrance.center().add(0, -.7 - player.getEyeHeight(), -1), 0, pitch);
    AABB target = new AABB(exit.center().add(-.3, -.95, 2), exit.center().add(.3, -.398, 2.6));
    var cover = BlockPos.containing(exit.center().add(0, -.7, 1.5));
    world.block(cover, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
    ItemStack weapon = new ItemStack(Items.IRON_SWORD);
    weapon.set(DataComponents.ATTACK_RANGE, new AttackRange(0, 4, 0, 4, 0, 1));
    check(player.isWithinAttackRange(weapon, target, 0),
        "The aimed attack did not see the target above the slab");
    player.setYRot(90);
    check(player.isWithinAttackRange(weapon, target, 0),
        "A stale server look lost the narrow exposed part of the hitbox");
    weapon.set(DataComponents.ATTACK_RANGE, new AttackRange(0, 3, 0, 3, 0, 1));
    check(!player.isWithinAttackRange(weapon, target, 0),
        "An exposed hitbox bypassed the weapon's reach");

    weapon.set(DataComponents.ATTACK_RANGE, new AttackRange(0, 4, 0, 4, 0, 1));
    world.block(cover, Blocks.STONE.defaultBlockState());
    world.block(cover.above(), Blocks.STONE.defaultBlockState());
    check(!player.isWithinAttackRange(weapon, target, 0),
        "A stale server look accepted an attack through full cover");
    world.reset();
  }

  private void weaponMarginRespectsCover() {
    var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 106, 144));
    var exit = world.endpoint(Direction.SOUTH, new Vec3(192, 106, 192));
    var link = world.link(entrance, exit);
    world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
    world.publish(link);
    var player = world.player;
    PlayerPortalVisibility.of(player).ready(link);
    player.snapTo(entrance.center().add(0, .5 - player.getEyeHeight(), -1), 0, 0);
    var pane = BlockPos.containing(exit.center().add(0, .5, 1.5));
    var target = AABB.ofSize(Vec3.atCenterOf(pane).add(.15, 0, .30), .12, .4, .12);
    var weapon = new ItemStack(Items.IRON_SPEAR);
    weapon.set(DataComponents.ATTACK_RANGE, new AttackRange(0, 4, 0, 4, .5F, 1));
    check(PortalInteractionPath.attack(player, weapon, target, 0)
        .filter(route -> route.links().equals(List.of(link))).isPresent(),
        "A visible target within the weapon margin was missed");
    world.block(pane, Blocks.GLASS_PANE.defaultBlockState()
        .setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
    check(PortalInteractionPath.attack(player, weapon, target, 0).isEmpty(),
        "Weapon margin reached a target behind a glass pane through a portal");
    world.reset();
  }

  private void interactionStopsAtThirdEntrance() {
    var links = new ArrayList<PortalLink>();
    var directed = new ArrayList<PortalLink>();
    for (int i = 0; i < 3; i++) {
      var entrance = world.endpoint(Direction.NORTH, new Vec3(144, 106, 144 + i * 16));
      var exit = world.endpoint(Direction.SOUTH, new Vec3(143, 106, 158 + i * 16));
      var link = world.link(entrance, exit);
      links.add(link);
      directed.add(link);
      directed.add(link.inverse());
      world.clear(entrance.center(), 2);
      world.clear(exit.center(), 2);
    }
    for (var link : directed) {
      for (var pos : PortalSurface.placementFootprint(link.entrance().endpoint()).blocks())
        world.block(pos, Blocks.STONE.defaultBlockState());
    }
    PortalClosureResolver.updateLinks(world.level, directed);
    var player = world.player;
    for (var link : links) PlayerPortalVisibility.of(player).ready(link);
    Vec3 start = links.getFirst().entrance().endpoint().center().add(0, 0, -1);
    Vec3 motion = new Vec3(0, 0, 8);
    var selection = PortalRaycast.castInteraction(player, start, motion);
    check(selection.links().equals(links.subList(0, 2)) && selection.hit().getType() == HitResult.Type.BLOCK,
        "Interaction continued through the third portal opening");
    var unrestricted = PortalRaycast.cast(player, start, motion, (from, to) -> world.level.clipIncludingBorder(
        new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)));
    check(unrestricted.links().equals(links), "The third portal in the interaction test was unreachable");
    world.reset();
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
    boolean occludedShortcut = mode == Mode.OCCLUDED_SHORTCUT;
    var exit = world.endpoint(Direction.EAST, occludedShortcut ? new Vec3(141, 106, 143) : new Vec3(192, 106, 192));
    var link = world.link(entrance, exit);
    Vec3 source = entrance.center().add(entrance.normal().scale(occludedShortcut ? 1.8 : 1.1)).subtract(0, 1.6, 0);
    Vec3 exitOffset = exit.normal().scale(occludedShortcut ? .6 : 1.2).subtract(0, 1.6, 0);
    Vec3 virtualSource = link.transform().point(source);
    Vec3 playerFeet;
    Vec3 targetFeet;
    Vec3 expectedSource;
    float yaw;
    if (throughPortal) {
      world.surface(entrance, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      world.surface(exit, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState());
      if (occludedShortcut) {
        for (var pos : PortalSurface.placementFootprint(entrance).blocks())
          world.block(pos, Blocks.STONE.defaultBlockState());
        world.block(new BlockPos(142, 106, 142), Blocks.STONE.defaultBlockState());
        world.block(new BlockPos(142, 105, 142), Blocks.STONE.defaultBlockState());
      }
      world.publish(link);
      PlayerPortalVisibility.of(world.player).ready(link);
      playerFeet = source;
      targetFeet = exit.center().add(exitOffset);
      expectedSource = virtualSource;
      yaw = 0;
    } else {
      Vec3 center = new Vec3(168, 106, 168);
      world.clear(center, 5);
      PortalClosureResolver.updateLinks(world.level, List.of());
      playerFeet = center.add(virtualSource.subtract(exit.center()));
      targetFeet = center.add(exitOffset);
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
    if (occludedShortcut && throughPortal) {
      player.setYRot(90);
      var selected = PortalInteractionPath.attack(player, player.getMainHandItem(), target.getBoundingBox(), 3);
      check(selected.isPresent() && selected.orElseThrow().links().equals(List.of(link))
              && target.getBoundingBox().distanceToSqr(player.getEyePosition())
                  < Math.pow(selected.orElseThrow().distance(), 2),
          "The shorter direct route through a wall took precedence over the visible portal route");
      player.setYRot(yaw);
    }
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
    var entity = TestEntityTypes.HUSK.create(world.level, EntitySpawnReason.COMMAND);
    check(entity != null, "Could not create the combat target");
    entity.setPos(feet);
    entity.setNoAi(true);
    entity.setDeltaMovement(Vec3.ZERO);
    world.add(entity);
    entity.setNoGravity(false);
    return entity;
  }
}
