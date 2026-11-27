package dev.portalix.neoforge.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.portalix.PortalixMod;
import dev.portalix.client.compat.minecraft.PipelineVariant;
import dev.portalix.client.gun.render.GunRenderEvents;
import dev.portalix.client.platform.ClientPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.resources.VanillaClientListeners;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientPlatform implements ClientPlatform {
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
  public void registerRenderEvents() {
    NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent event) ->
        GunRenderEvents.collect(event.getSubmitNodeCollector(), event.getLevelRenderState()));
    NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Post event) -> {
      if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)
          && !Minecraft.getInstance().options.hideGui)
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
          variant.source().getSamplers(),
          variant.uniforms(),
          variant.source().getColorTargetState(),
          variant.source().getDepthStencilState(),
          variant.source().getPolygonMode(),
          variant.source().isCull(),
          variant.format(),
          variant.mode(),
          variant.source().getSortKey(),
          variant.source().getStencilTest());
    }
  }
}
