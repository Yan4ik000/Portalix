package dev.portalix.client.mixin.compat.iris;

import java.util.EnumMap;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SodiumPrograms.class, remap = false)
interface IrisSodiumProgramAccessor {
  @Accessor("shaders")
  EnumMap<SodiumPrograms.Pass, GlProgram<ChunkShaderInterface>> portalix$shaders();
}
