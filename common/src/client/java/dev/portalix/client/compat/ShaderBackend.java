package dev.portalix.client.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.portalix.client.compat.iris.IrisDrawContext;
import java.util.List;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
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
  void register(RenderPipeline pipeline);
  void invalidatePrimaryHistory();
  Scene scene();
  IrisDrawContext captureDrawContext();
  RenderScope itemContext(Item item);
  RenderScope drawContext(IrisDrawContext context);
  long vertexKey();
  RenderTarget effectTarget();
  RenderScope beginEffect();
  void clearEffects();
  void endFrame();
  List<PreparedRenderType.Texture> handTextures();
  void aperture(Runnable draw);
  void submit(Runnable draw);
  void clearComposite();

  interface Scene extends AutoCloseable {
    void invalidateHistory();
    boolean exchangePrimary();
    RenderScope extract();
    RenderScope begin();
    @Override void close();
  }
}
