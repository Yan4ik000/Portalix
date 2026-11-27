package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.sodium.SodiumShaderAbi;
import dev.portalix.client.portal.clip.PortalClipShaders;
import dev.portalix.client.portal.clip.PortalClipping;
import java.util.WeakHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.gl.shader.GlShader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderConstants;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.caffeinemc.mods.sodium.client.gl.shader.ShaderParser;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformBlock;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ShaderChunkRenderer.class, remap = false)
abstract class SodiumShaderMixin {
  @Shadow protected GlProgram<ChunkShaderInterface> activeProgram;

  @Unique private final Map<GlProgram<?>, ClipBinding> portalix$bindings = new WeakHashMap<>();

  @WrapOperation(
      method = "createShader",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader;loadShader(Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderType;Lnet/minecraft/resources/Identifier;Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderConstants;)Lnet/caffeinemc/mods/sodium/client/gl/shader/GlShader;"),
      require = 2,
      allow = 2)
  private GlShader portalix$clipShader(net.caffeinemc.mods.sodium.client.gl.shader.ShaderType type,
      Identifier name, ShaderConstants constants, Operation<GlShader> original) {
    var parsed = ShaderParser.parseShader(ShaderLoader.getShaderSource(name), constants);
    int versionEnd = parsed.src().indexOf('\n');
    String source = parsed.src().substring(0, versionEnd + 1) + "#define PORTALIX_CLIP\n"
        + (type == net.caffeinemc.mods.sodium.client.gl.shader.ShaderType.VERTEX
            ? "uniform bool " + SodiumShaderAbi.SEPARATE_OCCLUSION + ";\n" : "")
        + parsed.src().substring(versionEnd + 1);
    ShaderType stage = type == net.caffeinemc.mods.sodium.client.gl.shader.ShaderType.VERTEX
        ? ShaderType.VERTEX : ShaderType.FRAGMENT;
    var id = name.withPath(path -> path.substring(0, path.length() - 4));
    return new GlShader(type, name,
        new ShaderParser.ParsedShader(PortalClipShaders.shader(id, source, stage), parsed.includeIds()));
  }

  @Inject(method = "begin", at = @At("TAIL"))
  private void portalix$bindClip(CallbackInfo ci) {
    var binding = portalix$bindings.computeIfAbsent(activeProgram, program -> {
      var clip = program.bindUniformBlockOptional(PortalClipping.UNIFORM_NAME, 1);
      if (clip == null)
        clip = program.bindUniformBlock("iris_" + PortalClipping.UNIFORM_NAME, 1);
      return new ClipBinding(clip, GL20C.glGetUniformLocation(program.handle(), SodiumShaderAbi.SEPARATE_OCCLUSION));
    });
    binding.clip.bindBufferRange(PortalClipping.uniform());
    if (binding.separateOcclusion >= 0) {
      var shaders = ShaderBackend.INSTANCE;
      GL20C.glUniform1i(binding.separateOcclusion, shaders != null && shaders.terrainSeparateOcclusion() ? 1 : 0);
    }
  }

  @Unique private record ClipBinding(GlUniformBlock clip, int separateOcclusion) {}
}
