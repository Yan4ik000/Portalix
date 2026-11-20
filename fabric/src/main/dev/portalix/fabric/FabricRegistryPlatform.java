package dev.portalix.fabric;

import dev.portalix.platform.RegistryPlatform;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public final class FabricRegistryPlatform implements RegistryPlatform {
  @Override
  @SuppressWarnings("unchecked")
  public <T, R extends T> Supplier<R> register(
      ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<R> factory) {
    Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
    R value = Registry.register(target, id, factory.get());
    return () -> value;
  }

  @Override
  public void appendItems(ResourceKey<CreativeModeTab> tab, List<Supplier<Item>> items) {
    CreativeModeTabEvents.modifyOutputEvent(tab).register(entries -> {
      for (var item : items) entries.accept(item.get());
    });
  }
}
