package dev.portalix.client.compat.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuiAccess {
  private GuiAccess() {}

  public static Screen screen(Minecraft client) {
    return client.gui.screen();
  }

  public static void setScreen(Minecraft client, Screen screen) {
    client.gui.setScreen(screen);
  }

  public static Overlay overlay(Minecraft client) {
    return client.gui.overlay();
  }

  public static boolean hidden(Minecraft client) {
    return client.gui.hud.isHidden();
  }

  public static boolean renderedHudHidden(Minecraft client) {
    return client.gameRenderer.gameRenderState().guiRenderState.isHudHidden;
  }

  public static void toggleHud(Minecraft client) {
    client.gui.hud.toggle();
  }

  public static void setOverlayMessage(Minecraft client, Component message, boolean animate) {
    client.gui.hud.setOverlayMessage(message, animate);
  }

  public static void addChatMessage(Minecraft client, Component message) {
    client.gui.hud.getChat().addClientSystemMessage(message);
  }
}
