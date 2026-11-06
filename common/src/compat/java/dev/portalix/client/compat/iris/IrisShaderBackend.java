package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.mixin.compat.iris.IrisRenderingPipelineAccessor;
import java.util.List;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;

public final class IrisShaderBackend implements ShaderBackend {
  @Override
  public boolean active() {
    return IrisApi.getInstance().isShaderPackInUse();
  }

  @Override
  public boolean shadowPass() {
    return IrisApi.getInstance().isRenderingShadowPass();
  }

  @Override
  public void shadowMatrices(Matrix4f projection, Matrix4f rotation) {
    projection.set(net.irisshaders.iris.shadows.ShadowRenderer.PROJECTION);
    rotation.set(net.irisshaders.iris.shadows.ShadowRenderer.MODELVIEW);
  }

  @Override
  public boolean vanillaEntityShadows() {
    if (!active()) return true;
    var pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
    return pipeline == null || !pipeline.shouldDisableVanillaEntityShadows();
  }

  @Override
  public boolean overrides(RenderPipeline pipeline) {
    if (!active()) return false;
    if (pipeline.getLocation().getNamespace().equals("iris")) return true;
    return overridesGeometry(pipeline);
  }

  @Override
  public boolean overridesGeometry(RenderPipeline pipeline) {
    if (!active()) return false;
    var current = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
    return current instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris
        && IrisPipelines.getPipeline(iris, pipeline) != null;
  }

  @Override
  public void register(RenderPipeline pipeline) {
    if (!pipeline.getLocation().getNamespace().equals("portalix")) return;
    if (!pipeline.getShaderDefines().flags().contains("EMISSIVE")) {
      IrisPipelines.copyPipeline(RenderPipelines.ENTITY_SOLID, pipeline);
      return;
    }
    IrisApi api = IrisApi.getInstance();
    api.assignPipeline(pipeline, IrisProgram.EMISSIVE_ENTITIES);
    api.assignPipelineShadow(pipeline, IrisShadowProgram.SHADOW_ENTITIES);
  }

  @Override
  public Scene scene() {
    return new IrisScene();
  }

  @Override
  public void invalidatePrimaryHistory() {
    if (!active()) return;
    var primary = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
    if (primary instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris)
      ((IrisTargetHistory) ((IrisRenderingPipelineAccessor) iris).portalix$targets())
          .portalix$invalidateHistory();
  }

  @Override
  public IrisDrawContext captureDrawContext() {
    return IrisDrawState.capture();
  }

  @Override
  public RenderScope itemContext(Item item) {
    return IrisDrawState.forItem(item);
  }

  @Override
  public RenderScope drawContext(IrisDrawContext context) {
    return IrisDrawState.begin(context);
  }

  @Override
  public long vertexKey() {
    return IrisDrawState.vertexKey();
  }

  @Override
  public RenderTarget effectTarget() {
    return IrisEffectFramebuffer.current();
  }

  @Override
  public RenderScope beginEffect() {
    return IrisEffectFramebuffer.begin();
  }

  @Override
  public void clearEffects() {
    IrisEffectFramebuffer.clear();
  }

  @Override
  public void endFrame() {
    IrisEffectFramebuffer.endFrame();
  }

  @Override
  public List<PreparedRenderType.Texture> handTextures() {
    return IrisPortalComposite.handTextures();
  }

  @Override
  public void aperture(Runnable draw) {
    IrisPortalComposite.aperture(draw);
  }

  @Override
  public void submit(Runnable draw) {
    IrisPortalComposite.submit(draw);
  }

  @Override
  public void clearComposite() {
    IrisPortalComposite.clear();
  }
}
