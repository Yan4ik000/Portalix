package dev.portalix.client.carry;

import dev.portalix.carry.CarryBindings;
import dev.portalix.client.carry.prediction.CarriedEntityPrediction;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryReleasePayload;
import dev.portalix.network.carry.CarryStatePayload;
import dev.portalix.portal.PortalLink;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class ClientCarryController {
  private final Map<UUID, CarriedEntityPrediction> tracks = new HashMap<>();
  private final Minecraft client;
  private long ticks;
  private long renderFrame;

  public ClientCarryController(Minecraft client) {
    this.client = client;
  }

  private double clock(float partial) {
    return ticks + (double) partial;
  }

  private float partial() {
    return client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
  }

  public void clear() {
    tracks.clear();
    ticks = 0;
  }

  public void tick() {
    if (client.level == null || client.player == null) return;
    ticks++;
    var iterator = tracks.values().iterator();
    while (iterator.hasNext()) {
      var track = iterator.next();
      var entity = track.entity();
      boolean present = client.level.getEntity(entity.getId()) == entity && !entity.isRemoved();
      if (!present) {
        if (track.ownerId() >= 0 && client.level.getEntity(track.ownerId()) == null)
          CarryBindings.unbindTarget(entity);
        iterator.remove();
        continue;
      }

      track.tick(clock(0));
      if (track.ownerId() >= 0) {
        Entity owner = client.level.getEntity(track.ownerId());
        if (owner != null && (!owner.getUUID().equals(track.ownerUuid()) || !(owner instanceof Player))) {
          CarryBindings.unbindTarget(entity);
          track.lostOwner(clock(0), 0);
        } else if (owner instanceof Player player && !GunPermissions.canCarry(player))
          track.suspend(clock(0), 0);
      }
      if (track.phase() == CarriedEntityPrediction.Phase.LOCAL_HOLD && entity.isAlive())
        track.advance(client.player, clock(0));
    }
  }

  public void suspend(int owner, int id) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    var track = entity == null ? null : existing(entity);
    if (track == null || track.ownerId() != owner) return;
    float partial = partial();
    track.suspend(clock(partial), partial);
  }

  public void grab(CarryGrabPayload grab) {
    CarriedEntityPrediction track = track(grab.entityId(), grab.entityUuid());
    if (track == null) return;
    float partial = partial();
    boolean local =
        client.player != null
            && client.player.getId() == grab.ownerId()
            && client.player.getUUID().equals(grab.ownerUuid());
    if (!track.grab(grab, local, clock(partial), partial)) return;
    CarryBindings.bind(
        client.level, grab.ownerId(), grab.entityId(), grab.ownerUuid(), grab.entityUuid());
    CarryBindings.route(track.entity(), grab.route());
    for (CarriedEntityPrediction previous : tracks.values()) {
      if (previous != track
          && previous.ownerId() == grab.ownerId()
          && grab.ownerUuid().equals(previous.ownerUuid()))
        previous.lostOwner(clock(partial), partial);
    }
  }

  public void receive(CarryStatePayload update) {
    CarriedEntityPrediction track = update.released()
        ? track(update.entityId(), update.entityUuid())
        : existing(update.entityId(), update.entityUuid());
    if (track == null) return;
    float partial = partial();
    track.update(update, clock(partial), partial);
  }

  public void release(CarryReleasePayload release) {
    CarriedEntityPrediction track = track(release.entityId(), release.entityUuid());
    if (track == null) return;
    float partial = partial();
    track.release(release, clock(partial), partial);
  }

  public void release(int owner, int id) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    CarriedEntityPrediction track = entity == null ? null : existing(entity);
    if (track == null || track.ownerId() != owner) return;
    float partial = partial();
    track.lostOwner(clock(partial), partial);
  }

  public void teleport(Entity entity, Vec3 position) {
    CarriedEntityPrediction track = existing(entity);
    if (track != null) track.teleport(position, clock(partial()));
  }

  public void crossed(Entity entity, PortalLink link) {
    CarriedEntityPrediction track = existing(entity);
    if (track != null) track.crossed(link);
  }

  public Player owner(Entity target) {
    if (client.level == null || target.level() != client.level) return null;
    CarriedEntityPrediction track = existing(target);
    int owner = track == null ? CarryBindings.holderId(target) : track.ownerId();
    return client.level.getEntity(owner) instanceof Player player
            && GunPermissions.canCarry(player)
            && (track == null || player.getUUID().equals(track.ownerUuid()))
        ? player
        : null;
  }

  public AABB predictedBounds(Entity entity) {
    CarriedEntityPrediction track = existing(entity);
    return track == null || track.phase() == CarriedEntityPrediction.Phase.VANILLA
        ? entity.getBoundingBox()
        : track.bounds(clock(0));
  }

  public boolean predicted(Entity entity) {
    CarriedEntityPrediction track = existing(entity);
    return track != null && track.phase() != CarriedEntityPrediction.Phase.VANILLA;
  }

  public void beginRenderFrame() {
    renderFrame++;
  }

  public Vec3 renderCenter(Entity entity, float partial) {
    CarriedEntityPrediction track = existing(entity);
    return track == null || track.phase() == CarriedEntityPrediction.Phase.VANILLA
        ? null
        : track.renderSample(clock(partial), partial, renderFrame);
  }

  public Quaternionf renderRotation(Entity entity, float partial) {
    CarriedEntityPrediction track = existing(entity);
    if (track == null || track.phase() == CarriedEntityPrediction.Phase.VANILLA) return null;
    track.renderSample(clock(partial), partial, renderFrame);
    return track.rotation();
  }

  private CarriedEntityPrediction existing(int id, UUID uuid) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    return entity == null || !entity.getUUID().equals(uuid) ? null : existing(entity);
  }

  private CarriedEntityPrediction existing(Entity entity) {
    var track = tracks.get(entity.getUUID());
    return track != null && track.entity() == entity && !entity.isRemoved() ? track : null;
  }

  private CarriedEntityPrediction track(int id, UUID uuid) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    if (entity == null || entity.isRemoved() || !entity.getUUID().equals(uuid)) return null;
    var previous = tracks.get(uuid);
    if (previous != null && previous.entity() == entity) return previous;
    var track = new CarriedEntityPrediction(entity, partial());
    tracks.put(uuid, track);
    return track;
  }
}
