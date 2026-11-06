package dev.portalix.fabric.client;

import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.gun.model.PortalixItemModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public final class PortalixFabricClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register(
        (model, item) -> PortalixItemModel.MODELS.contains(item.itemId())
            ? new PortalixItemModel(model) : model));
    ClientNetworking.register(new FabricClientNetworking());
    PortalixClient.register();
    PortalixClient.initialize();
    ClientTickEvents.END_CLIENT_TICK.register(PortalixClient::tick);
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> PortalixClient.disconnected(client.player));
  }
}
