package dev.portalix.platform;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public interface RegistryPlatform {
  RegistryPlatform INSTANCE =
      ServiceLoader.load(RegistryPlatform.class, RegistryPlatform.class.getClassLoader())
          .findFirst()
          .orElseThrow();

  <T, R extends T> Supplier<R> register(
      ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<R> factory);

  void appendItems(ResourceKey<CreativeModeTab> tab, List<Supplier<Item>> items);
}
