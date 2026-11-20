package dev.portalix.client.portal;

import dev.portalix.client.ClientNetworking;
import dev.portalix.client.PortalixClient;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.network.portal.PortalViewStatusPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.PortalViewLimits;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class PortalViewReadiness {
  private static final Long2ObjectOpenHashMap<Ready> notified = new Long2ObjectOpenHashMap<>();
  private static List<PortalLink> checkedLinks = List.of();
  private static boolean chunksChanged;

  private PortalViewReadiness() {}

  public static void register() {
    PortalTraversalReadiness.setClientReadiness((entity, link) -> ready(link));
  }

  public static void registerNetworking(ClientNetworking.Registration registration) {
    registration.register(PortalViewStatusPayload.TYPE, (packet, client) -> {
      PortalixClient.ensureSession(client);
      var known = notified.get(packet.entrance());
      if (known != null && known.generation == packet.generation()) known.confirmed = packet.ready();
    });
  }

  public static boolean ready(PortalLink link) {
    var client = Minecraft.getInstance();
    if (client.level == null || !exitLoaded(client, link)) return false;
    return notifyServer(link).confirmed;
  }

  public static void tick(Minecraft client) {
    if (client.level == null) return;
    var links = PortalTopology.links(client.level);
    if (links != checkedLinks) {
      var entries = notified.long2ObjectEntrySet().fastIterator();
      while (entries.hasNext()) {
        var entry = entries.next();
        if (PortalTopology.find(client.level, entry.getLongKey(), entry.getValue().generation) == null)
          entries.remove();
      }
      checkedLinks = links;
    } else if (!chunksChanged) {
      return;
    }
    chunksChanged = false;
    for (var link : links) {
      var known = notified.get(link.entrance().identity());
      if ((known == null || known.generation != link.generation()) && exitLoaded(client, link))
        notifyServer(link);
    }
  }

  public static void chunkLoaded() {
    chunksChanged = true;
  }

  public static void chunkForgotten(ChunkPos position) {
    notified.values().removeIf(ready ->
        ready.center.getChessboardDistance(position) <= PortalViewLimits.CORE_RADIUS);
  }

  private static Ready notifyServer(PortalLink link) {
    long entrance = link.entrance().identity(), generation = link.generation();
    var known = notified.get(entrance);
    if (known != null && known.generation == generation) return known;
    boolean serverConfirms = ClientNetworking.canServerReceive(PortalViewStatusPayload.TYPE);
    known = new Ready(generation, PortalViewLimits.frontColumn(link.exit().endpoint()), !serverConfirms);
    notified.put(entrance, known);
    ClientNetworking.sendToServer(serverConfirms
        ? new PortalViewStatusPayload(entrance, generation, true) : new PortalViewReadyPayload(entrance, generation));
    return known;
  }

  private static boolean exitLoaded(Minecraft client, PortalLink link) {
    var chunks = client.level.getChunkSource();
    return PortalViewLimits.hasExitCore(link.exit().endpoint(), key ->
        chunks.getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false) != null);
  }

  public static void clear() {
    notified.clear();
    checkedLinks = List.of();
    chunksChanged = false;
  }

  private static final class Ready {
    final long generation;
    final ChunkPos center;
    boolean confirmed;

    Ready(long generation, ChunkPos center, boolean confirmed) {
      this.generation = generation;
      this.center = center;
      this.confirmed = confirmed;
    }
  }
}
