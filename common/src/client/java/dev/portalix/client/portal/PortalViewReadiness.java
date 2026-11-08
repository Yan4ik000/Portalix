package dev.portalix.client.portal;

import dev.portalix.client.ClientNetworking;
import dev.portalix.network.portal.PortalViewReadyPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.traversal.PortalTraversalReadiness;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
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

  public static boolean ready(PortalLink link) {
    var client = Minecraft.getInstance();
    if (client.level == null || !exitLoaded(client, link)) return false;
    notifyServer(link);
    return true;
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
        Math.abs(ready.x - position.x()) <= 1 && Math.abs(ready.z - position.z()) <= 1);
  }

  private static void notifyServer(PortalLink link) {
    long entrance = link.entrance().identity(), generation = link.generation();
    var known = notified.get(entrance);
    if (known != null && known.generation == generation) return;
    var center = link.exit().endpoint().center();
    notified.put(entrance, new Ready(generation,
        SectionPos.posToSectionCoord(center.x), SectionPos.posToSectionCoord(center.z)));
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
    checkedLinks = List.of();
    chunksChanged = false;
  }

  private record Ready(long generation, int x, int z) {}
}
