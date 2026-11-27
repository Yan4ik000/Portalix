package dev.portalix.client.compat.minecraft;

import net.minecraft.client.renderer.LevelRenderer;

public interface SceneTransientBlocks {
  void portalix$transientBlockOwner(LevelRenderer owner);
  void portalix$clearTransientBlocks();
}
