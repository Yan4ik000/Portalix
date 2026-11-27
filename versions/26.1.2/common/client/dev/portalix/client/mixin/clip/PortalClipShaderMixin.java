package dev.portalix.client.mixin.clip;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.portalix.client.portal.clip.PortalClipShaders;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class PortalClipShaderMixin {
  @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
  private void portalix$clip(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
    cir.setReturnValue(PortalClipShaders.shader(id, cir.getReturnValue(), type));
  }
}
