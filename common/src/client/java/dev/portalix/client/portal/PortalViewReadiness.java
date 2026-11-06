package dev.portalix.client.portal;

import dev.portalix.client.ClientNetworking;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class PortalViewReadiness {
  private static final Long2LongOpenHashMap notified = new Long2LongOpenHashMap();

  private PortalViewReadiness() {}

  public static void register() {
    PortalTraversalReadiness.setClientReadiness((entity, link) -> ready(link));
  }

  public static boolean ready(PortalLink link) {
    var client = Minecraft.getInstance();
    if (client.level == null || !exitLoaded(client, link)) return false;
    notifyServer(link);
    return true;
  }

  public static void tick(Minecraft client) {
    if (client.level == null) return;
    var links = PortalTopology.links(client.level);
    var entries = notified.long2LongEntrySet().fastIterator();
    while (entries.hasNext()) {
      var entry = entries.next();
      boolean present = false;
      for (var link : links) {
        if (link.entrance().identity() == entry.getLongKey()
            && link.generation() == entry.getLongValue()) {
          present = true;
          break;
        }
      }
      if (!present) entries.remove();
    }
    for (var link : links) {
      if (exitLoaded(client, link)) notifyServer(link);
      else notified.remove(link.entrance().identity(), link.generation());
    }
  }

  private static void notifyServer(PortalLink link) {
    long entrance = link.entrance().identity(), generation = link.generation();
    if (notified.containsKey(entrance) && notified.get(entrance) == generation) return;
    notified.put(entrance, generation);
    ClientNetworking.sendToServer(new PortalViewReadyPayload(entrance, generation));
  }

  private static boolean exitLoaded(Minecraft client, PortalLink link) {
    var center = link.exit().endpoint().center();
    int cx = (int) Math.floor(center.x) >> 4, cz = (int) Math.floor(center.z) >> 4;
    for (int x = cx - 1; x <= cx + 1; x++)
      for (int z = cz - 1; z <= cz + 1; z++)
        if (client.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null)
          return false;
    return true;
  }

  public static void clear() {
    notified.clear();
  }
}
