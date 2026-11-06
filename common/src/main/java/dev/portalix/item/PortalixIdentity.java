package dev.portalix.item;

import dev.portalix.PortalixMod;
import dev.portalix.platform.RegistryPlatform;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

public final class PortalixIdentity {
  public static final Supplier<DataComponentType<UUID>> ID =
      RegistryPlatform.INSTANCE.register(
          Registries.DATA_COMPONENT_TYPE,
          PortalixMod.id("gun_id"),
          () -> DataComponentType.<UUID>builder()
              .persistent(UUIDUtil.CODEC)
              .networkSynchronized(UUIDUtil.STREAM_CODEC)
              .build());

  private PortalixIdentity() {}

  public static UUID get(ItemStack stack) {
    return PortalixMod.isPortalix(stack) ? stack.get(ID.get()) : null;
  }

  public static UUID ensure(ItemStack stack) {
    if (!PortalixMod.isPortalix(stack)) throw new IllegalArgumentException("Not a portalix");
    UUID id = stack.get(ID.get());
    if (id == null) {
      id = UUID.randomUUID();
      stack.set(ID.get(), id);
    }
    return id;
  }

  public static void initialize() {}
}
