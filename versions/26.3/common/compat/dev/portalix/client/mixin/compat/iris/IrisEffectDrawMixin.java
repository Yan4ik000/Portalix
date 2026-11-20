package dev.portalix.client.mixin.compat.iris;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.iris.IrisEffectTarget;
import dev.portalix.client.compat.iris.IrisShaders;
import dev.portalix.client.portal.clip.PortalClipping;
import dev.portalix.client.portal.render.PortalLevelDraws;
import java.util.Optional;
import java.util.OptionalDouble;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PreparedRenderType.class)
abstract class IrisEffectDrawMixin {
  @Unique private static final Identifier portalix$SOURCE_PARTICLE = PortalixMod.id("core/source_particle");
  @Unique private static final Identifier portalix$GUN_INDICATOR = PortalixMod.id("core/gun_indicator");

  @Shadow public abstract RenderPipeline pipeline();

  @WrapMethod(method = "drawFromBuffer")
  private void portalix$effectTarget(ExecuteInfo geometry, RenderPass pass, Operation<Void> original) {
    var shader = pipeline().getShaders().get(ShaderType.VERTEX);
    if (ImmediateState.bypass
        || !(portalix$SOURCE_PARTICLE.equals(shader) || portalix$GUN_INDICATOR.equals(shader))) {
      original.call(geometry, pass);
      return;
    }
    if (IrisShaders.shadowPass()) return;
    if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris)
        || !iris.shouldOverrideShaders()) {
      original.call(geometry, pass);
      return;
    }

    var draw = (PreparedRenderType) (Object) this;
    var projection = RenderSystem.getProjectionMatrixBuffer();
    var projectionType = RenderSystem.getProjectionType();
    var clip = PortalClipping.active() ? PortalClipping.uniform() : null;
    Runnable effect = () -> PortalClipping.withUniform(clip,
        () -> portalix$drawEffect(draw, geometry, projection, projectionType));
    Runnable flush = () -> {
      if (IrisEffectTarget.supportsColorEffects()) effect.run();
      else IrisEffectTarget.deferDraw(effect);
    };
    if (!IrisEffectTarget.enqueue(flush)) PortalLevelDraws.afterTerrain(flush);
  }

  @Unique
  @SuppressWarnings("try")
  private static void portalix$drawEffect(PreparedRenderType draw, ExecuteInfo geometry,
      GpuBufferSlice projection, ProjectionType projectionType) {
    var previousProjection = RenderSystem.getProjectionMatrixBuffer();
    var previousType = RenderSystem.getProjectionType();
    try (var ignored = IrisEffectTarget.begin()) {
      RenderSystem.setProjectionMatrix(projection, projectionType);
      var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
      var color = IrisEffectTarget.color(target);
      var pipeline = IrisEffectTarget.pipeline(draw.pipeline(), color.texture().getFormat());
      var prepared = new PreparedRenderType(draw.name(), pipeline, null,
          draw.dynamicTransforms(), draw.scissorState(), draw.textures());
      try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
          prepared::name, color, Optional.empty(), IrisEffectTarget.depth(target), OptionalDouble.empty())) {
        prepared.drawFromBuffer(geometry, pass);
      }
    } finally {
      RenderSystem.setProjectionMatrix(previousProjection, previousType);
    }
  }
}
