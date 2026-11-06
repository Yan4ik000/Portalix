package dev.portalix.carry;

import dev.portalix.portal.PortalLink;
import dev.portalix.portal.PortalLinkId;
import dev.portalix.portal.PortalRoutes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class CarryBindings {
  private final Map<UUID, Relation> byOwner = new HashMap<>();
  private final Map<UUID, Relation> byTarget = new HashMap<>();
  private final Map<Integer, UUID> ownerHints = new HashMap<>();
  private final Map<Integer, UUID> targetHints = new HashMap<>();

  private static CarryBindings forLevel(Level level) {
    return ((Access) level).portalix$relations();
  }

  public static void bind(Level level, int owner, int target) {
    if (level == null) return;
    Entity ownerEntity = level.getEntity(owner);
    Entity targetEntity = level.getEntity(target);
    if (ownerEntity != null && targetEntity != null)
      bind(level, owner, target, ownerEntity.getUUID(), targetEntity.getUUID());
  }

  public static void bind(Level level, int owner, int target, UUID ownerUuid, UUID targetUuid) {
    if (level == null
        || owner < 0
        || target < 0
        || owner == target
        || ownerUuid == null
        || targetUuid == null
        || ownerUuid.equals(targetUuid)) return;
    CarryBindings index = forLevel(level);
    Relation existing = index.byOwner.get(ownerUuid);
    if (existing != null
        && existing.ownerId == owner
        && existing.targetId == target
        && existing.targetUuid.equals(targetUuid)) return;
    unbindOwner(level, owner);
    index.removeRelation(index.byOwner.get(ownerUuid));
    index.removeRelation(index.byTarget.get(targetUuid));
    UUID displacedId = index.targetHints.get(target);
    if (displacedId != null) index.removeRelation(index.byTarget.get(displacedId));
    Relation relation = new Relation(owner, target, ownerUuid, targetUuid);
    index.byOwner.put(ownerUuid, relation);
    index.byTarget.put(targetUuid, relation);
    index.ownerHints.put(owner, ownerUuid);
    index.targetHints.put(target, targetUuid);
  }

  public static void unbindOwner(Level level, int owner) {
    if (level == null) return;
    CarryBindings index = forLevel(level);
    UUID id = index.ownerHints.remove(owner);
    if (id != null) index.removeRelation(index.byOwner.get(id));
  }

  public static void unbindEntity(Level level, Entity entity) {
    if (level == null || entity == null) return;
    CarryBindings index = forLevel(level);
    UUID id = entity.getUUID();
    index.removeRelation(index.byOwner.get(id));
    index.removeRelation(index.byTarget.get(id));
    index.ownerHints.remove(entity.getId());
    index.targetHints.remove(entity.getId());
  }

  private void removeRelation(Relation relation) {
    if (relation == null) return;
    byOwner.remove(relation.ownerUuid);
    byTarget.remove(relation.targetUuid);
    ownerHints.remove(relation.ownerId);
    targetHints.remove(relation.targetId);
  }

  public static int heldEntityId(Level level, int owner) {
    if (level == null) return -1;
    CarryBindings index = forLevel(level);
    UUID id = index.ownerHints.get(owner);
    Relation relation = id == null ? null : index.byOwner.get(id);
    if (relation == null) return -1;
    if (!matchesEntities(level, relation)) {
      index.removeRelation(relation);
      return -1;
    }
    return relation.targetId;
  }

  public static int holderId(Entity target) {
    Level level = target.level();
    CarryBindings index = forLevel(level);
    Relation relation = index.byTarget.get(target.getUUID());
    if (relation == null) return -1;
    if (!matchesEntities(level, relation)) {
      index.removeRelation(relation);
      return -1;
    }

    return level.getEntity(relation.ownerId) != null && level.getEntity(relation.targetId) == target
        ? relation.ownerId
        : -1;
  }

  private static boolean matchesEntities(Level level, Relation relation) {
    Entity owner = level.getEntity(relation.ownerId);
    Entity target = level.getEntity(relation.targetId);
    if ((owner != null && !relation.ownerUuid.equals(owner.getUUID()))
        || (target != null && !relation.targetUuid.equals(target.getUUID()))) return false;
    if (owner != null && !owner.isAlive()) return false;
    if (target != null && !target.isAlive()) return false;
    return true;
  }

  public static void clear(Level level) {
    if (level == null) return;
    CarryBindings index = forLevel(level);
    index.byOwner.clear();
    index.byTarget.clear();
    index.ownerHints.clear();
    index.targetHints.clear();
  }

  public static boolean ignores(Entity first, Entity second) {
    return second != null
        && first.level() == second.level()
        && (holderId(first) == second.getId() || holderId(second) == first.getId());
  }

  public static void crossed(Entity entity, PortalLink link) {
    crossed(entity, PortalLinkId.fromLink(link), 1);
  }

  public static void ownerCrossed(Entity entity, PortalLinkId link) {
    crossedOwner(forLevel(entity.level()).byOwner.get(entity.getUUID()), link);
  }

  private static void crossed(Entity entity, PortalLinkId link, int count) {
    CarryBindings index = forLevel(entity.level());
    crossedOwner(index.byOwner.get(entity.getUUID()), link);
    Relation target = index.byTarget.get(entity.getUUID());
    if (target != null) {
      var route = new ArrayList<>(target.route);
      route.add(link);
      target.route = PortalRoutes.simplifyIds(route);
      target.crossings += count;
    }
  }

  private static void crossedOwner(Relation owner, PortalLinkId link) {
    if (owner != null) {
      var route = new ArrayList<PortalLinkId>();
      route.add(link.inverse());
      route.addAll(owner.route);
      owner.route = PortalRoutes.simplifyIds(route);
    }
  }

  public static long crossings(Entity target) {
    Relation relation = forLevel(target.level()).byTarget.get(target.getUUID());
    return relation == null ? 0 : relation.crossings;
  }

  public static void undoCrossing(Entity entity, PortalLink link) {
    crossed(entity, PortalLinkId.fromLink(link.inverse()), -1);
  }

  public static List<PortalLinkId> route(Entity target) {
    Relation relation = forLevel(target.level()).byTarget.get(target.getUUID());
    return relation == null ? List.of() : relation.route;
  }

  public static void route(Entity target, List<PortalLinkId> route) {
    Relation relation = forLevel(target.level()).byTarget.get(target.getUUID());
    if (relation != null) relation.route = List.copyOf(route);
  }

  public static List<PortalLink> resolve(Entity target) {
    return CarryAnchor.resolveRoute(target, route(target));
  }

  public interface Access {
    CarryBindings portalix$relations();
  }

  private static final class Relation {
    final int ownerId, targetId;
    final UUID ownerUuid, targetUuid;
    List<PortalLinkId> route = List.of();
    long crossings;

    Relation(int owner, int target, UUID ownerUuid, UUID targetUuid) {
      this.ownerId = owner;
      this.targetId = target;
      this.ownerUuid = ownerUuid;
      this.targetUuid = targetUuid;
    }
  }
}
