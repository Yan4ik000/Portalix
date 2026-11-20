package dev.portalix.client.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.portalix.client.compat.RenderScope;
import dev.portalix.client.compat.ShaderBackend;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.compat.sodium.SodiumShaderAbi;
import dev.portalix.client.portal.render.PortalScreenRegion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.world.item.Item;
import org.joml.Matrix4f;

public final class IrisShaderBackend implements ShaderBackend {
  private static final ShaderDefines SEPARATE_OCCLUSION =
      ShaderDefines.builder().define(SodiumShaderAbi.SEPARATE_OCCLUSION).build();
  private final Map<TerrainFormat, RenderPipeline> terrainFallbacks = new HashMap<>();

  private record TerrainFormat(RenderPipeline pipeline, VertexFormat format, boolean separateOcclusion) {}

  @Override
  public boolean active() {
    return Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline;
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
    var pipeline = Iris.getPipelineManager().getPipelineNullable();
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
    var current = Iris.getPipelineManager().getPipelineNullable();
    return current instanceof IrisRenderingPipeline iris
        && IrisPipelines.getPipeline(iris, pipeline) != null;
  }

  @Override
  public RenderPipeline terrainPipeline(RenderPipeline pipeline, VertexFormat format) {
    if (format == ChunkMeshFormats.COMPACT.getVertexFormat() || active()) return pipeline;
    // Iris keeps the compact binding as its override key, even for shared extended meshes.
    var terrain = new TerrainFormat(pipeline, format, WorldRenderingSettings.INSTANCE.shouldUseSeparateAo());
    return terrainFallbacks.computeIfAbsent(terrain, key -> {
      var variant = new PipelineVariant(key.pipeline, key.pipeline.getLocation().withSuffix("_portalix_terrain"))
          .geometry(key.format, key.pipeline.getPrimitiveTopology());
      if (key.separateOcclusion) variant.shaderDefines(SEPARATE_OCCLUSION);
      return variant.build();
    });
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
    var primary = Iris.getPipelineManager().getPipelineNullable();
    if (primary instanceof IrisPipelineHistory history) history.portalix$invalidateHistory();
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
  public RenderScope beginEffect() {
    return IrisEffectFramebuffer.begin();
  }

  @Override
  public void clearEffects() {
    terrainFallbacks.clear();
    IrisEffectFramebuffer.clear();
  }

  @Override
  public void endFrame() {
    IrisEffectFramebuffer.endFrame();
  }

  @Override
  public List<PreparedRenderType.Texture> handTextures(boolean opensDepth) {
    return IrisPortalComposite.handTextures(opensDepth);
  }

  @Override
  public void aperture(PortalScreenRegion region, Runnable draw) {
    IrisPortalComposite.aperture(region, draw);
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
