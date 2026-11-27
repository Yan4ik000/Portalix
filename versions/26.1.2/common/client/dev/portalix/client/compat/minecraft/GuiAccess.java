package dev.portalix.client.compat.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuiAccess {
  private GuiAccess() {}

  public static Screen screen(Minecraft client) {
    return client.screen;
  }

  public static void setScreen(Minecraft client, Screen screen) {
    client.setScreen(screen);
  }

  public static Overlay overlay(Minecraft client) {
    return client.getOverlay();
  }

  public static boolean hidden(Minecraft client) {
    return client.options.hideGui;
  }

  public static boolean renderedHudHidden(Minecraft client) {
    return client.gameRenderer.getGameRenderState().optionsRenderState.hideGui;
  }

  public static void toggleHud(Minecraft client) {
    client.options.hideGui = !client.options.hideGui;
  }

  public static void setOverlayMessage(Minecraft client, Component message, boolean animate) {
    client.gui.setOverlayMessage(message, animate);
  }

  public static void addChatMessage(Minecraft client, Component message) {
    client.gui.getChat().addClientSystemMessage(message);
  }
}
