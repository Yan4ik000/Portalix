package dev.portalix.neoforge.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.portalix.item.PortalLoot;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ReloadableServerRegistries.class)
abstract class PortalBuiltinLootMixin {
  @WrapOperation(
      method = "*",
      at = @At(value = "INVOKE", target =
          "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectoryWithOptionalValues(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V"),
      allow = 1)
  private static <T> void portalix$builtinLoot(
      ResourceManager resources,
      ResourceKey<? extends Registry<T>> registry,
      DynamicOps<JsonElement> ops,
      Codec<Optional<T>> codec,
      Map<Identifier, Optional<T>> tables,
      Operation<Void> original) {
    original.call(resources, registry, ops, codec, tables);
    if (!registry.equals(Registries.LOOT_TABLE)) return;
    var id = BuiltInLootTables.ABANDONED_MINESHAFT.identifier();
    var table = tables.get(id);
    if (table == null || !(table.orElse(null) instanceof LootTable loot)) return;
    // NeoForge's loot event omits pack provenance, which is still available at this point.
    resources.getResource(FileToIdConverter.registry(Registries.LOOT_TABLE).idToFile(id))
        .filter(resource -> resource.knownPackInfo().isPresent()
            || resource.source().location().source() == PackSource.BUILT_IN)
        .ifPresent(resource -> loot.addPool(PortalLoot.aiModulePool().build()));
  }
}
