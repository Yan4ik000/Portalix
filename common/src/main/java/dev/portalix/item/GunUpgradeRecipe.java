package dev.portalix.item;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.portalix.PortalixMod;
import dev.portalix.platform.RegistryPlatform;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

public final class GunUpgradeRecipe extends NormalCraftingRecipe {
  private static final MapCodec<GunUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
      instance.group(
          CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
          CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
          ItemStackTemplate.CODEC.validate(result ->
              result.item().value() instanceof PortalixItem && result.count() == 1
                  ? DataResult.success(result)
                  : DataResult.error(() -> "A Portalix upgrade must produce one Portalix"))
              .fieldOf("result").forGetter(recipe -> recipe.output),
          Ingredient.CODEC.listOf(2, 9).fieldOf("ingredients").forGetter(recipe -> recipe.ingredients))
          .apply(instance, GunUpgradeRecipe::new));
  private static final StreamCodec<RegistryFriendlyByteBuf, GunUpgradeRecipe> STREAM_CODEC =
      StreamCodec.composite(
          CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
          CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
          ItemStackTemplate.STREAM_CODEC, recipe -> recipe.output,
          Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), recipe -> recipe.ingredients,
          GunUpgradeRecipe::new);
  private static final Supplier<RecipeSerializer<GunUpgradeRecipe>> SERIALIZER =
      RegistryPlatform.INSTANCE.register(
          Registries.RECIPE_SERIALIZER, PortalixMod.id("gun_upgrade"),
          () -> new RecipeSerializer<>(CODEC, STREAM_CODEC));

  private final ItemStackTemplate output;
  private final List<Ingredient> ingredients;
  private final ShapelessRecipe shapeless;

  private GunUpgradeRecipe(CommonInfo common, CraftingBookInfo book, ItemStackTemplate output,
      List<Ingredient> ingredients) {
    super(common, book);
    this.output = output;
    this.ingredients = ingredients;
    this.shapeless = new ShapelessRecipe(common, book, output, ingredients);
  }

  public static void initialize() {}

  @Override
  public RecipeSerializer<GunUpgradeRecipe> getSerializer() {
    return SERIALIZER.get();
  }

  @Override
  public boolean matches(CraftingInput input, Level level) {
    return shapeless.matches(input, level) && !baseGun(input).isEmpty();
  }

  @Override
  protected PlacementInfo createPlacementInfo() {
    return shapeless.placementInfo();
  }

  @Override
  public List<RecipeDisplay> display() {
    return shapeless.display();
  }

  @Override
  public ItemStack assemble(CraftingInput input) {
    var base = baseGun(input);
    if (base.isEmpty()) return ItemStack.EMPTY;
    var result = TransmuteRecipe.createWithOriginalComponents(output, base);
    if (!result.isEmpty()) {
      var identity = PortalixIdentity.get(base);
      if (identity != null) result.set(PortalixIdentity.ID.get(), identity);
      else result.remove(PortalixIdentity.ID.get());
    }
    return result;
  }

  private static ItemStack baseGun(CraftingInput input) {
    ItemStack base = ItemStack.EMPTY;
    for (var stack : input.items()) {
      if (PortalixMod.isPortalix(stack)) {
        if (!base.isEmpty() || stack.getCount() != 1) return ItemStack.EMPTY;
        base = stack;
      }
    }
    return base;
  }
}
