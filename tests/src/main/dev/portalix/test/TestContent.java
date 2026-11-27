package dev.portalix.test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class TestContent {
  private TestContent() {}

  public static Block concrete(DyeColor color) {
    return BuiltInRegistries.BLOCK.getValueOrThrow(
        ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(color.getName() + "_concrete")));
  }

  public static Item dye(DyeColor color) {
    return BuiltInRegistries.ITEM.getValueOrThrow(
        ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace(color.getName() + "_dye")));
  }
}
