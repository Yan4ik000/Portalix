package dev.portalix.neoforge.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.gun.model.PortalixItemModel;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PortalixMod.MOD_ID, dist = Dist.CLIENT)
public final class PortalixNeoForgeClient {
  public PortalixNeoForgeClient(IEventBus bus) {
    bus.addListener((ModelEvent.ModifyBakingResult event) ->
        event.getBakingResult().itemStackModels().replaceAll(
            (id, model) -> PortalixItemModel.MODELS.contains(id) ? new PortalixItemModel(model) : model));
    bus.addListener((RegisterClientPayloadHandlersEvent event) ->
        ClientNetworking.register(new NeoForgeClientNetworking(event)));
    PortalixClient.register();
    bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(PortalixClient::initialize));
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) ->
        PortalixClient.tick(Minecraft.getInstance()));
    NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
        PortalixClient.disconnected(event.getPlayer()));
  }
}
