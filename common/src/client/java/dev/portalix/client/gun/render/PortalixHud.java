package dev.portalix.client.gun.render;

import dev.portalix.PortalixMod;
import dev.portalix.client.PortalixClient;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class PortalixHud {
  private static final int CROSSHAIR_SIZE = 15;
  private static final float RETICLE_SCALE = 1.0F / 3.0F;

  private PortalixHud() {}

  public static int activeMask() {
    var player = Minecraft.getInstance().player;
    return player == null
        ? 0
        : PortalixClient.portals().activeMask(PortalixIdentity.get(player.getMainHandItem()));
  }

  public static void render(GuiGraphicsExtractor g) {
    float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
    float centerX = (g.guiWidth() - CROSSHAIR_SIZE) / 2 + CROSSHAIR_SIZE / 2.0F;
    float centerY = (g.guiHeight() - CROSSHAIR_SIZE) / 2 + CROSSHAIR_SIZE / 2.0F;
    g.nextStratum();
    g.pose().pushMatrix();
    g.pose()
        .translate(
            Math.round(centerX * guiScale) / guiScale,
            Math.round(centerY * guiScale) / guiScale);
    g.pose().scale(1.0F / guiScale);

    int mask = activeMask();
    boolean blue = (mask & 1) != 0;
    boolean orange = (mask & 2) != 0;

    String palette =
        PortalixVariant.of(Minecraft.getInstance().player.getMainHandItem()).palette();
    Identifier blueTex =
        PortalixMod.id("textures/gui/" + palette + "/reticle_" + (blue ? 2 : 0) + ".png");
    Identifier orangeTex =
        PortalixMod.id("textures/gui/" + palette + "/reticle_" + (orange ? 3 : 1) + ".png");

    float reticleScale = RETICLE_SCALE * guiScale;
    drawReticle(g, blueTex, -30, -42, reticleScale);
    drawReticle(g, orangeTex, -18, -22, reticleScale);

    g.pose().popMatrix();
  }

  private static void drawReticle(
      GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float scale) {
    // Align texture edges to framebuffer pixels to avoid extra filtering from subpixel offsets.
    graphics.blit(
        texture,
        Math.round(x * scale),
        Math.round(y * scale),
        Math.round((x + 48) * scale),
        Math.round((y + 64) * scale),
        0.0F,
        1.0F,
        0.0F,
        1.0F);
  }
}
