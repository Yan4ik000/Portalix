package dev.portalix.client.mixin.compat.iris;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import dev.portalix.client.portal.clip.PortalClipping;
import java.util.ArrayList;
import java.util.List;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.FallbackShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({ExtendedShader.class, FallbackShader.class})
abstract class IrisClipBindingsMixin {
  @ModifyArg(
      method = "<init>",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/opengl/GlProgram;setupBindGroupLayouts(Ljava/util/List;)V"),
      allow = 1)
  private List<BindGroupLayout> portalix$clipBinding(List<BindGroupLayout> layouts) {
    var result = new ArrayList<>(layouts);
    result.add(PortalClipping.LAYOUT);
    return result;
  }
}
