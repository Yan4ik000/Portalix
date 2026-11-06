package dev.portalix.fabric.client;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.gun.model.GunMeshFeatureRenderer;
import dev.portalix.client.gun.render.GunRenderEvents;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.render.LinearParticleRenderer;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3fc;

public final class FabricClientPlatform implements ClientPlatform {
  @Override
  public void registerFeatures() {
    FeatureRendererRegistry.register(GunMeshFeatureRenderer.TYPE, GunMeshFeatureRenderer::new);
    FeatureRendererRegistry.register(PortalSurfaceRenderer.TYPE, PortalSurfaceRenderer::new);
    FeatureRendererRegistry.register(LinearParticleRenderer.TYPE, LinearParticleRenderer::new);
  }

  @Override
  public void registerReloadListener(Identifier id, PreparableReloadListener listener) {
    var loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
    loader.registerReloadListener(id, listener);
    loader.addListenerOrdering(ResourceReloaderKeys.Client.TEXTURES, id);
    loader.addListenerOrdering(ResourceReloaderKeys.Client.SHADERS, id);
  }

  @Override
  public KeyMapping.Category registerKeyCategory(Identifier id) {
    return KeyMapping.Category.register(id);
  }

  @Override
  public void registerKeyMapping(KeyMapping mapping) {
    KeyMappingHelper.registerKeyMapping(mapping);
  }

  @Override
  public boolean canServerReceive(CustomPacketPayload.Type<?> type) {
    return ClientPlayNetworking.canSend(type);
  }

  @Override
  public void submit(OrderedSubmitNodeCollector collector, Phase phase, SubmitNode node) {
    collector.submitCustom(switch (phase) {
      case SOLID -> SubmitRenderPhases.SOLID;
      case AFTER_TERRAIN -> SubmitRenderPhases.AFTER_TERRAIN;
      case TRANSLUCENT_CUSTOM_GEOMETRY -> SubmitRenderPhases.TRANSLUCENT_CUSTOM_GEOMETRY;
    }, node);
  }

  @Override
  public void registerRenderEvents() {
    LevelRenderEvents.COLLECT_SUBMITS.register(
        context -> GunRenderEvents.collect(context.submitNodeCollector(), context.levelState()));
    LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(
        (context, outline) -> !GunRenderEvents.hidesBlockOutline(outline.pos()));
    HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original ->
        (graphics, delta) -> {
          original.extractRenderState(graphics, delta);
          GunRenderEvents.renderCrosshair(graphics);
        });
  }

  @Override
  public void chunkLoaded(ClientLevel level, LevelChunk chunk) {
    ClientChunkEvents.CHUNK_LOAD.invoker().onChunkLoad(level, chunk);
  }

  @Override
  public void chunkUnloaded(ClientLevel level, LevelChunk chunk) {
    ClientChunkEvents.CHUNK_UNLOAD.invoker().onChunkUnload(level, chunk);
  }

  @Override
  public RenderPipeline pipeline(PipelineVariant variant) {
    return new FabricPipeline(variant);
  }

  private static final class FabricPipeline extends RenderPipeline {
    private final boolean guiDrawMode;

    FabricPipeline(PipelineVariant variant) {
      super(
          variant.location(),
          variant.vertexShader(),
          variant.source().getFragmentShader(),
          variant.defines(),
          variant.layouts(),
          variant.colors(),
          variant.source().getDepthStencilState(),
          variant.source().getPolygonMode(),
          variant.source().isCull(),
          variant.bindings(),
          variant.topology(),
          variant.source().getSortKey());
      guiDrawMode = variant.source().usePipelineDrawModeForGui();
    }

    @Override
    public boolean usePipelineDrawModeForGui() {
      return guiDrawMode;
    }
  }

  @Override
  public Supplier<Vector3fc[]> itemExtents(QuadCollection quads, Supplier<Vector3fc[]> original) {
    if (!(quads instanceof MeshQuadCollection mesh)) return original;
    return Suppliers.memoize(() -> {
      var vertices = new ArrayList<Vector3fc>();
      mesh.getMesh().forEach(quad -> {
        for (int i = 0; i < 4; i++) vertices.add(quad.copyPos(i, null));
      });
      return vertices.toArray(Vector3fc[]::new);
    });
  }
}
