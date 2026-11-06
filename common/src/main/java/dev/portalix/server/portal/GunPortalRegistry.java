package dev.portalix.server.portal;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.PortalStatePayload;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.BlockPortalPlacement;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.server.EntityObservers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GunPortalRegistry {
  private final MinecraftServer server;

  private record Pair(UUID gun, PortalInstance blue, PortalInstance orange) {}

  private final Map<UUID, Pair> states = new HashMap<>();
  private long revision;
  private final Set<UUID> dirty = new HashSet<>();
  private final PortalSpatialIndex spatial = new PortalSpatialIndex();
  private final Map<UUID, Map<UUID, Pair>> subscriptions = new HashMap<>();

  public GunPortalRegistry(MinecraftServer server) {
    this.server = server;
  }

  public int hudMask(UUID gun) {
    Pair state = states.get(gun);
    if (state == null) return 0;
    return (state.blue() != null ? PortalColor.BLUE.mask() : 0)
        | (state.orange() != null ? PortalColor.ORANGE.mask() : 0);
  }

  public PortalColor latestColor(UUID gun) {
    Pair state = states.get(gun);
    if (state == null || state.orange() == null) return PortalColor.BLUE;
    return state.blue() == null || state.orange().identity() > state.blue().identity()
        ? PortalColor.ORANGE
        : PortalColor.BLUE;
  }

  public PortalEndpoint unlinkedEndpoint(ServerPlayer player) {
    var gun = PortalixIdentity.get(player.getMainHandItem());
    var state = states.get(gun);
    if (state == null || state.blue() != null && state.orange() != null) return null;
    var member = state.blue() != null ? state.blue() : state.orange();
    return member.endpoint().dimension().equals(player.level().dimension())
        ? member.endpoint() : null;
  }

  public List<PortalEndpoint> obstacles(
      UUID gun, PortalColor replacing, ResourceKey<Level> dimension, Vec3 impact) {
    List<PortalInstance> members = new ArrayList<>();

    double reach = Math.sqrt(PortalSurface.MAX_BUMP_DISTANCE_SQUARED) + Math.sqrt(5);
    for (UUID candidate :
        spatial.candidates(
            dimension, impact.x - reach, impact.z - reach, impact.x + reach, impact.z + reach)) {
      var state = states.get(candidate);
      for (PortalColor color : PortalColor.values()) {
        var member = color == PortalColor.BLUE ? state.blue() : state.orange();
        if (member != null
            && member.endpoint().dimension().equals(dimension)
            && !(candidate.equals(gun) && replacing == color)
            && member.endpoint().center().distanceToSqr(impact) <= reach * reach)
          members.add(member);
      }
    }
    members.sort(Comparator.comparingLong(PortalInstance::identity));
    return members.stream().map(PortalInstance::endpoint).toList();
  }

  public boolean apply(
      ServerPlayer player,
      UUID gun,
      PortalColor color,
      PortalEndpoint endpoint,
      PortalixVariant variant) {
    if (obstacles(gun, color, endpoint.dimension(), endpoint.center()).stream()
        .anyMatch(other -> PortalSurface.overlapsPortal(endpoint, other))) return false;
    Pair previous = states.get(gun);
    var blue = previous == null ? null : previous.blue();
    var orange = previous == null ? null : previous.orange();
    var member = new PortalInstance(endpoint, server.getTickCount(), ++revision, variant);
    if (!BlockPortalPlacement.prepare(player, endpoint)) return false;
    update(
        gun,
        color == PortalColor.BLUE ? member : blue,
        color == PortalColor.ORANGE ? member : orange);
    return true;
  }

  private void update(UUID gun, PortalInstance blue, PortalInstance orange) {
    Pair state = new Pair(gun, blue, orange);
    dirty.add(gun);
    Pair previous = states.get(gun);
    if (previous != null) {
      spatial.remove(gun, previous.blue());
      spatial.remove(gun, previous.orange());
    }
    if (blue == null && orange == null) states.remove(gun);
    else states.put(gun, state);
    spatial.add(gun, blue);
    spatial.add(gun, orange);
    publishLinks();
    Set<ServerPlayer> observers = new HashSet<>();
    for (var entry : subscriptions.entrySet()) {
      if (entry.getValue().containsKey(gun)) {
        var observer = server.getPlayerList().getPlayer(entry.getKey());
        if (observer != null) observers.add(observer);
      }
    }
    for (var player : server.getPlayerList().getPlayers()) {
      if (hasGun(player, gun)) observers.add(player);
    }
    for (var member : new PortalInstance[] {blue, orange}) {
      if (member == null) continue;
      var level = server.getLevel(member.endpoint().dimension());
      if (level != null) {
        var footprint = PortalSurface.placementFootprint(member.endpoint());
        for (var block : footprint.blocks()) observers.addAll(EntityObservers.tracking(level, block));
      }
    }
    for (ServerPlayer observer : observers) refreshGun(observer, gun, true);
  }

  private void publishLinks() {
    var links =
        states.values().stream()
            .flatMap(
                state ->
                    PortalTopology.linkPair(state.gun(), state.blue(), state.orange()).stream())
            .toList();
    for (var level : server.getAllLevels()) {
      PortalTopology.updateLinks(
          level,
          links.stream()
              .filter(link -> link.entrance().endpoint().dimension().equals(level.dimension()))
              .toList());
    }
  }

  private void send(ServerPlayer observer, Pair state, boolean effects) {
    if (ModNetworking.canPlayerReceive(observer, PortalStatePayload.TYPE))
      ModNetworking.sendToPlayer(
          observer,
          new PortalStatePayload(
              state.gun(),
              ++revision,
              server.getTickCount(),
              state.blue(),
              state.orange(),
              effects));
  }

  public void synchronize(ServerPlayer observer) {
    refresh(observer, true);
  }

  public void observerMoved(ServerPlayer observer) {
    refresh(observer, false);
  }

  public void observePortal(ServerPlayer observer, UUID gun) {
    refreshGun(observer, gun, false);
  }

  private boolean near(ServerPlayer observer, PortalInstance member) {
    if (member == null || !member.endpoint().dimension().equals(observer.level().dimension()))
      return false;
    var footprint = PortalSurface.placementFootprint(member.endpoint());
    var chunks = observer.level().getChunkSource().chunkMap;
    return footprint.blocks().stream()
        .anyMatch(pos -> chunks.isChunkTracked(observer, pos.getX() >> 4, pos.getZ() >> 4));
  }

  private void refreshGun(ServerPlayer observer, UUID gun, boolean effects) {
    var known = subscriptions.computeIfAbsent(observer.getUUID(), key -> new HashMap<>());
    Pair state = states.get(gun);
    boolean wanted =
        state != null
            && (hasGun(observer, gun)
                || near(observer, state.blue())
                || near(observer, state.orange()));
    if (wanted) {
      if (known.put(gun, state) != state) send(observer, state, effects);
    } else if (known.remove(gun) != null) {
      send(observer, new Pair(gun, null, null), effects);
    }
  }

  private void refresh(ServerPlayer observer, boolean force) {
    var known = subscriptions.computeIfAbsent(observer.getUUID(), key -> new HashMap<>());
    Set<UUID> inventory = new HashSet<>();
    for (int slot = 0; slot < observer.getInventory().getContainerSize(); slot++) {
      UUID gun = PortalixIdentity.get(observer.getInventory().getItem(slot));
      if (gun != null) inventory.add(gun);
    }
    Map<UUID, Pair> wanted = new HashMap<>();
    for (var state : states.values()) {
      if (inventory.contains(state.gun())
          || near(observer, state.blue())
          || near(observer, state.orange())) wanted.put(state.gun(), state);
    }
    for (UUID gun : known.keySet()) {
      if (!wanted.containsKey(gun)) send(observer, new Pair(gun, null, null), false);
    }
    for (var entry : wanted.entrySet()) {
      if (force || known.get(entry.getKey()) != entry.getValue())
        send(observer, entry.getValue(), false);
    }
    known.clear();
    known.putAll(wanted);
  }

  public void forget(UUID observer) {
    subscriptions.remove(observer);
  }

  public Set<UUID> tick() {
    for (var state : new ArrayList<>(states.values())) {
      PortalInstance blue = validate(state.blue());
      PortalInstance orange = validate(state.orange());
      if (blue != state.blue() || orange != state.orange()) {
        update(state.gun(), blue, orange);
      }
    }
    if (server.getTickCount() % 10 == 0) {
      for (ServerPlayer observer : server.getPlayerList().getPlayers()) refresh(observer, false);
    }
    Set<UUID> changed = Set.copyOf(dirty);
    dirty.clear();
    return changed;
  }

  private PortalInstance validate(PortalInstance member) {
    return member != null && BlockPortalPlacement.isValid(server, member.endpoint())
        ? member
        : null;
  }

  public boolean clear(UUID gun) {
    if (!states.containsKey(gun)) return false;
    update(gun, null, null);
    return true;
  }

  private static boolean hasGun(ServerPlayer player, UUID gun) {
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      if (gun.equals(PortalixIdentity.get(player.getInventory().getItem(slot)))) return true;
    }
    return false;
  }

  public void clearAll() {
    for (UUID gun : new ArrayList<>(states.keySet())) clear(gun);
    subscriptions.clear();
    spatial.clear();
    dirty.clear();
  }
}
