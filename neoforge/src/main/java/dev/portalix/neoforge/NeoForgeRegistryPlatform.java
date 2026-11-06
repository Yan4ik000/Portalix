package dev.portalix.neoforge;

import dev.portalix.PortalixMod;
import dev.portalix.platform.RegistryPlatform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NeoForgeRegistryPlatform implements RegistryPlatform {
  private final Map<ResourceKey<?>, DeferredRegister<?>> registries = new HashMap<>();

  @Override
  public <T, R extends T> Supplier<R> register(
      ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<R> factory) {
    return registry(registry).register(id.getPath(), factory);
  }

  @SuppressWarnings("unchecked")
  private <T> DeferredRegister<T> registry(ResourceKey<? extends Registry<T>> key) {
    return (DeferredRegister<T>) registries.computeIfAbsent(key, ignored -> {
      var registry = DeferredRegister.create(key, PortalixMod.MOD_ID);
      registry.register(eventBus());
      return registry;
    });
  }

  @Override
  public void appendItems(ResourceKey<CreativeModeTab> tab, List<Supplier<Item>> items) {
    eventBus().addListener((BuildCreativeModeTabContentsEvent event) -> {
      if (event.getTabKey().equals(tab)) {
        for (var item : items) event.accept(item.get());
      }
    });
  }

  private static IEventBus eventBus() {
    return ModList.get().getModContainerById(PortalixMod.MOD_ID).orElseThrow().getEventBus();
  }
}
