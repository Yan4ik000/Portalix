package dev.portalix.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftTestBootstrap {
  private MinecraftTestBootstrap() {}

  public static void initialize() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }
}
