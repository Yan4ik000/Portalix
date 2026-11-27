package dev.portalix.client.compat.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public final class MinecraftRenderAccess {
  private MinecraftRenderAccess() {}

  public static Camera camera(Minecraft client) {
    return client.gameRenderer.getMainCamera();
  }

  public static boolean zeroToOneDepth() {
    return RenderSystem.getDevice().isZZeroToOne();
  }

  public static boolean reversedDepth() {
    return false;
  }
}
