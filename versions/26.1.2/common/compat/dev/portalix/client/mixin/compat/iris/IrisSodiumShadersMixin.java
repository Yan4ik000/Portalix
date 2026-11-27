package dev.portalix.client.mixin.compat.iris;

import dev.portalix.client.compat.iris.IrisPortalShaders;
import java.util.Map;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SodiumPrograms.class, remap = false)
abstract class IrisSodiumShadersMixin {
  @Inject(method = "transformShaders", at = @At("RETURN"), cancellable = true)
  private void portalix$clip(ProgramSource source, AlphaTest alphaTest, ProgramSet programs, boolean shadow,
      CallbackInfoReturnable<Map<PatchShaderType, String>> ci) {
    ci.setReturnValue(IrisPortalShaders.sodium(ci.getReturnValue(), shadow));
  }
}
