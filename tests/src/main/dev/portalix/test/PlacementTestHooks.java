package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PlacementTestHooks {
  private static final ScopedValue<PlacementTestHooks> ACTIVE = ScopedValue.newInstance();

  private final Phase phase;
  private final Player player;
  private final BlockPos pos;
  private final BooleanSupplier onBreak;
  private boolean called;

  private PlacementTestHooks(Phase phase, Player player, BlockPos pos, BooleanSupplier onBreak) {
    this.phase = phase;
    this.player = player;
    this.pos = pos;
    this.onBreak = onBreak;
  }

  public static boolean beforeBreak(Player player, BlockPos pos) {
    var hook = find(Phase.BEFORE_BREAK, player, pos);
    return hook == null || hook.run();
  }

  public static void afterBreak(Entity breaker, BlockPos pos) {
    var hook = find(Phase.AFTER_BREAK, breaker, pos);
    if (hook != null) hook.run();
  }

  public static void afterLoot(Entity breaker, BlockPos pos, List<ItemStack> drops) {
    var hook = find(Phase.AFTER_LOOT, breaker, pos);
    if (hook == null) return;
    check(drops.stream().anyMatch(stack -> stack.is(Items.MOSS_CARPET)),
        "The test block did not drop moss carpet");
    hook.run();
  }

  static void expectRejection(Phase phase, Player player, BlockPos pos, BooleanSupplier onBreak,
      BooleanSupplier place) {
    var hook = new PlacementTestHooks(phase, player, pos, onBreak);
    ScopedValue.where(ACTIVE, hook).run(() -> {
      boolean placed = place.getAsBoolean();
      check(hook.called, "Placement did not hit " + phase + " at " + pos);
      check(!placed, "Placement succeeded after the test changed or vetoed " + phase + " at " + pos);
    });
  }

  private static PlacementTestHooks find(Phase phase, Entity breaker, BlockPos pos) {
    if (!ACTIVE.isBound()) return null;
    var hook = ACTIVE.get();
    return hook.phase == phase && breaker == hook.player && pos.equals(hook.pos) ? hook : null;
  }

  private boolean run() {
    called = true;
    return onBreak.getAsBoolean();
  }

  enum Phase { BEFORE_BREAK, AFTER_BREAK, AFTER_LOOT }
}
