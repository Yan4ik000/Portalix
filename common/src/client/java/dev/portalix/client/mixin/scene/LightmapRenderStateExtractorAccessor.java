package dev.portalix.client.mixin.scene;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightmapRenderStateExtractor.class)
public interface LightmapRenderStateExtractorAccessor {
  @Accessor("needsUpdate")
  boolean portalix$needsUpdate();

  @Accessor("needsUpdate")
  void portalix$needsUpdate(boolean value);
}
