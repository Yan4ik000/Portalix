package dev.portalix.test;

import static dev.portalix.test.ServerRegressionSuite.check;

import dev.portalix.item.ModItems;
import dev.portalix.item.PortalixIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

final class ContentRegression {
  private ContentRegression() {}

  static void run(RegressionWorld world) {
    Item board = ModItems.CONTROL_BOARD.get();
    Item core = ModItems.PORTAL_CORE.get();
    Item cooling = ModItems.COOLING_UNIT.get();
    Item shell = ModItems.PORTALIX_SHELL.get();
    Item blackHole = ModItems.MINIATURE_BLACK_HOLE.get();
    craft(world, board, Items.AIR, Items.COPPER_INGOT, Items.AIR,
        Items.REDSTONE, Items.QUARTZ, Items.REDSTONE, Items.AIR, Items.COPPER_INGOT, Items.AIR);
    craft(world, core, Items.IRON_INGOT, Items.GLASS, Items.IRON_INGOT,
        Items.QUARTZ, Items.ENDER_PEARL, Items.QUARTZ, Items.IRON_INGOT, board, Items.IRON_INGOT);
    craft(world, cooling, Items.IRON_INGOT, Items.COPPER_INGOT, Items.IRON_INGOT,
        Items.COPPER_INGOT, board, Items.COPPER_INGOT, Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT);
    craft(world, shell, Items.IRON_INGOT, Items.QUARTZ_BLOCK, Items.IRON_INGOT,
        Items.AIR, Items.GLASS, Items.AIR, Items.IRON_INGOT, Items.QUARTZ_BLOCK, Items.IRON_INGOT);
    craft(world, blackHole, Items.OBSIDIAN, Items.ENDER_PEARL, Items.OBSIDIAN,
        Items.ENDER_PEARL, Items.NETHER_STAR, Items.ENDER_PEARL, Items.OBSIDIAN, Items.ENDER_PEARL, Items.OBSIDIAN);
    Item gun = ModItems.PORTALIX.get();
    craft(world, gun, Items.AIR, cooling, Items.AIR, core, blackHole, shell, Items.AIR, board, Items.AIR);
    craft(world, ModItems.BLUE_PORTALIX.get(), gun, board, Items.DYE.pick(DyeColor.CYAN), Items.DYE.pick(DyeColor.PURPLE));
    craft(world, ModItems.ORANGE_PORTALIX.get(), gun, board, Items.DYE.pick(DyeColor.YELLOW), Items.DYE.pick(DyeColor.RED));
    craft(world, ModItems.POTATOS.get(), ModItems.AI_MODULE.get(), Items.POTATO);
    craft(world, ModItems.POTATO_PORTALIX.get(), gun, ModItems.POTATOS.get());

    var identities = new HashSet<UUID>();
    for (Item item : List.of(gun, ModItems.BLUE_PORTALIX.get(), ModItems.ORANGE_PORTALIX.get(),
        ModItems.POTATO_PORTALIX.get())) {
      var stack = new ItemStack(item);
      check(stack.getMaxStackSize() == 1, "Portalixs must not stack");
      var identity = PortalixIdentity.ensure(stack);
      check(identities.add(identity), "Independent guns share an identity");
      check(identity.equals(PortalixIdentity.get(stack.copy())), "Copying an item lost its gun identity");
    }
    check(PortalixIdentity.get(new ItemStack(Items.IRON_INGOT)) == null, "An ordinary item became a portalix");
    BuiltinLootRegression.run(world);
  }

  private static void craft(RegressionWorld world, Item result, Item... ingredients) {
    var stacks = new ArrayList<ItemStack>();
    for (Item item : ingredients) stacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
    while (stacks.size() < 9) stacks.add(ItemStack.EMPTY);
    var input = CraftingInput.of(3, 3, stacks);
    var recipe = world.level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, world.level);
    check(recipe.isPresent(), "No crafting recipe accepts the ingredients for " + result);
    var output = recipe.orElseThrow().value().assemble(input);
    check(output.is(result) && output.getCount() == 1, "Crafting produced the wrong item for " + result);
  }
}
