package dev.portalix.client.mixin.scene;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
  @Invoker("shouldRenderBlockOutline")
  boolean portalix$renderBlockOutline();

  @Invoker("bobView")
  void portalix$bobView(CameraRenderState camera, PoseStack poses);

  @Accessor("lightmapRenderStateExtractor")
  LightmapRenderStateExtractor portalix$lightmapExtractor();

  @Accessor("resourcePool")
  CrossFrameResourcePool portalix$resources();
}
