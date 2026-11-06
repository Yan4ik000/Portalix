package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.item.ModItems;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

final class BuiltinLootRegression {
  private BuiltinLootRegression() {}

  static void run(RegressionWorld world) {
    var server = world.level.getServer();
    var vanilla = server.getPackRepository().getPack("vanilla");
    check(vanilla != null, "The vanilla datapack is unavailable");
    // GameTest enables experimental packs, including a mineshaft override from trade_rebalance.
    try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(vanilla.open()))) {
      var loaded = ReloadableServerRegistries.reload(server.registries(), List.of(), resources, Runnable::run).join();
      var table = loaded.lookupWithUpdatedTags().lookupOrThrow(Registries.LOOT_TABLE)
          .getOrThrow(BuiltInLootTables.ABANDONED_MINESHAFT).value();
      var params = new LootParams.Builder(world.level)
          .withParameter(LootContextParams.ORIGIN, world.player.position())
          .create(LootContextParamSets.CHEST);
      boolean foundModule = false;
      boolean foundOrdinaryLoot = false;
      for (var stack : table.getRandomItems(params, RandomSource.create(17))) {
        check(!stack.isEmpty() && stack.getCount() <= stack.getMaxStackSize(),
            "Mineshaft chest generated an invalid item stack");
        if (stack.is(ModItems.AI_MODULE.get())) foundModule = true;
        else foundOrdinaryLoot = true;
      }
      check(foundOrdinaryLoot, "Mineshaft chest generated no ordinary loot");
      check(foundModule, "Mineshaft chest generated no AI module");
    }
  }
}
