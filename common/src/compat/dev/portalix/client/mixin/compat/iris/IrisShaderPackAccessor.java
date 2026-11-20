package dev.portalix.client.mixin.compat.iris;

import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderPack.class)
public interface IrisShaderPackAccessor {
  @Accessor("shaderProperties")
  ShaderProperties portalix$properties();
}
