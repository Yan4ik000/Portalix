package dev.portalix.neoforge.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.config.PortalixClientSettings;
import dev.portalix.client.config.PortalixSettingsScreen;
import dev.portalix.client.gun.model.PortalixItemModel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PortalixMod.MOD_ID, dist = Dist.CLIENT)
public final class PortalixNeoForgeClient {
  public PortalixNeoForgeClient(IEventBus bus, ModContainer container) {
    container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new PortalixSettingsScreen(parent));
    bus.addListener((ModelEvent.ModifyBakingResult event) ->
        event.getBakingResult().itemStackModels().replaceAll(PortalixItemModel::wrap));
    bus.addListener((RegisterClientPayloadHandlersEvent event) ->
        ClientNetworking.register(new NeoForgeClientNetworking(event)));
    PortalixClient.register();
    bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
      PortalixClientSettings.load(FMLPaths.CONFIGDIR.get());
      PortalixClient.initialize();
    }));
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
        PortalixClient.tick(Minecraft.getInstance()));
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
        PortalixClient.disconnected(event.getPlayer()));
  }
}
