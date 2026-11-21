package dev.portalix.portal.traversal;

import dev.portalix.api.PortalEndpoint;
import dev.portalix.compat.minecraft.EntityMotion;
import dev.portalix.compat.minecraft.ServerPlayerMotion;
import dev.portalix.network.ModNetworking;
import dev.portalix.network.portal.EntityPortalCrossingPayload;
import dev.portalix.portal.PortalLink;
import dev.portalix.portal.query.PlayerPortalVisibility;
import dev.portalix.server.EntityObservers;
import dev.portalix.server.PortalServerEvents;
import dev.portalix.sound.GunSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PortalCrossingSync {
  private static final ScopedValue<PortalEntityMovement.CrossingListener> CONTROLLER_MOVE = ScopedValue.newInstance();
  private static final Map<Entity, PassengerUpdates> passengerUpdates = new HashMap<>();
  private static PortalEntityMovement.CrossingListener clientCrossing = (entity, event) -> {};
  private static PortalEntityMovement.CompletionListener clientCompletion = (entity, result) -> {};

  private PortalCrossingSync() {}

  public static void flushPassengerUpdates() {
    passengerUpdates.values().forEach(group -> {
      group.updates.sort(Comparator.comparingInt(PassengerUpdate::depth));
      var packets = new HashMap<ServerPlayer, List<Packet<? super ClientGamePacketListener>>>();
      for (var update : group.updates)
        update.packets.forEach((observer, values) ->
            packets.computeIfAbsent(observer, ignored -> new ArrayList<>()).addAll(values));
      send(packets);
      group.after.values().forEach(Runnable::run);
    });
    passengerUpdates.clear();
  }

  public static void afterPassengerUpdates(Entity entity, Runnable action) {
    passengerUpdates.computeIfAbsent(entity.getRootVehicle(), ignored -> new PassengerUpdates())
        .after.put(entity, action);
  }

  public static void clear() {
    passengerUpdates.clear();
  }

  public static void trackingStopped(Entity entity, ServerPlayer observer) {
    var outgoing = EntityPortalState.of(entity).outgoing;
    if (outgoing == null) return;
    outgoing.observers.remove(observer.getUUID());
    outgoing.updates.remove(observer);
  }

  public static void setClientListeners(
      PortalEntityMovement.CrossingListener crossing,
      PortalEntityMovement.CompletionListener completion) {
    clientCrossing = crossing;
    clientCompletion = completion;
  }

  static void crossed(Entity entity, PortalEntityMovement.Crossing crossing) {
    if (entity.level().isClientSide()) {
      clientCrossing.crossed(entity, crossing);
      return;
    }
    if (!entity.isPassenger() && !entity.isSilent()) {
      playSound(entity.level(), crossing.link().entrance().endpoint(), GunSounds.PORTAL_ENTER);
      playSound(entity.level(), crossing.link().exit().endpoint(), GunSounds.PORTAL_EXIT);
    }
    if (entity instanceof ServerPlayer player) PortalServerEvents.cameraCrossed(player, crossing.link());
    var state = EntityPortalState.of(entity);
    if (state.outgoing == null) state.outgoing = new EntityPortalState.OutgoingCrossings();
    var outgoing = state.outgoing;
    if (outgoing.events.isEmpty()) {
      EntityObservers.tracking(entity)
          .forEach(observer -> outgoing.observers.add(observer.getUUID()));
      if (entity instanceof ServerPlayer rider && rider.isPassenger())
        outgoing.observers.add(rider.getUUID());
    }
    outgoing.events.add(crossing);
    if (CONTROLLER_MOVE.isBound()) CONTROLLER_MOVE.get().crossed(entity, crossing);
    entity.needsSync = true;
  }

  private static void playSound(Level level, PortalEndpoint portal, GunSounds sound) {
    Vec3 position = portal.center().add(portal.normal().scale(.25));
    var random = level.getRandom();
    level.playSound(null, position.x, position.y, position.z, sound.sound(), SoundSource.BLOCKS,
        sound.volume(random), sound.pitch(random));
  }

  static void completed(Entity entity, PortalEntityMovement.MoveResult result) {
    if (entity.level().isClientSide()) clientCompletion.onMovementCompleted(entity, result);
  }

  static void moveController(Entity subject, ServerPlayer player, MoverType mover, Vec3 movement) {
    var crossings = new LinkedHashMap<Entity, List<PortalEntityMovement.Crossing>>();
    PortalEntityMovement.CrossingListener collect = (member, crossing) -> {
      if (member.getRootVehicle() == subject)
        crossings.computeIfAbsent(member, ignored -> new ArrayList<>()).add(crossing);
    };
    ScopedValue.where(CONTROLLER_MOVE, collect).run(() ->
        PortalEntityMovement.moveAuthoritatively(subject, mover, movement, subject.getDeltaMovement()));
    if (crossings.isEmpty()) return;
    var packets = new ArrayList<Packet<? super ClientGamePacketListener>>(crossings.size());
    crossings.forEach((member, events) ->
        packets.add(new ClientboundCustomPayloadPacket(payload(member, events))));
    ServerPlayerMotion.rebase(subject);
    ServerPlayerMotion.synchronizePosition(player, packets);
  }

  public static boolean pending(Entity entity) {
    var outgoing = EntityPortalState.of(entity).outgoing;
    return outgoing != null && !outgoing.events.isEmpty();
  }

  public static List<PortalLink> crossingsSince(Entity entity, long event) {
    var outgoing = EntityPortalState.of(entity).outgoing;
    if (outgoing == null || outgoing.events.isEmpty()) return List.of();
    var links = new ArrayList<PortalLink>();
    for (var crossing : outgoing.events)
      if (crossing.eventId() > event) links.add(crossing.link());
    return List.copyOf(links);
  }

  public static void sendPending(Entity entity, ServerPlayer observer,
      Consumer<Packet<? super ClientGamePacketListener>> packets) {
    if (entity == observer && (!entity.isPassenger()
        || entity.getRootVehicle().getControllingPassenger() == entity)) return;
    var outgoing = EntityPortalState.of(entity).outgoing;
    if (outgoing == null || outgoing.events.isEmpty()
        || !ModNetworking.canPlayerReceive(observer, EntityPortalCrossingPayload.TYPE)) return;
    var crossings = List.copyOf(outgoing.events);
    crossings.stream().map(crossing -> crossing.link().gun()).distinct()
        .forEach(gun -> PortalServerEvents.observePortal(observer, gun));
    // The hook snapshot uses this owner's current frame, before its ordinary tracker may flush.
    packets.accept(new ClientboundCustomPayloadPacket(payload(entity, crossings)));
  }

  public static Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> beginTracking(
      Entity entity) {
    if (!pending(entity)) return null;
    EntityMotion.prepareTracking(entity);
    return new HashMap<>();
  }

  public static void collectTracking(
      Entity entity,
      Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> updates,
      Packet<? super ClientGamePacketListener> packet,
      boolean self,
      Predicate<ServerPlayer> predicate) {
    var recipients = new HashSet<>(EntityObservers.tracking(entity));
    if (self && entity instanceof ServerPlayer player) recipients.add(player);
    for (var observer : recipients) {
      if (predicate != null && !predicate.test(observer)) continue;
      var packets = updates.computeIfAbsent(observer, ignored -> new ArrayList<>());
      if (packet instanceof ClientboundBundlePacket bundle)
        bundle.subPackets().forEach(packets::add);
      else packets.add(packet);
    }
  }

  public static void sendAfterCrossing(
      Entity entity, ServerPlayer observer, CustomPacketPayload payload) {
    var outgoing = EntityPortalState.of(entity).outgoing;
    if (outgoing == null || outgoing.events.isEmpty()) {
      ModNetworking.sendToPlayer(observer, payload);
      return;
    }
    // Carry snapshots must follow the native pose and crossing that define their coordinate frame.
    outgoing.updates
        .computeIfAbsent(observer, ignored -> new ArrayList<>())
        .add(new ClientboundCustomPayloadPacket(payload));
  }

  public static void afterTracking(
      Entity entity, Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> updates) {
    var state = EntityPortalState.of(entity);
    var outgoing = state.outgoing;
    if (outgoing != null) {
      if (!outgoing.events.isEmpty()) append(entity, outgoing, updates);
      outgoing.events.clear();
      outgoing.observers.clear();
      outgoing.updates.forEach(
          (observer, packets) ->
              updates.computeIfAbsent(observer, ignored -> new ArrayList<>()).addAll(packets));
      outgoing.updates.clear();
    }
    Entity root = entity.getRootVehicle();
    if (root == entity && entity.getPassengers().isEmpty()) send(updates);
    else {
      var group = passengerUpdates.computeIfAbsent(root, ignored -> new PassengerUpdates());
      int depth = 0;
      for (Entity vehicle = entity.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) depth++;
      group.updates.add(new PassengerUpdate(depth, updates));
    }
  }

  private static void send(
      Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> updates) {
    updates.forEach(
        (observer, packets) -> observer.connection.send(new ClientboundBundlePacket(packets)));
  }

  private static void append(
      Entity entity,
      EntityPortalState.OutgoingCrossings outgoing,
      Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> updates) {
    var crossings = List.copyOf(outgoing.events);
    var packet = payload(entity, crossings);
    var observers = new HashSet<>(EntityObservers.tracking(entity));
    observers.addAll(updates.keySet());
    observers.addAll(outgoing.updates.keySet());
    if (entity instanceof ServerPlayer rider && rider.isPassenger()) observers.add(rider);
    var driver = entity.getRootVehicle().getControllingPassenger();
    for (var observer : observers) {
      if (observer == driver || observer == entity && !entity.isPassenger()) continue;
      if (!outgoing.observers.contains(observer.getUUID()))
        continue;
      if (ModNetworking.canPlayerReceive(observer, EntityPortalCrossingPayload.TYPE)) {
        crossings.stream()
            .map(crossing -> crossing.link().gun())
            .distinct()
            .forEach(gun -> PortalServerEvents.observePortal(observer, gun));
        updates
            .computeIfAbsent(observer, ignored -> new ArrayList<>())
            .addFirst(new ClientboundCustomPayloadPacket(packet));
      }
    }
  }

  private static EntityPortalCrossingPayload payload(Entity entity, List<PortalEntityMovement.Crossing> crossings) {
    return new EntityPortalCrossingPayload(
        entity.getId(),
        entity.getUUID(),
        crossings,
        entity.position(),
        entity.getDeltaMovement(),
        entity.getYRot(),
        entity.getXRot(),
        entity instanceof LivingEntity living ? living.getYHeadRot() : entity.getYRot(),
        entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot(),
        PortalPassagePose.scale(entity),
        entity instanceof ServerPlayer player ? PlayerPortalVisibility.of(player).resumeLoadingAt() : 0);
  }

  private static final class PassengerUpdates {
    final List<PassengerUpdate> updates = new ArrayList<>();
    final Map<Entity, Runnable> after = new HashMap<>();
  }

  private record PassengerUpdate(
      int depth, Map<ServerPlayer, List<Packet<? super ClientGamePacketListener>>> packets) {}
}
