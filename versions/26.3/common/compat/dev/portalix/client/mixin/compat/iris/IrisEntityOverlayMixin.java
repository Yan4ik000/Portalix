package dev.portalix.client.mixin.compat.iris;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.opengl.GlProgram;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.util.List;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.FallbackShader;
import net.irisshaders.iris.pipeline.programs.IrisBindings;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ExtendedShader.class, FallbackShader.class})
abstract class IrisEntityOverlayMixin {
  @Unique private boolean portalix$usesOverlay;

  @Inject(method = "<init>", at = @At("RETURN"), allow = 1)
  private void portalix$findOverlay(CallbackInfo ci) {
    int program = ((GlProgram) (Object) this).getProgramId();
    portalix$usesOverlay = GL33C.glGetUniformLocation(program, "iris_overlay") >= 0
        || GL33C.glGetUniformLocation(program, "Sampler1") >= 0;
  }

  @Inject(method = "iris$setupState", at = @At("TAIL"), allow = 1)
  private void portalix$bindOverlay(List<BindGroupLayout.UniformDescription> uniforms, CallbackInfo ci) {
    if (!portalix$usesOverlay) return;
    for (var uniform : uniforms) {
      if (uniform.name().equals("Sampler1")) return;
    }
    // Vanilla omits the overlay binding on armor, but the Iris entity shader still samples it.
    var view = Minecraft.getInstance().gameRenderer.overlayTexture().getTextureView();
    IrisRenderSystem.bindTextureToUnit(GL33C.GL_TEXTURE_2D, IrisBindings.OVERLAY_TEXTURE,
        ((GlTexture) view.texture()).glId());
  }
}
