package dev.portalix.neoforge;

import dev.portalix.PortalixMod;
import dev.portalix.network.ModNetworking;
import dev.portalix.server.PortalServerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(PortalixMod.MOD_ID)
public final class PortalixNeoForge {
  public PortalixNeoForge(IEventBus bus) {
    NeoForgePortalData.register(bus);
    PortalixMod.initialize();
    bus.addListener((RegisterPayloadHandlersEvent event) ->
        ModNetworking.register(new NeoForgeNetworking(event.registrar(PortalixMod.MOD_ID).optional())));
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) ->
        PortalServerEvents.started(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) ->
        PortalServerEvents.stopping(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) ->
        PortalServerEvents.stopped(event.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
        PortalServerEvents.tick(event.getServer()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
      if (event.getEntity() instanceof ServerPlayer player)
        PortalServerEvents.disconnected(player);
    });
    NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) ->
        PortalServerEvents.entityRemoved(event.getEntity(), event.getLevel()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
      if (event.getEntity() instanceof ServerPlayer observer)
        PortalServerEvents.trackingStarted(event.getTarget(), observer);
    });
    NeoForge.EVENT_BUS.addListener((PlayerEvent.StopTracking event) -> {
      if (event.getEntity() instanceof ServerPlayer observer)
        PortalServerEvents.trackingStopped(event.getTarget(), observer);
    });
  }
}
