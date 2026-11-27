package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
  @Mutable
  @Accessor("levelRenderer")
  void portalix$levelRenderer(LevelRenderer renderer);

  @Accessor("mainRenderTarget")
  RenderTarget portalix$mainTarget();

  @Mutable
  @Accessor("mainRenderTarget")
  void portalix$mainTarget(RenderTarget target);
}
