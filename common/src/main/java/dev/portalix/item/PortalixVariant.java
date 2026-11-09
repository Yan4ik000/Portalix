package dev.portalix.item;

import com.google.gson.JsonArray;
import dev.portalix.PortalixMod;
import dev.portalix.resource.ModAssetLoader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public enum PortalixVariant {
  STANDARD("standard", "", "portalix"),
  POTATO("standard", "", "potato_portalix"),
  ORANGE("orange"),
  BLUE("blue"),
  GREEN("green"),
  AMETHYST("amethyst"),
  NEON("neon"),
  MONOCHROME("monochrome");

  private final String palette;
  private final String skin;
  private final Identifier itemId;

  PortalixVariant(String palette) {
    this(palette, "_" + palette, palette + "_portalix");
  }

  PortalixVariant(String palette, String skin, String itemName) {
    this.palette = palette;
    this.skin = skin;
    this.itemId = PortalixMod.id(itemName);
  }

  public static PortalixVariant of(ItemStack stack) {
    if (stack.getItem() instanceof PortalixItem gun) return gun.variant();
    throw new IllegalArgumentException("Not a portalix");
  }

  public String palette() {
    return palette;
  }

  public String skin() {
    return skin;
  }

  public Identifier itemId() {
    return itemId;
  }

  public boolean potato() {
    return this == POTATO;
  }

  public int particleColor(boolean secondary) {
    return Palettes.BY_NAME.get(palette).particles().select(secondary);
  }

  public int rimColor(boolean secondary) {
    return Palettes.BY_NAME.get(palette).rim().select(secondary);
  }

  public int particleGlowColor(boolean secondary) {
    return Palettes.BY_NAME.get(palette).glow().select(secondary);
  }

  public boolean usesTintedRim() {
    return Palettes.BY_NAME.get(palette).rim() != null;
  }

  private record Colors(int primary, int secondary) {
    private static Colors read(JsonArray pair) {
      return new Colors(rgb(pair.get(0).getAsJsonArray()), rgb(pair.get(1).getAsJsonArray()));
    }

    private static int rgb(JsonArray color) {
      return color.get(0).getAsInt() << 16 | color.get(1).getAsInt() << 8 | color.get(2).getAsInt();
    }

    int select(boolean useSecondary) {
      return useSecondary ? secondary : primary;
    }
  }

  private record Palette(Colors particles, Colors rim, Colors glow) {}

  private static final class Palettes {
    static final Map<String, Palette> BY_NAME = load();

    private static Map<String, Palette> load() {
      var data = ModAssetLoader.json("/assets/portalix/gun_palettes.json");
      Map<String, Palette> palettes = new HashMap<>();
      for (PortalixVariant variant : values()) {
        palettes.computeIfAbsent(
            variant.palette(),
            name -> {
              var entry = data.getAsJsonObject(name);
              var particles = Colors.read(entry.getAsJsonArray("particles"));
              var rim = entry.get("rim");
              var glow = entry.get("glow");
              return new Palette(
                  particles,
                  rim.isJsonNull() ? null : Colors.read(rim.getAsJsonArray()),
                  glow.isJsonNull() ? particles : Colors.read(glow.getAsJsonArray()));
            });
      }
      return Map.copyOf(palettes);
    }
  }
}
