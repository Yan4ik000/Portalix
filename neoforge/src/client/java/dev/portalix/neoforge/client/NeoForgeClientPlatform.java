package dev.portalix.neoforge.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.gun.model.GunMeshFeatureRenderer;
import dev.portalix.client.gun.render.GunRenderEvents;
import dev.portalix.client.platform.ClientPlatform;
import dev.portalix.client.portal.render.PortalSurfaceRenderer;
import dev.portalix.client.render.LinearParticleRenderer;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.joml.Vector3fc;

public final class NeoForgeClientPlatform implements ClientPlatform {
  @Override
  public void registerFeatures() {
    NeoForge.EVENT_BUS.addListener((RegisterFeatureRenderersEvent event) -> {
      event.register(GunMeshFeatureRenderer.TYPE, new GunMeshFeatureRenderer());
      event.register(PortalSurfaceRenderer.TYPE, new PortalSurfaceRenderer());
      event.register(LinearParticleRenderer.TYPE, new LinearParticleRenderer());
    });
  }

  @Override
  public void registerReloadListener(Identifier id, PreparableReloadListener listener) {
    eventBus().addListener((AddClientReloadListenersEvent event) -> {
          event.addListener(id, listener);
          event.addDependency(VanillaClientListeners.TEXTURES, id);
          event.addDependency(VanillaClientListeners.SHADERS, id);
        });
  }

  @Override
  public KeyMapping.Category registerKeyCategory(Identifier id) {
    var category = new KeyMapping.Category(id);
    eventBus().addListener((RegisterKeyMappingsEvent event) -> event.registerCategory(category));
    return category;
  }

  @Override
  public void registerKeyMapping(KeyMapping mapping) {
    eventBus().addListener((RegisterKeyMappingsEvent event) -> event.register(mapping));
  }

  @Override
  public boolean canServerReceive(CustomPacketPayload.Type<?> type) {
    var connection = Minecraft.getInstance().getConnection();
    return connection != null && connection.hasChannel(type);
  }

  private static IEventBus eventBus() {
    return ModList.get().getModContainerById(PortalixMod.MOD_ID).orElseThrow().getEventBus();
  }

  @Override
  public void submit(OrderedSubmitNodeCollector collector, Phase phase, SubmitNode node) {
    collector.submitSpecial(switch (phase) {
      case SOLID -> RenderPhaseKeys.SOLID;
      case AFTER_TERRAIN -> RenderPhaseKeys.AFTER_TERRAIN;
      case TRANSLUCENT_CUSTOM_GEOMETRY -> RenderPhaseKeys.TRANSLUCENT_CUSTOM_GEOMETRY;
    }, node);
  }

  @Override
  public void registerRenderEvents() {
    NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent event) ->
        GunRenderEvents.collect(event.getSubmitNodeCollector(), event.getLevelRenderState()));
    NeoForge.EVENT_BUS.addListener((ExtractBlockOutlineRenderStateEvent event) -> {
      if (GunRenderEvents.hidesBlockOutline(event.getBlockPos())) event.setCanceled(true);
    });
    NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Post event) -> {
      if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
          && !Minecraft.getInstance().gui.hud.isHidden())
        GunRenderEvents.renderCrosshair(event.getGuiGraphics());
    });
  }

  @Override
  public RenderPipeline pipeline(PipelineVariant variant) {
    return new NeoForgePipeline(variant);
  }

  private static final class NeoForgePipeline extends RenderPipeline {
    NeoForgePipeline(PipelineVariant variant) {
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
          variant.source().getStencilTest(),
          variant.source().getSortKey());
    }
  }

  @Override
  public void chunkLoaded(ClientLevel level, LevelChunk chunk) {
    NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
  }

  @Override
  public void chunkUnloaded(ClientLevel level, LevelChunk chunk) {
    NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
  }

  @Override
  public Supplier<Vector3fc[]> itemExtents(QuadCollection quads, Supplier<Vector3fc[]> original) {
    return original;
  }
}
