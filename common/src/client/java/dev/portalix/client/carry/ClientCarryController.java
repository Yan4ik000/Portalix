package dev.portalix.client.carry;

import dev.portalix.carry.CarryBindings;
import dev.portalix.client.ClientNetworking;
import dev.portalix.client.carry.prediction.CarriedEntityPrediction;
import dev.portalix.gun.GunPermissions;
import dev.portalix.network.carry.CarryGrabPayload;
import dev.portalix.network.carry.CarryInputPayload;
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
  private long sequence;
  private long ticks;

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
    sequence = 0;
    ticks = 0;
  }

  public void tick() {
    if (client.level == null || client.player == null) return;
    ticks++;
    tracks
        .values()
        .removeIf(
            track ->
                client.level.getEntity(track.entity().getId()) != track.entity()
                    || !track.present());
    for (CarriedEntityPrediction track : tracks.values()) {
      track.tick(clock(0));
      if (track.ownerId() >= 0) {
        Entity owner = client.level.getEntity(track.ownerId());
        if (owner != null
            && (!owner.getUUID().equals(track.ownerUuid())
                || !(owner instanceof Player player)
                || !GunPermissions.canCarry(player))) track.lostOwner(clock(0), 0);
      }
      if (track.phase() != CarriedEntityPrediction.Phase.LOCAL_HOLD || !track.entity().isAlive())
        continue;
      long input = ++sequence;
      CarryInputPayload movement = track.advance(client.player, input, clock(0));
      if (movement != null && ClientNetworking.canServerReceive(CarryInputPayload.TYPE))
        ClientNetworking.sendToServer(movement);
    }
  }

  public void grab(CarryGrabPayload grab) {
    CarriedEntityPrediction track = track(grab.entityId(), grab.entityUuid());
    if (track == null || grab.generation() <= track.generation()) return;
    CarryBindings.bind(
        client.level, grab.ownerId(), grab.entityId(), grab.ownerUuid(), grab.entityUuid());
    CarryBindings.route(track.entity(), grab.route());
    float partial = partial();
    for (CarriedEntityPrediction previous : tracks.values()) {
      if (previous != track
          && previous.ownerId() == grab.ownerId()
          && grab.ownerUuid().equals(previous.ownerUuid()))
        previous.lostOwner(clock(partial), partial);
    }
    boolean local =
        client.player != null
            && client.player.getId() == grab.ownerId()
            && client.player.getUUID().equals(grab.ownerUuid());
    if (local) sequence = Math.max(sequence, grab.sequence());
    track.grab(grab, local, clock(partial), partial);
  }

  public void receive(CarryStatePayload update) {
    CarriedEntityPrediction track = track(update.entityId(), update.entityUuid());
    if (track == null) return;
    float partial = partial();
    track.update(update, clock(partial), partial);
    if (track.phase() == CarriedEntityPrediction.Phase.LOCAL_HOLD
        && track.generation() == update.generation())
      sequence = Math.max(sequence, update.sequence());
  }

  public void release(CarryReleasePayload release) {
    CarriedEntityPrediction track = track(release.entityId(), release.entityUuid());
    if (track == null) return;
    float partial = partial();
    track.release(release, clock(partial), partial);
  }

  public void release(int owner, int id) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    CarriedEntityPrediction track = entity == null ? null : tracks.get(entity.getUUID());
    if (track == null || track.ownerId() != owner) return;
    float partial = partial();
    track.lostOwner(clock(partial), partial);
  }

  public void teleport(Entity entity, Vec3 position) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    if (track != null) track.teleport(position, clock(partial()));
  }

  public void crossed(Entity entity, PortalLink link) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    if (track != null) track.crossed(link);
  }

  public void nativePosition(Entity entity, Vec3 position) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    if (track == null) return;
    float partial = partial();
    track.nativePosition(position, clock(partial), partial);
  }

  public Player owner(Entity target) {
    if (client.level == null || target.level() != client.level) return null;
    CarriedEntityPrediction track = tracks.get(target.getUUID());
    int owner = track == null ? CarryBindings.holderId(target) : track.ownerId();
    return client.level.getEntity(owner) instanceof Player player
            && GunPermissions.canCarry(player)
            && (track == null || player.getUUID().equals(track.ownerUuid()))
        ? player
        : null;
  }

  public AABB predictedBounds(Entity entity) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    return track == null ? entity.getBoundingBox() : track.bounds(clock(0));
  }

  public boolean predicted(Entity entity) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    return track != null && track.phase() != CarriedEntityPrediction.Phase.VANILLA;
  }

  public Vec3 renderCenter(Entity entity, float partial) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    return track == null || track.phase() == CarriedEntityPrediction.Phase.VANILLA
        ? null
        : track.sample(clock(partial), partial);
  }

  public Quaternionf renderRotation(Entity entity, float partial) {
    CarriedEntityPrediction track = tracks.get(entity.getUUID());
    if (track == null || track.phase() == CarriedEntityPrediction.Phase.VANILLA) return null;
    track.sample(clock(partial), partial);
    return track.rotation();
  }

  private CarriedEntityPrediction track(int id, UUID uuid) {
    Entity entity = client.level == null ? null : client.level.getEntity(id);
    if (entity == null || !entity.getUUID().equals(uuid)) return null;
    return tracks.compute(
        uuid,
        (key, track) ->
            track == null || track.entity() != entity
                ? new CarriedEntityPrediction(entity, partial())
                : track);
  }
}
