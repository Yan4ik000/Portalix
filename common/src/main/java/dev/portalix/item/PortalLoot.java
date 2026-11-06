package dev.portalix.item;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

public final class PortalLoot {
  private PortalLoot() {}

  public static LootPool.Builder aiModulePool() {
    return LootPool.lootPool()
        .when(LootItemRandomChanceCondition.randomChance(0.05F))
        .add(LootItem.lootTableItem(ModItems.AI_MODULE.get()));
  }
}
