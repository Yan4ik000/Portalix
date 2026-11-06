package dev.portalix.item;

import dev.portalix.PortalixMod;
import dev.portalix.platform.RegistryPlatform;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
  public static final Supplier<Item> PORTALIX = registerGun("portalix", PortalixVariant.STANDARD);
  public static final Supplier<Item> POTATO_PORTALIX =
      registerGun("potato_portalix", PortalixVariant.POTATO);
  public static final Supplier<Item> ORANGE_PORTALIX =
      registerGun("orange_portalix", PortalixVariant.ORANGE);
  public static final Supplier<Item> BLUE_PORTALIX =
      registerGun("blue_portalix", PortalixVariant.BLUE);
  public static final Supplier<Item> CONTROL_BOARD = register("control_board");
  public static final Supplier<Item> PORTAL_CORE = register("portal_core");
  public static final Supplier<Item> PORTALIX_SHELL = register("portalix_shell");
  public static final Supplier<Item> MINIATURE_BLACK_HOLE = register("miniature_black_hole");
  public static final Supplier<Item> COOLING_UNIT = register("cooling_unit");
  public static final Supplier<Item> AI_MODULE = register("ai_module");
  public static final Supplier<Item> POTATOS = register("potatos");

  private ModItems() {}

  private static Supplier<Item> registerGun(String name, PortalixVariant variant) {
    var id = PortalixMod.id(name);
    return RegistryPlatform.INSTANCE.register(Registries.ITEM, id, () -> new PortalixItem(
        new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1), variant));
  }

  private static Supplier<Item> register(String name) {
    var id = PortalixMod.id(name);
    return RegistryPlatform.INSTANCE.register(Registries.ITEM, id, () -> new Item(
        new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));
  }

  public static void initialize() {
    RegistryPlatform.INSTANCE.appendItems(
        ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")),
        List.of(PORTALIX, POTATO_PORTALIX, ORANGE_PORTALIX, BLUE_PORTALIX));
    RegistryPlatform.INSTANCE.appendItems(
        ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients")),
        List.of(CONTROL_BOARD, PORTALIX_SHELL, PORTAL_CORE, MINIATURE_BLACK_HOLE,
            COOLING_UNIT, AI_MODULE, POTATOS));
  }
}
