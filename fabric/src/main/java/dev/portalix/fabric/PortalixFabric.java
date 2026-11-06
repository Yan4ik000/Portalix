package dev.portalix.fabric;

import dev.portalix.PortalixMod;
import dev.portalix.item.PortalLoot;
import dev.portalix.network.ModNetworking;
import dev.portalix.server.PortalServerEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public final class PortalixFabric implements ModInitializer {
  @Override
  public void onInitialize() {
    PortalixMod.initialize();
    ModNetworking.register(new FabricNetworking());
    ServerLifecycleEvents.SERVER_STARTED.register(PortalServerEvents::started);
    ServerLifecycleEvents.SERVER_STOPPING.register(PortalServerEvents::stopping);
    ServerLifecycleEvents.SERVER_STOPPED.register(PortalServerEvents::stopped);
    ServerTickEvents.END_SERVER_TICK.register(PortalServerEvents::tick);
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> PortalServerEvents.disconnected(handler.player));
    ServerEntityEvents.ENTITY_UNLOAD.register(PortalServerEvents::entityRemoved);
    EntityTrackingEvents.START_TRACKING.register(PortalServerEvents::trackingStarted);
    EntityTrackingEvents.STOP_TRACKING.register(PortalServerEvents::trackingStopped);
    LootTableEvents.MODIFY.register((key, table, source, registries) -> {
      if (source.isBuiltin() && key.equals(BuiltInLootTables.ABANDONED_MINESHAFT))
        table.withPool(PortalLoot.aiModulePool());
    });
  }
}
