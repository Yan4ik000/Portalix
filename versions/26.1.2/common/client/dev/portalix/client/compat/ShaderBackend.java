package dev.portalix.client.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.iris.IrisDrawContext;
import dev.portalix.client.portal.render.PortalScreenRegion;
import net.minecraft.client.renderer.rendertype.RenderSetup.TextureAndSampler;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;

public interface ShaderBackend {
  ShaderBackend INSTANCE = OptionalRenderServices.load(ShaderBackend.class, "iris");

  boolean active();
  boolean shadowPass();
  void shadowMatrices(Matrix4f projection, Matrix4f rotation);
  boolean vanillaEntityShadows();
  boolean overrides(RenderPipeline pipeline);
  boolean overridesGeometry(RenderPipeline pipeline);
  boolean terrainSeparateOcclusion();
  void register(RenderPipeline pipeline);
  void invalidatePrimaryHistory();
  Scene scene();
  void renderLevel(Runnable render);
  IrisDrawContext captureDrawContext();
  RenderScope itemContext(Item item);
  RenderScope drawContext(IrisDrawContext context);
  long vertexKey();
  boolean supportsColorEffects();
  RenderScope beginEffect();
  void clearEffects();
  void endFrame();
  PortalDepthTextures portalDepthTextures(boolean opensDepth);
  void aperture(PortalScreenRegion region, Runnable draw);
  void submit(Runnable draw);
  void clearComposite();

  record PortalDepthTextures(TextureAndSampler world, TextureAndSampler hand,
      TextureAndSampler occlusion) {}

  interface Scene extends AutoCloseable {
    void invalidateHistory();
    boolean exchangePrimary();
    RenderScope extract();
    RenderScope begin();
    @Override void close();
  }
}
