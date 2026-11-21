package dev.portalix.fabric.client;

import dev.portalix.PortalixMod;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.client.PortalixUpdateChecker;
import dev.portalix.client.config.PortalixClientSettings;
import dev.portalix.client.gun.model.PortalixItemModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class PortalixFabricClient implements ClientModInitializer {
  @Override
  public void onInitializeClient() {
    FabricLoader loader = FabricLoader.getInstance();
    PortalixClientSettings.load(loader.getConfigDir());
    String version = loader.getModContainer(PortalixMod.MOD_ID).orElseThrow()
        .getMetadata().getVersion().getFriendlyString();
    ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register(
        (model, item) -> PortalixItemModel.wrap(item.itemId(), model)));
    FabricClientNetworking.registerProtocolCheck();
    ClientNetworking.register(new FabricClientNetworking());
    PortalixClient.register();
    PortalixClient.initialize();
    ClientTickEvents.END_CLIENT_TICK.register(PortalixClient::tick);
    ClientPlayConnectionEvents.JOIN.register((listener, sender, client) ->
        PortalixUpdateChecker.check(client, listener.getConnection(), "fabric", version));
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> PortalixClient.disconnected(client.player));
  }
}
