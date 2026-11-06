package dev.portalix.server.portal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

final class PortalSpatialIndex {
  private final Map<ResourceKey<Level>, Map<Long, Set<UUID>>> dimensions = new HashMap<>();

  void add(UUID gun, PortalInstance portal) {
    if (portal == null) return;
    var endpoint = portal.endpoint();
    dimensions
        .computeIfAbsent(endpoint.dimension(), ignored -> new HashMap<>())
        .computeIfAbsent(chunk(endpoint), ignored -> new HashSet<>())
        .add(gun);
  }

  void remove(UUID gun, PortalInstance portal) {
    if (portal == null) return;
    var endpoint = portal.endpoint();
    var chunks = dimensions.get(endpoint.dimension());
    if (chunks == null) return;
    long chunk = chunk(endpoint);
    var guns = chunks.get(chunk);
    if (guns != null) {
      guns.remove(gun);
      if (guns.isEmpty()) chunks.remove(chunk);
    }
    if (chunks.isEmpty()) dimensions.remove(endpoint.dimension());
  }

  Set<UUID> candidates(
      ResourceKey<Level> dimension, double minX, double minZ, double maxX, double maxZ) {
    Set<UUID> result = new HashSet<>();
    var chunks = dimensions.get(dimension);
    if (chunks == null) return result;
    for (int x = (int) Math.floor(minX) >> 4; x <= (int) Math.floor(maxX) >> 4; x++) {
      for (int z = (int) Math.floor(minZ) >> 4; z <= (int) Math.floor(maxZ) >> 4; z++) {
        var guns = chunks.get(ChunkPos.pack(x, z));
        if (guns != null) result.addAll(guns);
      }
    }
    return result;
  }

  void clear() {
    dimensions.clear();
  }

  private static long chunk(PortalEndpoint endpoint) {
    return ChunkPos.pack(
        (int) Math.floor(endpoint.center().x) >> 4, (int) Math.floor(endpoint.center().z) >> 4);
  }
}
