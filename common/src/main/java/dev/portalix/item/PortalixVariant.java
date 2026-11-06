package dev.portalix.item;

import com.google.gson.JsonArray;
import dev.portalix.resource.ModAssetLoader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

public enum PortalixVariant {
  STANDARD("standard", "", false),
  POTATO("standard", "", true),
  ORANGE("orange", "_orange", false),
  BLUE("blue", "_blue", false);

  private final String palette;
  private final String skin;
  private final boolean potato;

  PortalixVariant(String palette, String skin, boolean potato) {
    this.palette = palette;
    this.skin = skin;
    this.potato = potato;
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

  public boolean potato() {
    return potato;
  }

  public int particleColor(boolean secondary) {
    ParticleColors colors = Palettes.COLORS.get(palette);
    return secondary ? colors.secondary() : colors.primary();
  }

  public int particleGlowColor(boolean secondary) {
    int color = particleColor(secondary);
    int red = color >> 16 & 255;
    int green = color >> 8 & 255;
    int blue = color & 255;
    float scale = 255.0F / Math.max(red, Math.max(green, blue));
    return Math.round(red * scale) << 16
        | Math.round(green * scale) << 8
        | Math.round(blue * scale);
  }

  public boolean cooperative() {
    return this == ORANGE || this == BLUE;
  }

  private record ParticleColors(int primary, int secondary) {
    private static int rgb(JsonArray color) {
      return color.get(0).getAsInt() << 16 | color.get(1).getAsInt() << 8 | color.get(2).getAsInt();
    }
  }

  private static final class Palettes {
    static final Map<String, ParticleColors> COLORS = load();

    private static Map<String, ParticleColors> load() {
      var data = ModAssetLoader.json("/assets/portalix/gun_palettes.json");
      Map<String, ParticleColors> colors = new HashMap<>();
      for (PortalixVariant variant : values()) {
        colors.computeIfAbsent(
            variant.palette(),
            name -> {
              var particles = data.getAsJsonObject(name).getAsJsonArray("particles");
              return new ParticleColors(
                  ParticleColors.rgb(particles.get(0).getAsJsonArray()),
                  ParticleColors.rgb(particles.get(1).getAsJsonArray()));
            });
      }
      return Map.copyOf(colors);
    }
  }
}
