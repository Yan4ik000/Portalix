package dev.portalix.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.portalix.item.PortalLoot;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
abstract class PortalBuiltinLootMixin {
  @WrapOperation(method = "loadFromResource", at = @At(value = "INVOKE", target =
      "Lnet/neoforged/neoforge/event/EventHooks;loadLootTable(Lnet/minecraft/core/HolderGetter$Provider;Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/level/storage/loot/LootTable;)Lnet/minecraft/world/level/storage/loot/LootTable;"),
      require = 1, allow = 1)
  private static @Nullable LootTable portalix$builtinLoot(HolderGetter.Provider registries, Identifier id,
      LootTable table, Operation<LootTable> original, @Local(argsOnly = true) Resource resource) {
    // The resource provides pack provenance, and listeners can still replace the table.
    if (table != LootTable.EMPTY && id.equals(BuiltInLootTables.ABANDONED_MINESHAFT.identifier())
        && (resource.knownPackInfo().isPresent() || resource.source().location().source() == PackSource.BUILT_IN)) {
      table.addPool(PortalLoot.aiModulePool().build());
    }
    return original.call(registries, id, table);
  }
}
