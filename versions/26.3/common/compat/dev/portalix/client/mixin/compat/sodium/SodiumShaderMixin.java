package dev.portalix.client.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.sodium.SodiumTerrainPipelines;
import dev.portalix.client.portal.clip.PortalClipPipelineCache;
import dev.portalix.client.portal.clip.PortalClipping;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer",
    remap = false)
abstract class SodiumShaderMixin implements SodiumTerrainPipelines {
  @Shadow @Final protected VertexFormat vertexFormat;

  @Shadow
  protected abstract RenderPipeline compileProgram(TerrainRenderPass pass, @Nullable OitStage stage);

  @ModifyExpressionValue(
      method = {"createShader", "createOITShader"},
      at = @At(value = "INVOKE", target =
          "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withPushConstantSize(I)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),
      require = 2, allow = 2)
  private RenderPipeline.Builder portalix$terrainLayout(RenderPipeline.Builder builder) {
    return builder.withBindGroupLayout(PortalClipping.LAYOUT);
  }

  @ModifyExpressionValue(
      method = "begin",
      at = @At(value = "INVOKE", target =
          "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;compileProgram(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/minecraft/client/renderer/oit/OitStage;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;"),
      allow = 1)
  private RenderPipeline portalix$terrainFormat(RenderPipeline pipeline) {
    return portalix$selectTerrainFormat(pipeline);
  }

  @Unique
  private RenderPipeline portalix$selectTerrainFormat(RenderPipeline pipeline) {
    var shaders = ShaderBackend.INSTANCE;
    return shaders == null ? pipeline : shaders.terrainPipeline(pipeline, vertexFormat);
  }

  @Inject(method = "<init>", at = @At("TAIL"))
  private void portalix$preparePrograms(CallbackInfo ci) {
    PortalClipping.without(() -> portalix$visitTerrainPipelines(pipeline -> {
      RenderSystem.getCompiledPipeline(pipeline);
      RenderSystem.getCompiledPipeline(PortalClipPipelineCache.clipped(pipeline));
    }));
  }

  @Override
  public void portalix$visitTerrainPipelines(Consumer<RenderPipeline> visitor) {
    for (var pass : DefaultTerrainRenderPasses.ALL)
      visitor.accept(portalix$selectTerrainFormat(compileProgram(pass, null)));
    if (Minecraft.getInstance().options.improvedTransparency().get()) {
      for (var stage : OitStage.values()) {
        visitor.accept(portalix$selectTerrainFormat(compileProgram(DefaultTerrainRenderPasses.TRANSLUCENT, stage)));
      }
    }
  }
}
