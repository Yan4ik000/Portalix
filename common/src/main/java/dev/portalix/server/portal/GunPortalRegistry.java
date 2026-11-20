package dev.portalix.server.portal;

import dev.portalix.api.PortalColor;
import dev.portalix.api.PortalEndpoint;
import dev.portalix.api.PortalInstance;
import dev.portalix.item.PortalixIdentity;
import dev.portalix.item.PortalixVariant;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.PortalStatePayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalTopology;
import dev.portalix.portal.placement.BlockPortalPlacement;
import dev.portalix.portal.placement.PortalSurface;
import dev.portalix.world.chunk.ChunkDeliveryAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GunPortalRegistry {
  private final MinecraftServer server;

  private record Member(PortalInstance portal, PortalSurface.Footprint footprint, long[] columns) {
    PortalEndpoint endpoint() {
      return portal.endpoint();
    }
  }

  private record Pair(UUID gun, Member blue, Member orange) {}

  private static final class Subscription {
    final Map<UUID, Pair> known = new HashMap<>();
    final Set<UUID> changedDelivery = new HashSet<>();
    ServerLevel level;
    ChunkTrackingView primary = ChunkTrackingView.EMPTY;
  }

  private final Map<UUID, Pair> states = new HashMap<>();
  private long revision;
  private final Set<UUID> dirty = new HashSet<>();
  private final PortalSpatialIndex spatial = new PortalSpatialIndex();
  private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Set<UUID>>> footprints = new HashMap<>();
  private final Map<ResourceKey<Level>, Map<UUID, List<PortalLink>>> linksByLevel = new HashMap<>();
  private final Map<UUID, Subscription> subscriptions = new HashMap<>();

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
    return state.blue() == null || state.orange().portal.identity() > state.blue().portal.identity()
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
          members.add(member.portal);
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
    if (overlaps(gun, color, endpoint)) return false;
    Pair previous = states.get(gun);
    var footprint = PortalSurface.placementFootprint(endpoint);
    long beforeClearance = revision;
    BooleanSupplier stillApplicable = () ->
        previous == states.get(gun) && (beforeClearance == revision || !overlaps(gun, color, endpoint));
    if (!BlockPortalPlacement.prepare(player, endpoint, footprint, stillApplicable)
        || !stillApplicable.getAsBoolean()) return false;
    var blue = previous == null ? null : previous.blue();
    var orange = previous == null ? null : previous.orange();
    var portal = new PortalInstance(endpoint, server.getTickCount(), ++revision, variant);
    var columns = new LongOpenHashSet();
    for (var block : footprint.blocks()) columns.add(ChunkPos.pack(block.getX() >> 4, block.getZ() >> 4));
    var member = new Member(portal, footprint, columns.toLongArray());
    update(
        gun,
        color == PortalColor.BLUE ? member : blue,
        color == PortalColor.ORANGE ? member : orange);
    return true;
  }

  private boolean overlaps(UUID gun, PortalColor color, PortalEndpoint endpoint) {
    return obstacles(gun, color, endpoint.dimension(), endpoint.center()).stream()
        .anyMatch(other -> PortalSurface.overlapsPortal(endpoint, other));
  }

  private void update(UUID gun, Member blue, Member orange) {
    Pair state = new Pair(gun, blue, orange);
    dirty.add(gun);
    Pair previous = states.get(gun);
    if (previous != null) {
      removeIndex(gun, previous.blue());
      removeIndex(gun, previous.orange());
    }
    if (blue == null && orange == null) states.remove(gun);
    else states.put(gun, state);
    addIndex(gun, blue);
    addIndex(gun, orange);
    updateLinks(gun, linkedDimension(previous), linkedDimension(state), state);
    Set<ServerPlayer> observers = new HashSet<>();
    for (var entry : subscriptions.entrySet()) {
      if (entry.getValue().known.containsKey(gun)) {
        var observer = server.getPlayerList().getPlayer(entry.getKey());
        if (observer != null) observers.add(observer);
      }
    }
    for (var player : server.getPlayerList().getPlayers()) {
      if (hasGun(player, gun)) observers.add(player);
    }
    for (var member : new Member[] {blue, orange}) {
      if (member == null) continue;
      var level = server.getLevel(member.endpoint().dimension());
      if (level != null) {
        for (long column : member.columns)
          observers.addAll(level.getChunkSource().chunkMap.getPlayers(ChunkPos.unpack(column), false));
      }
    }
    for (ServerPlayer observer : observers) refreshGun(observer, gun, true);
  }

  private void addIndex(UUID gun, Member member) {
    if (member == null) return;
    spatial.add(gun, member.portal);
    var columns = footprints.computeIfAbsent(member.endpoint().dimension(),
        ignored -> new Long2ObjectOpenHashMap<>());
    for (long column : member.columns)
      columns.computeIfAbsent(column, ignored -> new HashSet<>()).add(gun);
  }

  private void removeIndex(UUID gun, Member member) {
    if (member == null) return;
    spatial.remove(gun, member.portal);
    var dimension = member.endpoint().dimension();
    var columns = footprints.get(dimension);
    if (columns == null) return;
    for (long column : member.columns) {
      var guns = columns.get(column);
      if (guns != null && guns.remove(gun) && guns.isEmpty()) columns.remove(column);
    }
    if (columns.isEmpty()) footprints.remove(dimension);
  }

  private static PortalInstance portal(Member member) {
    return member == null ? null : member.portal;
  }

  private static ResourceKey<Level> linkedDimension(Pair pair) {
    if (pair == null || pair.blue() == null || pair.orange() == null) return null;
    var dimension = pair.blue().endpoint().dimension();
    return dimension.equals(pair.orange().endpoint().dimension()) ? dimension : null;
  }

  private void updateLinks(UUID gun, ResourceKey<Level> before, ResourceKey<Level> after, Pair pair) {
    if (before != null && !before.equals(after)) {
      var links = linksByLevel.get(before);
      links.remove(gun);
      if (links.isEmpty()) linksByLevel.remove(before);
      publishLinks(before, links);
    }
    if (after != null) {
      var links = linksByLevel.computeIfAbsent(after, ignored -> new HashMap<>());
      links.put(gun, PortalTopology.linkPair(gun, portal(pair.blue()), portal(pair.orange())));
      publishLinks(after, links);
    }
  }

  private void publishLinks(ResourceKey<Level> dimension, Map<UUID, List<PortalLink>> pairs) {
    var level = server.getLevel(dimension);
    if (level != null)
      PortalClosureResolver.updateLinks(level, pairs.values().stream().flatMap(List::stream).toList());
  }

  private void send(ServerPlayer observer, Pair state, boolean effects) {
    if (ModNetworking.canPlayerReceive(observer, PortalStatePayload.TYPE))
      ModNetworking.sendToPlayer(
          observer,
          new PortalStatePayload(
              state.gun(),
              ++revision,
              server.getTickCount(),
              portal(state.blue()),
              portal(state.orange()),
              effects));
  }

  public void synchronize(ServerPlayer observer) {
    refresh(observer, true);
  }

  public void observerMoved(ServerPlayer observer) {
    if (states.isEmpty()) return;
    var subscription = subscription(observer);
    var level = observer.level();
    var primary = observer.getChunkTrackingView();
    if (subscription.level != level || !subscription.primary.equals(primary)) {
      subscription.level = level;
      subscription.primary = primary;
      refresh(observer, false);
    }
  }

  public void chunkDeliveryChanged(ServerPlayer observer, long column) {
    var columns = footprints.get(observer.level().dimension());
    if (columns == null) return;
    var guns = columns.get(column);
    if (guns != null) subscription(observer).changedDelivery.addAll(guns);
  }

  public void observePortal(ServerPlayer observer, UUID gun) {
    refreshGun(observer, gun, false);
  }

  private boolean near(ServerPlayer observer, Member member) {
    if (member == null || !member.endpoint().dimension().equals(observer.level().dimension()))
      return false;
    var chunks = observer.level().getChunkSource().chunkMap;
    for (long column : member.columns)
      if (chunks.isChunkTracked(observer, ChunkPos.getX(column), ChunkPos.getZ(column))) return true;
    return false;
  }

  private Subscription subscription(ServerPlayer observer) {
    return subscriptions.computeIfAbsent(observer.getUUID(), key -> new Subscription());
  }

  private void refreshGun(ServerPlayer observer, UUID gun, boolean effects) {
    var known = subscription(observer).known;
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
    if (states.isEmpty()) return;
    var known = subscription(observer).known;
    Set<UUID> inventory = new HashSet<>();
    for (int slot = 0; slot < observer.getInventory().getContainerSize(); slot++) {
      UUID gun = PortalixIdentity.get(observer.getInventory().getItem(slot));
      if (gun != null) inventory.add(gun);
    }
    Set<UUID> candidates = new HashSet<>(known.keySet());
    candidates.addAll(inventory);
    var level = observer.level();
    var columns = footprints.get(level.dimension());
    if (columns != null) {
      var primary = observer.getChunkTrackingView();
      var delivered = ((ChunkDeliveryAccess) observer.connection.chunkSender).portalix$presentColumns(level);
      if (primary instanceof ChunkTrackingView.Positioned positioned
          && columns.size() > delivered.size() + primaryArea(positioned)) {
        int radius = positioned.viewDistance() + 1;
        var center = positioned.center();
        for (int x = center.x() - radius; x <= center.x() + radius; x++)
          for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            var guns = columns.get(ChunkPos.pack(x, z));
            if (guns != null) candidates.addAll(guns);
          }
        for (long column : delivered) {
          var guns = columns.get(column);
          if (guns != null) candidates.addAll(guns);
        }
      } else {
        var entries = columns.long2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
          var entry = entries.next();
          long column = entry.getLongKey();
          if (delivered.contains(column) || primary.contains(ChunkPos.getX(column), ChunkPos.getZ(column)))
            candidates.addAll(entry.getValue());
        }
      }
    }
    Map<UUID, Pair> wanted = new HashMap<>();
    for (var gun : candidates) {
      var state = states.get(gun);
      if (state != null && (inventory.contains(gun) || near(observer, state.blue()) || near(observer, state.orange())))
        wanted.put(gun, state);
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

  private static int primaryArea(ChunkTrackingView.Positioned view) {
    int width = 2 * view.viewDistance() + 3;
    return width * width;
  }

  public void forget(UUID observer) {
    subscriptions.remove(observer);
  }

  public Set<UUID> tick() {
    for (var state : new ArrayList<>(states.values())) {
      Member blue = validate(state.blue());
      Member orange = validate(state.orange());
      if (blue != state.blue() || orange != state.orange()) {
        update(state.gun(), blue, orange);
      }
    }
    boolean inventoryCheck = server.getTickCount() % 10 == 0;
    for (var observer : server.getPlayerList().getPlayers()) {
      var subscription = subscriptions.get(observer.getUUID());
      if (inventoryCheck) refresh(observer, false);
      else if (subscription != null)
        for (var gun : subscription.changedDelivery) refreshGun(observer, gun, false);
      if (subscription != null) subscription.changedDelivery.clear();
    }
    Set<UUID> changed = Set.copyOf(dirty);
    dirty.clear();
    return changed;
  }

  private Member validate(Member member) {
    return member != null && BlockPortalPlacement.isValid(server, member.endpoint(), member.footprint)
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
    footprints.clear();
    dirty.clear();
  }
}
